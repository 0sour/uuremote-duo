package com.sour.duoremote.protocol.proto

/**
 * Minimal protobuf wire reader for the small inbound messages we need to inspect.
 * Unknown wire types are skipped so newer host fields do not break parsing.
 */
class ProtoReader(private val data: ByteArray) {

    data class Field(
        val number: Int,
        val wire: Int,
        val integer: Long = 0L,
        val bytes: ByteArray = ByteArray(0),
    )

    private var offset = 0

    fun readField(): Field? {
        if (offset >= data.size) return null
        val tag = readVarint()
        val number = (tag ushr 3).toInt()
        val wire = (tag and 7L).toInt()
        return when (wire) {
            0 -> Field(number, wire, integer = readVarint())
            1 -> {
                skip(8)
                Field(number, wire)
            }

            2 -> Field(number, wire, bytes = readLengthDelimited())
            5 -> {
                skip(4)
                Field(number, wire)
            }

            else -> null
        }
    }

    private fun readVarint(): Long {
        var value = 0L
        var shift = 0
        while (offset < data.size && shift <= 63) {
            val byte = data[offset++].toInt() and 0xff
            value = value or ((byte and 0x7f).toLong() shl shift)
            if (byte and 0x80 == 0) return value
            shift += 7
        }
        error("Truncated varint")
    }

    private fun readLengthDelimited(): ByteArray {
        val length = readVarint().toInt()
        require(length >= 0 && offset + length <= data.size) { "Truncated field" }
        val result = data.copyOfRange(offset, offset + length)
        offset += length
        return result
    }

    private fun skip(count: Int) {
        require(offset + count <= data.size) { "Truncated field" }
        offset += count
    }
}
