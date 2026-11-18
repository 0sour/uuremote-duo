package com.sour.duoremote.protocol.signing

import java.nio.charset.StandardCharsets
import java.util.TreeMap
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * UU API request signing.
 *
 * payload = method + pathWithQuery + sorted(lowercase x-param headers, excluding SIGN) + body
 * signature = lowercase hex(HMAC-SHA256(key, payload))
 *
 * Never log the key or signature inputs.
 */
object RequestSigner {

    fun sign(
        key: ByteArray,
        method: String,
        pathWithQuery: String,
        headers: Map<String, String>,
        body: String,
    ): String {
        val sorted = TreeMap<String, String>()
        headers.forEach { (name, value) ->
            val lower = name.lowercase()
            if (lower.startsWith("x-param-") && lower != "x-param-sign") {
                sorted[lower] = value
            }
        }
        val headerString = sorted.entries.joinToString("&") { (name, value) -> "$name=$value" }
        val payload = method + pathWithQuery + headerString + body
        return hmacSha256Hex(key, payload.toByteArray(StandardCharsets.UTF_8))
    }

    fun hmacSha256Hex(key: ByteArray, data: ByteArray): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        val digest = mac.doFinal(data)
        val out = CharArray(digest.size * 2)
        val digits = "0123456789abcdef"
        for (i in digest.indices) {
            val value = digest[i].toInt() and 0xff
            out[i * 2] = digits[value ushr 4]
            out[i * 2 + 1] = digits[value and 0x0f]
        }
        return String(out)
    }
}
