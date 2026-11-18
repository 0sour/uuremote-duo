package com.sour.duoremote.protocol.proto

/**
 * Text-only clipboard RPC carried as binary frames on TEXT_DATA_CHANNEL.
 *
 * Envelope { 21: Request { 1: Header{id}, 10: ClipboardTextChangeRequest } }
 * Request text { 1: format_id (13 = Unicode text), 2: data }
 * Response { 22: Response { 1: Header{id}, 6: ClipboardTextChangeResponse{1: err} } }
 *
 * Only text is handled; file clipboard messages (Request field 9) are ignored.
 */
object ClipboardCodec {

    const val FORMAT_UNICODE_TEXT = 13
    const val CB_SUCCEEDED = 1
    const val MAX_TEXT_LENGTH = 2048

    data class IncomingText(val id: Long, val formatId: Int, val text: String)

    fun encodeTextRequest(id: Long, text: String): ByteArray {
        val header = ProtoWriter().apply { integer(1, id) }.toByteArray()
        val textMessage = ProtoWriter().apply {
            integer(1, FORMAT_UNICODE_TEXT.toLong())
            text(2, text)
        }.toByteArray()
        val request = ProtoWriter().apply {
            message(1, header)
            message(10, textMessage)
        }.toByteArray()
        return ProtoWriter().apply { message(21, request) }.toByteArray()
    }

    fun encodeTextResponse(id: Long, err: Int = CB_SUCCEEDED): ByteArray {
        val header = ProtoWriter().apply { integer(1, id) }.toByteArray()
        val textMessage = ProtoWriter().apply { integer(1, err.toLong()) }.toByteArray()
        val response = ProtoWriter().apply {
            message(1, header)
            message(6, textMessage)
        }.toByteArray()
        return ProtoWriter().apply { message(22, response) }.toByteArray()
    }

    fun parseIncomingText(data: ByteArray): IncomingText? {
        val request = readEnvelopeRequest(data) ?: return null
        var id = 0L
        var textBytes: ByteArray? = null
        val reader = ProtoReader(request)
        while (true) {
            val field = reader.readField() ?: break
            when {
                field.number == 1 && field.wire == 2 -> id = firstInteger(field.bytes)
                field.number == 10 && field.wire == 2 -> textBytes = field.bytes
            }
        }
        val message = textBytes ?: return null
        var formatId = 0
        var text: String? = null
        val textReader = ProtoReader(message)
        while (true) {
            val field = textReader.readField() ?: break
            when {
                field.number == 1 && field.wire == 0 -> formatId = field.integer.toInt()
                field.number == 2 && field.wire == 2 ->
                    text = String(field.bytes, Charsets.UTF_8)
            }
        }
        if (formatId != FORMAT_UNICODE_TEXT) return null
        val value = text ?: return null
        if (value.isEmpty() || value.length > MAX_TEXT_LENGTH) return null
        return IncomingText(id, formatId, value)
    }

    private fun readEnvelopeRequest(data: ByteArray): ByteArray? {
        val reader = ProtoReader(data)
        while (true) {
            val field = reader.readField() ?: return null
            if (field.number == 21 && field.wire == 2) return field.bytes
        }
    }

    private fun firstInteger(data: ByteArray): Long {
        val reader = ProtoReader(data)
        while (true) {
            val field = reader.readField() ?: return 0L
            if (field.number == 1 && field.wire == 0) return field.integer
        }
    }
}
