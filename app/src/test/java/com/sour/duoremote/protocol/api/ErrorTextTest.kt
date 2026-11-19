package com.sour.duoremote.protocol.api

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorTextTest {

    @Test
    fun dnsAndTimeoutErrorsAreActionable() {
        assertEquals(
            "无法解析服务器地址，请检查网络或 DNS",
            ErrorText.friendly(UnknownHostException("api.example.com")),
        )
        assertEquals(
            "连接超时，请检查网络后重试",
            ErrorText.friendly(SocketTimeoutException("timeout")),
        )
        assertEquals(
            "无法连接服务器，请检查网络后重试",
            ErrorText.friendly(ConnectException("refused")),
        )
    }

    @Test
    fun wrappedNetworkCauseIsFound() {
        val wrapped = IllegalStateException("join failed", IOException("socket closed"))
        assertEquals("网络连接中断，请检查网络后重试", ErrorText.friendly(wrapped))
    }

    @Test
    fun apiAndFallbackMessagesArePreserved() {
        assertEquals(
            "设备不存在",
            ErrorText.friendly(UuApiClient.UuApiException(404, 404, "设备不存在")),
        )
        assertEquals("自定义失败", ErrorText.friendly(RuntimeException("自定义失败")))
        assertEquals("连接失败", ErrorText.friendly(null, "连接失败"))
    }
}
