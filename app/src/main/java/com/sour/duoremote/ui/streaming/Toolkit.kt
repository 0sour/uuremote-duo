package com.sour.duoremote.ui.streaming

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.Mouse
import androidx.compose.material.icons.outlined.OpenWith
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.VolumeOff
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.os.SystemClock
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.session.SessionController
import com.sour.duoremote.session.StreamStats
import com.sour.duoremote.ui.components.feedbackAction
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoColors
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun ToolkitToolbar(
    title: String,
    status: String?,
    stats: StreamStats,
    muted: Boolean,
    gamepadPassthrough: Boolean,
    onMenu: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleGamepad: () -> Unit,
    onSettings: () -> Unit,
    onDisconnect: () -> Unit,
    onCollapse: (() -> Unit)? = null,
    compact: Boolean = false,
    diagnostics: Boolean = true,
    streamChip: String,
    viewOnly: Boolean = false,
    onOpenControlCenter: () -> Unit,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(44.dp)
            .padding(horizontal = 16.dp)
            .drawBehind {
                drawLine(
                    colors.outline.copy(alpha = 0.55f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    1.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).background(if (status != null) colors.online else colors.warning, CircleShape))
        Text(
            title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text(
            if (status != null) "已连接" else "连接中",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textSecondary,
            modifier = Modifier
                .height(20.dp)
                .clip(RoundedCornerShape(DuoRadius.pill))
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                .padding(horizontal = 8.dp, vertical = 3.dp),
        )
        Row(
            Modifier
                .height(20.dp)
                .clip(RoundedCornerShape(DuoRadius.pill))
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                .clickable(onClick = onOpenControlCenter)
                .padding(horizontal = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                streamChip,
                style = DuoMonoStyle.copy(fontSize = 9.sp),
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
        if (viewOnly) {
            Row(
                Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .background(colors.warning.copy(alpha = 0.10f))
                    .border(1.dp, colors.warning.copy(alpha = 0.32f), RoundedCornerShape(DuoRadius.pill))
                    .clickable(onClick = onOpenControlCenter)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    "仅观看",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.warning,
                    maxLines = 1,
                )
            }
        } else {
            Row(
                Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .background(if (gamepadPassthrough) colors.accentSoft else colors.warning.copy(alpha = 0.10f))
                    .border(
                        1.dp,
                        if (gamepadPassthrough) colors.accent.copy(alpha = 0.28f) else colors.warning.copy(alpha = 0.32f),
                        RoundedCornerShape(DuoRadius.pill),
                    )
                    .clickable(onClick = onToggleGamepad)
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Outlined.SportsEsports,
                    contentDescription = null,
                    tint = if (gamepadPassthrough) colors.accent else colors.warning,
                    modifier = Modifier.size(12.dp),
                )
                Text(
                    if (gamepadPassthrough) "手柄 直通电脑" else "手柄 本地操作",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = if (gamepadPassthrough) colors.accent else colors.warning,
                    maxLines = 1,
                )
            }
        }
        if (!compact && diagnostics) {
            Text(
                String.format(Locale.US, "%.0f ms · %.0f FPS", stats.rttMs, stats.fps),
                style = DuoMonoStyle.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        Spacer(Modifier.weight(1f))
        ToolbarIcon(Icons.Outlined.Menu, "本地菜单", onMenu)
        if (onCollapse != null) {
            ToolbarIcon(Icons.Outlined.KeyboardArrowDown, "收起抽屉", onCollapse)
        } else {
            ToolbarIcon(if (muted) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp, "静音", onToggleMute)
            ToolbarIcon(Icons.Outlined.Settings, "设置", onSettings)
        }
        ToolbarIcon(Icons.Outlined.PowerSettingsNew, "断开", onDisconnect, danger = true)
    }
}

@Composable
private fun ToolbarIcon(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    danger: Boolean = false,
) {
    val colors = LocalDuoColors.current
    val feedback = feedbackAction(onClick)
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = feedback),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = if (danger) colors.error else colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
fun ToolkitTabs(
    tab: ToolTab,
    onTab: (ToolTab) -> Unit,
    onSystemIme: () -> Unit,
    compact: Boolean = false,
    focusedIndex: Int? = null,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 36.dp else 38.dp)
            .padding(horizontal = 16.dp)
            .drawBehind {
                drawLine(
                    colors.outline.copy(alpha = 0.55f),
                    Offset(0f, size.height),
                    Offset(size.width, size.height),
                    1.dp.toPx(),
                )
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ToolTab.entries.forEach { item ->
            val selected = item == tab
            Box(Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .height(28.dp)
                        .gamepadFocusRing(focusedIndex == item.ordinal, DuoRadius.sm)
                        .clip(RoundedCornerShape(DuoRadius.sm))
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .clickable { onTab(item) }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        item.label,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                        color = if (selected) colors.accent else colors.textSecondary,
                    )
                }
                if (selected) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .width(18.dp)
                            .height(2.dp)
                            .background(colors.accent, RoundedCornerShape(1.dp)),
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .height(28.dp)
                .gamepadFocusRing(focusedIndex == 4, DuoRadius.sm)
                .clip(RoundedCornerShape(DuoRadius.sm))
                .clickable(onClick = onSystemIme)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                Icons.Outlined.Keyboard,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "系统输入法",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.accent,
            )
        }
    }
}

@Composable
fun ToolkitBody(
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    stats: StreamStats,
    compact: Boolean,
    onReconnect: () -> Unit,
    onBackToList: () -> Unit,
) {
    val bodyFocus = if (ui.toolkitFocusRow == 1) ui.toolkitBodyIndex else null
    when (ui.tab) {
        ToolTab.TOUCHPAD -> TouchpadPanel(ui, settings, session, compact, bodyFocus)
        ToolTab.KEYBOARD -> KeyboardPanel(ui, settings, session, compact)
        ToolTab.SHORTCUTS -> ShortcutsPanel(session, compact, bodyFocus)
        ToolTab.STATS -> StatsPanel(
            stats = stats,
            compact = compact,
            diagnostics = settings.diagnostics,
            onReconnect = onReconnect,
            onBackToList = onBackToList,
            focusedIndex = bodyFocus,
        )
    }
}

@Composable
fun TouchpadPanel(
    ui: StreamingUiState,
    settings: DuoSettings,
    session: SessionController,
    compact: Boolean,
    focusedIndex: Int? = null,
) {
    val colors = LocalDuoColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(DuoRadius.lg))
                .background(colors.surfaceInset)
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                .drawBehind {
                    val step = 18.dp.toPx()
                    val radius = 0.6.dp.toPx()
                    val dot = colors.textPrimary.copy(alpha = 0.05f)
                    var y = step / 2f
                    while (y < size.height) {
                        var x = step / 2f
                        while (x < size.width) {
                            drawCircle(dot, radius, Offset(x, y))
                            x += step
                        }
                        y += step
                    }
                    val center = Offset(size.width * ui.cursorX, size.height * ui.cursorY)
                    drawCircle(colors.accent.copy(alpha = 0.16f), 8.dp.toPx(), center)
                    drawCircle(colors.accent, 5.dp.toPx(), center)
                }
                .pointerInput(settings.touchpadSensitivity) {
                    val touchSlop = viewConfiguration.touchSlop
                    val longPressTimeout = viewConfiguration.longPressTimeoutMillis
                    fun moveCursor(delta: Offset) {
                        val sensitivity = settings.touchpadSensitivity
                        ui.cursorX = (ui.cursorX + delta.x / size.width * sensitivity).coerceIn(0f, 1f)
                        ui.cursorY = (ui.cursorY + delta.y / size.height * sensitivity).coerceIn(0f, 1f)
                        session.mouseAbsolute(ui.cursorX, ui.cursorY)
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        val startAt = down.uptimeMillis
                        var last = down.position
                        var travel = 0f
                        var twoFinger = false
                        var scrollTravel = 0f
                        var longDragging = false
                        while (true) {
                            val event = if (!twoFinger && travel <= touchSlop && !longDragging) {
                                val remaining = startAt + longPressTimeout - SystemClock.uptimeMillis()
                                withTimeoutOrNull(remaining.coerceAtLeast(1L)) { awaitPointerEvent() }
                            } else {
                                awaitPointerEvent()
                            }
                            if (event == null) {
                                // Finger held still: press left and turn the rest of the gesture into a drag.
                                if (!ui.dragLock) {
                                    session.mouseAbsolute(ui.cursorX, ui.cursorY)
                                    session.mouseButton(1, true)
                                    longDragging = true
                                }
                                continue
                            }
                            val pressed = event.changes.filter { it.pressed }
                            if (pressed.isEmpty()) break
                            if (pressed.size >= 2) {
                                twoFinger = true
                                val delta = pressed
                                    .map { it.positionChange() }
                                    .reduce { a, b -> a + b } / pressed.size.toFloat()
                                scrollTravel += delta.getDistance()
                                if (delta.y != 0f) {
                                    session.scroll(0, (delta.y * 2f).roundToInt())
                                }
                                event.changes.forEach { it.consume() }
                            } else {
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: event.changes.firstOrNull { it.pressed }
                                    ?: break
                                val delta = change.position - last
                                last = change.position
                                travel += delta.getDistance()
                                when {
                                    longDragging -> {
                                        moveCursor(delta)
                                        change.consume()
                                    }

                                    !twoFinger && travel > touchSlop -> {
                                        moveCursor(delta)
                                        change.consume()
                                    }

                                    else -> Unit
                                }
                            }
                        }
                        when {
                            longDragging -> session.mouseButton(1, false)
                            twoFinger && scrollTravel <= touchSlop &&
                                SystemClock.uptimeMillis() - startAt < 320L -> {
                                session.mouseAbsolute(ui.cursorX, ui.cursorY)
                                session.mouseClick(2)
                            }

                            !twoFinger && travel <= touchSlop -> {
                                session.mouseAbsolute(ui.cursorX, ui.cursorY)
                                session.mouseClick(1)
                            }
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 14.dp)
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .background(Color.Black.copy(alpha = 0.62f))
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    "单指移动 · 点击左键 · 双指滚动 · 双指点击右键 · 长按拖拽",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                )
            }
            Text(
                "${String.format(Locale.US, "%.1f", settings.touchpadSensitivity)}×",
                modifier = Modifier.align(Alignment.TopEnd).padding(10.dp),
                style = DuoMonoStyle.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PadButton(
                label = "左键",
                icon = Icons.Outlined.Mouse,
                primary = true,
                focused = focusedIndex == 0,
                modifier = Modifier.weight(1f),
            ) {
                touchpadClick(ui, session, 1)
            }
            PadButton(
                "中键",
                focused = focusedIndex == 1,
                modifier = Modifier.weight(1f),
            ) {
                touchpadClick(ui, session, 16)
            }
            PadButton(
                "右键",
                focused = focusedIndex == 2,
                modifier = Modifier.weight(1f),
            ) {
                touchpadClick(ui, session, 2)
            }
            PadButton(
                label = "拖拽锁定",
                icon = Icons.Outlined.OpenWith,
                active = ui.dragLock,
                focused = focusedIndex == 3,
                modifier = Modifier.weight(1.2f),
            ) {
                toggleDragLock(ui, session)
            }
            Box(
                Modifier
                    .size(40.dp)
                    .gamepadFocusRing(focusedIndex == 4, 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                    .clickable { },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Settings, "灵敏度", tint = colors.textSecondary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun PadButton(
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    primary: Boolean = false,
    active: Boolean = false,
    focused: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val fg = when {
        primary -> colors.onAccent
        active -> colors.accent
        else -> colors.textSecondary
    }
    val feedback = feedbackAction(onClick)
    Row(
        modifier
            .height(40.dp)
            .gamepadFocusRing(focused, 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    primary -> colors.accent
                    active -> colors.accentSoft
                    else -> colors.surface1
                },
            )
            .border(
                1.dp,
                when {
                    primary -> colors.accent
                    active -> colors.accent.copy(alpha = 0.4f)
                    else -> colors.outline
                },
                RoundedCornerShape(10.dp),
            )
            .clickable(onClick = feedback),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
        }
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg,
            maxLines = 1,
        )
    }
}

data class ToolkitShortcut(val label: String, val keys: List<Int>)

val ToolkitShortcuts: List<ToolkitShortcut> = listOf(
    ToolkitShortcut("Ctrl + C", listOf(17, 67)),
    ToolkitShortcut("Ctrl + V", listOf(17, 86)),
    ToolkitShortcut("Ctrl + X", listOf(17, 88)),
    ToolkitShortcut("Ctrl + Z", listOf(17, 90)),
    ToolkitShortcut("Ctrl + A", listOf(17, 65)),
    ToolkitShortcut("Alt + Tab", listOf(18, 9)),
    ToolkitShortcut("Win + D", listOf(91, 68)),
    ToolkitShortcut("Win + E", listOf(91, 69)),
    ToolkitShortcut("Esc", listOf(27)),
    ToolkitShortcut("Tab", listOf(9)),
    ToolkitShortcut("Ctrl + Shift + Esc", listOf(17, 16, 27)),
    ToolkitShortcut("Win + L", listOf(91, 76)),
)

@Composable
fun ShortcutsPanel(
    session: SessionController,
    compact: Boolean,
    focusedIndex: Int? = null,
) {
    val colors = LocalDuoColors.current
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToolkitShortcuts.chunked(4).forEachIndexed { rowIndex, row ->
                Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEachIndexed { column, shortcut ->
                        val index = rowIndex * 4 + column
                        Column(
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .gamepadFocusRing(focusedIndex == index, 10.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(colors.surface1)
                                .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
                                .clickable { session.shortcut(shortcut.keys) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                shortcut.label,
                                style = DuoMonoStyle.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                                color = if (shortcut.label == "Win + L") colors.error else colors.accent,
                                maxLines = 1,
                            )
                            Text(
                                shortcutName(shortcut.label),
                                modifier = Modifier.padding(top = 4.dp),
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                                color = colors.textSecondary,
                                maxLines = 1,
                            )
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
        Text(
            "组合键按顺序发送并自动释放；切页/断开时释放所有按键。",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun shortcutName(label: String): String = when (label) {
    "Ctrl + C" -> "复制"
    "Ctrl + V" -> "粘贴"
    "Ctrl + X" -> "剪切"
    "Ctrl + Z" -> "撤销"
    "Ctrl + A" -> "全选"
    "Alt + Tab" -> "切换窗口"
    "Win + D" -> "显示桌面"
    "Win + E" -> "文件管理器"
    "Esc" -> "退出/取消"
    "Tab" -> "切换焦点"
    "Ctrl + Shift + Esc" -> "任务管理器"
    "Win + L" -> "锁定电脑"
    else -> ""
}

@Composable
fun StatsPanel(
    stats: StreamStats,
    compact: Boolean,
    onReconnect: () -> Unit,
    onBackToList: () -> Unit,
    focusedIndex: Int? = null,
    diagnostics: Boolean = true,
) {
    val colors = LocalDuoColors.current
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (diagnostics) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("帧率", formatFps(stats.fps), "FPS", Modifier.weight(1f))
                StatCard("码率", String.format(Locale.US, "%.1f", stats.bitrateMbps), "Mbps", Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard("延迟", String.format(Locale.US, "%.0f", stats.rttMs), "ms", Modifier.weight(1f))
                StatCard("丢包", stats.packetsLost.toString(), "pkts", Modifier.weight(1f))
            }
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(DuoRadius.lg))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                StatLine("画面", if (stats.width > 0) "${stats.width}×${stats.height}" else "—")
                StatLine("编码 / 解码", stats.codec?.removePrefix("video/")?.let { "$it · MediaCodec" } ?: "H.264 · MediaCodec")
                StatLine("会话", formatElapsed(stats.elapsedSeconds))
            }
        } else {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(DuoRadius.lg))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg)),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "诊断统计已关闭",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                )
                Text(
                    "可在 设置 → 网络 → 诊断统计 中开启",
                    modifier = Modifier.padding(top = 6.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PadButton(
                "重新连接",
                modifier = Modifier.weight(1f),
                focused = focusedIndex == 0,
                onClick = onReconnect,
            )
            PadButton(
                "返回设备列表",
                modifier = Modifier.weight(1f),
                primary = true,
                focused = focusedIndex == 1,
                onClick = onBackToList,
            )
        }
    }
}

@Composable
private fun StatCard(label: String, value: String, unit: String, modifier: Modifier) {
    val colors = LocalDuoColors.current
    Column(
        modifier
            .height(76.dp)
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.8.sp),
            color = colors.textTertiary,
        )
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = DuoMonoStyle.copy(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                " $unit",
                modifier = Modifier.padding(bottom = 2.dp, start = 2.dp),
                style = DuoMonoStyle.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun StatLine(key: String, value: String) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(26.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(key, style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp), color = colors.textTertiary)
        Text(value, style = DuoMonoStyle.copy(fontSize = 11.sp), color = colors.textSecondary)
    }
}

internal fun touchpadClick(ui: StreamingUiState, session: SessionController, button: Int) {
    session.mouseAbsolute(ui.cursorX, ui.cursorY)
    session.mouseClick(button)
}

internal fun toggleDragLock(ui: StreamingUiState, session: SessionController) {
    ui.dragLock = !ui.dragLock
    session.mouseButton(1, ui.dragLock)
}

private fun formatFps(value: Double): String =
    if (value <= 0.0) "—" else String.format(Locale.US, "%.0f", value)

internal fun formatElapsed(seconds: Long): String {
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
    else String.format(Locale.US, "%02d:%02d", m, s)
}
