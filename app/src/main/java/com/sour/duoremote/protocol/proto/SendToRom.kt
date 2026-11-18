package com.sour.duoremote.protocol.proto

/**
 * `Main.send_to_rom` (field 11) with `SendToRom.RomMsg_Text`.
 *
 * Older hosts and the TV path accept Unicode text through the TEXT data channel even when the
 * CONTROL `text_input` action is unavailable. The raw protobuf is sent as a binary data-channel
 * message; `index` is the remote monitor index (0 for the primary display).
 */
object SendToRom {

    fun encodeText(text: String, index: Int = 0): ByteArray {
        require(index >= 0) { "Invalid index" }
        val sendToRom = ProtoWriter().apply {
            integer(1, 1L)
            text(2, text)
            integer(3, index.toLong())
        }.toByteArray()
        return ProtoWriter().apply {
            message(11, sendToRom)
        }.toByteArray()
    }
}
