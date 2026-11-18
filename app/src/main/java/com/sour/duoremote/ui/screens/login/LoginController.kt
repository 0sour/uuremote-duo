package com.sour.duoremote.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sour.duoremote.protocol.api.UuApiClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class LoginMode { QR, SMS }

enum class LoginFocusTarget { REFRESH, SMS_TOGGLE, PHONE, CODE, SEND, LOGIN }

/**
 * Activity-scoped login state shared by the top QR pane and the bottom controls.
 * Owns the polling/cooldown jobs so both screens see the same state.
 */
class LoginController(
    private val api: UuApiClient,
    private val scope: CoroutineScope,
    private val onLoggedIn: () -> Unit,
) {
    var mode by mutableStateOf(LoginMode.QR)
        private set
    var focusIndex by mutableStateOf(0)
        private set
    var editing by mutableStateOf(false)
    var editToken by mutableStateOf(0)
        private set
    var qr by mutableStateOf<UuApiClient.QrLogin?>(null)
    var qrLoading by mutableStateOf(false)
    var qrError by mutableStateOf<String?>(null)
    var qrStatus by mutableStateOf(1)
    var remaining by mutableStateOf(0)
    var expired by mutableStateOf(false)

    var countryCode by mutableStateOf("+86")
    var mobile by mutableStateOf("")
    var smsCode by mutableStateOf("")
    var smsSending by mutableStateOf(false)
    var smsLogging by mutableStateOf(false)
    var smsError by mutableStateOf<String?>(null)
    var cooldown by mutableStateOf(0)

    private var pollJob: Job? = null
    private var timerJob: Job? = null
    private var refreshJob: Job? = null
    private var cooldownJob: Job? = null
    private var completed = false

    val focusTargets: List<LoginFocusTarget>
        get() = when (mode) {
            LoginMode.QR -> listOf(LoginFocusTarget.REFRESH, LoginFocusTarget.SMS_TOGGLE)
            LoginMode.SMS -> listOf(
                LoginFocusTarget.PHONE,
                LoginFocusTarget.CODE,
                LoginFocusTarget.SEND,
                LoginFocusTarget.LOGIN,
                LoginFocusTarget.SMS_TOGGLE,
            )
        }

    val focusedTarget: LoginFocusTarget?
        get() = focusTargets.getOrNull(focusIndex)

    fun isFocused(target: LoginFocusTarget): Boolean = focusedTarget == target

    fun switchMode(next: LoginMode) {
        if (mode == next) return
        mode = next
        focusIndex = 0
        editing = false
        smsError = null
    }

    fun toggleMode() {
        switchMode(if (mode == LoginMode.QR) LoginMode.SMS else LoginMode.QR)
    }

    fun focusOn(target: LoginFocusTarget) {
        val index = focusTargets.indexOf(target)
        if (index >= 0) focusIndex = index
    }

    fun moveFocus(delta: Int) {
        if (editing || focusTargets.isEmpty()) return
        focusIndex = (focusIndex + delta).coerceIn(0, focusTargets.lastIndex)
    }

    /** A button: activate the selected control, or leave the current text field. */
    fun activateFocused(): Boolean {
        if (editing) {
            editing = false
            return true
        }
        return when (focusedTarget) {
            LoginFocusTarget.REFRESH -> {
                refreshQr()
                true
            }

            LoginFocusTarget.SMS_TOGGLE -> {
                toggleMode()
                true
            }

            LoginFocusTarget.PHONE, LoginFocusTarget.CODE -> {
                editing = true
                editToken += 1
                true
            }

            LoginFocusTarget.SEND -> {
                sendSmsCode()
                true
            }

            LoginFocusTarget.LOGIN -> {
                loginSms()
                true
            }

            null -> false
        }
    }

    fun start() {
        if (qr == null && !qrLoading && qrError == null) refreshQr()
    }

    fun refreshQr() {
        if (qrLoading) return
        pollJob?.cancel()
        timerJob?.cancel()
        refreshJob?.cancel()
        qrLoading = true
        qrError = null
        qr = null
        expired = false
        qrStatus = 1
        refreshJob = scope.launch {
            try {
                val created = api.generateQr()
                qr = created
                qrStatus = created.status
                startTimer()
                startPolling(created)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                qrError = e.message ?: "获取二维码失败"
                expired = true
            } finally {
                qrLoading = false
            }
        }
    }

    private fun startTimer() {
        timerJob?.cancel()
        val expiresAt = System.currentTimeMillis() + 180_000L
        timerJob = scope.launch {
            while (true) {
                val left = ((expiresAt - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L).toInt()
                remaining = left
                if (left == 0) {
                    expired = true
                    break
                }
                delay(500)
            }
        }
    }

    private fun startPolling(created: UuApiClient.QrLogin) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var status = created.status
            while (true) {
                delay(1800)
                if (expired) break
                try {
                    val poll = api.pollQr(created.copy(status = status))
                    status = poll.status
                    qrStatus = status
                    if (status == 4) {
                        api.loginByQr(created)
                        completeLogin()
                        break
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    qrError = e.message ?: "扫码状态获取失败"
                    expired = true
                    break
                }
            }
        }
    }

    fun sendSmsCode() {
        if (smsSending || cooldown > 0) return
        val digits = mobile.filter { it.isDigit() }
        if (digits.length < 4) {
            smsError = "请输入有效手机号"
            return
        }
        smsError = null
        smsSending = true
        scope.launch {
            try {
                api.sendSmsCode(countryCode, digits)
                startCooldown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                smsError = e.message ?: "验证码发送失败"
            } finally {
                smsSending = false
            }
        }
    }

    fun loginSms() {
        if (smsLogging) return
        val digits = mobile.filter { it.isDigit() }
        if (digits.length < 4 || smsCode.length != 6) {
            smsError = "请输入有效手机号和 6 位验证码"
            return
        }
        smsError = null
        smsLogging = true
        scope.launch {
            try {
                api.loginBySms(countryCode, digits, smsCode)
                completeLogin()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                smsError = e.message ?: "登录失败"
            } finally {
                smsLogging = false
            }
        }
    }

    private fun startCooldown() {
        cooldownJob?.cancel()
        cooldown = 60
        cooldownJob = scope.launch {
            while (cooldown > 0) {
                delay(1000)
                cooldown -= 1
            }
        }
    }

    private fun completeLogin() {
        if (completed) return
        completed = true
        editing = false
        pollJob?.cancel()
        timerJob?.cancel()
        cooldownJob?.cancel()
        onLoggedIn()
    }

    fun dispose() {
        pollJob?.cancel()
        timerJob?.cancel()
        refreshJob?.cancel()
        cooldownJob?.cancel()
    }
}
