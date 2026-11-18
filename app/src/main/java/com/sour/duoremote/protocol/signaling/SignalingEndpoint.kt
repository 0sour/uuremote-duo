package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonObject
import java.net.URI
import java.util.Locale

/**
 * Validated Socket.IO/Engine.IO v4 endpoint derived from a room-join response.
 *
 * The server may return either a bare host ("signal.nrd.nie.163.com") or a wss URL.
 * Only 163.com hosts are accepted; any path/query in the source is replaced with
 * the fixed Socket.IO path used by the UU signaling service.
 */
data class SignalingEndpoint(val url: String) {

    companion object {
        fun fromRoom(room: JsonObject): SignalingEndpoint {
            val addresses = buildList {
                room.getAsJsonArray("signaling_list")?.forEach { entry ->
                    if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) {
                        entry.asString.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                room.get("signaling_server")
                    ?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }
                    ?.asString
                    ?.takeIf { it.isNotBlank() }
                    ?.let(::add)
            }
            val address = addresses.firstOrNull()
                ?: throw IllegalArgumentException("加入房间未返回信令服务器")
            return fromAddress(address)
        }

        fun fromAddress(address: String): SignalingEndpoint {
            val raw = address.trim()
            require(raw.isNotEmpty()) { "信令地址为空" }
            val source = if ("://" in raw) raw else "wss://$raw"
            val uri = try {
                URI(source)
            } catch (e: Exception) {
                throw IllegalArgumentException("信令地址无效", e)
            }
            require(uri.scheme.equals("wss", ignoreCase = true)) { "信令必须使用 WSS" }
            require(uri.userInfo == null) { "信令地址不能包含用户信息" }
            val host = uri.host?.lowercase(Locale.ROOT)
                ?: throw IllegalArgumentException("信令地址缺少主机名")
            require(host == "163.com" || host.endsWith(".163.com")) { "不受信任的信令主机" }
            val authority = if (uri.port != -1) "$host:${uri.port}" else host
            return SignalingEndpoint("wss://$authority/socket.io/?EIO=4&transport=websocket")
        }
    }
}
