package com.sour.duoremote.session

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.MotionEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sour.duoremote.protocol.api.UuApiClient
import com.sour.duoremote.protocol.file.FileTransferCodec
import com.sour.duoremote.protocol.proto.ConnectOptions
import com.sour.duoremote.protocol.proto.ClipboardCodec
import com.sour.duoremote.protocol.proto.FeatureFlags
import com.sour.duoremote.protocol.proto.SendToRom
import com.sour.duoremote.protocol.rtc.UuRtcClient
import com.sour.duoremote.protocol.signaling.SignalingClient
import com.sour.duoremote.protocol.signaling.SignalingEndpoint
import com.sour.duoremote.protocol.signaling.SoacCodec
import com.sour.duoremote.ui.input.GamepadMapper
import com.sour.duoremote.ui.input.GamepadState
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.webrtc.DataChannel
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.PeerConnection
import org.webrtc.VideoTrack
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

enum class SessionStage {
    IDLE,
    JOINING,
    SIGNALING,
    NEGOTIATING,
    CONNECTING,
    CHANNEL_READY,
    FAILED,
}

data class StreamStats(
    val fps: Double = 0.0,
    val width: Int = 0,
    val height: Int = 0,
    val bitrateMbps: Double = 0.0,
    val rttMs: Double = 0.0,
    val packetsLost: Long = 0,
    val codec: String? = null,
    val elapsedSeconds: Long = 0,
)

/**
 * Activity-scoped orchestration for one remote-control attempt.
 *
 * M2 currently establishes the full signaling/RTC path and exposes the remote
 * video track plus data channels. The video renderer and input layer consume
 * these in the next steps.
 */
class SessionController(
    context: Context,
    private val api: UuApiClient,
    private val scope: CoroutineScope,
    private val eglContext: EglBase.Context,
) {
    var stage by mutableStateOf(SessionStage.IDLE)
        private set
    var message by mutableStateOf<String?>(null)
        private set
    var videoTrack by mutableStateOf<VideoTrack?>(null)
        private set
    var stats by mutableStateOf(StreamStats())
        private set
    var fileState by mutableStateOf(FileTransferState())
        private set
    val dataChannels = LinkedHashMap<String, DataChannel>()
    private val heldKeys = LinkedHashSet<Int>()
    private val heldMouseButtons = LinkedHashSet<Int>()
    private var statsJob: Job? = null
    private var statsStartedAt = 0L
    private var lastBytes = 0L
    private var lastFrames = 0L
    private var lastStatsAt = 0L
    private val statsLock = Any()

    private val appContext = context.applicationContext
    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        appContext.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        appContext.getSystemService(Vibrator::class.java)
    }
    private val clipboardManager: ClipboardManager? =
        appContext.getSystemService(ClipboardManager::class.java)
    private var generation = 0
    private var connectJob: Job? = null
    private var signaling: SignalingClient? = null
    private var rtc: UuRtcClient? = null
    private var roomIceServers: JsonArray? = null
    private var remoteClientId: String? = null
    private var iceId: String? = null
    private var appControlId: String? = null
    private var closing = false
    private val earlySoac = ArrayDeque<JsonObject>()
    private var protocolReady = false
    private var handshakeJob: Job? = null
    private var protocolSequence = 1L
    private val fileTransfer = FileTransferEngine(
        send = { bytes -> rtc?.sendProtobufText(UuRtcClient.TEXT_CHANNEL, bytes) == true },
        onState = { next ->
            scope.launch(Dispatchers.Main.immediate) { fileState = next }
        },
        now = { System.currentTimeMillis() },
    )

    init {
        scope.launch {
            while (isActive) {
                delay(2000)
                fileTransfer.tick()
            }
        }
    }

    private var gamepadPassthrough = true
    private var gamepadVibration = true
    private var gamepadDeadzone = GamepadState.DEADZONE
    private var invertLeftY = false
    private var invertRightY = false
    private var autoReconnect = true
    private var gamepadActive = false
    private var gamepadSuppressed = false
    private var gamepadState = GamepadState()
    private var gamepadSent: GamepadState? = null
    private var gamepadSentAt = 0L
    private var gamepadJob: Job? = null
    private var reconnectAttempts = 0
    private var reconnectJob: Job? = null
    private var disconnectJob: Job? = null
    private var lastDeviceId: String? = null
    private var lastOptions: ByteArray? = null
    private var userClosing = false
    private var rumbleAmplitude = -1
    private var clipboardSync = false
    private var viewOnly = false
    private var clipboardListening = false
    private var ignoreClipboardChange = false
    private var lastClipboardText: String? = null
    private var clipboardRequestId = 1L
    private val clipboardListener = ClipboardManager.OnPrimaryClipChangedListener {
        onLocalClipboardChanged()
    }

    fun connect(deviceId: String, options: ByteArray = ConnectOptions.encode()) {
        lastDeviceId = deviceId
        lastOptions = options
        reconnectAttempts = 0
        userClosing = false
        gamepadSuppressed = false
        reconnectJob?.cancel()
        reconnectJob = null
        startConnect()
    }

    private fun startConnect() {
        val deviceId = lastDeviceId ?: return
        stopGamepad()
        stopClipboard()
        lastClipboardText = null
        ignoreClipboardChange = false
        releaseAllInput()
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectJob?.cancel()
        disconnectJob = null
        closing = true
        generation++
        connectJob?.cancel()
        connectJob = null
        stopStats()
        protocolReady = false
        protocolSequence = 1L
        handshakeJob?.cancel()
        handshakeJob = null
        fileTransfer.reset()
        signaling?.close()
        signaling = null
        rtc?.close()
        rtc = null
        videoTrack = null
        dataChannels.clear()
        earlySoac.clear()
        remoteClientId = null
        iceId = null
        appControlId = null
        closing = false
        val run = generation
        setState(SessionStage.JOINING, "正在加入远控房间…")
        connectJob = scope.launch {
            try {
                val room = withContext(Dispatchers.IO) { api.joinRoom(deviceId) }
                if (run != generation) return@launch
                roomIceServers = room.iceServers
                val endpoint = SignalingEndpoint.fromRoom(room.raw)
                val client = SignalingClient(signalingListener(run))
                signaling = client
                setState(SessionStage.SIGNALING, "正在连接信令服务器…")
                client.open(endpoint, room.token)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (run == generation) fail(e)
            }
        }
    }

    fun disconnect() {
        userClosing = true
        reconnectAttempts = 0
        reconnectJob?.cancel()
        reconnectJob = null
        disconnectJob?.cancel()
        disconnectJob = null
        lastDeviceId = null
        lastOptions = null
        stopGamepad()
        stopClipboard()
        releaseAllInput()
        closing = true
        generation++
        connectJob?.cancel()
        connectJob = null
        stopStats()
        protocolReady = false
        protocolSequence = 1L
        handshakeJob?.cancel()
        handshakeJob = null
        fileTransfer.reset()
        signaling?.close()
        signaling = null
        rtc?.close()
        rtc = null
        videoTrack = null
        dataChannels.clear()
        earlySoac.clear()
        setState(SessionStage.IDLE, null)
    }

    /**
     * Applies settings that affect a live session. `gamepadPassthrough=false` sends a zero
     * update plus `gpd_disconnect` before returning control to the local UI.
     */
    fun configure(
        gamepadPassthrough: Boolean,
        gamepadVibration: Boolean,
        autoReconnect: Boolean,
        clipboardSync: Boolean,
        gamepadDeadzone: Float,
        invertLeftY: Boolean,
        invertRightY: Boolean,
        viewOnly: Boolean,
    ) {
        val passthroughChanged = this.gamepadPassthrough != gamepadPassthrough
        val clipboardChanged = this.clipboardSync != clipboardSync
        val viewOnlyChanged = this.viewOnly != viewOnly
        if (viewOnly && !this.viewOnly) {
            // Enabling view-only must not leave a held key/button/pad sample on the host.
            releaseAllInput()
        }
        this.gamepadPassthrough = gamepadPassthrough
        this.gamepadVibration = gamepadVibration
        this.autoReconnect = autoReconnect
        this.clipboardSync = clipboardSync
        this.viewOnly = viewOnly
        this.gamepadDeadzone = gamepadDeadzone.coerceIn(0f, 0.9f)
        this.invertLeftY = invertLeftY
        this.invertRightY = invertRightY
        if (passthroughChanged) {
            if (gamepadPassthrough && !viewOnly) startGamepad() else stopGamepad()
        }
        if (viewOnlyChanged) {
            if (viewOnly) {
                stopGamepad()
            } else if (gamepadPassthrough && !gamepadSuppressed) {
                startGamepad()
            }
        }
        if (!gamepadVibration) cancelRumble()
        if (clipboardChanged || viewOnlyChanged) {
            if (clipboardSync && !viewOnly) maybeStartClipboard() else stopClipboard()
        }
    }

    /** True while a menu/IME/settings overlay owns input: releases keys and disconnects the pad. */
    fun setInputSuppressed(suppressed: Boolean) {
        if (gamepadSuppressed == suppressed) return
        gamepadSuppressed = suppressed
        if (suppressed) {
            releaseAllInput()
            stopGamepad()
        } else if (gamepadPassthrough && !viewOnly) {
            startGamepad()
        }
    }

    fun listFiles(path: String = fileState.path) {
        if (viewOnly) return
        fileTransfer.list(path)
    }

    fun uploadFile(
        name: String,
        size: Long,
        modifiedTime: Long,
        input: InputStream,
        remoteDir: String = fileState.path,
    ) {
        if (viewOnly) {
            runCatching { input.close() }
            return
        }
        fileTransfer.upload(name, size, modifiedTime, input, remoteDir)
    }

    fun downloadFile(
        remotePath: String,
        name: String,
        size: Long,
        output: OutputStream,
    ) {
        if (viewOnly) {
            runCatching { output.close() }
            return
        }
        fileTransfer.download(remotePath, name, size, output)
    }

    fun cancelFileTransfer() {
        fileTransfer.cancel()
    }

    fun handleGamepadKey(keyCode: Int, action: Int): Boolean {
        if (viewOnly) return false
        if (GamepadMapper.controlForKey(keyCode) == null &&
            keyCode != KeyEvent.KEYCODE_BUTTON_L2 &&
            keyCode != KeyEvent.KEYCODE_BUTTON_R2
        ) {
            return false
        }
        if (action != KeyEvent.ACTION_DOWN && action != KeyEvent.ACTION_UP) return true
        gamepadState = GamepadMapper.applyKey(
            gamepadState,
            keyCode,
            action == KeyEvent.ACTION_DOWN,
        )
        return true
    }

    fun handleGamepadMotion(event: MotionEvent): Boolean {
        if (viewOnly) return false
        if (!event.isFromSource(android.view.InputDevice.SOURCE_JOYSTICK) &&
            !event.isFromSource(android.view.InputDevice.SOURCE_GAMEPAD)
        ) {
            return false
        }
        gamepadState = GamepadMapper.applyMotion(
            gamepadState,
            event,
            deadzone = gamepadDeadzone,
            invertLeftY = invertLeftY,
            invertRightY = invertRightY,
        )
        return true
    }

    fun sendControlText(text: String): Boolean = rtc?.sendControlText(text) ?: false

    fun setAudioPlayout(enabled: Boolean) {
        rtc?.setAudioPlayout(enabled)
    }

    fun mouseAbsolute(x: Float, y: Float) {
        if (viewOnly) return
        sendAction(
            "mouse_move_absolute",
            "abs_x" to x.coerceIn(0f, 1f),
            "abs_y" to y.coerceIn(0f, 1f),
        )
    }

    fun mouseRelative(dx: Float, dy: Float) {
        if (viewOnly) return
        sendAction("mouse_move_relative", "delta_x" to dx, "delta_y" to dy)
    }

    fun mouseButton(button: Int, down: Boolean) {
        if (viewOnly) return
        if (down) {
            if (heldMouseButtons.add(button)) {
                sendAction("mouse_press", "button" to button)
            }
        } else {
            if (heldMouseButtons.remove(button)) {
                sendAction("mouse_release", "button" to button)
            }
        }
    }

    fun mouseClick(button: Int) {
        if (viewOnly) return
        sendAction("mouse_click", "button" to button)
    }

    fun scroll(deltaX: Int, deltaY: Int) {
        if (viewOnly) return
        sendAction("mouse_scroll", "delta_x" to deltaX, "delta_y" to deltaY)
    }

    fun key(vk: Int, down: Boolean) {
        if (viewOnly) return
        if (down) {
            if (heldKeys.add(vk)) {
                sendAction("kbd_press", "key" to vk)
            }
        } else {
            if (heldKeys.remove(vk)) {
                sendAction("kbd_release", "key" to vk)
            }
        }
    }

    fun tapKey(vk: Int) {
        if (viewOnly) return
        key(vk, true)
        key(vk, false)
    }

    fun shortcut(keys: List<Int>) {
        if (viewOnly) return
        keys.forEach { key(it, true) }
        keys.asReversed().forEach { key(it, false) }
    }

    fun textInput(text: String) {
        if (viewOnly) return
        if (text.isEmpty()) return
        val sent = sendAction("text_input", "content" to text)
        if (!sent) {
            // CONTROL may not be open yet (or the host only supports the legacy path).
            // TEXT_DATA_CHANNEL carries Main.SendToRom{RomMsg_Text} with the official PPID 51.
            rtc?.sendProtobufText(UuRtcClient.TEXT_CHANNEL, SendToRom.encodeText(text))
        }
    }

    fun releaseAllKeys() {
        heldKeys.toList().forEach { key(it, false) }
    }

    fun releaseAllMouseButtons() {
        heldMouseButtons.toList().forEach { mouseButton(it, false) }
    }

    /** Releases keyboard, held mouse buttons, and neutralizes the pad in one call. */
    fun releaseAllInput() {
        releaseAllKeys()
        releaseAllMouseButtons()
        releaseGamepad()
    }

    /** Sends a neutral pad sample immediately; used when a page/menu takes over input. */
    fun releaseGamepad() {
        gamepadState = GamepadState()
        if (gamepadActive) {
            sendGamepadUpdate(GamepadState())
            gamepadSent = GamepadState()
            gamepadSentAt = SystemClock.elapsedRealtime()
        }
        cancelRumble()
    }

    private fun canGamepad(): Boolean =
        gamepadPassthrough &&
            !gamepadSuppressed &&
            !viewOnly &&
            rtc != null &&
            dataChannels.containsKey(UuRtcClient.CONTROL_CHANNEL)

    private fun startGamepad() {
        if (gamepadActive || !canGamepad()) return
        gamepadActive = true
        gamepadState = GamepadState()
        gamepadSent = null
        gamepadSentAt = 0L
        sendAction("gpd_connect", "index" to 0)
        gamepadJob?.cancel()
        gamepadJob = scope.launch {
            while (isActive && gamepadActive) {
                val now = SystemClock.elapsedRealtime()
                val state = gamepadState
                if (state != gamepadSent || now - gamepadSentAt >= KEEPALIVE_MS) {
                    sendGamepadUpdate(state)
                    gamepadSent = state
                    gamepadSentAt = now
                }
                delay(FRAME_MS)
            }
        }
    }

    private fun stopGamepad() {
        val wasActive = gamepadActive
        gamepadActive = false
        gamepadJob?.cancel()
        gamepadJob = null
        if (wasActive) {
            sendGamepadUpdate(GamepadState())
            sendAction("gpd_disconnect", "index" to 0)
        }
        gamepadState = GamepadState()
        gamepadSent = null
        cancelRumble()
    }

    private fun sendGamepadUpdate(state: GamepadState) {
        sendAction(
            "gpd_update",
            "index" to 0,
            "buttons" to state.buttons,
            "left_trigger" to state.leftTrigger,
            "right_trigger" to state.rightTrigger,
            "left_thumb_x" to state.leftThumbX,
            "left_thumb_y" to state.leftThumbY,
            "right_thumb_x" to state.rightThumbX,
            "right_thumb_y" to state.rightThumbY,
        )
    }

    private fun handleHostControlMessage(text: String) {
        val json = runCatching { JsonParser.parseString(text).asJsonObject }.getOrNull() ?: return
        if (json.stringOrNull("action") != "gpd_vibration") return
        val left = json.get("left_motor")?.takeIf { !it.isJsonNull }?.let {
            runCatching { it.asInt }.getOrNull()
        } ?: 0
        val right = json.get("right_motor")?.takeIf { !it.isJsonNull }?.let {
            runCatching { it.asInt }.getOrNull()
        } ?: 0
        applyRumble(maxOf(left, right).coerceIn(0, 255))
    }

    private fun applyRumble(amplitude: Int) {
        if (!gamepadVibration) return
        val motor = vibrator ?: return
        if (amplitude <= 0) {
            cancelRumble()
            return
        }
        if (amplitude == rumbleAmplitude) return
        rumbleAmplitude = amplitude
        val effect = VibrationEffect.createWaveform(
            longArrayOf(0L, 1000L),
            intArrayOf(0, amplitude.coerceAtLeast(1)),
            0,
        )
        runCatching { motor.vibrate(effect) }
    }

    private fun cancelRumble() {
        if (rumbleAmplitude < 0) return
        rumbleAmplitude = -1
        runCatching { vibrator?.cancel() }
    }

    private fun maybeStartClipboard() {
        if (!clipboardSync || viewOnly || clipboardListening) return
        val manager = clipboardManager ?: return
        if (!dataChannels.containsKey(UuRtcClient.TEXT_CHANNEL)) return
        clipboardListening = true
        runCatching { manager.addPrimaryClipChangedListener(clipboardListener) }
    }

    private fun stopClipboard() {
        if (!clipboardListening) return
        clipboardListening = false
        runCatching { clipboardManager?.removePrimaryClipChangedListener(clipboardListener) }
    }

    private fun onLocalClipboardChanged() {
        if (!clipboardSync || viewOnly) return
        if (ignoreClipboardChange) {
            ignoreClipboardChange = false
            return
        }
        val text = runCatching {
            clipboardManager?.primaryClip
                ?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)
                ?.coerceToText(appContext)
                ?.toString()
        }.getOrNull() ?: return
        if (text.isEmpty() || text.length > ClipboardCodec.MAX_TEXT_LENGTH) return
        if (text == lastClipboardText) return
        lastClipboardText = text
        rtc?.sendProtobufText(
            UuRtcClient.TEXT_CHANNEL,
            ClipboardCodec.encodeTextRequest(clipboardRequestId++, text),
        )
    }

    private fun onRemoteClipboard(bytes: ByteArray) {
        if (!clipboardSync || viewOnly) return
        val incoming = runCatching { ClipboardCodec.parseIncomingText(bytes) }.getOrNull() ?: return
        val manager = clipboardManager ?: return
        if (incoming.text != lastClipboardText) {
            ignoreClipboardChange = true
            runCatching {
                manager.setPrimaryClip(ClipData.newPlainText("DuoRemote", incoming.text))
            }
            lastClipboardText = incoming.text
            scope.launch {
                delay(500)
                ignoreClipboardChange = false
            }
        }
        rtc?.sendProtobufText(
            UuRtcClient.TEXT_CHANNEL,
            ClipboardCodec.encodeTextResponse(incoming.id),
        )
    }

    private fun sendAction(action: String, vararg fields: Pair<String, Any>): Boolean {
        val payload = JsonObject().apply {
            addProperty("action", action)
            fields.forEach { (name, value) ->
                when (value) {
                    is Int -> addProperty(name, value)
                    is Long -> addProperty(name, value)
                    is Float -> addProperty(name, value)
                    is Double -> addProperty(name, value)
                    is Boolean -> addProperty(name, value)
                    else -> addProperty(name, value.toString())
                }
            }
        }
        return rtc?.sendControlText(payload.toString()) ?: false
    }

    private fun signalingListener(run: Int) = object : SignalingClient.Listener {
        override fun onSignalingReady() {
            if (run != generation) return
            setState(SessionStage.NEGOTIATING, "正在申请远控权限…")
        }

        override fun onRoomInfo(room: JsonObject) {
            if (run != generation) return
            val publisher = room.get("publisher")
            val clientId = when {
                publisher == null || publisher.isJsonNull -> null
                publisher.isJsonObject -> publisher.asJsonObject.stringOrNull("client_id")
                    ?: publisher.asJsonObject.stringOrNull("id")
                publisher.isJsonPrimitive -> runCatching { publisher.asString }.getOrNull()
                else -> null
            }
            if (clientId.isNullOrBlank()) {
                fail(IllegalStateException("房间内没有在线电脑"), retryable = false)
                return
            }
            remoteClientId = clientId
            val controlId = UUID.randomUUID().toString()
            appControlId = controlId
            signaling?.requestControl(lastOptions ?: ConnectOptions.encode(), controlId)
        }

        override fun onControlGranted(grant: SignalingClient.ControlGrant) {
            if (run != generation) return
            val clientId = remoteClientId ?: grant.clientId
            remoteClientId = grant.clientId.ifBlank { clientId }
            iceId = grant.iceId
            val peer = UuRtcClient(appContext, eglContext, rtcListener(run))
            rtc = peer
            setState(SessionStage.CONNECTING, "正在建立音视频通道…")
            peer.start(grant, roomIceServers)
            peer.setAudioPlayout(true)
            while (earlySoac.isNotEmpty()) {
                peer.handleSignal(earlySoac.removeFirst())
            }
        }

        override fun onSoac(payload: JsonObject) {
            if (run != generation) return
            val peer = rtc
            if (peer != null) {
                peer.handleSignal(payload)
            } else if (earlySoac.size < 128) {
                earlySoac.addLast(payload)
            }
        }

        override fun onReleased(reason: String) {
            if (run != generation) return
            fail(IllegalStateException(reason), retryable = false)
        }

        override fun onFailure(error: Throwable) {
            if (run != generation) return
            fail(error)
        }

        override fun onClosed() {
            if (run != generation || closing) return
            if (stage == SessionStage.CHANNEL_READY) {
                fail(IllegalStateException("信令连接已断开"))
            } else {
                fail(IllegalStateException("信令连接已关闭"))
            }
        }
    }

    private fun rtcListener(run: Int) = object : UuRtcClient.Listener {
        override fun onLocalOffer(sdp: String) {
            if (run != generation) return
            val clientId = remoteClientId ?: return
            val ice = iceId ?: return
            val control = appControlId ?: return
            signaling?.sendSoac(SoacCodec.description(clientId, "offer", sdp, ice, control))
        }

        override fun onLocalAnswer(sdp: String) {
            if (run != generation) return
            val clientId = remoteClientId ?: return
            val ice = iceId ?: return
            val control = appControlId ?: return
            signaling?.sendSoac(SoacCodec.description(clientId, "answer", sdp, ice, control))
        }

        override fun onLocalCandidate(candidate: IceCandidate) {
            if (run != generation) return
            val clientId = remoteClientId ?: return
            val ice = iceId ?: return
            val control = appControlId ?: return
            signaling?.sendSoac(
                SoacCodec.candidate(
                    clientId,
                    SoacCodec.candidateInit(candidate.sdp, candidate.sdpMid, candidate.sdpMLineIndex),
                    ice,
                    control,
                ),
            )
        }

        override fun onRemoteVideoTrack(track: VideoTrack) {
            if (run != generation) return
            videoTrack = track
            if (stage != SessionStage.FAILED) {
                reconnectAttempts = 0
                reconnectJob?.cancel()
                reconnectJob = null
                setState(SessionStage.CHANNEL_READY, "串流通道已建立")
                startStats()
                startGamepad()
            }
        }

        override fun onDataChannel(label: String, channel: DataChannel) {
            if (run != generation) return
            dataChannels[label] = channel
            if (label == UuRtcClient.CONTROL_CHANNEL && stage != SessionStage.FAILED) {
                reconnectAttempts = 0
                reconnectJob?.cancel()
                reconnectJob = null
                setState(SessionStage.CHANNEL_READY, "串流通道已建立")
                startStats()
                startGamepad()
            }
            if (label == UuRtcClient.CONTROL_CHANNEL || label == UuRtcClient.TEXT_CHANNEL) {
                maybeStartHandshake()
            }
            if (label == UuRtcClient.TEXT_CHANNEL) {
                maybeStartClipboard()
            }
        }

        override fun onConnectionState(state: PeerConnection.PeerConnectionState) {
            if (run != generation) return
            when (state) {
                PeerConnection.PeerConnectionState.CONNECTED -> {
                    disconnectJob?.cancel()
                    disconnectJob = null
                    setState(SessionStage.CHANNEL_READY, "串流通道已建立，视频渲染待接入")
                    startGamepad()
                    maybeStartHandshake()
                }

                PeerConnection.PeerConnectionState.FAILED -> {
                    fail(IllegalStateException("无法建立 WebRTC 连接，请检查电脑在线与网络"))
                }

                PeerConnection.PeerConnectionState.DISCONNECTED -> {
                    if (stage == SessionStage.CHANNEL_READY) {
                        setState(SessionStage.CONNECTING, "网络已中断，正在等待恢复…")
                        stopGamepad()
                        releaseAllInput()
                        disconnectJob?.cancel()
                        disconnectJob = scope.launch {
                            delay(DISCONNECT_GRACE_MS)
                            if (stage == SessionStage.CONNECTING) {
                                fail(IllegalStateException("网络连接已断开，正在重试"))
                            }
                        }
                    }
                }

                else -> Unit
            }
        }

        override fun onIceConnectionState(state: PeerConnection.IceConnectionState) {
            if (run != generation) return
            if (state == PeerConnection.IceConnectionState.COMPLETED &&
                stage != SessionStage.FAILED
            ) {
                disconnectJob?.cancel()
                disconnectJob = null
                setState(SessionStage.CHANNEL_READY, "串流通道已建立")
                startGamepad()
            } else if (state == PeerConnection.IceConnectionState.CONNECTED &&
                stage == SessionStage.CONNECTING
            ) {
                setState(SessionStage.CONNECTING, "网络通道已就绪，正在协商加密…")
            }
        }

        override fun onFailure(error: Throwable) {
            if (run != generation) return
            fail(error)
        }

        override fun onControlMessage(text: String) {
            if (run != generation) return
            handleHostControlMessage(text)
        }

        override fun onTextMessage(bytes: ByteArray) {
            if (run != generation) return
            if (handleProtocolMessage(bytes)) return
            onRemoteClipboard(bytes)
        }
    }

    private fun handleProtocolMessage(bytes: ByteArray): Boolean {
        val incoming = runCatching { FileTransferCodec.parseMain(bytes) }.getOrNull() ?: return false
        return when (incoming) {
            is FileTransferCodec.Incoming.Action -> {
                if (incoming.action == 0) {
                    val sent = rtc?.sendBinary(
                        UuRtcClient.CONTROL_CHANNEL,
                        FileTransferCodec.encodeAction(
                            seq = incoming.seq,
                            timestamp = incoming.timestamp,
                            action = 1,
                            flags = FeatureFlags(),
                        ),
                    ) == true
                    if (sent) {
                        protocolReady = true
                        handshakeJob?.cancel()
                        handshakeJob = null
                    }
                } else if (incoming.action == 1) {
                    protocolReady = true
                    handshakeJob?.cancel()
                    handshakeJob = null
                }
                true
            }

            is FileTransferCodec.Incoming.Request,
            is FileTransferCodec.Incoming.Response,
            -> {
                fileTransfer.onMessage(bytes)
                true
            }

            FileTransferCodec.Incoming.Other -> false
        }
    }

    private fun maybeStartHandshake() {
        if (protocolReady || handshakeJob?.isActive == true) return
        if (!dataChannels.containsKey(UuRtcClient.CONTROL_CHANNEL) ||
            !dataChannels.containsKey(UuRtcClient.TEXT_CHANNEL)
        ) {
            return
        }
        handshakeJob = scope.launch {
            repeat(HANDSHAKE_ATTEMPTS) {
                if (protocolReady) return@launch
                val sent = rtc?.sendBinary(
                    UuRtcClient.CONTROL_CHANNEL,
                    FileTransferCodec.encodeAction(
                        seq = protocolSequence++,
                        timestamp = System.currentTimeMillis(),
                        action = 0,
                    ),
                ) == true
                if (!sent) return@launch
                delay(500)
            }
        }
    }

    private fun startStats() {
        if (statsJob?.isActive == true) return
        statsStartedAt = SystemClock.elapsedRealtime()
        synchronized(statsLock) {
            lastBytes = 0L
            lastFrames = 0L
            lastStatsAt = 0L
        }
        statsJob = scope.launch {
            while (isActive) {
                rtc?.collectStats { snapshot ->
                    val now = SystemClock.elapsedRealtime()
                    val bitrate: Double
                    val fps: Double
                    synchronized(statsLock) {
                        val elapsed = if (lastStatsAt == 0L) 0L else (now - lastStatsAt).coerceAtLeast(1L)
                        bitrate = if (lastStatsAt == 0L) 0.0 else {
                            (snapshot.bytesReceived - lastBytes).coerceAtLeast(0L) *
                                8.0 / elapsed / 1000.0
                        }
                        fps = if (lastStatsAt == 0L) 0.0 else {
                            (snapshot.framesDecoded - lastFrames).coerceAtLeast(0L) *
                                1000.0 / elapsed
                        }
                        lastBytes = snapshot.bytesReceived
                        lastFrames = snapshot.framesDecoded
                        lastStatsAt = now
                    }
                    val elapsedSeconds = (now - statsStartedAt).coerceAtLeast(0L) / 1000L
                    scope.launch(Dispatchers.Main.immediate) {
                        stats = StreamStats(
                            fps = fps,
                            width = snapshot.width,
                            height = snapshot.height,
                            bitrateMbps = bitrate,
                            rttMs = snapshot.rttMs,
                            packetsLost = snapshot.packetsLost,
                            codec = snapshot.codec,
                            elapsedSeconds = elapsedSeconds,
                        )
                    }
                }
                delay(1000)
            }
        }
    }

    private fun stopStats() {
        statsJob?.cancel()
        statsJob = null
        stats = StreamStats()
    }

    private fun setState(next: SessionStage, text: String?) {
        scope.launch(Dispatchers.Main.immediate) {
            stage = next
            message = text
        }
    }

    private fun fail(error: Throwable, retryable: Boolean = true) {
        if (stage == SessionStage.FAILED) return
        // Invalidate callbacks from the failed peer before scheduling a retry, otherwise a second
        // FAILED callback during the backoff window consumes another reconnect attempt.
        generation++
        releaseAllInput()
        stopGamepad()
        stopClipboard()
        disconnectJob?.cancel()
        disconnectJob = null
        closing = true
        stopStats()
        protocolReady = false
        protocolSequence = 1L
        handshakeJob?.cancel()
        handshakeJob = null
        fileTransfer.reset()
        signaling?.close()
        signaling = null
        rtc?.close()
        rtc = null
        videoTrack = null
        dataChannels.clear()

        val deviceId = lastDeviceId
        if (retryable && autoReconnect && !userClosing && deviceId != null &&
            reconnectAttempts < MAX_RECONNECT
        ) {
            reconnectAttempts++
            val waitMs = 1000L shl reconnectAttempts
            setState(
                SessionStage.CONNECTING,
                "连接中断，${waitMs / 1000} 秒后重试（$reconnectAttempts/$MAX_RECONNECT）…",
            )
            reconnectJob?.cancel()
            reconnectJob = scope.launch {
                delay(waitMs)
                if (!userClosing && lastDeviceId == deviceId) startConnect()
            }
        } else {
            setState(SessionStage.FAILED, error.message ?: "连接失败")
        }
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        const val FRAME_MS = 16L
        const val KEEPALIVE_MS = 500L
        const val DISCONNECT_GRACE_MS = 5000L
        const val MAX_RECONNECT = 3
        const val HANDSHAKE_ATTEMPTS = 12
    }
}
