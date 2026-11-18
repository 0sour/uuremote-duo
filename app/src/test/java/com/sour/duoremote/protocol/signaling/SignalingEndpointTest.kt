package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class SignalingEndpointTest {

    @Test
    fun acceptsBareAndFullHosts() {
        assertEquals(
            "wss://signal.nrd.nie.163.com/socket.io/?EIO=4&transport=websocket",
            SignalingEndpoint.fromAddress("signal.nrd.nie.163.com").url,
        )
        assertEquals(
            "wss://signal.nie.163.com:443/socket.io/?EIO=4&transport=websocket",
            SignalingEndpoint.fromAddress("wss://signal.nie.163.com:443/ignored").url,
        )
    }

    @Test
    fun prefersFirstSignalingListEntry() {
        val room = JsonParser.parseString(
            """{"signaling_server":"signal.fallback.163.com","signaling_list":[null,"signal.primary.163.com"]}""",
        ).asJsonObject
        assertEquals(
            "wss://signal.primary.163.com/socket.io/?EIO=4&transport=websocket",
            SignalingEndpoint.fromRoom(room).url,
        )
    }

    @Test
    fun fallsBackToSignalingServer() {
        val room = JsonParser.parseString(
            """{"signaling_server":"signal.fallback.163.com"}""",
        ).asJsonObject
        assertTrue(SignalingEndpoint.fromRoom(room).url.contains("signal.fallback.163.com"))
    }

    @Test
    fun rejectsUntrustedAddresses() {
        listOf(
            "http://signal.nie.163.com",
            "ws://signal.nie.163.com",
            "wss://evil163.com",
            "wss://attacker@signal.nie.163.com",
            "wss://163.com.attacker.org",
        ).forEach { address ->
            try {
                SignalingEndpoint.fromAddress(address)
                fail("Expected rejection for $address")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message!!.isNotEmpty())
            }
        }
    }
}
