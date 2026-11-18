package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SocketIoCodecTest {

    @Test
    fun parsesEngineIoConnectAck() {
        val packet = SocketIoCodec.parse("40{\"sid\":\"abc\"}")
        assertEquals(0, packet.type)
        assertEquals(-1, packet.id)
        assertEquals("abc", packet.data?.asJsonObject?.get("sid")?.asString)
    }

    @Test
    fun parsesPlainEvent() {
        val packet = SocketIoCodec.parse("42[\"left\",{}]")
        assertEquals(2, packet.type)
        assertEquals(-1, packet.id)
        val args = packet.data as JsonArray
        assertEquals("left", args[0].asString)
    }

    @Test
    fun parsesAckId() {
        val packet = SocketIoCodec.parse("431[{\"ok\":true}]")
        assertEquals(3, packet.type)
        assertEquals(1, packet.id)
        assertTrue(packet.data is JsonArray)
    }

    @Test
    fun parsesBinaryAttachmentHeader() {
        val packet = SocketIoCodec.parse("451-0[\"control\",{\"app_data\":{\"_placeholder\":true,\"num\":0}}]")
        assertEquals(5, packet.type)
        assertEquals(0, packet.id)
        assertEquals(1, packet.attachments)
        assertEquals("control", (packet.data as JsonArray)[0].asString)
    }

    @Test
    fun parsesBinaryAckHeader() {
        val packet = SocketIoCodec.parse("461-23[null,{\"ice_id\":4}]")
        assertEquals(6, packet.type)
        assertEquals(23, packet.id)
        assertEquals(1, packet.attachments)
    }

    @Test
    fun rejectsMalformedHeaders() {
        listOf("4516x[]", "45-", "42/other,[]", "50", "4").forEach { input ->
            try {
                SocketIoCodec.parse(input)
                fail("Expected rejection for $input")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
    }

    @Test
    fun encodesEventAndAck() {
        val payload = JsonParser.parseString("{\"force_join\":false}").asJsonObject
        assertEquals("42[\"room_info\"]", SocketIoCodec.event("room_info"))
        assertEquals(
            "427[\"soac\",{\"force_join\":false}]",
            SocketIoCodec.event("soac", payload, ackId = 7),
        )
        assertEquals("430[]", SocketIoCodec.ack(0))
    }

    @Test
    fun encodesBinaryEvent() {
        val payload = JsonObject().apply { addProperty("app_control_id", "id") }
        val encoded = SocketIoCodec.eventWithAttachment("control", payload, ackId = 3)
        assertTrue(encoded.startsWith("451-3[\"control\",{"))
    }

    @Test
    fun ackValueAcceptsSingleObjectAndPair() {
        val single = SocketIoCodec.ackValue(
            JsonParser.parseString("[{\"publisher\":{\"client_id\":\"pc\"}}]"),
            "room_info",
        )
        assertEquals("pc", single.getAsJsonObject("publisher").get("client_id").asString)

        val pair = SocketIoCodec.ackValue(
            JsonParser.parseString("[\"succ\",{\"client_id\":\"pc\",\"ice_id\":\"ice\"}]"),
            "control",
        )
        assertEquals("ice", pair.get("ice_id").asString)
    }

    @Test
    fun ackValueRejectsErrors() {
        try {
            SocketIoCodec.ackValue(JsonParser.parseString("[{\"code\":2,\"msg\":\"occupied\"}]"), "room_info")
            fail("Expected error")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("occupied"))
        }
        try {
            SocketIoCodec.ackValue(JsonParser.parseString("[\"error\",null]"), "control")
            fail("Expected error")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("control"))
        }
        try {
            SocketIoCodec.ackValue(null, "room_info")
            fail("Expected error")
        } catch (expected: IllegalArgumentException) {
            assertTrue(expected.message!!.contains("room_info"))
        }
    }
}
