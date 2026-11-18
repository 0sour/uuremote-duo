package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Engine.IO v4 + Socket.IO transport for UU signaling.
 *
 * The client owns handshake, ping/pong, ack ids, binary attachments and the
 * room_info/control/soac event flow. It never logs the room token, SDP or ICE.
 */
class SignalingClient(
    private val listener: Listener,
    private val okHttp: OkHttpClient = defaultClient(),
) {

    interface Listener {
        fun onSignalingReady()
        fun onRoomInfo(room: JsonObject)
        fun onControlGranted(grant: ControlGrant)
        fun onSoac(payload: JsonObject)
        fun onReleased(reason: String)
        fun onFailure(error: Throwable)
        fun onClosed()
    }

    data class ControlGrant(
        val clientId: String,
        val iceId: String,
        val iceServers: JsonArray?,
        val forceRelay: Boolean,
        val streamerData: String?,
        val raw: JsonObject,
    )

    private data class PendingAttachment(
        val packet: SocketIoPacket,
        val remaining: Int,
        val buffers: MutableList<ByteArray>,
    )

    private val nextAckId = AtomicInteger(1)
    private val pendingAcks = ConcurrentHashMap<Int, String>()
    private val failed = AtomicBoolean(false)
    private val closing = AtomicBoolean(false)
    private var webSocket: WebSocket? = null
    private var pendingAttachment: PendingAttachment? = null

    fun open(endpoint: SignalingEndpoint, token: String) {
        require(token.isNotBlank()) { "信令凭据为空" }
        if (webSocket != null) throw IllegalStateException("信令连接已存在")
        failed.set(false)
        closing.set(false)
        val request = Request.Builder()
            .url(endpoint.url)
            .header("X-NRD-AUTH", token)
            .header("X-NRD-CONTROLLING", "0")
            .header("streamer_version", STREAMER_VERSION)
            .build()
        webSocket = okHttp.newWebSocket(request, SocketListener())
    }

    /**
     * Request control with the 0x04-prefixed ConnectOptions protobuf attachment.
     * Must be called after [Listener.onRoomInfo].
     */
    fun requestControl(connectOptions: ByteArray, appControlId: String = UUID.randomUUID().toString()) {
        val socket = webSocket ?: return
        val payload = JsonObject().apply {
            add("app_data", JsonObject().apply {
                addProperty("_placeholder", true)
                addProperty("num", 0)
            })
            addProperty("streamer_data", "")
            addProperty("app_control_id", appControlId)
        }
        val ackId = nextAckId.getAndIncrement()
        pendingAcks[ackId] = "control"
        // A binary attachment must be announced with a type-5 event ("451-<id>…"),
        // not a plain "42" event plus a stray binary frame.
        socket.send(SocketIoCodec.eventWithAttachment("control", payload, ackId))
        val attachment = ByteArray(connectOptions.size + 1)
        attachment[0] = 0x04
        connectOptions.copyInto(attachment, destinationOffset = 1)
        socket.send(ByteString.of(*attachment))
    }

    fun sendSoac(payload: JsonObject) {
        sendEvent("soac", payload)
    }

    fun close() {
        if (!closing.compareAndSet(false, true)) return
        pendingAcks.clear()
        pendingAttachment = null
        val socket = webSocket
        if (socket != null) {
            socket.send(SocketIoCodec.event("leave"))
            socket.send("41")
            socket.send("1")
            socket.close(1000, "client close")
        }
        webSocket = null
    }

    private fun sendEvent(event: String, payload: JsonElement?, ackKey: String? = null): Int? {
        val socket = webSocket ?: return null
        val ackId = ackKey?.let {
            val id = nextAckId.getAndIncrement()
            pendingAcks[id] = it
            id
        }
        socket.send(SocketIoCodec.event(event, payload, ackId))
        return ackId
    }

    private fun handleText(text: String) {
        if (text.isEmpty()) return
        when (text[0]) {
            '0' -> {
                val hello = runCatching { JsonParser.parseString(text.substring(1)).asJsonObject }.getOrNull()
                if (hello == null) {
                    fail(IllegalStateException("Engine.IO 握手格式错误"))
                    return
                }
                webSocket?.send("40")
            }

            '1' -> fail(IOException("UU 已关闭信令连接"))
            '2' -> webSocket?.send("3" + text.substring(1))
            '4' -> handleSocketIo(text)
            else -> Unit
        }
    }

    private fun handleSocketIo(text: String) {
        val packet = try {
            SocketIoCodec.parse(text)
        } catch (e: Exception) {
            fail(e)
            return
        }

        if (pendingAttachment != null && packet.type != 5 && packet.type != 6) {
            fail(IllegalStateException("信令附件交错"))
            return
        }

        when (packet.type) {
            0 -> {
                listener.onSignalingReady()
                sendEvent("room_info", null, ackKey = "room_info")
            }

            1 -> fail(IllegalStateException("UU 信令已断开"))
            4 -> {
                val reason = (packet.data as? JsonObject)
                    ?.get("message")
                    ?.takeIf { !it.isJsonNull }
                    ?.let { runCatching { it.asString }.getOrNull() }
                fail(IllegalStateException(reason ?: "UU 信令拒绝连接"))
            }

            2 -> handleEvent(packet)

            3 -> handleAck(packet)

            5 -> {
                if (packet.attachments <= 0) {
                    fail(IllegalStateException("信令事件缺少附件"))
                    return
                }
                pendingAttachment = PendingAttachment(
                    packet = packet,
                    remaining = packet.attachments,
                    buffers = ArrayList(packet.attachments),
                )
            }

            6 -> {
                if (packet.attachments <= 0) handleAck(packet)
                else pendingAttachment = PendingAttachment(
                    packet = packet,
                    remaining = packet.attachments,
                    buffers = ArrayList(packet.attachments),
                )
            }
        }
    }

    private fun handleBinary(bytes: ByteString) {
        val pending = pendingAttachment ?: run {
            fail(IllegalStateException("意外的信令二进制帧"))
            return
        }
        val data = bytes.toByteArray()
        if (data.isEmpty()) {
            fail(IllegalStateException("信令二进制帧为空"))
            return
        }
        // UU's native controller prefixes binaries with 0x04; the browser path does not.
        pending.buffers += if (data[0] == 0x04.toByte()) data.copyOfRange(1, data.size) else data
        if (pending.buffers.size < pending.remaining) return

        pendingAttachment = null
        when (pending.packet.type) {
            5 -> handleEvent(pending.packet)
            6 -> handleAck(pending.packet)
        }
    }

    private fun handleAck(packet: SocketIoPacket) {
        val ackKey = pendingAcks.remove(packet.id) ?: return
        val value = try {
            SocketIoCodec.ackValue(packet.data, ackKey)
        } catch (e: Exception) {
            fail(e)
            return
        }
        when (ackKey) {
            "room_info" -> listener.onRoomInfo(value)
            "control" -> {
                val grant = try {
                    parseGrant(value)
                } catch (e: Exception) {
                    fail(e)
                    return
                }
                listener.onControlGranted(grant)
            }
        }
    }

    private fun handleEvent(packet: SocketIoPacket) {
        val args = packet.data as? JsonArray
        val event = args?.takeIf { it.size() > 0 }?.get(0)
            ?.takeIf { it.isJsonPrimitive }?.asString
        if (event == null) {
            fail(IllegalStateException("信令事件格式错误"))
            return
        }
        val payload = args.takeIf { it.size() > 1 }?.get(1)
            ?.takeIf { it.isJsonObject }?.asJsonObject
        when (event) {
            "soac" -> payload?.let(listener::onSoac)
            "released" -> listener.onReleased("远控权限已被释放")
            "left" -> listener.onReleased("被控端已退出房间")
        }
        if (packet.id >= 0) {
            webSocket?.send(SocketIoCodec.ack(packet.id))
        }
    }

    private fun parseGrant(value: JsonObject): ControlGrant {
        val clientId = value.stringOrNull("client_id")
            ?: throw IllegalStateException("控制权响应缺少 client_id")
        val iceId = value.stringOrNull("ice_id")
            ?: throw IllegalStateException("控制权响应缺少 ice_id")
        val servers = value.get("iceServers")?.takeIf { it.isJsonArray }?.asJsonArray
        val relay = value.get("force_relay")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        return ControlGrant(
            clientId = clientId,
            iceId = iceId,
            iceServers = servers,
            forceRelay = relay,
            streamerData = value.stringOrNull("streamer_data"),
            raw = value,
        )
    }

    private fun fail(error: Throwable) {
        if (closing.get()) return
        if (!failed.compareAndSet(false, true)) return
        closing.set(true)
        pendingAcks.clear()
        pendingAttachment = null
        listener.onFailure(error)
        webSocket?.cancel()
        webSocket = null
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

        override fun onMessage(webSocket: WebSocket, text: String) {
            handleText(text)
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            handleBinary(bytes)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            if (!failed.get() && !closing.get()) listener.onClosed()
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            fail(t)
        }
    }

    companion object {
        private const val STREAMER_VERSION = "V1.14.22"

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .build()
    }
}
