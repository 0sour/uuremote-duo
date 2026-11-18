package com.sour.duoremote.protocol.signaling

import org.junit.Assert.assertEquals
import org.junit.Test

class SoacCodecTest {

    @Test
    fun offerCarriesClientDataGenerationAndControl() {
        val message = SoacCodec.description(
            clientId = "pc-1",
            type = "offer",
            sdp = "v=0",
            iceId = "ice-1",
            appControlId = "control-1",
        )
        assertEquals("pc-1", message.get("client_id").asString)
        val data = message.getAsJsonObject("data")
        assertEquals("offer", data.get("type").asString)
        assertEquals("v=0", data.get("sdp").asString)
        assertEquals("ice-1", data.get("ice_id").asString)
        assertEquals("control-1", data.get("app_control_id").asString)
    }

    @Test
    fun candidateIsNestedUnderData() {
        val message = SoacCodec.candidate(
            clientId = "pc-1",
            candidate = SoacCodec.candidateInit("candidate:1 1 udp 1 1.2.3.4 1 typ host", "0", 0),
            iceId = "ice-1",
            appControlId = "control-1",
        )
        val data = message.getAsJsonObject("data")
        assertEquals("candidate", data.get("type").asString)
        assertEquals(
            "0",
            data.getAsJsonObject("candidate").get("sdpMid").asString,
        )
        assertEquals(0, data.getAsJsonObject("candidate").get("sdpMLineIndex").asInt)
    }
}
