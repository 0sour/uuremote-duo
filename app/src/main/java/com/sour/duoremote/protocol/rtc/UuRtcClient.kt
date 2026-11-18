package com.sour.duoremote.protocol.rtc

import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.sour.duoremote.protocol.signaling.SignalingClient
import org.webrtc.DataChannel
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RtpCapabilities
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Receive-only WebRTC peer for the UU controller role.
 *
 * The controller creates the offer, five ordered data channels, and recvonly
 * video/audio transceivers. SDP/ICE are exchanged through SignalingClient.
 * The renderer owns the EglBase and attaches itself to the remote video track.
 */
class UuRtcClient(
    context: Context,
    private val eglContext: EglBase.Context,
    private val listener: Listener,
) {

    interface Listener {
        fun onLocalOffer(sdp: String)
        fun onLocalAnswer(sdp: String)
        fun onLocalCandidate(candidate: IceCandidate)
        fun onRemoteVideoTrack(track: VideoTrack)
        fun onDataChannel(label: String, channel: DataChannel)
        fun onConnectionState(state: PeerConnection.PeerConnectionState)
        fun onIceConnectionState(state: PeerConnection.IceConnectionState)
        fun onFailure(error: Throwable)

        /** Text frame received on the CONTROL channel (host -> controller, e.g. gpd_vibration). */
        fun onControlMessage(text: String) = Unit

        /** Binary frame received on TEXT_DATA_CHANNEL (clipboard / legacy ROM messages). */
        fun onTextMessage(bytes: ByteArray) = Unit
    }

    private val appContext = context.applicationContext
    private val executor = Executors.newSingleThreadExecutor()
    private val closed = AtomicBoolean(false)
    private val pendingCandidates = ArrayDeque<IceCandidate>()
    private var factory: PeerConnectionFactory? = null
    private var peer: PeerConnection? = null
    private var iceId: String = ""
    private var remoteDescriptionSet = false
    private val channels = LinkedHashMap<String, DataChannel>()

    fun start(grant: SignalingClient.ControlGrant, roomIceServers: JsonArray?) {
        executor.execute {
            if (closed.get()) return@execute
            try {
                startInternal(grant, grant.iceServers ?: roomIceServers)
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    /** Apply a raw soac payload: {"client_id":..., "data":{...}} */
    fun handleSignal(payload: JsonObject) {
        executor.execute {
            if (closed.get()) return@execute
            try {
                applySignal(payload)
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    fun setAudioPlayout(enabled: Boolean) {
        executor.execute {
            peer?.setAudioPlayout(enabled)
        }
    }

    fun collectStats(callback: (RtcStatsSnapshot) -> Unit) {
        executor.execute {
            val connection = peer ?: return@execute
            connection.getStats { report ->
                val stats = report.statsMap
                val video = stats.values.firstOrNull {
                    it.type == "inbound-rtp" &&
                        (it.members["kind"] == "video" || it.members["mediaType"] == "video")
                }
                val members = video?.members.orEmpty()
                val pair = stats.values.firstOrNull {
                    it.type == "candidate-pair" &&
                        (it.members["state"] == "succeeded" || it.members["nominated"] == true)
                }
                val codecId = members["codecId"] as? String
                val codec = codecId?.let { stats[it] }
                    ?.members
                    ?.get("mimeType") as? String
                callback(
                    RtcStatsSnapshot(
                        fps = (members["framesPerSecond"] as? Number)?.toDouble() ?: 0.0,
                        width = (members["frameWidth"] as? Number)?.toInt() ?: 0,
                        height = (members["frameHeight"] as? Number)?.toInt() ?: 0,
                        bytesReceived = (members["bytesReceived"] as? Number)?.toLong() ?: 0L,
                        packetsLost = (members["packetsLost"] as? Number)?.toLong() ?: 0L,
                        framesDecoded = (members["framesDecoded"] as? Number)?.toLong() ?: 0L,
                        rttMs = ((pair?.members?.get("currentRoundTripTime") as? Number)?.toDouble()
                            ?: 0.0) * 1000.0,
                        codec = codec,
                    ),
                )
            }
        }
    }

    data class RtcStatsSnapshot(
        val fps: Double,
        val width: Int,
        val height: Int,
        val bytesReceived: Long,
        val packetsLost: Long,
        val framesDecoded: Long = 0L,
        val rttMs: Double,
        val codec: String?,
    )

    fun sendControl(bytes: ByteArray): Boolean {
        val channel = channels[CONTROL_CHANNEL] ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(bytes), true))
    }

    fun sendControlText(text: String): Boolean {
        return sendControl(text.toByteArray(Charsets.UTF_8))
    }

    /** Sends a binary payload on a named data channel (used by the SendToRom fallback). */
    fun sendBinary(label: String, bytes: ByteArray): Boolean {
        val channel = channels[label] ?: return false
        if (channel.state() != DataChannel.State.OPEN) return false
        return channel.send(DataChannel.Buffer(java.nio.ByteBuffer.wrap(bytes), true))
    }

    fun close() {
        if (!closed.compareAndSet(false, true)) return
        executor.execute {
            channels.values.forEach {
                runCatching { it.unregisterObserver() }
                runCatching { it.close() }
                runCatching { it.dispose() }
            }
            channels.clear()
            pendingCandidates.clear()
            peer?.let {
                runCatching { it.close() }
                runCatching { it.dispose() }
            }
            peer = null
            factory?.let { runCatching { it.dispose() } }
            factory = null
        }
        executor.shutdown()
    }

    private fun startInternal(grant: SignalingClient.ControlGrant, roomIceServers: JsonArray?) {
        ensureFactory()
        iceId = grant.iceId
        val config = PeerConnection.RTCConfiguration(parseIceServers(grant.iceServers ?: roomIceServers))
        config.sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
        config.bundlePolicy = PeerConnection.BundlePolicy.MAXBUNDLE
        config.iceTransportsType = if (grant.forceRelay) {
            PeerConnection.IceTransportsType.RELAY
        } else {
            PeerConnection.IceTransportsType.ALL
        }
        val connection = factory?.createPeerConnection(config, observer)
            ?: throw IllegalStateException("无法创建 WebRTC PeerConnection")
        peer = connection
        // The probe stage has no speaker/mute UI yet, so keep remote audio silent
        // until the streaming surface owns audio lifecycle.
        connection.setAudioPlayout(false)
        connection.setAudioRecording(false)

        val video = connection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )
        val h264 = factory?.getRtpReceiverCapabilities(MediaStreamTrack.MediaType.MEDIA_TYPE_VIDEO)
            ?.codecs
            ?.filter { it.mimeType.equals("video/H264", ignoreCase = true) }
            .orEmpty()
        if (h264.isNotEmpty()) {
            runCatching { video.setCodecPreferences(h264) }
        }
        connection.addTransceiver(
            MediaStreamTrack.MediaType.MEDIA_TYPE_AUDIO,
            RtpTransceiver.RtpTransceiverInit(RtpTransceiver.RtpTransceiverDirection.RECV_ONLY),
        )

        CHANNELS.forEach { label ->
            val channel = connection.createDataChannel(
                label,
                DataChannel.Init().apply { ordered = true },
            )
            channel.registerObserver(dataChannelObserver(label))
            channels[label] = channel
        }

        connection.createOffer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                connection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) = Unit
                    override fun onSetSuccess() {
                        listener.onLocalOffer(connection.localDescription?.description ?: sdp.description)
                    }

                    override fun onCreateFailure(error: String) = fail(IllegalStateException(error))
                    override fun onSetFailure(error: String) = fail(IllegalStateException(error))
                }, sdp)
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = fail(IllegalStateException(error))
            override fun onSetFailure(error: String) = fail(IllegalStateException(error))
        }, MediaConstraints())
    }

    private fun applySignal(payload: JsonObject) {
        val data = payload.getAsJsonObject("data") ?: return
        val signalIceId = data.stringOrNull("ice_id")
        if (signalIceId != null && signalIceId != iceId) return
        when (data.stringOrNull("type")) {
            "answer" -> {
                val sdp = data.stringOrNull("sdp") ?: return
                setRemote(SessionDescription(SessionDescription.Type.ANSWER, sdp), answer = true)
            }

            "offer", "restart_ice" -> {
                val sdp = data.stringOrNull("sdp") ?: return
                setRemote(SessionDescription(SessionDescription.Type.OFFER, sdp), answer = false)
            }

            "candidate" -> {
                val source = data.getAsJsonObject("candidate") ?: data
                val candidate = source.stringOrNull("candidate") ?: return
                if (candidate.isBlank()) return
                val remote = IceCandidate(
                    source.stringOrNull("sdpMid") ?: "0",
                    source.get("sdpMLineIndex")?.takeIf { !it.isJsonNull }?.asInt ?: 0,
                    candidate,
                )
                if (remoteDescriptionSet) {
                    peer?.addIceCandidate(remote)
                } else {
                    if (pendingCandidates.size >= MAX_PENDING_CANDIDATES) {
                        throw IllegalStateException("远端 ICE 候选过多")
                    }
                    pendingCandidates.addLast(remote)
                }
            }
        }
    }

    private fun setRemote(description: SessionDescription, answer: Boolean) {
        val connection = peer ?: return
        connection.setRemoteDescription(object : SdpObserver {
            override fun onCreateSuccess(description: SessionDescription) = Unit
            override fun onSetSuccess() {
                remoteDescriptionSet = true
                while (pendingCandidates.isNotEmpty()) {
                    connection.addIceCandidate(pendingCandidates.removeFirst())
                }
                if (!answer) createAnswer(connection)
            }

            override fun onCreateFailure(error: String) = fail(IllegalStateException(error))
            override fun onSetFailure(error: String) = fail(IllegalStateException(error))
        }, description)
    }

    private fun createAnswer(connection: PeerConnection) {
        connection.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(sdp: SessionDescription) {
                connection.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(description: SessionDescription) = Unit
                    override fun onSetSuccess() {
                        listener.onLocalAnswer(connection.localDescription?.description ?: sdp.description)
                    }

                    override fun onCreateFailure(error: String) = fail(IllegalStateException(error))
                    override fun onSetFailure(error: String) = fail(IllegalStateException(error))
                }, sdp)
            }

            override fun onSetSuccess() = Unit
            override fun onCreateFailure(error: String) = fail(IllegalStateException(error))
            override fun onSetFailure(error: String) = fail(IllegalStateException(error))
        }, MediaConstraints())
    }

    private fun ensureFactory() {
        if (factory != null) return
        synchronized(FACTORY_LOCK) {
            if (!initialized.get()) {
                PeerConnectionFactory.initialize(
                    PeerConnectionFactory.InitializationOptions.builder(appContext)
                        .setEnableInternalTracer(false)
                        .createInitializationOptions(),
                )
                initialized.set(true)
            }
        }
        val audio = JavaAudioDeviceModule.builder(appContext)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()
        factory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(audio)
            .setVideoDecoderFactory(DefaultVideoDecoderFactory(eglContext))
            .setVideoEncoderFactory(DefaultVideoEncoderFactory(eglContext, true, true))
            .createPeerConnectionFactory()
    }

    private fun parseIceServers(value: JsonArray?): List<PeerConnection.IceServer> {
        if (value == null) return emptyList()
        val result = ArrayList<PeerConnection.IceServer>()
        value.forEach { element ->
            if (!element.isJsonObject) return@forEach
            val server = element.asJsonObject
            val urls = when (val raw = server.get("urls") ?: server.get("url")) {
                null -> return@forEach
                is com.google.gson.JsonArray -> raw.mapNotNull { it.takeIf { e -> e.isJsonPrimitive }?.asString }
                else -> listOfNotNull(raw.takeIf { it.isJsonPrimitive }?.asString)
            }
            val username = server.stringOrNull("username")
            val credential = server.stringOrNull("credential") ?: server.stringOrNull("password")
            urls.filter { it.isNotBlank() }.forEach { url ->
                val builder = PeerConnection.IceServer.builder(url)
                if (username != null) builder.setUsername(username)
                if (credential != null) builder.setPassword(credential)
                result += builder.createIceServer()
            }
        }
        return result
    }

    private fun dataChannelObserver(label: String): DataChannel.Observer = object : DataChannel.Observer {
        override fun onBufferedAmountChange(previousAmount: Long) = Unit
        override fun onStateChange() {
            val channel = channels[label] ?: return
            if (channel.state() == DataChannel.State.OPEN) listener.onDataChannel(label, channel)
        }

        override fun onMessage(buffer: DataChannel.Buffer) {
            val data = buffer.data
            if (!data.hasRemaining()) return
            val bytes = ByteArray(data.remaining())
            data.get(bytes)
            when {
                label == CONTROL_CHANNEL && !buffer.binary ->
                    listener.onControlMessage(String(bytes, Charsets.UTF_8))

                label == TEXT_CHANNEL && buffer.binary ->
                    listener.onTextMessage(bytes)
            }
        }
    }

    private val observer = object : PeerConnection.Observer {
        override fun onSignalingChange(state: PeerConnection.SignalingState) = Unit
        override fun onIceConnectionChange(state: PeerConnection.IceConnectionState) {
            listener.onIceConnectionState(state)
        }

        override fun onIceConnectionReceivingChange(receiving: Boolean) = Unit
        override fun onIceGatheringChange(state: PeerConnection.IceGatheringState) = Unit
        override fun onIceCandidate(candidate: IceCandidate) {
            listener.onLocalCandidate(candidate)
        }

        override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>) = Unit
        override fun onAddStream(stream: org.webrtc.MediaStream) = Unit
        override fun onRemoveStream(stream: org.webrtc.MediaStream) = Unit

        override fun onDataChannel(channel: DataChannel) {
            val label = channel.label()
            channels[label] = channel
            channel.registerObserver(dataChannelObserver(label))
        }

        override fun onRenegotiationNeeded() = Unit

        override fun onTrack(transceiver: RtpTransceiver) {
            val track = transceiver.receiver.track()
            if (track is VideoTrack) listener.onRemoteVideoTrack(track)
        }

        override fun onConnectionChange(state: PeerConnection.PeerConnectionState) {
            listener.onConnectionState(state)
        }
    }

    private fun fail(error: Throwable) {
        if (closed.get()) return
        listener.onFailure(error)
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    companion object {
        const val CONTROL_CHANNEL = "CONTROL_DATA_CHANNEL"
        const val TEXT_CHANNEL = "TEXT_DATA_CHANNEL"
        private const val MAX_PENDING_CANDIDATES = 128
        private val CHANNELS = listOf(
            CONTROL_CHANNEL,
            TEXT_CHANNEL,
            "STREAMER_DATA_CHANNEL",
            "FILE_DATA_CHANNEL",
            "BINARY_DATA_CHANNEL",
        )
        private val FACTORY_LOCK = Any()
        private val initialized = AtomicBoolean(false)
    }
}
