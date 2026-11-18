package com.sour.duoremote.protocol.api

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.sour.duoremote.data.SecureStore
import com.sour.duoremote.protocol.signing.RequestSigner
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Minimal UU master-client API.
 *
 * The signing key is supplied by SecureStore; this class never logs it or request bodies.
 * All endpoints and bodies follow the TV 3.5.0-compatible flow documented in research.md.
 */
class UuApiClient(
    private val secure: SecureStore,
    private val signingKey: ByteArray,
    private val okHttp: OkHttpClient = defaultClient(),
    private val gson: Gson = Gson(),
) {

    private val initMutex = Mutex()

    data class QrLogin(
        val raw: JsonObject,
        val status: Int = 1,
    )

    data class QrPoll(
        val status: Int,
        val raw: JsonObject,
    )

    data class LoginResult(
        val token: String,
        val userId: String,
        val nickname: String?,
    )

    data class Device(
        val id: String,
        val name: String,
        val online: Boolean,
        val os: String,
        val lastSeen: String?,
        val raw: JsonObject,
    )

    data class Room(
        val raw: JsonObject,
        val token: String,
        val signalingAddresses: List<String>,
        val iceServers: JsonArray?,
        val wsConnectTimeoutMs: Long?,
    )

    suspend fun initialize() {
        if (!secure.deviceId().isNullOrBlank()) return
        initMutex.withLock {
            if (!secure.deviceId().isNullOrBlank()) return
            val body = JsonObject().apply {
                addProperty("name", "DuoRemote")
                addProperty("client_id", secure.getOrCreateClientId())
                addProperty("system_id", secure.getOrCreateClientId())
                addProperty("system_version", "android")
                addProperty("gaid", "")
                addProperty("install_id", secure.getOrCreateClientId())
                addProperty("build_fingerprint", "duoremote")
                addProperty("brand", "Retroid")
                addProperty("manufacturer", "Retroid")
                addProperty("model", "Pocket Duo")
                addProperty("product", "duoremote")
                addProperty("rom", "")
                addProperty("abi", "arm64-v8a")
                addProperty("resolution", "1920x1080")
                addProperty("screen_size", "")
                addProperty("dpi", 369)
            }
            val data = request("POST", "/api/v1/device/android_tv/init", body)
            val deviceId = data?.get("device_id")?.takeIf { !it.isJsonNull }?.asString
                ?: data?.getAsJsonObject("current_device")?.get("device_id")?.asString
                ?: throw UuApiException(0, 0, "初始化响应缺少 device_id")
            secure.saveDeviceId(deviceId)
        }
    }

    suspend fun generateQr(): QrLogin {
        initialize()
        val data = request("POST", "/api/v1/qrcode/gen/login", null)
            ?: throw UuApiException(0, 0, "二维码响应为空")
        if (data.get("qrcode_jump_url")?.asString.isNullOrBlank() || data.get("token")?.asString.isNullOrBlank()) {
            throw UuApiException(0, 0, "二维码响应缺少登录信息")
        }
        return QrLogin(raw = data, status = 1)
    }

    suspend fun pollQr(qr: QrLogin): QrPoll {
        val body = JsonObject().apply {
            addProperty("login_status", qr.status)
            qr.raw.entrySet().forEach { (key, value) -> add(key, value) }
        }
        val data = request("POST", "/api/v1/qrcode/login/status", body)
            ?: throw UuApiException(0, 0, "扫码状态为空")
        val status = data.get("login_status")?.asInt ?: throw UuApiException(0, 0, "扫码状态缺少 login_status")
        return QrPoll(status = status, raw = qr.raw)
    }

    suspend fun loginByQr(qr: QrLogin): LoginResult {
        val data = request("POST", "/api/v1/login/by_qrcode", qr.raw)
            ?: throw UuApiException(0, 0, "扫码登录响应为空")
        return parseLogin(data)
    }

    suspend fun sendSmsCode(countryCode: String, mobile: String) {
        initialize()
        val body = JsonObject().apply {
            addProperty("country_code", countryCode.removePrefix("+"))
            addProperty("mobile", mobile)
            addProperty("type", "login")
        }
        request("POST", "/api/v1/security/mobile/code", body)
    }

    suspend fun loginBySms(countryCode: String, mobile: String, code: String): LoginResult {
        initialize()
        val body = JsonObject().apply {
            addProperty("country_code", countryCode.removePrefix("+"))
            addProperty("mobile", mobile)
            addProperty("code", code)
        }
        val data = request("POST", "/api/v1/login/by_mobile", body)
            ?: throw UuApiException(0, 0, "短信登录响应为空")
        return parseLogin(data)
    }

    suspend fun getUserInfo(): JsonObject {
        return request("GET", "/api/v1/user/info", null)
            ?: throw UuApiException(0, 0, "用户信息为空")
    }

    suspend fun getDevices(): List<Device> {
        val data = request("GET", "/api/v1/device/list", null)
            ?: throw UuApiException(0, 0, "设备列表为空")
        val ownDeviceId = secure.deviceId()
        val merged = mutableListOf<JsonObject>()
        data.getAsJsonArray("my_binded_devices")?.forEach { element ->
            if (element.isJsonObject) merged += element.asJsonObject
        }
        data.getAsJsonArray("others_shared_devices")?.forEach { element ->
            if (element.isJsonObject) merged += element.asJsonObject
        }

        val seen = mutableSetOf<String>()
        return merged.mapNotNull { item ->
            val id = item.stringOrNull("device_id") ?: return@mapNotNull null
            if (id == ownDeviceId || !seen.add(id)) return@mapNotNull null
            val status = item.stringOrNull("status")
                ?: item.get("online")?.takeIf { !it.isJsonNull }?.asBoolean?.toString()
            val online = status != "offline" && status != "false" && status != "0"
            Device(
                id = id,
                name = item.stringOrNull("alias")
                    ?: item.stringOrNull("device_name")
                    ?: item.stringOrNull("name")
                    ?: "我的电脑",
                online = online,
                os = item.stringOrNull("system_name")
                    ?: item.stringOrNull("os")
                    ?: "Windows",
                lastSeen = item.stringOrNull("last_online_time")
                    ?: item.stringOrNull("last_seen"),
                raw = item,
            )
        }
    }

    /**
     * Join a remote-control room. The returned token is only used as the WebSocket
     * X-NRD-AUTH header and must never be logged or persisted.
     */
    suspend fun joinRoom(deviceId: String): Room {
        initialize()
        val path = "/api/v1/room/join/by_device/" +
            URLEncoder.encode(deviceId, StandardCharsets.UTF_8.name()).replace("+", "%20")
        val body = JsonObject().apply { addProperty("force_join", false) }
        val data = request("POST", path, body)
            ?: throw UuApiException(0, 0, "加入房间响应为空")
        val token = data.stringOrNull("token")
            ?: throw UuApiException(0, 0, "加入房间响应缺少信令凭据")
        val addresses = buildList {
            data.getAsJsonArray("signaling_list")?.forEach { entry ->
                if (entry.isJsonPrimitive && entry.asJsonPrimitive.isString) {
                    entry.asString.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            data.stringOrNull("signaling_server")?.let { add(it) }
        }.distinct()
        val iceServers = data.get("iceServers")
            ?.takeIf { it.isJsonArray }
            ?.asJsonArray
        val timeout = data.get("ws_connect_timeout_ms")
            ?.takeIf { !it.isJsonNull }
            ?.let { runCatching { it.asLong }.getOrNull() }
        return Room(
            raw = data,
            token = token,
            signalingAddresses = addresses,
            iceServers = iceServers,
            wsConnectTimeoutMs = timeout,
        )
    }

    private fun parseLogin(data: JsonObject): LoginResult {
        val token = data.stringOrNull("token") ?: throw UuApiException(0, 0, "登录响应缺少 token")
        val userId = data.stringOrNull("user_id") ?: throw UuApiException(0, 0, "登录响应缺少 user_id")
        val nickname = data.stringOrNull("nickname")
        secure.saveToken(token)
        secure.saveUserId(userId)
        return LoginResult(token = token, userId = userId, nickname = nickname)
    }

    private suspend fun request(method: String, path: String, body: JsonElement?): JsonObject? {
        val bodyText = when {
            method == "GET" -> ""
            body == null -> ""
            body.isJsonObject || body.isJsonArray -> gson.toJson(body)
            else -> body.toString()
        }
        val headers = buildHeaders(method, path, bodyText)
        val requestBuilder = Request.Builder()
            .url(BASE_URL + path)
            .headers(headers)
        if (method != "GET") {
            requestBuilder.method(method, bodyText.toRequestBody(JSON_MEDIA_TYPE))
        } else {
            requestBuilder.get()
        }
        val request = requestBuilder.build()

        val response = execute(request)
        val statusCode = response.code
        val text = response.body?.string().orEmpty()
        if (statusCode == 204 || statusCode == 205) return null
        val root = try {
            JsonParser.parseString(text).asJsonObject
        } catch (e: Exception) {
            throw UuApiException(0, statusCode, "UU API 返回非 JSON 响应（HTTP $statusCode）")
        }
        val code = root.get("code")?.takeIf { !it.isJsonNull }?.asInt ?: -1
        if (statusCode == 401) {
            secure.clearSession()
            throw AuthExpiredException(root.stringOrNull("msg") ?: "登录已过期")
        }
        if (!response.isSuccessful || code != 0) {
            val message = root.stringOrNull("msg") ?: "UU API 请求失败（HTTP $statusCode，code $code）"
            throw UuApiException(code, statusCode, message)
        }
        val data = root.get("data")
        return when {
            data == null || data.isJsonNull -> null
            data.isJsonObject -> data.asJsonObject
            else -> JsonObject().apply { add("data", data) }
        }
    }

    private fun buildHeaders(method: String, path: String, body: String): Headers {
        val values = linkedMapOf(
            "X-Param-CHN" to "gwqd",
            "X-Param-OPR" to "",
            "X-Param-PKGN" to "com.netease.uuremote",
            "X-Param-PLAT" to "5",
            "X-Param-REL" to "prod",
            "X-Param-TS" to System.currentTimeMillis().toString(),
            "X-Param-VC" to "30500",
            "X-Param-VN" to "3.5.0",
            "X-Param-ABI" to "arm64-v8a",
            "X-Param-client-id" to secure.getOrCreateClientId(),
            "X-Param-device-id" to (secure.deviceId() ?: ""),
            "X-Param-user-id" to (secure.userId() ?: ""),
            "X-Param-OAID" to "",
            "X-Param-CNT" to "CN",
            "X-Param-LANG" to "zh-CN",
        )
        values["X-Param-SIGN"] = RequestSigner.sign(signingKey, method, path, values, body)
        val builder = Headers.Builder()
        values.forEach { (name, value) -> builder.add(name, value) }
        builder.add("Content-Type", "application/json; charset=utf-8")
        secure.token()?.let { builder.add("Authorization", "Bearer $it") }
        return builder.build()
    }

    private suspend fun execute(request: Request): Response = suspendCancellableCoroutine { continuation ->
        val call = okHttp.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                if (continuation.isActive) continuation.resume(response) else response.close()
            }
        })
    }

    private fun JsonObject.stringOrNull(name: String): String? {
        val value = get(name) ?: return null
        if (value.isJsonNull) return null
        return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    class UuApiException(
        val code: Int,
        val httpStatus: Int,
        message: String,
    ) : Exception(message)

    class AuthExpiredException(message: String) : Exception(message)

    companion object {
        private const val BASE_URL = "https://api.nrd.nie.163.com"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        private fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(70, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .callTimeout(80, TimeUnit.SECONDS)
            .build()
    }
}
