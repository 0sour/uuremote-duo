package com.sour.duoremote.ui.screens.login

import android.view.KeyEvent as AndroidKeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.JsonObject
import com.sour.duoremote.protocol.qrcode.QrCodeGenerator
import com.sour.duoremote.ui.home.DuoBrandMark
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import java.util.Locale

@Composable
fun LoginTopPane(
    controller: LoginController,
) {
    val colors = LocalDuoColors.current
    LaunchedEffect(Unit) {
        controller.start()
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg),
    ) {
        Row(
            Modifier.fillMaxWidth().height(40.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoBrandMark()
            Spacer(Modifier.width(9.dp))
            Text(
                "DuoRemote",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "非官方",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(6.dp)
                    .background(if (controller.mode == LoginMode.QR) colors.warning else colors.accent, RoundedCornerShape(3.dp)),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                if (controller.mode == LoginMode.QR) "等待扫码" else "短信登录",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
            if (controller.mode == LoginMode.QR && controller.qr != null) {
                Spacer(Modifier.width(6.dp))
                Text(
                    String.format(Locale.US, "%02d:%02d", controller.remaining / 60, controller.remaining % 60),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.05f)) {
                if (controller.mode == LoginMode.QR) {
                    Text(
                        "登录 UU 远程账号",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textTertiary,
                    )
                    Text(
                        "用手机扫一下",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Text(
                        "打开手机上的官方 UU 远程 App，扫描右侧二维码并确认登录。登录后会自动同步你账号下的电脑。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    QrSteps(roomy = true)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LoginChip("仅保存在本机")
                        LoginChip("不保存二维码内容")
                    }
                } else {
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.accentSoft)
                            .border(1.dp, colors.accent.copy(alpha = 0.25f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Smartphone, null, tint = colors.accent, modifier = Modifier.size(24.dp))
                    }
                    Text(
                        "短信验证码",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
                        ),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 12.dp),
                    )
                    Text(
                        "不用扫码，收一条短信",
                        style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    Text(
                        "在下屏输入手机号，点“发送验证码”，收到 6 位数字后输入并登录。验证码只用于本次登录，不会保存到应用或日志。",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, lineHeight = 20.sp),
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(Modifier.weight(1f)) {
                            LoginMeta("区号", "${controller.countryCode}（默认）")
                            Spacer(Modifier.height(10.dp))
                            LoginMeta("重发", if (controller.cooldown > 0) "${controller.cooldown} 秒倒计时" else "发送后 60 秒可重发")
                        }
                        Column(Modifier.weight(1f)) {
                            LoginMeta("验证码", "6 位数字")
                            Spacer(Modifier.height(10.dp))
                            LoginMeta("安全", "仅本机加密保存")
                        }
                    }
                }
            }

            if (controller.mode == LoginMode.QR) {
                Column(
                    Modifier
                        .weight(0.95f)
                        .clip(RoundedCornerShape(DuoRadius.xl))
                        .background(colors.surface1)
                        .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.xl))
                        .padding(14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    QrBlock(controller = controller, size = 176.dp)
                    Text(
                        "二维码有效期 3 分钟",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    Text(
                        when {
                            controller.qrError != null -> "获取失败，可在下屏换一张或改用短信登录"
                            controller.expired -> "已过期，请在下屏刷新"
                            controller.qrStatus == 2 -> "已扫码，请在手机上确认登录"
                            else -> "二维码仅在上屏展示，操作请在下屏完成"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = when {
                            controller.qrError != null -> colors.error
                            controller.qrStatus == 2 -> colors.online
                            controller.expired -> colors.warning
                            else -> colors.textTertiary
                        },
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
            }
        }

        LoginFooter(
            left = emptyList(),
            right = "上屏只读 · 不参与手柄焦点 · 登录操作在下屏",
        )
    }
}

@Composable
private fun LoginChip(text: String) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .clip(RoundedCornerShape(DuoRadius.pill))
            .background(colors.surface2)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text("✓", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.accent)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textSecondary)
    }
}

@Composable
private fun LoginMeta(label: String, value: String) {
    val colors = LocalDuoColors.current
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.6.sp),
            color = colors.textTertiary,
        )
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textPrimary,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
private fun LoginFooter(left: List<Pair<String, String>>, right: String) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(34.dp)
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            left.forEach { (key, action) ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        key,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .border(1.dp, colors.outline, RoundedCornerShape(4.dp))
                            .background(colors.surface1)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                    )
                    Text(action, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(right, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
    }
}

@Composable
fun LoginBottomPane(
    controller: LoginController,
    onBack: () -> Unit,
    showQr: Boolean = false,
) {
    val colors = LocalDuoColors.current
    val focusRequester = remember { FocusRequester() }
    BackHandler {
        if (controller.editing) controller.editing = false else onBack()
    }
    LaunchedEffect(Unit) {
        controller.start()
        runCatching { focusRequester.requestFocus() }
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .loginKeyHandler(controller, onBack)
            .focusRequester(focusRequester)
            .focusable(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(44.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (controller.mode == LoginMode.QR) "扫码登录" else "短信验证码登录",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = colors.textPrimary,
                )
                Text(
                    if (controller.mode == LoginMode.QR) "手机与 Duo 需在同一个网络/账号体系"
                    else "输入手机号，接收 6 位验证码",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
            Box(
                Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable {
                        if (controller.mode == LoginMode.SMS) controller.switchMode(LoginMode.QR) else onBack()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "返回", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
            }
        }

        if (controller.mode == LoginMode.QR) {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    Modifier.weight(1.1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        "两步连上你的电脑",
                        style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        "登录只用于同步设备列表和申请远控权限，不会读取账号里的其他数据。",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, lineHeight = 16.sp),
                        color = colors.textSecondary,
                    )
                    QrSteps(roomy = true)
                    LoginNote("首次使用需导入官方 UU TV 3.5.0 APK，用来提取本机请求签名材料；不会上传或写入日志。")
                }
                Column(
                    Modifier.weight(0.9f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DuoRadius.xl))
                            .background(colors.surface1)
                            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.xl))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (showQr) {
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                QrBlock(controller = controller, size = 150.dp)
                            }
                        }
                        QrStateRow(controller)
                        Text(
                            when {
                                controller.qrError != null -> "获取失败，可重试或改用短信登录。"
                                controller.expired -> "二维码已过期，按 Y 或点下方按钮刷新。"
                                controller.qrStatus == 2 -> "已扫码，请在手机上确认登录。"
                                else -> "扫码后手机端会显示“登录确认”，点确认即可。"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                            color = colors.textSecondary,
                        )
                        Button(
                            onClick = { controller.refreshQr() },
                            enabled = !controller.qrLoading,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .gamepadFocusRing(controller.isFocused(LoginFocusTarget.REFRESH), DuoRadius.md),
                            shape = RoundedCornerShape(DuoRadius.md),
                            colors = loginButtonColors(),
                        ) {
                            Text(if (controller.expired || controller.qrError != null) "刷新二维码" else "换一张")
                        }
                        Button(
                            onClick = { controller.switchMode(LoginMode.SMS) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(40.dp)
                                .gamepadFocusRing(controller.isFocused(LoginFocusTarget.SMS_TOGGLE), DuoRadius.md),
                            shape = RoundedCornerShape(DuoRadius.md),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.surface2,
                                contentColor = colors.textPrimary,
                            ),
                        ) {
                            Icon(Icons.Outlined.QrCode, null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("使用短信验证码登录", fontSize = 11.sp)
                        }
                    }
                }
            }
        } else {
            Row(
                Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Column(Modifier.weight(1.55f)) {
                    SmsForm(controller)
                }
                SmsKeypad(controller, Modifier.weight(0.85f))
            }
        }

        LoginFooter(
            left = if (controller.mode == LoginMode.QR) {
                listOf("B" to "选择", "Y" to "刷新", "X" to "短信登录", "A" to "返回")
            } else {
                listOf("B" to "收键盘/确认", "X" to "切换扫码", "A" to "返回")
            },
            right = if (controller.mode == LoginMode.QR) "登录状态保存在本机加密存储" else "发送后 60 秒内可重发",
        )
    }
}

@Composable
private fun LoginNote(text: String) {
    val colors = LocalDuoColors.current
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DuoRadius.md))
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.md))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
        color = colors.textTertiary,
    )
}

@Composable
private fun SmsKeypad(controller: LoginController, modifier: Modifier = Modifier) {
    val colors = LocalDuoColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            "数字键盘",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("＋＊＃", "0", "⌫"),
        )
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { label ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.surface2)
                            .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                            .clickable {
                                when {
                                    label == "⌫" -> {
                                        if (controller.focusedTarget == LoginFocusTarget.CODE && controller.smsCode.isNotEmpty()) {
                                            controller.smsCode = controller.smsCode.dropLast(1)
                                        } else {
                                            controller.mobile = controller.mobile.dropLast(1)
                                        }
                                    }

                                    label.first().isDigit() -> {
                                        if (controller.focusedTarget == LoginFocusTarget.CODE) {
                                            controller.smsCode = (controller.smsCode + label).filter { it.isDigit() }.take(6)
                                        } else {
                                            controller.mobile = (controller.mobile + label).filter { it.isDigit() }.take(15)
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = if (label.length > 1) 12.sp else 16.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = colors.textPrimary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QrStateRow(controller: LoginController) {
    val colors = LocalDuoColors.current
    val label = when {
        controller.qrError != null -> "获取失败"
        controller.expired -> "已过期"
        controller.qrStatus == 2 -> "已扫码"
        controller.qrLoading -> "获取中"
        else -> "等待扫码"
    }
    val dot = when {
        controller.qrError != null -> colors.error
        controller.expired -> colors.warning
        controller.qrStatus == 2 -> colors.online
        else -> colors.accent
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(6.dp).background(dot, RoundedCornerShape(3.dp)))
        Text(
            label,
            modifier = Modifier.padding(start = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = colors.textPrimary,
        )
        Spacer(Modifier.weight(1f))
        if (controller.qr != null && !controller.expired) {
            Text(
                "${controller.remaining}s",
                style = MaterialTheme.typography.labelSmall,
                color = if (controller.remaining <= 30) colors.warning else colors.textTertiary,
            )
        }
    }
}

@Composable
private fun InfoNote(text: String) {
    val colors = LocalDuoColors.current
    Text(
        text,
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface1, RoundedCornerShape(DuoRadius.md))
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.md))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.textSecondary,
    )
}

private fun Modifier.loginKeyHandler(
    controller: LoginController,
    onBack: () -> Unit,
): Modifier = onPreviewKeyEvent { event ->
    val native = event.nativeKeyEvent
    if (native.action != AndroidKeyEvent.ACTION_UP) {
        false
    } else {
        when (native.keyCode) {
            AndroidKeyEvent.KEYCODE_DPAD_UP -> {
                if (controller.editing) return@onPreviewKeyEvent false
                controller.moveFocus(-1)
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_DOWN -> {
                if (controller.editing) return@onPreviewKeyEvent false
                controller.moveFocus(1)
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_LEFT -> {
                if (controller.editing) return@onPreviewKeyEvent false
                controller.moveFocus(-1)
                true
            }

            AndroidKeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (controller.editing) return@onPreviewKeyEvent false
                controller.moveFocus(1)
                true
            }

            AndroidKeyEvent.KEYCODE_BUTTON_Y -> {
                if (controller.mode == LoginMode.QR) controller.refreshQr()
                true
            }

            AndroidKeyEvent.KEYCODE_BUTTON_X -> {
                controller.toggleMode()
                true
            }

            // Events are normalized at the window entry: canonical A = physical B (confirm).
            AndroidKeyEvent.KEYCODE_BUTTON_A -> {
                if (!controller.activateFocused()) onBack()
                true
            }

            AndroidKeyEvent.KEYCODE_BUTTON_B -> {
                onBack()
                true
            }

            // Ghost confirm events from gamepad stacks; canonical A is authoritative.
            AndroidKeyEvent.KEYCODE_DPAD_CENTER, AndroidKeyEvent.KEYCODE_ENTER,
            AndroidKeyEvent.KEYCODE_NUMPAD_ENTER -> true

            else -> false
        }
    }
}

@Composable
fun LoginSingleScreen(
    controller: LoginController,
    onBack: () -> Unit,
) {
    // Single-screen hosts have no second panel; reuse the responsive two-column login pane.
    LoginBottomPane(controller = controller, onBack = onBack, showQr = true)
}

@Composable
private fun LoginBrand(hostLabel: String, exitLabel: String, onExit: () -> Unit) {
    val colors = LocalDuoColors.current
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        DuoBrandMark(28.dp)
        Text("DuoRemote", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(hostLabel, style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        TextButton(onClick = onExit) {
            Text(exitLabel, color = colors.textTertiary)
        }
    }
}

@Composable
private fun QrBlock(controller: LoginController, size: Dp) {
    val colors = LocalDuoColors.current
    val qrUrl = controller.qr?.raw?.stringOrNull("qrcode_jump_url")
    val bitmap = remember(qrUrl) {
        qrUrl?.let { runCatching { QrCodeGenerator.generate(it, size.value.toInt()) }.getOrNull() }
    }
    Box(
        Modifier
            .size(size)
            .background(Color.White, RoundedCornerShape(DuoRadius.xl))
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "登录二维码",
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(if (controller.qrStatus == 2) 0.35f else 1f),
                contentScale = ContentScale.Fit,
            )

            controller.qrLoading -> CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp)
            else -> Text("二维码不可用", color = Color(0xFF6F7C8C))
        }
    }
}

@Composable
private fun QrStatus(controller: LoginController) {
    val colors = LocalDuoColors.current
    val text = when {
        controller.qrError != null -> controller.qrError!!
        controller.expired && controller.qr != null -> "二维码已过期，请刷新"
        controller.qrStatus == 2 -> "已扫码，请在手机上确认"
        controller.qrLoading -> "正在获取二维码…"
        controller.qr != null -> "二维码有效期 ${controller.remaining}s"
        else -> "等待二维码"
    }
    Text(
        text,
        modifier = Modifier.padding(top = 6.dp),
        style = MaterialTheme.typography.labelSmall,
        color = when {
            controller.qrError != null -> colors.error
            controller.qrStatus == 2 -> colors.online
            controller.expired -> colors.warning
            controller.remaining in 1..30 -> colors.warning
            else -> colors.textTertiary
        },
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun QrSteps(roomy: Boolean = false) {
    val colors = LocalDuoColors.current
    Column(verticalArrangement = Arrangement.spacedBy(if (roomy) 9.dp else 6.dp)) {
        Step(1, "手机打开官方 UU 远程 App", colors)
        Step(2, "扫描屏幕上的二维码", colors)
        Step(3, "手机上点“确认登录”", colors)
    }
}

@Composable
private fun Step(number: Int, text: String, colors: com.sour.duoremote.ui.theme.DuoColors) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier
                .size(22.dp)
                .background(colors.surface2, RoundedCornerShape(11.dp))
                .border(1.dp, colors.outline, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$number", style = MaterialTheme.typography.labelSmall, color = colors.accent)
        }
        Text(text, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun SmsForm(controller: LoginController) {
    val colors = LocalDuoColors.current
    val phoneFocus = remember { FocusRequester() }
    val codeFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    LaunchedEffect(controller.editToken) {
        if (!controller.editing) return@LaunchedEffect
        when (controller.focusedTarget) {
            LoginFocusTarget.PHONE -> phoneFocus.requestFocus()
            LoginFocusTarget.CODE -> codeFocus.requestFocus()
            else -> return@LaunchedEffect
        }
        keyboard?.show()
    }
    LaunchedEffect(controller.editing) {
        if (!controller.editing) {
            focusManager.clearFocus()
            keyboard?.hide()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text("手机号", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = controller.countryCode,
                onValueChange = { controller.countryCode = it.take(5) },
                placeholder = { Text("+86", color = colors.textPrimary) },
                singleLine = true,
                modifier = Modifier.width(74.dp).height(56.dp),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = loginFieldColors(),
            )
            OutlinedTextField(
                value = controller.mobile,
                onValueChange = { controller.mobile = it.filter { c -> c.isDigit() }.take(15) },
                placeholder = { Text("输入手机号", color = colors.textTertiary) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .focusRequester(phoneFocus)
                    .onFocusChanged {
                        if (it.isFocused) {
                            controller.focusOn(LoginFocusTarget.PHONE)
                            controller.editing = true
                        }
                    }
                    .gamepadFocusRing(
                        controller.isFocused(LoginFocusTarget.PHONE),
                        DuoRadius.md,
                    ),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                colors = loginFieldColors(),
            )
        }

        Text("验证码", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = controller.smsCode,
                onValueChange = { controller.smsCode = it.filter { c -> c.isDigit() }.take(6) },
                placeholder = { Text("6 位验证码", color = colors.textTertiary) },
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp)
                    .focusRequester(codeFocus)
                    .onFocusChanged {
                        if (it.isFocused) {
                            controller.focusOn(LoginFocusTarget.CODE)
                            controller.editing = true
                        }
                    }
                    .gamepadFocusRing(
                        controller.isFocused(LoginFocusTarget.CODE),
                        DuoRadius.md,
                    ),
                shape = RoundedCornerShape(10.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = loginFieldColors(),
            )
            Button(
                onClick = { controller.sendSmsCode() },
                enabled = !controller.smsSending && controller.cooldown == 0,
                modifier = Modifier
                    .height(42.dp)
                    .gamepadFocusRing(
                        controller.isFocused(LoginFocusTarget.SEND),
                        DuoRadius.md,
                    ),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.surface2,
                    contentColor = colors.textPrimary,
                    disabledContainerColor = colors.surface2,
                    disabledContentColor = colors.textTertiary,
                ),
            ) {
                Text(
                    when {
                        controller.smsSending -> "发送中"
                        controller.cooldown > 0 -> "重发 ${controller.cooldown}s"
                        else -> "获取验证码"
                    },
                    fontSize = 11.sp,
                )
            }
        }
        controller.smsError?.let {
            Text(it, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.error)
        }

        Button(
            onClick = { controller.loginSms() },
            enabled = !controller.smsLogging,
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .gamepadFocusRing(
                    controller.isFocused(LoginFocusTarget.LOGIN),
                    DuoRadius.md,
                ),
            shape = RoundedCornerShape(10.dp),
            colors = loginButtonColors(),
        ) {
            if (controller.smsLogging) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), color = colors.onAccent, strokeWidth = 2.dp)
            } else {
                Text("登录", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Button(
            onClick = { controller.switchMode(LoginMode.QR) },
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .gamepadFocusRing(
                    controller.isFocused(LoginFocusTarget.SMS_TOGGLE),
                    DuoRadius.md,
                ),
            shape = RoundedCornerShape(10.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.surface1,
                contentColor = colors.textPrimary,
            ),
        ) {
            Icon(Icons.Outlined.QrCode, null, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text("返回扫码登录", fontSize = 11.sp)
        }
        LoginNote("验证码由官方 UU 短信服务发送；应用只把手机号/验证码用于登录请求，不落盘。")
    }
}

@Composable
private fun loginButtonColors() = ButtonDefaults.buttonColors(
    containerColor = LocalDuoColors.current.accent,
    contentColor = LocalDuoColors.current.onAccent,
    disabledContainerColor = LocalDuoColors.current.surface2,
    disabledContentColor = LocalDuoColors.current.textTertiary,
)

@Composable
private fun loginFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = LocalDuoColors.current.accent,
    unfocusedBorderColor = LocalDuoColors.current.outline,
    focusedContainerColor = LocalDuoColors.current.surface1,
    unfocusedContainerColor = LocalDuoColors.current.surface1,
    focusedTextColor = LocalDuoColors.current.textPrimary,
    unfocusedTextColor = LocalDuoColors.current.textPrimary,
    cursorColor = LocalDuoColors.current.accent,
    focusedLabelColor = LocalDuoColors.current.accent,
    unfocusedLabelColor = LocalDuoColors.current.textTertiary,
)

private fun JsonObject.stringOrNull(name: String): String? {
    val value = get(name) ?: return null
    if (value.isJsonNull) return null
    return runCatching { value.asString }.getOrNull()?.takeIf { it.isNotBlank() }
}
