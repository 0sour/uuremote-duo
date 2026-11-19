package com.sour.duoremote.ui.devices

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Laptop
import androidx.compose.material.icons.outlined.Monitor
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.components.BannerTone
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoActionBar
import com.sour.duoremote.ui.components.DuoBanner
import com.sour.duoremote.ui.components.DuoPageHeader
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.home.DemoDevice
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import kotlinx.coroutines.delay

@Composable
fun DeviceManageScreen(
    devices: List<DemoDevice>,
    selectedId: String,
    ui: DeviceManageUiState,
    onSelect: (DemoDevice) -> Unit,
    onRename: (DemoDevice) -> Unit,
    onReboot: (DemoDevice) -> Unit,
    onRemove: (DemoDevice) -> Unit,
    onConfirm: () -> Unit,
    onCancelAction: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val listState = rememberLazyListState()
    val selected = devices.getOrNull(ui.index)
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = FocusRequester()

    BackHandler(onBack = onBack)
    LaunchedEffect(devices.size) { ui.clamp(devices.size) }
    LaunchedEffect(ui.index, devices.size) {
        if (devices.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(ui.index.coerceIn(0, devices.lastIndex)) }
        }
    }
    LaunchedEffect(ui.action) {
        if (ui.action == DeviceManageAction.RENAME) {
            delay(120)
            runCatching { focusRequester.requestFocus() }
            keyboard?.show()
        } else {
            keyboard?.hide()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            DuoPageHeader(
                title = "设备管理",
                subtitle = "重命名、重启或从账号移除",
                onBack = onBack,
                trailing = "${devices.count { it.online }} 台在线",
                compact = !wide,
            )
            val message = ui.error ?: ui.notice
            if (message != null) {
                DuoBanner(
                    text = message,
                    tone = if (ui.error != null) BannerTone.Error else BannerTone.Info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            if (devices.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "还没有设备，回到主页按 Y 刷新",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f).fillMaxWidth().padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(devices, key = { _, device -> device.id }) { index, device ->
                        DeviceRow(
                            device = device,
                            selected = index == ui.index,
                            current = device.id == selectedId,
                            onClick = {
                                ui.select(index)
                                onSelect(device)
                            },
                        )
                    }
                }
            }
            DuoActionBar(
                actions = listOf(
                    DuoAction(
                        "重命名",
                        enabled = selected != null && !ui.busy,
                    ),
                    DuoAction(
                        "重启",
                        enabled = selected?.online == true && !ui.busy,
                    ),
                    DuoAction(
                        "移除",
                        enabled = selected != null && selected.online.not() && !ui.busy,
                    ),
                    DuoAction("返回"),
                ),
                onAction = { index ->
                    when (index) {
                        0 -> selected?.let(onRename)
                        1 -> selected?.let(onReboot)
                        2 -> selected?.let(onRemove)
                        else -> onBack()
                    }
                },
                height = 38,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
            )
            Text(
                "X 重命名 · Y 重启 · SELECT 移除 · A 返回",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = colors.textTertiary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }

        val action = ui.action
        if (action != null) {
            DeviceActionOverlay(
                action = action,
                device = selected,
                ui = ui,
                focusRequester = focusRequester,
                onConfirm = onConfirm,
                onCancel = onCancelAction,
            )
        }
    }
}

@Composable
fun DeviceManageTopPreview(
    devices: List<DemoDevice>,
    selectedId: String,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val selected = devices.firstOrNull { it.id == selectedId } ?: devices.firstOrNull()
    Column(modifier.fillMaxSize().background(colors.bg)) {
        DuoPageHeader(
            title = "设备管理",
            subtitle = "上屏只读 · 请在下屏操作",
            onBack = null,
            trailing = "${devices.count { it.online }} 台在线",
            compact = false,
        )
        Column(
            Modifier.weight(1f).fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                selected?.name ?: "还没有设备",
                style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            if (selected != null) {
                Text(
                    "${if (selected.online) "在线" else "离线"} · ${selected.os} · " +
                        if (selected.shared) "他人共享" else "我的设备",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "重命名会修改账号里的设备别名；重启需要设备在线；移除只允许离线设备。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
        }
    }
}

@Composable
private fun DeviceRow(
    device: DemoDevice,
    selected: Boolean,
    current: Boolean,
    onClick: () -> Unit,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .gamepadFocusRing(selected, DuoRadius.sm)
            .clip(RoundedCornerShape(DuoRadius.sm))
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.45f) else colors.outline,
                RoundedCornerShape(DuoRadius.sm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            if (device.os.startsWith("mac", ignoreCase = true)) Icons.Outlined.Laptop else Icons.Outlined.Monitor,
            contentDescription = null,
            tint = if (device.online) colors.accent else colors.textTertiary,
            modifier = Modifier.size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    device.name,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (current) {
                    Spacer(Modifier.width(6.dp))
                    Text("当前", fontSize = 8.sp, color = colors.accent)
                }
                if (device.shared) {
                    Spacer(Modifier.width(6.dp))
                    Text("共享", fontSize = 8.sp, color = colors.textTertiary)
                }
            }
            Text(
                "${if (device.online) "在线" else "离线"} · ${device.os}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
        Text(
            device.lastSeen,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun DeviceActionOverlay(
    action: DeviceManageAction,
    device: DemoDevice?,
    ui: DeviceManageUiState,
    focusRequester: FocusRequester,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
) {
    val colors = LocalDuoColors.current
    Box(
        Modifier
            .fillMaxSize()
            .imePadding()
            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.62f))
            .clickable(onClick = {}),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier
                .padding(top = 18.dp)
                .width(560.dp)
                .heightIn(max = 320.dp)
                .clip(RoundedCornerShape(DuoRadius.lg))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                when (action) {
                    DeviceManageAction.RENAME -> "重命名设备"
                    DeviceManageAction.REBOOT -> "重启电脑"
                    DeviceManageAction.REMOVE -> "从账号移除"
                },
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary,
            )
            Text(
                device?.name.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
            )
            when (action) {
                DeviceManageAction.RENAME -> OutlinedTextField(
                    value = ui.draftName,
                    onValueChange = ui::updateDraftName,
                    singleLine = true,
                    label = { Text("设备名称（1–40 字）") },
                    modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                )

                DeviceManageAction.REBOOT -> Text(
                    "将向这台在线设备发送重启请求。未保存的工作可能丢失。",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                )

                DeviceManageAction.REMOVE -> Text(
                    if (device?.shared == true) {
                        "将从当前账号的共享设备列表移除，不会影响设备本身。"
                    } else {
                        "将解除该设备与当前账号的绑定。设备离线时才可移除。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.warning,
                )
            }
            if (ui.error != null) {
                DuoBanner(text = ui.error.orEmpty(), tone = BannerTone.Error)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ui.busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        color = colors.accent,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onCancel, enabled = !ui.busy) {
                    Text(
                        "取消",
                        color = colors.textSecondary,
                        modifier = Modifier.gamepadFocusRing(ui.confirmIndex == 1, DuoRadius.sm),
                    )
                }
                TextButton(
                    onClick = onConfirm,
                    enabled = !ui.busy && when (action) {
                        DeviceManageAction.RENAME -> ui.draftName.trim().isNotEmpty() && ui.draftName.length <= 40
                        else -> device != null
                    },
                ) {
                    Text(
                        if (action == DeviceManageAction.REMOVE) "确认移除" else "确认",
                        color = colors.accent,
                        modifier = Modifier.gamepadFocusRing(ui.confirmIndex == 0, DuoRadius.sm),
                    )
                }
            }
        }
    }
}
