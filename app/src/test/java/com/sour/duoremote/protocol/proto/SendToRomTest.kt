package com.sour.duoremote.protocol.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SendToRomTest {

    @Test
    fun wrapsRomMsgTextInMainFieldEleven() {
        val top = fields(SendToRom.encodeText("你好", index = 2))
        val sendToRom = fields(top.single(11).bytes)
        assertEquals(1uL, sendToRom.single(1).long)
        assertEquals("你好", String(sendToRom.single(2).bytes, Charsets.UTF_8))
        assertEquals(2uL, sendToRom.single(3).long)
    }

    @Test
    fun defaultsToPrimaryIndex() {
        val top = fields(SendToRom.encodeText("abc"))
        val sendToRom = fields(top.single(11).bytes)
        assertEquals(0uL, sendToRom.single(3).long)
    }

    @Test
    fun keepsUtf8LengthInBytes() {
        val top = fields(SendToRom.encodeText("远程"))
        val sendToRom = fields(top.single(11).bytes)
        assertEquals(6, sendToRom.single(2).bytes.size)
    }

    private data class Field(
        val number: Int,
        val wire: Int,
        val long: ULong = 0uL,
        val bytes: ByteArray = ByteArray(0),
    )

    private fun fields(data: ByteArray): List<Field> {
        val output = ArrayList<Field>()
        var offset = 0
        while (offset < data.size) {
            val (tag, tagLength) = readVarint(data, offset)
            offset += tagLength
            val number = (tag shr 3).toInt()
            val wire = (tag and 7uL).toInt()
            assertTrue(number > 0)
            when (wire) {
                0 -> {
                    val (value, length) = readVarint(data, offset)
                    offset += length
                    output += Field(number, wire, long = value)
                }

                2 -> {
                    val (length, lengthBytes) = readVarint(data, offset)
                    offset += lengthBytes
                    val size = length.toInt()
                    assertTrue(offset + size <= data.size)
                    output += Field(number, wire, bytes = data.copyOfRange(offset, offset + size))
                    offset += size
                }

                else -> error("Unexpected wire type $wire")
            }
        }
        return output
    }

    private fun readVarint(data: ByteArray, start: Int): Pair<ULong, Int> {
        var value = 0uL
        var shift = 0
        var offset = start
        while (offset < data.size && shift <= 63) {
            val byte = data[offset++].toInt() and 0xff
            value = value or ((byte and 0x7f).toULong() shl shift)
            if (byte and 0x80 == 0) return value to (offset - start)
            shift += 7
        }
        error("Truncated varint")
    }

    private fun List<Field>.single(number: Int): Field {
        val fields = filter { it.number == number }
        assertEquals("field $number count", 1, fields.size)
        return fields.single()
    }
}
