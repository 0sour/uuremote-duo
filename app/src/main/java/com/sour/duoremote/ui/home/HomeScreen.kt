package com.sour.duoremote.ui.home

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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.R
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoActionBar
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoColors
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class DemoDevice(
    val id: String,
    val name: String,
    val os: String,
    val online: Boolean,
    val lastSeen: String,
    val resolution: String,
)

val EmptyDevice = DemoDevice("", "还没有设备", "—", false, "—", "—")

val DemoDevices = listOf(
    DemoDevice("desktop", "客厅台式机", "Windows 11", true, "今天 14:32", "1920×1080"),
    DemoDevice("mac", "MacBook Pro", "macOS 15", true, "昨天 21:10", "2560×1600"),
    DemoDevice("living", "客厅 mini PC", "Windows 11", false, "3 天前", "1920×1080"),
    DemoDevice("bedroom", "卧室笔记本", "Windows 10", true, "上周", "1920×1080"),
)

private fun nowLabel(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())

/** Black host used by the top Activity while the whole UI lives on the bottom panel. */
@Composable
fun BlackHost(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Color.Black))
}

/* ------------------------------------------------------------------ */
/* Shared atoms — values mirror docs/ui/mockup.css                      */
/* ------------------------------------------------------------------ */

@Composable
internal fun DuoBrandMark(size: Dp = 22.dp) {
    val colors = LocalDuoColors.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.32f))
            .background(colors.surfaceInset)
            .border(1.dp, colors.outline, RoundedCornerShape(size * 0.32f)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.ic_brand_mark),
            contentDescription = null,
            modifier = Modifier.size(size * 0.82f),
        )
    }
}

@Composable
private fun StatusDot(
    color: Color,
    size: Dp = 7.dp,
) {
    Box(Modifier.size(size).background(color, CircleShape))
}

@Composable
internal fun DuoChip(
    text: String,
    tone: ChipTone = ChipTone.Ghost,
    leadingOnlineDot: Boolean = false,
) {
    val colors = LocalDuoColors.current
    val (fg, bg, border) = when (tone) {
        ChipTone.Online -> Triple(colors.online, colors.online.copy(alpha = 0.10f), Color.Transparent)
        ChipTone.Accent -> Triple(colors.accent, colors.accentSoft, Color.Transparent)
        ChipTone.Ghost -> Triple(colors.textSecondary, Color.Transparent, colors.outline)
    }
    Row(
        Modifier
            .clip(RoundedCornerShape(DuoRadius.pill))
            .background(bg)
            .then(if (border != Color.Transparent) Modifier.border(1.dp, border, RoundedCornerShape(DuoRadius.pill)) else Modifier)
            .padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (leadingOnlineDot) StatusDot(colors.online, 5.dp)
        Text(text, style = MaterialTheme.typography.labelSmall, color = fg, maxLines = 1)
    }
}

internal enum class ChipTone { Online, Accent, Ghost }

@Composable
internal fun DuoKeycap(text: String, accent: Boolean = false, small: Boolean = false) {
    val colors = LocalDuoColors.current
    val height = if (small) 18.dp else 30.dp
    val hPad = if (small) 5.dp else 9.dp
    Box(
        Modifier
            .height(height)
            .clip(RoundedCornerShape(if (small) 4.dp else 9.dp))
            .background(if (accent) colors.accent else colors.surface2)
            .border(
                1.dp,
                if (accent) colors.accent else colors.outlineStrong,
                RoundedCornerShape(if (small) 4.dp else 9.dp),
            )
            .padding(horizontal = hPad),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (accent) colors.onAccent else if (small) colors.textSecondary else colors.textPrimary,
            style = if (small) DuoMonoStyle.copy(fontSize = 9.sp) else MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun DuoIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    size: Dp = 34.dp,
    iconSize: Dp = 18.dp,
    tint: Color? = null,
) {
    val colors = LocalDuoColors.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = tint ?: colors.textSecondary,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun FooterKeys(
    items: List<Pair<String, String>>,
    trailing: String,
    compact: Boolean = false,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 28.dp else 34.dp)
            .padding(horizontal = if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (compact) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { (key, action) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    DuoKeycap(key, small = true)
                    Text(
                        action,
                        style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
                        else MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                    )
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            trailing,
            style = if (compact) MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp)
            else MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Device card                                                         */
/* ------------------------------------------------------------------ */

@Composable
private fun DeviceCard(
    device: DemoDevice,
    selected: Boolean,
    compact: Boolean,
    showFocus: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val height = if (compact) 64.dp else 76.dp
    val radius = DuoRadius.lg
    Row(
        modifier
            .fillMaxWidth()
            .height(height)
            .alpha(if (device.online) 1f else 0.55f)
            .gamepadFocusRing(selected && showFocus, radius)
            .drawWithContent {
                drawContent()
                if (selected) {
                    val barWidth = 3.dp.toPx()
                    val inset = 12.dp.toPx()
                    drawRoundRect(
                        color = colors.accent,
                        topLeft = Offset(-7.dp.toPx(), inset),
                        size = Size(barWidth, size.height - inset * 2),
                        cornerRadius = CornerRadius(barWidth / 2),
                    )
                }
            }
            .clip(RoundedCornerShape(radius))
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.45f) else colors.outline,
                RoundedCornerShape(radius),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(if (compact) 34.dp else 38.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(colors.surface2)
                .border(1.dp, colors.outline, RoundedCornerShape(11.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (device.os.startsWith("mac", ignoreCase = true)) Icons.Outlined.Laptop else Icons.Outlined.Monitor,
                contentDescription = null,
                tint = if (device.online) colors.accent else colors.textTertiary,
                modifier = Modifier.size(if (compact) 18.dp else 20.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                device.name,
                style = if (compact) MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                else MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                StatusDot(if (device.online) colors.online else colors.textTertiary, 6.dp)
                Text(
                    if (device.online) "在线" else "离线",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 10.sp else 11.sp),
                    color = colors.textTertiary,
                )
                StatusDot(colors.textTertiary, 3.dp)
                Text(
                    device.os,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 10.sp else 11.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!compact && device.lastSeen.isNotBlank() && device.lastSeen != "—") {
                    StatusDot(colors.textTertiary, 3.dp)
                    Text(
                        device.lastSeen,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
        if (!compact) {
            Text(
                "我的",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                modifier = Modifier
                    .border(1.dp, colors.outline, RoundedCornerShape(6.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun EmptyDeviceList(modifier: Modifier = Modifier) {
    val colors = LocalDuoColors.current
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(1.dp, colors.outline, CircleShape)
                .background(colors.surface1),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Monitor, null, tint = colors.accent, modifier = Modifier.size(28.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text("还没有设备", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = colors.textSecondary)
        Text(
            "在电脑上打开 UU 远程并登录，然后按 Y 刷新",
            style = MaterialTheme.typography.labelSmall,
            color = colors.textTertiary,
        )
    }
}

/* ------------------------------------------------------------------ */
/* Dual-screen home                                                    */
/* ------------------------------------------------------------------ */

@Composable
private fun HomeActionBar(
    homeUi: HomeUiState,
    hasDevice: Boolean,
    onConnect: () -> Unit,
    onPreferences: () -> Unit,
    onSettings: () -> Unit,
    onAccount: () -> Unit,
) {
    DuoActionBar(
        actions = listOf(
            DuoAction("连接", Icons.Outlined.PlayArrow, primary = true, enabled = hasDevice),
            DuoAction("偏好", Icons.Outlined.Tune, enabled = hasDevice),
            DuoAction("设置", Icons.Outlined.Settings),
            DuoAction("账号", Icons.Outlined.Person),
        ),
        focusIndex = if (homeUi.focus == HomeFocus.ACTIONS) homeUi.actionIndex else null,
        onAction = {
            when (it) {
                0 -> onConnect()
                1 -> onPreferences()
                2 -> onSettings()
                3 -> onAccount()
            }
        },
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        height = 38,
    )
}

@Composable
fun DualBottomScreen(
    devices: List<DemoDevice>,
    selectedId: String,
    homeUi: HomeUiState,
    onSelect: (DemoDevice) -> Unit,
    onConfirm: (DemoDevice) -> Unit,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPreferences: () -> Unit,
    onBack: () -> Unit,
    preferenceSummary: String,
    sessionStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    var status by remember { mutableStateOf<String?>(null) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull() ?: EmptyDevice
    val rootFocus = remember { FocusRequester() }
    val online = devices.count { it.online }
    val offline = devices.count { !it.online }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2600)
            status = null
        }
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { rootFocus.requestFocus() }
            delay(100)
        }
    }
    BackHandler(onBack = onBack)

    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .focusRequester(rootFocus)
            .focusable(),
    ) {
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
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "设备",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
                    color = colors.textPrimary,
                )
                Text(
                    sessionStatus ?: status ?: "$online 台在线 · $offline 台离线 · $preferenceSummary",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (sessionStatus != null) colors.accent else colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            DuoIconButton(Icons.Outlined.Person, "账号", onAccount)
            DuoIconButton(Icons.Outlined.Refresh, "刷新设备", onClick = {
                onRefresh()
                status = "正在刷新设备列表…"
            })
            DuoIconButton(Icons.Outlined.Settings, "设置", onOpenSettings)
        }

        if (devices.isEmpty()) {
            Box(Modifier.weight(1f)) { EmptyDeviceList() }
        } else {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(devices, key = { _, item -> item.id }) { _, device ->
                    DeviceCard(
                        device = device,
                        selected = device.id == selected.id,
                        compact = false,
                        showFocus = homeUi.focus == HomeFocus.LIST,
                        onClick = { onSelect(device) },
                    )
                }
            }
        }

        HomeActionBar(
            homeUi = homeUi,
            hasDevice = selected.id.isNotEmpty(),
            onConnect = { onConfirm(selected) },
            onPreferences = onOpenPreferences,
            onSettings = onOpenSettings,
            onAccount = onAccount,
        )

        FooterKeys(
            items = listOf(
                "B" to "选择/连接",
                "A" to "返回",
                "Y" to "刷新",
                "X" to "账号",
                "START" to "说明",
            ),
            trailing = if (selected.id.isEmpty()) "等待绑定电脑" else "焦点: ${selected.name}",
            compact = true,
        )
    }
}

@Composable
fun TopDetailScreen(
    device: DemoDevice,
    onlineCount: Int,
    preferenceSummary: String,
    sessionStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val hasDevice = device.id.isNotEmpty()

    Column(modifier.fillMaxSize().background(colors.bg)) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                DuoBrandMark()
                Text(
                    "DuoRemote",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                    color = colors.textPrimary,
                )
                DuoChip("非官方", ChipTone.Ghost)
            }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatusDot(if (sessionStatus != null) colors.warning else colors.online, 6.dp)
                Text(
                    sessionStatus ?: "$onlineCount 台设备在线",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = if (sessionStatus != null) colors.warning else colors.textTertiary,
                    maxLines = 1,
                )
                Text("·", style = MaterialTheme.typography.labelSmall, color = colors.textTertiary)
                Text(nowLabel(), style = DuoMonoStyle.copy(fontSize = 11.sp), color = colors.textTertiary)
            }
        }

        Row(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1.35f)) {
                Text(
                    if (hasDevice) "我的电脑 · 已选中" else "我的电脑",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        letterSpacing = 1.4.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.textTertiary,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                Text(
                    device.name,
                    style = MaterialTheme.typography.displayLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(bottom = 22.dp),
                ) {
                    DuoChip(
                        if (device.online) "在线" else "离线",
                        if (device.online) ChipTone.Online else ChipTone.Ghost,
                        leadingOnlineDot = device.online,
                    )
                    if (device.os.isNotBlank() && device.os != "—") DuoChip(device.os)
                    DuoChip("我的设备")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(1f)) { MetaItem("分辨率", device.resolution, mono = true) }
                    Column(Modifier.weight(1f)) { MetaItem("上次连接", device.lastSeen) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    Column(Modifier.weight(1f)) { MetaItem("连接偏好", preferenceSummary) }
                    Column(Modifier.weight(1f)) { MetaItem("账号", "本机账号 · 我的设备") }
                }
            }

            Box(
                Modifier
                    .weight(0.9f)
                    .height(230.dp)
                    .clip(RoundedCornerShape(DuoRadius.xl))
                    .background(
                        Brush.linearGradient(
                            listOf(Color.White.copy(alpha = 0.045f), Color.White.copy(alpha = 0.015f)),
                        ),
                    )
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.xl))
                    .drawBehind {
                        drawCircle(
                            brush = Brush.radialGradient(
                                listOf(colors.accent.copy(alpha = 0.18f), Color.Transparent),
                                center = Offset(size.width, 0f),
                                radius = size.width * 0.75f,
                            ),
                            radius = size.width * 0.75f,
                            center = Offset(size.width, 0f),
                        )
                    }
                    .padding(20.dp),
            ) {
                Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                    Column {
                        Text(
                            if (hasDevice) "准备远控" else "尚未绑定电脑",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold,
                            ),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                        Text(
                            if (hasDevice) "连接后，上屏显示桌面\n下屏切换为工具箱"
                            else "在手机上打开 UU 远程\n绑定电脑后即可远控",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp,
                            ),
                            color = colors.textPrimary,
                        )
                        Text(
                            if (hasDevice) "触摸板、键盘、快捷键和状态面板都在下屏；手柄会直通这台电脑。"
                            else "登录状态与设备列表只保存在本机，不会上传。",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp, lineHeight = 19.sp),
                            color = colors.textSecondary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            DuoKeycap(if (hasDevice) "B" else "Y", accent = true)
                            Text(
                                if (hasDevice) "连接" else "刷新",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.textPrimary,
                            )
                            Box(Modifier.width(1.dp).height(18.dp).background(colors.outline))
                            DuoKeycap("A")
                            Text("返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = colors.textSecondary)
                        }
                        Text(
                            if (hasDevice) "首次连接会请求远控权限；若电脑端有其他会话，会提示接管。"
                            else "刷新后再选择要连接的电脑。",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.textTertiary,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "上屏只读 · 不参与手柄焦点",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "连接偏好在下方动作条调整",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
internal fun MetaItem(label: String, value: String, mono: Boolean = false) {
    val colors = LocalDuoColors.current
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 11.sp, letterSpacing = 0.6.sp, fontWeight = FontWeight.Medium,
            ),
            color = colors.textTertiary,
        )
        Text(
            value,
            style = if (mono) DuoMonoStyle.copy(fontSize = 12.sp)
            else MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
            color = colors.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/* ------------------------------------------------------------------ */
/* Single-screen home                                                  */
/* ------------------------------------------------------------------ */

@Composable
fun SingleHomeScreen(
    devices: List<DemoDevice>,
    selectedId: String,
    hostLabel: String,
    homeUi: HomeUiState,
    onSelect: (DemoDevice) -> Unit,
    onConfirm: (DemoDevice) -> Unit,
    onRefresh: () -> Unit,
    onAccount: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPreferences: () -> Unit,
    onBack: () -> Unit,
    preferenceSummary: String,
    sessionStatus: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    var status by remember { mutableStateOf<String?>(null) }
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull() ?: EmptyDevice
    val rootFocus = remember { FocusRequester() }
    val online = devices.count { it.online }
    val offline = devices.count { !it.online }

    LaunchedEffect(status) {
        if (status != null) {
            delay(2600)
            status = null
        }
    }
    LaunchedEffect(Unit) {
        repeat(3) {
            runCatching { rootFocus.requestFocus() }
            delay(100)
        }
    }
    BackHandler(onBack = onBack)

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val compact = maxWidth < 700.dp
        Column(
            Modifier
                .fillMaxSize()
                .focusRequester(rootFocus)
                .focusable(),
        ) {
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
                DuoBrandMark()
                Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        "DuoRemote",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
                        color = colors.textPrimary,
                    )
                    Text(
                        "非官方 UU 远程客户端",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.textTertiary,
                    )
                }
                DuoChip(hostLabel, ChipTone.Ghost)
                Spacer(Modifier.weight(1f))
                if (sessionStatus != null) {
                    Text(
                        sessionStatus,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.accent,
                        maxLines = 1,
                    )
                } else {
                    DuoChip("$online 台在线", if (online > 0) ChipTone.Online else ChipTone.Ghost, online > 0)
                }
                DuoIconButton(Icons.Outlined.Person, "账号", onAccount)
                DuoIconButton(Icons.Outlined.Refresh, "刷新设备", onClick = {
                    onRefresh()
                    status = "正在刷新设备列表…"
                })
                DuoIconButton(Icons.Outlined.Settings, "设置", onOpenSettings)
            }

            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(Modifier.weight(0.58f).fillMaxHeight()) {
                    Row(
                        Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "我的电脑",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.textSecondary,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            status ?: "$online 台在线 · $offline 台离线",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colors.textTertiary,
                        )
                    }
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(DuoRadius.lg))
                            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg)),
                    ) {
                        if (devices.isEmpty()) {
                            EmptyDeviceList()
                        } else {
                            LazyColumn(
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                itemsIndexed(devices, key = { _, item -> item.id }) { _, device ->
                                    DeviceCard(
                                        device = device,
                                        selected = device.id == selected.id,
                                        compact = compact,
                                        showFocus = homeUi.focus == HomeFocus.LIST,
                                        onClick = { onSelect(device) },
                                    )
                                }
                            }
                        }
                    }
                }

                DeviceDetailCard(
                    device = selected,
                    compact = compact,
                    preferenceSummary = preferenceSummary,
                    showKeyHints = homeUi.focus == HomeFocus.LIST,
                    modifier = Modifier.weight(0.42f).fillMaxHeight(),
                )
            }

            HomeActionBar(
                homeUi = homeUi,
                hasDevice = selected.id.isNotEmpty(),
                onConnect = { onConfirm(selected) },
                onPreferences = onOpenPreferences,
                onSettings = onOpenSettings,
                onAccount = onAccount,
            )

            FooterKeys(
                items = listOf(
                    "B" to "选择/连接",
                    "A" to "返回",
                    "Y" to "刷新",
                    "X" to "账号",
                    "START" to "说明",
                ),
                trailing = if (selected.id.isEmpty()) "等待绑定电脑" else "焦点: ${selected.name}",
                compact = true,
            )
        }
    }
}

@Composable
private fun DeviceDetailCard(
    device: DemoDevice,
    compact: Boolean,
    preferenceSummary: String,
    showKeyHints: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val hasDevice = device.id.isNotEmpty()
    Column(
        modifier
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(if (compact) 12.dp else 18.dp),
    ) {
        Text(
            if (hasDevice) "已选中" else "我的电脑",
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.SemiBold,
            ),
            color = colors.textTertiary,
        )
        Text(
            device.name,
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = if (compact) 20.sp else 24.sp,
            ),
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DuoChip(
                if (device.online) "在线" else "离线",
                if (device.online) ChipTone.Online else ChipTone.Ghost,
                leadingOnlineDot = device.online,
            )
            if (device.os.isNotBlank() && device.os != "—") DuoChip(device.os)
        }

        if (hasDevice) {
            Spacer(Modifier.height(if (compact) 10.dp else 14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 10.dp)) {
                MetaItem("显示器", device.resolution, mono = true)
                MetaItem("连接偏好", preferenceSummary)
                MetaItem("上次连接", device.lastSeen)
                if (!compact) {
                    MetaItem("网络", "局域网 · P2P 优先")
                    MetaItem("账号", "本机账号 · 我的设备")
                } else {
                    MetaItem("系统", device.os)
                }
            }
        } else {
            Spacer(Modifier.height(10.dp))
            Text(
                "在手机上打开 UU 远程并绑定电脑，然后按 Y 刷新。",
                style = MaterialTheme.typography.labelSmall,
                color = colors.textSecondary,
            )
        }

        Spacer(Modifier.weight(1f))
        if (hasDevice) {
            if (!compact) {
                Text(
                    "连接后视频铺满，工具箱从底部抽屉展开；手柄默认直通电脑。",
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 17.sp),
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            if (showKeyHints) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    DuoKeycap("B", accent = true)
                    Text(
                        "连接",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Spacer(Modifier.width(4.dp))
                    DuoKeycap("A")
                    Text("返回", style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp), color = colors.textSecondary)
                }
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DuoKeycap("Y", accent = true)
                Text(
                    "刷新设备",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = colors.textPrimary,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------ */
/* Key routing is centralized in MainActivity so the top window, the  */
/* bottom Presentation and touch all drive the same selection.         */
/* ------------------------------------------------------------------ */

