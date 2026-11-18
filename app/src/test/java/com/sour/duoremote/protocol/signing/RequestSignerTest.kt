package com.sour.duoremote.protocol.signing

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.StandardCharsets

class RequestSignerTest {

    @Test
    fun hmacMatchesRfc4231Case2() {
        val actual = RequestSigner.hmacSha256Hex(
            "Jefe".toByteArray(StandardCharsets.UTF_8),
            "what do ya want for nothing?".toByteArray(StandardCharsets.UTF_8),
        )
        assertEquals(
            "5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
            actual,
        )
    }

    @Test
    fun signSortsAndFiltersXParamHeaders() {
        val key = "0123456789abcdef01234567".toByteArray(StandardCharsets.UTF_8)
        val actual = RequestSigner.sign(
            key = key,
            method = "POST",
            pathWithQuery = "/api/v1/test?x=1",
            headers = linkedMapOf(
                "X-Param-B" to "2",
                "Authorization" to "Bearer ignored",
                "x-param-a" to "1",
                "X-Param-SIGN" to "ignored",
            ),
            body = """{"a":1}""",
        )
        assertEquals("f4c13f5501da7b05ccb6b769902fe5cdd6e4734be3f78a6af7f28b41250565eb", actual)
    }
}
