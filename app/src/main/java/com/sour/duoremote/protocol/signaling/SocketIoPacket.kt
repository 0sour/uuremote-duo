package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * Socket.IO v5-over-Engine.IO v4 packet carried in a text WebSocket frame.
 * UU only uses the default namespace, so a namespace prefix is rejected.
 */
internal data class SocketIoPacket(
    val type: Int,
    val id: Int = -1,
    val attachments: Int = 0,
    val data: JsonElement? = null,
)

internal object SocketIoCodec {

    fun parse(text: String): SocketIoPacket {
        require(text.length >= 2 && text[0] == '4') { "Invalid Socket.IO packet" }
        val type = text[1] - '0'
        require(type in 0..6) { "Invalid Socket.IO packet type" }
        var index = 2

        fun number(): Int {
            val start = index
            var value = 0
            while (index < text.length && text[index].isDigit()) {
                require(value <= 1_000_000) { "Socket.IO number limit" }
                value = value * 10 + (text[index] - '0')
                index++
            }
            require(index > start) { "Missing Socket.IO number" }
            return value
        }

        var attachments = 0
        if (type == 5 || type == 6) {
            attachments = number()
            require(attachments <= 16) { "Too many Socket.IO attachments" }
            require(index < text.length && text[index] == '-') { "Invalid Socket.IO attachments" }
            index++
        }

        require(index >= text.length || text[index] != '/') { "Unsupported Socket.IO namespace" }

        var id = -1
        if (index < text.length && text[index].isDigit()) id = number()

        val data = if (index < text.length) {
            try {
                JsonParser.parseString(text.substring(index))
            } catch (e: Exception) {
                throw IllegalArgumentException("Invalid Socket.IO JSON payload", e)
            }
        } else {
            null
        }
        return SocketIoPacket(type = type, id = id, attachments = attachments, data = data)
    }

    fun event(event: String, payload: JsonElement? = null, ackId: Int? = null): String {
        val args = JsonArray().apply {
            add(event)
            if (payload != null) add(payload)
        }
        return buildString {
            append("42")
            if (ackId != null) append(ackId)
            append(args.toString())
        }
    }

    /**
     * Binary event: "45<attachments>-<ack-id><json>". UU uses the Socket.IO
     * placeholder convention for the ConnectOptions attachment.
     */
    fun eventWithAttachment(
        event: String,
        payload: JsonElement,
        ackId: Int? = null,
    ): String {
        val args = JsonArray().apply {
            add(event)
            add(payload)
        }
        return buildString {
            append("451-")
            if (ackId != null) append(ackId)
            append(args.toString())
        }
    }

    fun ack(id: Int, args: JsonArray = JsonArray()): String = "43$id$args"

    /**
     * Ack payloads come either as a single object (room_info) or as
     * [error, value]. Reject explicit non-zero codes and error sentinels.
     */
    fun ackValue(data: JsonElement?, event: String): JsonObject {
        val args = data as? JsonArray
        require(args != null && args.size() > 0) { "$event：empty acknowledgment" }
        if (args.size() == 1 && args[0].isJsonObject) {
            val value = args[0].asJsonObject
            val code = value.intOrNull("code")
            if (code != null && code != 0) {
                throw IllegalStateException("$event：${value.stringOrNull("msg") ?: code}")
            }
            return value
        }

        val error = args[0]
        if (!error.isJsonNull) {
            val ok = when {
                error.isJsonPrimitive && error.asJsonPrimitive.isString ->
                    error.asString == "succ" || error.asString == "success"
                error.isJsonObject -> error.asJsonObject.intOrNull("code") == 0
                else -> false
            }
            if (!ok) {
                val message = when {
                    error.isJsonObject -> error.asJsonObject.stringOrNull("msg")
                        ?: error.asJsonObject.intOrNull("code")?.toString()
                    error.isJsonPrimitive -> error.asString
                    else -> null
                }
                throw IllegalStateException("$event：${message ?: "请求被拒绝"}")
            }
        }

        val value = if (args.size() > 1) args[1] else null
        require(value != null && value.isJsonObject) { "$event：服务端未返回数据" }
        val objectValue = value.asJsonObject
        val code = objectValue.intOrNull("code")
        if (code != null && code != 0) {
            throw IllegalStateException("$event：${objectValue.stringOrNull("msg") ?: code}")
        }
        return objectValue
    }

    private fun JsonObject.intOrNull(name: String): Int? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else runCatching { value.asInt }.getOrNull()
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        return if (value.isJsonNull) null else runCatching { value.asString }.getOrNull()
    }
}
