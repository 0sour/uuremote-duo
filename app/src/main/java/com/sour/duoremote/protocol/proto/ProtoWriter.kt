package com.sour.duoremote.protocol.proto

/**
 * Minimal protobuf wire writer shared by the protocol messages we encode.
 * Only varints and length-delimited fields are needed by the controller payloads.
 */
class ProtoWriter {
    private val bytes = ArrayList<Byte>(64)

    fun integer(field: Int, value: Long) {
        varint((field * 8).toLong())
        varint(value)
    }

    fun message(field: Int, payload: ByteArray) {
        varint((field * 8 + 2).toLong())
        varint(payload.size.toLong())
        payload.forEach { bytes.add(it) }
    }

    fun text(field: Int, value: String) {
        message(field, value.toByteArray(Charsets.UTF_8))
    }

    private fun varint(value: Long) {
        var current = value
        while (true) {
            if (current and 0x7fL.inv() == 0L) {
                bytes.add((current and 0x7f).toByte())
                return
            }
            bytes.add(((current and 0x7f) or 0x80).toByte())
            current = current ushr 7
        }
    }

    fun toByteArray(): ByteArray = ByteArray(bytes.size) { bytes[it] }
}
