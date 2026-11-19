package com.sour.duoremote.protocol.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ConnectOptionsTest {

    @Test
    fun encodesExpectedTopLevelShape() {
        val top = fields(ConnectOptions.encode(width = 1920, height = 1080, fps = 60, quality = 3))

        assertEquals(1uL, top.single(1).long)
        assertEquals(ULong.MAX_VALUE, top.single(2).long)
        assertEquals(2uL, top.single(8).long)

        val capture = fields(top.single(3).bytes)
        assertEquals(2uL, capture.single(1).long)
        assertEquals(3uL, capture.single(2).long)
        assertEquals(1uL, capture.single(3).long)
        assertEquals(2uL, capture.single(4).long)
        val resolution = fields(capture.single(5).bytes)
        assertEquals(1920uL, resolution.single(1).long)
        assertEquals(1080uL, resolution.single(2).long)

        val decoder = fields(top.single(4).bytes)
        assertEquals(60uL, decoder.single(1).long)
        assertEquals(1uL, decoder.single(2).long)
        assertEquals(1920uL, decoder.single(3).long)
        assertEquals(1080uL, decoder.single(4).long)
    }

    @Test
    fun usesThirtyFpsClass() {
        val top = fields(ConnectOptions.encode(width = 1280, height = 720, fps = 30, quality = 2))
        val capture = fields(top.single(3).bytes)
        assertEquals(1uL, capture.single(1).long)
        assertEquals(2uL, capture.single(2).long)
        assertEquals(4uL, capture.single(4).long)
        assertEquals(1280uL, fields(capture.single(6).bytes).single(1).long)
    }

    @Test
    fun sendsBitrateCapAndFollowLocal() {
        val top = fields(
            ConnectOptions.encode(
                width = 1920,
                height = 1080,
                fps = 60,
                quality = 3,
                maxBitrateMbps = 50,
                followLocal = true,
            ),
        )
        val capture = fields(top.single(3).bytes)
        assertEquals(2uL, capture.single(4).long)
        assertEquals(50uL, capture.single(8).long)
    }

    @Test
    fun advertisesFileTransferAndClipboardFeatures() {
        val top = fields(ConnectOptions.encode())
        val flags = fields(top.single(11).bytes)
        assertEquals(4uL, flags.single(1).long)
        assertEquals(3uL, flags.single(2).long)
        assertEquals(2uL, flags.single(6).long)
        assertEquals(2uL, flags.single(7).long)
        assertEquals(3uL, flags.single(8).long)
    }

    @Test
    fun omitsBitrateWhenAutomatic() {
        val capture = fields(fields(ConnectOptions.encode(maxBitrateMbps = 0)).single(3).bytes)
        assertTrue(capture.none { it.number == 8 })
    }

    @Test
    fun mapsNinetyAndOneFortyFourFps() {
        assertEquals(
            3uL,
            fields(fields(ConnectOptions.encode(fps = 90)).single(3).bytes).single(1).long,
        )
        assertEquals(
            4uL,
            fields(fields(ConnectOptions.encode(fps = 144)).single(3).bytes).single(1).long,
        )
    }

    @Test
    fun rejectsUnsupportedValues() {
        listOf(
            { ConnectOptions.encode(width = -1, height = 1080, fps = 60, quality = 3) },
            { ConnectOptions.encode(width = 1920, height = 1080, fps = 0, quality = 3) },
            { ConnectOptions.encode(width = 1920, height = 1080, fps = 60, quality = 99) },
        ).forEach { block ->
            try {
                block()
                fail("Expected rejection")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
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
