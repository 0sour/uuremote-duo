package com.sour.duoremote.ui.controlcenter

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.ui.components.BannerTone
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoActionButton
import com.sour.duoremote.ui.components.DuoBanner
import com.sour.duoremote.ui.components.DuoPageHeader
import com.sour.duoremote.ui.components.DuoSettingRow
import com.sour.duoremote.ui.components.DuoTabBar
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors

/**
 * In-stream control center. The top host keeps the video; this pane owns the bottom panel in
 * dual mode and the whole frame in single mode. Playback changes stay pending until "应用并重连".
 */
@Composable
fun ControlCenterScreen(
    state: ControlCenterState,
    settings: DuoSettings,
    onAdjust: (ControlField, Int) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val rows = state.rows(settings)
    val pending = state.pendingCount(settings)
    val listState = rememberLazyListState()
    val subTabs = state.subTabCount()
    BackHandler(onBack = onClose)
    LaunchedEffect(state.group, state.subTab, state.rowIndex) {
        runCatching { listState.animateScrollToItem(state.rowIndex.coerceAtLeast(0)) }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            DuoPageHeader(
                title = "控制中心",
                subtitle = "${state.group.label} · ${state.group.hint}",
                onBack = onClose,
                trailing = if (pending > 0) "$pending 项待应用" else "改动即时保存",
                compact = !wide,
            )
            if (wide) {
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(bottom = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    GroupRail(
                        state = state,
                        onGroup = { state.moveGroup(it - state.group.ordinal) },
                        onSubTab = { state.moveSubTab(it - state.subTab) },
                    )
                    Column(Modifier.weight(1f).fillMaxHeight()) {
                        ContentHeader(state.group, state.subTab, pending)
                        ControlList(
                            rows = rows,
                            selectedIndex = state.rowIndex,
                            listState = listState,
                            onSelect = state::selectRow,
                            onAdjust = onAdjust,
                            onOpenHelp = onOpenHelp,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            } else {
                DuoTabBar(
                    items = ControlGroup.entries.map { it.label },
                    selectedIndex = state.group.ordinal,
                    onSelect = { state.moveGroup(it - state.group.ordinal) },
                    compact = true,
                )
                if (subTabs > 0) {
                    DuoTabBar(
                        items = subTabsFor(state.group),
                        selectedIndex = state.subTab,
                        onSelect = { state.moveSubTab(it - state.subTab) },
                        compact = true,
                    )
                }
                ControlList(
                    rows = rows,
                    selectedIndex = state.rowIndex,
                    listState = listState,
                    onSelect = state::selectRow,
                    onAdjust = onAdjust,
                    onOpenHelp = onOpenHelp,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                )
            }
            ApplyBar(
                pending = pending,
                onCancel = onCancel,
                onApply = onApply,
                onClose = onClose,
                wide = wide,
            )
            FooterHint(pending)
        }
    }
}

@Composable
private fun GroupRail(
    state: ControlCenterState,
    onGroup: (Int) -> Unit,
    onSubTab: (Int) -> Unit,
) {
    val colors = LocalDuoColors.current
    Column(
        Modifier
            .width(176.dp)
            .fillMaxHeight()
            .clip(RoundedCornerShape(DuoRadius.lg))
            .background(colors.surface1)
            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        ControlGroup.entries.forEach { item ->
            val selected = item == state.group
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .clip(RoundedCornerShape(DuoRadius.sm))
                    .background(if (selected) colors.accentSoft else Color.Transparent)
                    .clickable { onGroup(item.ordinal) }
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .width(3.dp)
                        .height(16.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (selected) colors.accent else Color.Transparent),
                )
                Text(
                    item.label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                    ),
                    color = if (selected) colors.accent else colors.textSecondary,
                )
            }
        }
        if (state.subTabCount() > 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .padding(horizontal = 6.dp)
                    .background(colors.outline.copy(alpha = 0.6f)),
            )
            subTabsFor(state.group).forEachIndexed { index, label ->
                val selected = index == state.subTab
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    ),
                    color = if (selected) colors.accent else colors.textTertiary,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(28.dp)
                        .clip(RoundedCornerShape(DuoRadius.sm))
                        .background(if (selected) colors.accentSoft else Color.Transparent)
                        .clickable { onSubTab(index) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            "上屏保持视频",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = colors.textTertiary,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp),
        )
    }
}

private fun subTabsFor(group: ControlGroup): List<String> = when (group) {
    ControlGroup.PICTURE -> listOf("画面", "显示")
    ControlGroup.PERIPHERAL -> listOf("手柄", "触摸板与键盘", "声音")
    ControlGroup.SECURITY, ControlGroup.MORE -> emptyList()
}

@Composable
private fun ContentHeader(group: ControlGroup, subTab: Int, pending: Int) {
    val colors = LocalDuoColors.current
    val sub = subTabsFor(group).getOrNull(subTab)
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (sub != null) "${group.label} · $sub" else group.label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.SemiBold),
            color = colors.textSecondary,
        )
        Spacer(Modifier.weight(1f))
        Text(
            if (pending > 0) "应用后重连" else "已保存",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (pending > 0) colors.warning else colors.textTertiary,
        )
    }
}

@Composable
private fun ControlList(
    rows: List<ControlRow>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
    onAdjust: (ControlField, Int) -> Unit,
    onOpenHelp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        itemsIndexed(rows) { index, row ->
            DuoSettingRow(
                title = row.title,
                description = row.description,
                value = row.value,
                selected = index == selectedIndex,
                enabled = row.enabled,
                pending = row.pending,
                danger = row.danger,
                tag = row.tag,
                height = 52,
                onClick = {
                    onSelect(index)
                    val field = row.field ?: return@DuoSettingRow
                    if (!row.enabled) return@DuoSettingRow
                    if (field == ControlField.GUIDE) onOpenHelp() else onAdjust(field, 1)
                },
            )
        }
    }
}

@Composable
private fun ApplyBar(
    pending: Int,
    onCancel: () -> Unit,
    onApply: () -> Unit,
    onClose: () -> Unit,
    wide: Boolean,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(if (pending > 0) 76.dp else 44.dp)
            .padding(horizontal = if (wide) 14.dp else 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (pending > 0) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                DuoBanner(
                    "画质改动，应用后会重连视频",
                    tone = BannerTone.Warning,
                    trailing = "$pending 项",
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DuoActionButton(
                        DuoAction("取消修改"),
                        selected = false,
                        onClick = onCancel,
                        modifier = Modifier.weight(1f),
                        height = 32,
                    )
                    DuoActionButton(
                        DuoAction("应用并重连", primary = true),
                        selected = false,
                        onClick = onApply,
                        modifier = Modifier.weight(1f),
                        height = 32,
                    )
                }
            }
        } else {
            Text(
                "缩放、外设与安全选项即时生效",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
                modifier = Modifier.weight(1f),
            )
            DuoActionButton(
                DuoAction("完成"),
                selected = false,
                onClick = onClose,
                height = 32,
            )
        }
    }
}

@Composable
private fun FooterHint(pending: Int) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text("L1/R1 分组", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Text("L2/R2 子页", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Text("←→ 调整", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Text("B 选择", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        Spacer(Modifier.weight(1f))
        if (pending > 0) {
            Text("X 应用 · Y 取消 · A 关闭", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.warning)
        } else {
            Text("A 关闭", style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp), color = colors.textTertiary)
        }
    }
}
