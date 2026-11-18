package com.sour.duoremote.ui.components

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors

/**
 * Global pad reference. Rendered in both host windows; the Activity consumes A/B/START while it
 * is visible so the remote never receives navigation keys from behind the overlay.
 */
@Composable
fun GamepadHelpOverlay(onClose: () -> Unit) {
    val colors = LocalDuoColors.current
    BackHandler(onBack = onClose)
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.scrim)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(DuoRadius.xl))
                .background(colors.surface1)
                .border(1.dp, colors.outlineStrong, RoundedCornerShape(DuoRadius.xl))
                .clickable { }
                .padding(18.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "手柄说明",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                    )
                    Text(
                        "上屏只读；焦点只在下屏或单屏。实体丝印：B 确认、A 返回。",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        color = colors.textTertiary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                Text(
                    "START / A 关闭",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.textSecondary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(DuoRadius.pill))
                        .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                HelpColumn(
                    Modifier.weight(1f),
                    listOf(
                        "方向键 / 左摇杆" to "移动焦点",
                        "B / Enter" to "确认、切换",
                        "A / Esc" to "返回、关闭浮层",
                        "L1 / R1" to "一级页签 / 分类",
                    ),
                )
                HelpColumn(
                    Modifier.weight(1f),
                    listOf(
                        "L2 / R2" to "二级页签（控制中心）",
                        "X / Y" to "页面动作",
                        "START" to "打开本说明",
                        "触摸" to "点按即聚焦并执行",
                    ),
                )
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "串流直通电脑时，START 属于远端手柄；按 SELECT 仍可打开本地菜单。退出直通后 START 打开本说明。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 13.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun HelpColumn(modifier: Modifier, rows: List<Pair<String, String>>) {
    val colors = LocalDuoColors.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { (key, action) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    key,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 9.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    color = colors.accent,
                    modifier = Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(colors.accentSoft)
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                    maxLines = 1,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    action,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}
