package com.sour.duoremote.protocol.api

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Maps transport exceptions to short, actionable Chinese text. Server/API messages are kept when
 * they already explain the problem; raw OkHttp text such as "Failed to connect to ..." is replaced
 * so the home/stream status line stays readable.
 */
object ErrorText {
    fun friendly(error: Throwable?, fallback: String = "操作失败，请重试"): String {
        val chain = generateSequence(error) { it.cause }.toList()
        chain.filterIsInstance<UuApiClient.AuthExpiredException>().firstOrNull()?.let {
            return "登录已过期，请重新登录"
        }
        chain.filterIsInstance<UuApiClient.UuApiException>().firstOrNull()?.message
            ?.takeIf { it.isNotBlank() }
            ?.let { return it }
        if (chain.any { it is UnknownHostException }) {
            return "无法解析服务器地址，请检查网络或 DNS"
        }
        if (chain.any { it is SocketTimeoutException }) {
            return "连接超时，请检查网络后重试"
        }
        if (chain.any { it is ConnectException }) {
            return "无法连接服务器，请检查网络后重试"
        }
        if (chain.any { it is SSLException }) {
            return "安全连接失败，请检查系统时间或网络代理"
        }
        if (chain.any { it is IOException }) {
            return "网络连接中断，请检查网络后重试"
        }
        return error?.message?.takeIf { it.isNotBlank() } ?: fallback
    }
}
