package com.sour.duoremote.ui.screens.keysetup

import android.content.Context
import android.net.Uri
import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sour.duoremote.BuildConfig
import com.sour.duoremote.data.SecureStore
import com.sour.duoremote.protocol.keys.DexKeyExtractor
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private sealed interface KeySetupState {
    data object Idle : KeySetupState
    data object Extracting : KeySetupState
    data class Ready(val fingerprintPrefix: String) : KeySetupState
    data class Error(val message: String) : KeySetupState
}

@Composable
fun KeySetupScreen(
    secure: SecureStore,
    onReady: () -> Unit,
    onExit: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = LocalDuoColors.current
    var state by remember { mutableStateOf<KeySetupState>(KeySetupState.Idle) }
    var pickedName by remember { mutableStateOf<String?>(null) }
    var focusIndex by remember { mutableStateOf(0) }
    val rootFocus = remember { FocusRequester() }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        runCatching { rootFocus.requestFocus() }
        if (uri == null) return@rememberLauncherForActivityResult
        pickedName = uri.lastPathSegment
        state = KeySetupState.Extracting
        scope.launch {
            val result = withContext(Dispatchers.IO) { importApk(context, uri) }
            result.fold(
                onSuccess = { key ->
                    secure.saveSigningKey(key)
                    state = KeySetupState.Ready(BuildConfig.UU_KEY_FINGERPRINT.take(8))
                    delay(350)
                    onReady()
                },
                onFailure = { error ->
                    state = KeySetupState.Error(
                        when (error) {
                            is DexKeyExtractor.Failure.MissingApk -> "找不到所选文件"
                            is DexKeyExtractor.Failure.NoDex -> "该 APK 中没有 classes*.dex"
                            is DexKeyExtractor.Failure.KeyNotFound -> "无法从该 APK 提取有效签名材料；请确认是官方 TV 3.5.0"
                            is DexKeyExtractor.Failure.ReadError -> "读取 APK 失败，请重新选择"
                            else -> "导入失败，请重试"
                        },
                    )
                },
            )
        }
    }

    fun openPicker() {
        if (state == KeySetupState.Extracting) return
        picker.launch(
            arrayOf(
                "application/vnd.android.package-archive",
                "application/octet-stream",
                "*/*",
            ),
        )
    }

    BackHandler(onBack = onExit)
    LaunchedEffect(Unit) {
        runCatching { rootFocus.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.bg)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_UP) {
                    false
                } else {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                        KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            focusIndex = 1 - focusIndex
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_A -> {
                            if (focusIndex == 0) openPicker() else onExit()
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            onExit()
                            true
                        }

                        // Swallow synthetic confirm events; BUTTON_A above is authoritative.
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                        KeyEvent.KEYCODE_NUMPAD_ENTER -> true

                        else -> false
                    }
                }
            }
            .focusRequester(rootFocus)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 560.dp)
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                "DuoRemote",
                style = MaterialTheme.typography.displayLarge,
                color = colors.textPrimary,
            )
            Text(
                "首次使用需要导入签名材料",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textSecondary,
            )
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(colors.surface1, RoundedCornerShape(DuoRadius.xl))
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.xl))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "选择官方 UU 远程 TV 3.5.0 APK",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
                Text(
                    "应用只在本机读取 APK 内的 classes*.dex，用 SHA-256 找到 24 字节签名材料，" +
                        "加密保存后删除临时文件。不会安装、执行或上传 APK。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
                Text(
                    "官方 Android 4.46 APK 不含该材料，必须使用 TV 3.5.0。",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                )
                Button(
                    onClick = { openPicker() },
                    enabled = state != KeySetupState.Extracting,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(46.dp)
                        .gamepadFocusRing(focusIndex == 0, DuoRadius.lg),
                    shape = RoundedCornerShape(DuoRadius.lg),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent,
                        disabledContainerColor = colors.surface2,
                        disabledContentColor = colors.textTertiary,
                    ),
                ) {
                    if (state == KeySetupState.Extracting) {
                        CircularProgressIndicator(
                            modifier = Modifier.height(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent,
                        )
                        Text("  正在解析…")
                    } else {
                        Text("选择官方 TV APK")
                    }
                }
            }

            when (val s = state) {
                KeySetupState.Idle -> {
                    Text(
                        if (pickedName == null) "等待选择文件" else "已选择：$pickedName",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textTertiary,
                    )
                }

                KeySetupState.Extracting -> {
                    Text(
                        "正在读取 DEX 并校验指纹…",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                    )
                }

                is KeySetupState.Ready -> {
                    Text(
                        "材料已加密保存 · 指纹 ${s.fingerprintPrefix}…",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.online,
                    )
                }

                is KeySetupState.Error -> {
                    Text(
                        s.message,
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.error,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            TextButton(
                onClick = onExit,
                modifier = Modifier.gamepadFocusRing(focusIndex == 1, DuoRadius.lg),
            ) {
                Text("退出", color = colors.textTertiary)
            }
            Text(
                "↑↓ 选择    A 确认    B 返回",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
            Text(
                "非官方客户端，与网易无隶属关系。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textTertiary,
            )
        }
    }
}

private fun importApk(context: Context, uri: Uri): Result<ByteArray> {
    val temp = File(context.cacheDir, "key-import.apk")
    return try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(temp).use { output -> input.copyTo(output) }
        } ?: return Result.failure(DexKeyExtractor.Failure.MissingApk())

        val result = DexKeyExtractor.extract(temp, BuildConfig.UU_KEY_FINGERPRINT)
        Result.success(result.key)
    } catch (e: Exception) {
        Result.failure(e)
    } finally {
        runCatching { temp.delete() }
    }
}
