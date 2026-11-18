package com.sour.duoremote.protocol.signaling

import com.google.gson.JsonObject
import com.sour.duoremote.protocol.proto.ConnectOptions
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.ByteString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class SignalingClientTest {

    @Test
    fun handshakeRoomInfoControlAndBinary() {
        val server = MockWebServer()
        val serverFailure = AtomicReference<Throwable?>()
        val serverSocket = AtomicReference<WebSocket?>()
        val controlId = AtomicInteger(-1)
        val binaryPrefix = AtomicInteger(-1)
        val pong = CountDownLatch(1)
        val roomLatch = CountDownLatch(1)
        val grantLatch = CountDownLatch(1)

        val serverListener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                serverSocket.set(webSocket)
                webSocket.send("0{\"sid\":\"s-1\",\"pingInterval\":25000,\"pingTimeout\":20000}")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                when {
                    text == "40" -> {
                        webSocket.send("40{\"sid\":\"s-1\"}")
                        webSocket.send("2")
                    }

                    text == "3" -> pong.countDown()

                    text.startsWith("42") && text.contains("\"room_info\"") -> {
                        webSocket.send("43${ackId(text)}[{\"publisher\":{\"client_id\":\"pc-1\"}}]")
                    }

                    text.startsWith("451-") && text.contains("\"control\"") -> {
                        controlId.set(attachmentAckId(text))
                    }
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                binaryPrefix.set(bytes[0].toInt() and 0xff)
                webSocket.send(
                    "43${controlId.get()}[\"succ\",{\"client_id\":\"pc-1\",\"ice_id\":\"ice-1\",\"iceServers\":[]}]",
                )
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                serverFailure.set(t)
            }
        }
        server.enqueue(MockResponse().withWebSocketUpgrade(serverListener))
        server.start()

        lateinit var client: SignalingClient
        try {
            client = SignalingClient(object : SignalingClient.Listener {
                override fun onSignalingReady() = Unit
                override fun onRoomInfo(room: JsonObject) {
                    assertEquals("pc-1", room.getAsJsonObject("publisher").get("client_id").asString)
                    roomLatch.countDown()
                    client.requestControl(ConnectOptions.encode(1280, 720, 30, 2))
                }

                override fun onControlGranted(grant: SignalingClient.ControlGrant) {
                    assertEquals("pc-1", grant.clientId)
                    assertEquals("ice-1", grant.iceId)
                    assertTrue(!grant.forceRelay)
                    grantLatch.countDown()
                }

                override fun onSoac(payload: JsonObject) = Unit
                override fun onReleased(reason: String) = Unit
                override fun onFailure(error: Throwable) = Unit
                override fun onClosed() = Unit
            })
            val endpoint = SignalingEndpoint(
                server.url("/socket.io/?EIO=4&transport=websocket").toString(),
            )
            client.open(endpoint, "test-room-token")

            assertTrue("room_info ack timed out", roomLatch.await(5, TimeUnit.SECONDS))
            assertTrue("control ack timed out", grantLatch.await(5, TimeUnit.SECONDS))
            assertTrue("ping/pong timed out", pong.await(5, TimeUnit.SECONDS))
            assertEquals(0x04, binaryPrefix.get())
            assertNotNull(controlId.get().takeIf { it >= 0 })
            assertEquals(null, serverFailure.get())
        } finally {
            client.close()
            serverSocket.get()?.close(1000, "test done")
            server.shutdown()
        }
    }

    private fun ackId(text: String): Int {
        var index = 2
        var value = 0
        while (index < text.length && text[index].isDigit()) {
            value = value * 10 + (text[index] - '0')
            index++
        }
        return value
    }

    private fun attachmentAckId(text: String): Int {
        var index = text.indexOf('-') + 1
        var value = 0
        while (index < text.length && text[index].isDigit()) {
            value = value * 10 + (text[index] - '0')
            index++
        }
        return value
    }
}
