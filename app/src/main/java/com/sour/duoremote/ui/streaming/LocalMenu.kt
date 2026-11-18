package com.sour.duoremote.ui.streaming

import android.view.KeyEvent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors

@Composable
fun LocalMenuOverlay(
    gamepadPassthrough: Boolean,
    ui: StreamingUiState,
    onClose: () -> Unit,
    onToggleGamepad: () -> Unit,
    onSettings: () -> Unit,
    onControlCenter: () -> Unit,
    onDisconnect: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val selected = ui.localMenuIndex
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) {
        ui.localMenuIndex = 0
        runCatching { focus.requestFocus() }
    }

    val options = 5
    fun activate(index: Int) {
        when (index) {
            0 -> onClose()
            1 -> {
                onClose()
                onControlCenter()
            }

            2 -> onToggleGamepad()
            3 -> {
                onClose()
                onSettings()
            }

            4 -> onDisconnect()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .onPreviewKeyEvent { event ->
                val native = event.nativeKeyEvent
                if (native.action != KeyEvent.ACTION_UP) {
                    false
                } else {
                    when (native.keyCode) {
                        KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_LEFT -> {
                            ui.localMenuIndex = (selected - 1 + options) % options
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_DOWN, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            ui.localMenuIndex = (selected + 1) % options
                            true
                        }

                        // 实体丝印 B（canonical A）= 确认；实体 A（canonical B）= 返回。
                        KeyEvent.KEYCODE_BUTTON_A -> {
                            activate(selected)
                            true
                        }

                        KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BACK -> {
                            onClose()
                            true
                        }

                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> true

                        else -> false
                    }
                }
            }
            .focusRequester(focus)
            .focusable()
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(430.dp)
                .clip(RoundedCornerShape(DuoRadius.xl))
                .background(colors.surface1)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(DuoRadius.xl))
                .clickable { }
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "本地菜单",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        if (gamepadPassthrough) {
                            "系统返回打开；直通时不占用任何手柄键"
                        } else {
                            "本地操作时 A 打开；B 确认 / A 关闭"
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                        color = colors.textTertiary,
                    )
                }
                Row(
                    Modifier
                        .height(22.dp)
                        .clip(RoundedCornerShape(DuoRadius.pill))
                        .background(colors.warning.copy(alpha = 0.10f))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Box(Modifier.size(5.dp).background(colors.warning, CircleShape))
                    Text(
                        "远端手柄已暂停",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.warning,
                    )
                }
            }

            MenuOption(
                icon = Icons.Outlined.ArrowBack,
                label = "继续远控",
                selected = selected == 0,
                onClick = { activate(0) },
            )
            MenuOption(
                icon = Icons.Outlined.Tune,
                label = "控制中心",
                selected = selected == 1,
                mode = "画面 / 外设 / 安全",
                onClick = { activate(1) },
            )
            MenuOption(
                icon = Icons.Outlined.SportsEsports,
                label = "手柄模式",
                selected = selected == 2,
                mode = if (gamepadPassthrough) "直通电脑" else "本地操作",
                onClick = { activate(2) },
            )
            MenuOption(
                icon = Icons.Outlined.Settings,
                label = "设置",
                selected = selected == 3,
                onClick = { activate(3) },
            )
            MenuOption(
                icon = Icons.Outlined.PowerSettingsNew,
                label = "断开连接",
                selected = selected == 4,
                danger = true,
                showKey = false,
                onClick = { activate(4) },
            )

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(colors.outline.copy(alpha = 0.5f)),
            )
            Text(
                "打开本地菜单时，应用会先发零值并 gpd_disconnect，释放远端所有按住的键；关闭后恢复直通并重新 gpd_connect。本地操作模式下，手柄才用于导航抽屉。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                color = colors.textTertiary,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun MenuOption(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    mode: String? = null,
    danger: Boolean = false,
    showKey: Boolean = true,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(42.dp)
            .gamepadFocusRing(selected, DuoRadius.md)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(if (selected) colors.accentSoft else colors.surface2)
            .border(
                1.dp,
                when {
                    danger -> colors.error.copy(alpha = 0.22f)
                    selected -> colors.accent.copy(alpha = 0.45f)
                    else -> colors.outline
                },
                RoundedCornerShape(DuoRadius.md),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) colors.error else if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium, fontSize = 12.sp),
            color = if (danger) colors.error else colors.textPrimary,
            modifier = Modifier.weight(1f),
        )
        if (mode != null) {
            Text(mode, style = DuoMonoStyle.copy(fontSize = 10.sp), color = colors.accent)
        }
        if (showKey) {
            Text(
                "B",
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 10.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(5.dp))
                    .border(1.dp, colors.outline, RoundedCornerShape(5.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )
        }
    }
}
