package com.sour.duoremote.protocol.proto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClipboardCodecTest {

    @Test
    fun encodesAndParsesUnicodeTextRequest() {
        val payload = ClipboardCodec.encodeTextRequest(42, "你好 clipboard")
        val incoming = ClipboardCodec.parseIncomingText(payload)!!
        assertEquals(42L, incoming.id)
        assertEquals(ClipboardCodec.FORMAT_UNICODE_TEXT, incoming.formatId)
        assertEquals("你好 clipboard", incoming.text)
    }

    @Test
    fun encodesSucceededResponse() {
        val payload = ClipboardCodec.encodeTextResponse(7)
        val envelope = reader(payload).single(22)
        val response = reader(envelope.bytes)
        assertEquals(7L, reader(response.single(1).bytes).single(1).integer)
        val text = reader(response.single(6).bytes)
        assertEquals(ClipboardCodec.CB_SUCCEEDED.toLong(), text.single(1).integer)
    }

    @Test
    fun ignoresNonTextAndUnknownEnvelopes() {
        assertNull(ClipboardCodec.parseIncomingText(byteArrayOf()))
        val other = ProtoWriter().apply { integer(1, 1) }.toByteArray()
        assertNull(ClipboardCodec.parseIncomingText(other))
    }

    @Test
    fun rejectsOversizedOrEmptyText() {
        assertNull(ClipboardCodec.parseIncomingText(ClipboardCodec.encodeTextRequest(1, "")))
        val tooLong = "x".repeat(ClipboardCodec.MAX_TEXT_LENGTH + 1)
        assertNull(ClipboardCodec.parseIncomingText(ClipboardCodec.encodeTextRequest(1, tooLong)))
    }

    private fun reader(data: ByteArray) = ListReader(data)

    private class ListReader(data: ByteArray) {
        private val fields = ArrayList<ProtoReader.Field>()
        init {
            val reader = ProtoReader(data)
            while (true) fields += reader.readField() ?: break
        }

        fun single(number: Int): ProtoReader.Field {
            val matches = fields.filter { it.number == number }
            assertEquals("field $number count", 1, matches.size)
            return matches.single()
        }
    }
}
