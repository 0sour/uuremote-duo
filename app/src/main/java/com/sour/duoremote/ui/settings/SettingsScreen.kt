package com.sour.duoremote.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Wifi
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.BuildConfig
import com.sour.duoremote.R
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.ui.components.DuoActionButton
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoPageHeader
import com.sour.duoremote.ui.components.DuoSettingRow
import com.sour.duoremote.ui.components.DuoTabBar
import com.sour.duoremote.ui.home.DuoBrandMark
import com.sour.duoremote.ui.host.ScreenMode
import com.sour.duoremote.ui.host.SingleHost
import com.sour.duoremote.ui.home.DeviceSort
import com.sour.duoremote.ui.input.GamepadEmulationMode
import com.sour.duoremote.ui.input.GamepadStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import java.util.Locale

internal data class SettingRow(
    val title: String,
    val description: String,
    val value: String,
    val action: () -> Unit,
    val adjust: ((Int) -> Unit)? = null,
    val danger: Boolean = false,
    val enabled: Boolean = true,
    val tag: String? = null,
)

private fun categoryIcon(category: SettingsCategory): ImageVector = when (category) {
    SettingsCategory.PICTURE -> Icons.Outlined.PlayArrow
    SettingsCategory.SCREEN -> Icons.Outlined.Monitor
    SettingsCategory.PERIPHERAL -> Icons.Outlined.SportsEsports
    SettingsCategory.SECURITY -> Icons.Outlined.Lock
    SettingsCategory.NETWORK -> Icons.Outlined.Wifi
    SettingsCategory.APPEARANCE -> Icons.Outlined.Palette
    SettingsCategory.ABOUT -> Icons.Outlined.Info
}

@Composable
fun SettingsTopPreview(
    settings: DuoSettings,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoBrandMark(22.dp)
            Spacer(Modifier.width(9.dp))
            Text(
                "DuoRemote",
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "设置",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(DuoRadius.pill))
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            )
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.Settings, null, tint = colors.textTertiary, modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(6.dp))
            Text("改动会立即保存", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1.1f)) {
                Text(
                    "当前配置",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textTertiary,
                )
                Text(
                    "${resolutionLabel(settings.resolution)} · ${settings.fps} FPS · ${qualityLabel(settings.quality)}",
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 28.sp),
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    Column(Modifier.weight(1f)) {
                        PreviewValue("画面缩放", scaleLabel(settings.videoScale))
                        Spacer(Modifier.height(10.dp))
                        val emulation = GamepadEmulationMode.fromStorage(settings.gamepadEmulation)
                        PreviewValue(
                            "手柄",
                            gamepadStyleLabel(settings.gamepadStyle) + " · " + when {
                                emulation != GamepadEmulationMode.OFF -> "映射 · ${emulation.label}"
                                settings.gamepadPassthrough -> "直通电脑"
                                else -> "本地操作"
                            },
                        )
                        Spacer(Modifier.height(10.dp))
                        PreviewValue("触摸板", String.format(Locale.US, "%.1f× 灵敏度", settings.touchpadSensitivity))
                    }
                    Column(Modifier.weight(1f)) {
                        PreviewValue("码率", if (settings.bitrateMbps <= 0) "自动 / 上限 100 Mbps" else "${settings.bitrateMbps} Mbps")
                        Spacer(Modifier.height(10.dp))
                        PreviewValue("观看", if (settings.viewOnly) "仅观看 · 不发送输入" else "可正常操作")
                        Spacer(Modifier.height(10.dp))
                        PreviewValue("屏幕", screenModeLabel(settings.screenMode) + " · " + singleHostLabel(settings.singleHost))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatusPill("7 类设置", colors.accent, colors.accentSoft)
                    StatusPill("设置只保存在本机", colors.textTertiary, Color.Transparent)
                }
            }

            Column(
                Modifier
                    .weight(0.9f)
                    .fillMaxHeight(0.82f)
                    .clip(RoundedCornerShape(DuoRadius.xl))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.xl))
                    .padding(16.dp),
            ) {
                Text("设置分类", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
                Spacer(Modifier.height(8.dp))
                SettingsCategory.entries.chunked(4).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { category ->
                            Text(
                                category.label,
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                                color = colors.textSecondary,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(DuoRadius.pill))
                                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                                    .padding(horizontal = 9.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "下屏管理分类、选项和确认层；上屏只显示摘要，不参与手柄焦点。",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp),
                    color = colors.textTertiary,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(34.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("上屏只读 · 不参与手柄焦点", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
            Spacer(Modifier.weight(1f))
            Text("下屏：B 选择 · A 返回 · L1/R1 切分类", style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
        }
    }
}

@Composable
private fun StatusPill(text: String, color: Color, background: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(DuoRadius.pill))
            .background(background)
            .border(
                1.dp,
                if (background == Color.Transparent) LocalDuoColors.current.outline else Color.Transparent,
                RoundedCornerShape(DuoRadius.pill),
            )
            .padding(horizontal = 9.dp, vertical = 4.dp),
    )
}

@Composable
private fun PreviewValue(key: String, value: String) {
    val colors = LocalDuoColors.current
    Column {
        Text(key, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp), color = colors.textTertiary)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

@Composable
fun SettingsBottomScreen(
    settings: DuoSettings,
    uiState: SettingsUiState,
    onChange: (DuoSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPane(settings, uiState, onChange, onBack, modifier)
}

@Composable
fun SettingsSingleScreen(
    settings: DuoSettings,
    uiState: SettingsUiState,
    onChange: (DuoSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPane(settings, uiState, onChange, onBack, modifier)
}

@Composable
private fun SettingsPane(
    settings: DuoSettings,
    uiState: SettingsUiState,
    onChange: (DuoSettings) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    val colors = LocalDuoColors.current
    val rows = settingsRows(uiState.category, settings, onChange) {
        uiState.confirmReset = true
        uiState.confirmSelection = 0
    }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    BackHandler {
        if (uiState.confirmReset) uiState.confirmReset = false else onBack()
    }
    LaunchedEffect(uiState.category) {
        runCatching { listState.scrollToItem(0) }
        repeat(2) {
            runCatching { focus.requestFocus() }
            kotlinx.coroutines.delay(60)
        }
    }
    LaunchedEffect(uiState.rowIndex) {
        runCatching { listState.animateScrollToItem(uiState.rowIndex.coerceAtLeast(0)) }
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { focus.requestFocus() }
            kotlinx.coroutines.delay(100)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            DuoPageHeader(
                title = "设置",
                subtitle = "画面 · 屏幕 · 外设 · 安全 · 网络 · 外观 · 关于",
                onBack = onBack,
                trailing = if (wide) "改动即时保存" else null,
                compact = !wide,
            )
            if (!wide) {
                DuoTabBar(
                    items = SettingsCategory.entries.map { it.label },
                    selectedIndex = uiState.category.ordinal,
                    onSelect = uiState::selectCategory,
                    compact = true,
                )
            }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = if (wide) 14.dp else 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (wide) {
                    CategoryRail(uiState)
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    if (uiState.category == SettingsCategory.ABOUT) {
                        AboutBrandHeader()
                    }
                    LazyColumn(
                        Modifier.fillMaxWidth().weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        itemsIndexed(rows) { index, row ->
                            DuoSettingRow(
                                title = row.title,
                                description = row.description,
                                value = row.value,
                                selected = index == uiState.rowIndex,
                                enabled = row.enabled,
                                danger = row.danger,
                                tag = row.tag,
                                height = 52,
                                onClick = {
                                    uiState.rowIndex = index
                                    row.action()
                                },
                            )
                        }
                    }
                }
            }
            SettingsFooter()
        }
        if (uiState.confirmReset) {
            SettingsConfirm(uiState = uiState, onCancel = { uiState.confirmReset = false }, onConfirm = {
                uiState.confirmReset = false
                onChange(DuoSettings())
            })
        }
        Box(
            Modifier
                .matchParentSize()
                .focusRequester(focus)
                .focusable(),
        )
    }
}

@Composable
private fun CategoryRail(uiState: SettingsUiState) {
    val colors = LocalDuoColors.current
    Column(
        Modifier
            .width(150.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SettingsCategory.entries.forEach { item ->
            val selected = item == uiState.category
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(DuoRadius.sm))
                    .background(if (selected) colors.accentSoft else Color.Transparent)
                    .clickable { uiState.selectCategory(item.ordinal) }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    categoryIcon(item),
                    contentDescription = null,
                    tint = if (selected) colors.accent else colors.textSecondary,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) colors.accent else colors.textSecondary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "L1 / R1 切分类",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

@Composable
private fun AboutBrandHeader() {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surfaceInset)
                .border(1.dp, colors.outline, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.ic_brand_mark),
                contentDescription = null,
                modifier = Modifier.size(32.dp),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                "DuoRemote",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                ),
                color = colors.textPrimary,
            )
            Text(
                "非官方 UU 远程客户端 · v${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun SettingsFooter() {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("L1/R1 分类", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Text("←→ 调整", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Text("B 选择", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        Text("A 返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
    }
}

@Composable
private fun SettingsConfirm(
    uiState: SettingsUiState,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = LocalDuoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onCancel),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 360.dp)
                .fillMaxWidth(0.84f)
                .clip(RoundedCornerShape(DuoRadius.xl))
                .background(colors.surface1)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(DuoRadius.xl))
                .clickable { }
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("确认", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
            Text(
                "恢复默认设置？不会清除登录态和签名材料。",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DuoActionButton(
                    DuoAction("取消"),
                    selected = uiState.confirmSelection == 0,
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    height = 38,
                )
                DuoActionButton(
                    DuoAction("恢复默认", primary = true),
                    selected = uiState.confirmSelection == 1,
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    height = 38,
                )
            }
        }
    }
}

internal fun settingsRows(
    category: SettingsCategory,
    settings: DuoSettings,
    onChange: (DuoSettings) -> Unit,
    onRestoreDefaults: () -> Unit,
): List<SettingRow> {
    fun row(
        title: String,
        description: String,
        value: String,
        enabled: Boolean = true,
        danger: Boolean = false,
        tag: String? = null,
        adjust: ((Int) -> Unit)? = null,
        action: (() -> Unit)? = null,
    ): SettingRow {
        val effectiveAdjust = adjust
        val effectiveAction: () -> Unit = action ?: { effectiveAdjust?.invoke(1) }
        return SettingRow(
            title = title,
            description = description,
            value = value,
            enabled = enabled,
            danger = danger,
            tag = tag,
            action = effectiveAction,
            adjust = effectiveAdjust,
        )
    }

    fun stringCycle(values: List<String>, current: String, apply: (String) -> Unit): (Int) -> Unit = { delta ->
        val step = if (delta == 0) 1 else delta
        val index = values.indexOf(current).let { if (it < 0) 0 else it }
        apply(values[(index + step + values.size) % values.size])
    }

    fun boolToggle(current: Boolean, apply: (Boolean) -> Unit): (Int) -> Unit = {
        apply(!current)
    }

    return when (category) {
        SettingsCategory.PICTURE -> listOf(
            row(
                "分辨率",
                "修改后需重连视频",
                resolutionLabel(settings.resolution),
                adjust = stringCycle(listOf("1920x1080", "1280x720", "window"), settings.resolution) {
                    onChange(settings.copy(resolution = it))
                },
            ),
            row(
                "帧率",
                "30 / 60 FPS",
                "${settings.fps} FPS",
                adjust = { delta ->
                    onChange(settings.copy(fps = if (delta < 0) 30 else 60))
                },
            ),
            row(
                "画质",
                "流畅 / 标准 / 高清",
                qualityLabel(settings.quality),
                adjust = stringCycle(listOf("smooth", "standard", "hd"), settings.quality) {
                    onChange(settings.copy(quality = it))
                },
            ),
            row(
                "码率上限",
                "自动或手动上限",
                if (settings.bitrateMbps == 0) "自动" else "${settings.bitrateMbps} Mbps",
                adjust = { delta ->
                    val values = listOf(0, 20, 50, 100)
                    val index = values.indexOf(settings.bitrateMbps).coerceAtLeast(0)
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(bitrateMbps = values[(index + step + values.size) % values.size]))
                },
            ),
            row(
                "画面缩放",
                "适应 / 铺满 / 原始比例",
                scaleLabel(settings.videoScale),
                adjust = stringCycle(listOf("fit", "fill", "original"), settings.videoScale) {
                    onChange(settings.copy(videoScale = it))
                },
            ),
        )

        SettingsCategory.SCREEN -> listOf(
            row(
                "屏幕模式",
                "自动：有下屏用双屏，无下屏用单屏",
                screenModeLabel(settings.screenMode),
                adjust = { delta ->
                    val values = ScreenMode.entries
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(screenMode = values[(settings.screenMode.ordinal + step + values.size) % values.size]))
                },
            ),
            row(
                "单屏宿主",
                "单屏模式下使用哪块屏",
                singleHostLabel(settings.singleHost),
                adjust = { delta ->
                    val values = SingleHost.entries
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(singleHost = values[(settings.singleHost.ordinal + step + values.size) % values.size]))
                },
            ),
            row(
                "后台小窗",
                "单屏串流时按 Home 进入画中画",
                if (settings.pipOnBackground) "开" else "关",
                adjust = boolToggle(settings.pipOnBackground) {
                    onChange(settings.copy(pipOnBackground = it))
                },
            ),
            row(
                "设备排序",
                "主页与设备管理页的列表顺序",
                DeviceSort.fromStorage(settings.deviceSort).label,
                adjust = { delta ->
                    val values = DeviceSort.entries
                    val current = DeviceSort.fromStorage(settings.deviceSort)
                    val step = if (delta == 0) 1 else delta
                    onChange(
                        settings.copy(
                            deviceSort = values[(current.ordinal + step + values.size) % values.size].storage,
                        ),
                    )
                },
            ),
        )

        SettingsCategory.PERIPHERAL -> listOf(
            row(
                "手柄风格",
                "入口归一化为标准 Xbox",
                gamepadStyleLabel(settings.gamepadStyle),
                adjust = { delta ->
                    val values = GamepadStyle.entries
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(gamepadStyle = values[(settings.gamepadStyle.ordinal + step + values.size) % values.size]))
                },
            ),
            row(
                "串流默认",
                "直通电脑或本地操作工具箱",
                if (settings.gamepadPassthrough) "直通电脑" else "本地操作",
                adjust = boolToggle(settings.gamepadPassthrough) { onChange(settings.copy(gamepadPassthrough = it)) },
            ),
            row(
                "手柄映射",
                "实体手柄转键鼠模板；关闭时直通电脑或本地操作",
                GamepadEmulationMode.fromStorage(settings.gamepadEmulation).label,
                adjust = { delta ->
                    val values = GamepadEmulationMode.entries
                    val current = GamepadEmulationMode.fromStorage(settings.gamepadEmulation)
                    val step = if (delta == 0) 1 else delta
                    val next = values[(current.ordinal + step + values.size) % values.size]
                    onChange(settings.copy(gamepadEmulation = next.storage))
                },
            ),
            row(
                "振动",
                "远端 gpd_vibration",
                if (settings.vibration) "开" else "关",
                adjust = boolToggle(settings.vibration) { onChange(settings.copy(vibration = it)) },
            ),
            row(
                "摇杆死区",
                "中心不动区，默认 16%",
                "${(settings.gamepadDeadzone * 100).toInt()}%",
                adjust = { delta ->
                    val values = listOf(0.08f, 0.16f, 0.25f)
                    val index = values.indexOfFirst {
                        kotlin.math.abs(it - settings.gamepadDeadzone) < 0.001f
                    }.coerceAtLeast(0)
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(gamepadDeadzone = values[(index + step + values.size) % values.size]))
                },
            ),
            row(
                "左摇杆 Y 反向",
                "仅影响远端直通",
                if (settings.invertLeftY) "开" else "关",
                adjust = boolToggle(settings.invertLeftY) { onChange(settings.copy(invertLeftY = it)) },
            ),
            row(
                "右摇杆 Y 反向",
                "仅影响远端直通",
                if (settings.invertRightY) "开" else "关",
                adjust = boolToggle(settings.invertRightY) { onChange(settings.copy(invertRightY = it)) },
            ),
            row(
                "触摸板灵敏度",
                "0.4×–2.5×",
                String.format(Locale.US, "%.1f×", settings.touchpadSensitivity),
                adjust = { delta ->
                    val values = listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f)
                    val index = values.indexOfFirst { it == settings.touchpadSensitivity }.coerceAtLeast(0)
                    val step = if (delta == 0) 1 else delta
                    onChange(settings.copy(touchpadSensitivity = values[(index + step + values.size) % values.size]))
                },
            ),
            row(
                "直接触摸视频",
                "触摸画面即移动鼠标；默认关闭",
                if (settings.directTouch) "开" else "关",
                adjust = boolToggle(settings.directTouch) { onChange(settings.copy(directTouch = it)) },
            ),
            row(
                "默认输入",
                "内置拼音或系统 IME",
                if (settings.keyboardDefault == "system") "系统 IME" else "内置拼音",
                adjust = { onChange(settings.copy(keyboardDefault = if (settings.keyboardDefault == "system") "builtin" else "system")) },
            ),
            row(
                "候选数",
                "5–9 个候选",
                "${settings.candidateCount}",
                adjust = { delta ->
                    val next = if (delta < 0) {
                        if (settings.candidateCount <= 5) 9 else settings.candidateCount - 1
                    } else {
                        if (settings.candidateCount >= 9) 5 else settings.candidateCount + 1
                    }
                    onChange(settings.copy(candidateCount = next))
                },
            ),
            row(
                "音频",
                "播放电脑声音",
                if (settings.audio) "开" else "关",
                adjust = boolToggle(settings.audio) { onChange(settings.copy(audio = it)) },
            ),
        )

        SettingsCategory.SECURITY -> listOf(
            row(
                "同步剪贴板",
                "文字双向同步；默认关闭",
                if (settings.clipboardSync) "开" else "关",
                adjust = boolToggle(settings.clipboardSync) { onChange(settings.copy(clipboardSync = it)) },
            ),
            row(
                "仅观看",
                "本机不发送键鼠、手柄和文字；保持到手动关闭",
                if (settings.viewOnly) "开" else "关",
                danger = settings.viewOnly,
                adjust = boolToggle(settings.viewOnly) { onChange(settings.copy(viewOnly = it)) },
            ),
            row(
                "锁屏保护",
                "需要被控端支持",
                "后续",
                enabled = false,
                tag = "后续",
            ),
            row(
                "防窥屏",
                "需要被控端支持",
                "后续",
                enabled = false,
                tag = "后续",
            ),
            row(
                "指纹确认",
                "本机生物识别绑定",
                "后续",
                enabled = false,
                tag = "后续",
            ),
        )

        SettingsCategory.NETWORK -> listOf(
            row(
                "诊断统计",
                "显示脱敏的 FPS/码率/RTT",
                if (settings.diagnostics) "开" else "关",
                adjust = boolToggle(settings.diagnostics) { onChange(settings.copy(diagnostics = it)) },
            ),
            row(
                "自动重连",
                "网络中断后尝试恢复",
                if (settings.autoReconnect) "开" else "关",
                adjust = boolToggle(settings.autoReconnect) { onChange(settings.copy(autoReconnect = it)) },
            ),
            row(
                "连接方式",
                "P2P 优先，失败后走中继",
                "自动",
                enabled = false,
                tag = "只读",
            ),
        )

        SettingsCategory.APPEARANCE -> listOf(
            row(
                "主题",
                "暗色 / 亮色 / 跟随系统",
                themeLabel(settings.theme),
                adjust = stringCycle(listOf("dark", "light", "system"), settings.theme) {
                    onChange(settings.copy(theme = it))
                },
            ),
            row(
                "焦点环",
                "白环 + 柔光，或纯青色",
                if (settings.focusRingStyle == "cyan") "纯青色" else "白环 + 柔光",
                adjust = { onChange(settings.copy(focusRingStyle = if (settings.focusRingStyle == "cyan") "white" else "cyan")) },
            ),
            row(
                "触感反馈",
                "按键触感",
                if (settings.hapticFeedback) "开" else "关",
                adjust = boolToggle(settings.hapticFeedback) { onChange(settings.copy(hapticFeedback = it)) },
            ),
            row(
                "按键声音",
                "按键提示音",
                if (settings.soundFeedback) "开" else "关",
                adjust = boolToggle(settings.soundFeedback) { onChange(settings.copy(soundFeedback = it)) },
            ),
        )

        SettingsCategory.ABOUT -> listOf(
            row(
                "版本",
                "DuoRemote 非官方客户端",
                BuildConfig.VERSION_NAME,
                enabled = false,
                tag = "只读",
            ),
            row(
                "许可证",
                "开源许可证",
                "GPL-3.0",
                enabled = false,
                tag = "只读",
            ),
            row(
                "签名材料",
                "已加密保存；不会写入日志",
                "已保存",
                enabled = false,
                tag = "只读",
            ),
            row(
                "恢复默认",
                "不清除登录态、签名材料和设备列表",
                "恢复",
                danger = true,
                action = onRestoreDefaults,
            ),
        )
    }
}

private fun resolutionLabel(value: String): String = when (value) {
    "1280x720" -> "720p"
    "window" -> "跟随窗口"
    else -> "1080p"
}

private fun qualityLabel(value: String): String = when (value) {
    "smooth" -> "流畅"
    "standard" -> "标准"
    else -> "高清"
}

private fun scaleLabel(value: String): String = when (value) {
    "fill" -> "铺满"
    "original" -> "原始"
    else -> "适应"
}

private fun screenModeLabel(value: ScreenMode): String = when (value) {
    ScreenMode.AUTO -> "自动"
    ScreenMode.DUAL -> "双屏"
    ScreenMode.SINGLE -> "单屏"
}

private fun singleHostLabel(value: SingleHost): String = when (value) {
    SingleHost.AUTO -> "自动"
    SingleHost.TOP -> "上屏"
    SingleHost.BOTTOM -> "下屏"
}

private fun gamepadStyleLabel(value: GamepadStyle): String = when (value) {
    GamepadStyle.NORMAL -> "标准"
    GamepadStyle.RETRO -> "Retro"
}

private fun themeLabel(value: String): String = when (value) {
    "light" -> "亮色"
    "system" -> "跟随系统"
    else -> "暗色"
}
