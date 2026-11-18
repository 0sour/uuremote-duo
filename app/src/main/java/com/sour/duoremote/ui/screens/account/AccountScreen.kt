package com.sour.duoremote.ui.screens.account

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.outlined.Logout
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.components.feedbackAction
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.home.ChipTone
import com.sour.duoremote.ui.home.DuoBrandMark
import com.sour.duoremote.ui.home.DuoChip
import com.sour.duoremote.ui.home.DuoKeycap
import com.sour.duoremote.ui.home.FooterKeys
import com.sour.duoremote.ui.home.MetaItem
import com.sour.duoremote.ui.theme.DuoMonoStyle
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import java.util.Locale

/**
 * Dual-screen account pane. The top display shows the account summary plus a mirror of the
 * actions; the bottom display owns the same selection index so either screen can drive it.
 */
@Composable
fun AccountTopPreview(
    accountName: String,
    deviceCount: Int,
    onlineCount: Int,
    version: String,
    status: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    Column(modifier.fillMaxSize().background(colors.bg)) {
        AccountHeader(compact = false)
        Row(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 24.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Column(Modifier.weight(1.25f)) {
                Text(
                    "当前账号",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp, letterSpacing = 0.8.sp, fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textTertiary,
                )
                Text(
                    accountName,
                    modifier = Modifier.padding(top = 8.dp),
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 30.sp),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    modifier = Modifier.padding(top = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    DuoChip("已登录", ChipTone.Online, leadingOnlineDot = true)
                    DuoChip("非官方", ChipTone.Ghost)
                    DuoChip("GPL-3.0", ChipTone.Ghost)
                }
                Spacer(Modifier.height(22.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(34.dp)) {
                    MetaItem("在线设备", "$onlineCount 台")
                    MetaItem("设备总数", "$deviceCount 台")
                    MetaItem("签名材料", "已保存")
                }
                Row(
                    modifier = Modifier.padding(top = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(34.dp),
                ) {
                    MetaItem("会话", "未连接")
                    MetaItem("版本", version, mono = true)
                    MetaItem("账号数据", "仅本机加密保存")
                }
                if (status != null) {
                    Text(
                        status,
                        modifier = Modifier.padding(top = 14.dp),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    "退出登录只清除 token，签名材料和画质设置保留。",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
            Column(Modifier.weight(0.9f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(RoundedCornerShape(DuoRadius.lg))
                        .background(colors.surface1)
                        .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "账号操作在下屏",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textSecondary,
                    )
                    AccountInfoLine("刷新设备", "重新拉取这台账号的设备列表")
                    AccountInfoLine("退出登录", "二次确认后清除本机 token")
                    AccountInfoLine("返回", "A 返回设备列表")
                    Spacer(Modifier.weight(1f))
                    Text(
                        "上屏只读，不参与手柄焦点；下屏保留完整操作和焦点环。",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 15.sp),
                        color = colors.textTertiary,
                    )
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
                "账号信息仅保存在本机",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun AccountInfoLine(label: String, detail: String) {
    val colors = LocalDuoColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(colors.surface2)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.md))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 11.sp),
            color = colors.textPrimary,
        )
        Text(
            detail,
            modifier = Modifier.padding(top = 2.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun AccountSingleScreen(
    accountName: String,
    deviceCount: Int,
    onlineCount: Int,
    version: String,
    selectedIndex: Int,
    confirmLogout: Boolean,
    status: String? = null,
    onRefresh: () -> Unit,
    onLogout: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    BackHandler(onBack = onBack)
    Column(modifier.fillMaxSize().background(colors.bg)) {
        AccountHeader(compact = true, onBack = onBack)
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(68.dp)
                    .clip(RoundedCornerShape(DuoRadius.lg))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(colors.accentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        accountName.take(1).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colors.accent,
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        accountName,
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$onlineCount 台在线 · 共 $deviceCount 台 · $version",
                        modifier = Modifier.padding(top = 2.dp),
                        style = DuoMonoStyle.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                    )
                }
                DuoChip("已登录", ChipTone.Online, leadingOnlineDot = true)
            }
            if (status != null) {
                Text(
                    status,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.accent,
                )
            }
            AccountActionRow(
                label = "刷新设备",
                detail = "重新拉取这台账号的设备列表",
                icon = Icons.Outlined.Refresh,
                selected = selectedIndex == 0,
                onClick = onRefresh,
            )
            AccountActionRow(
                label = if (confirmLogout) "确认退出登录" else "退出登录",
                detail = if (confirmLogout) "再按一次 B 确认；A 取消" else "清除本机 token，回到扫码登录",
                icon = Icons.Outlined.Logout,
                selected = selectedIndex == 1,
                danger = true,
                onClick = onLogout,
            )
            AccountActionRow(
                label = "返回",
                detail = "回到设备列表",
                icon = Icons.Outlined.KeyboardArrowLeft,
                selected = selectedIndex == 2,
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "退出登录只清除 token，签名材料和画质设置保留。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
        FooterKeys(
            items = listOf("↑↓" to "移动", "B" to "选择", "A" to "返回"),
            trailing = "账号信息仅保存在本机",
            compact = true,
        )
    }
}

@Composable
private fun AccountHeader(compact: Boolean, onBack: (() -> Unit)? = null) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 40.dp)
            .padding(horizontal = if (compact) 16.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (compact) {
            Text(
                "账号",
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "本机账号 · 已登录",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        } else {
            DuoBrandMark()
            Text(
                "DuoRemote",
                modifier = Modifier.padding(start = 8.dp),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary,
            )
            Spacer(Modifier.width(8.dp))
            DuoChip("账号", ChipTone.Ghost)
        }
        Spacer(Modifier.weight(1f))
        if (onBack != null) {
            Box(
                Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.KeyboardArrowLeft,
                    contentDescription = "返回",
                    tint = colors.textTertiary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

@Composable
private fun AccountActionRow(
    label: String,
    detail: String,
    icon: ImageVector,
    selected: Boolean,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalDuoColors.current
    val feedback = feedbackAction(onClick)
    Row(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .gamepadFocusRing(selected, DuoRadius.md)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(if (selected) colors.accentSoft else colors.surface2)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.35f) else colors.outline,
                RoundedCornerShape(DuoRadius.md),
            )
            .clickable(onClick = feedback)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (danger) colors.error else if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(16.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = if (danger) colors.error else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                detail,
                modifier = Modifier.padding(top = 2.dp),
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) DuoKeycap("B", accent = true, small = true)
    }
}
