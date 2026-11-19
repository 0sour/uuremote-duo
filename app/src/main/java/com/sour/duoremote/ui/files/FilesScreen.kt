package com.sour.duoremote.ui.files

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.protocol.file.FileEntry
import com.sour.duoremote.session.ActiveTransfer
import com.sour.duoremote.session.FileTransferState
import com.sour.duoremote.session.TransferKind
import com.sour.duoremote.session.TransferStatus
import com.sour.duoremote.ui.components.BannerTone
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoActionBar
import com.sour.duoremote.ui.components.DuoBanner
import com.sour.duoremote.ui.components.DuoPageHeader
import com.sour.duoremote.ui.components.DuoTabBar
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Bottom-panel remote file browser. Local selection is handled by [LocalPickerScreen] so the
 * transfer path never starts the system DocumentsUI; this screen only raises upload/download
 * intents to MainActivity.
 */
@Composable
fun FilesScreen(
    state: FileTransferState,
    ui: FilesUiState,
    onList: (String) -> Unit,
    onCancel: () -> Unit,
    onClose: () -> Unit,
    onUpload: () -> Unit,
    onDownload: (FileEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val entries = state.entries
    val selectedIndex = ui.index.coerceIn(0, maxOf(0, entries.size - 1))
    val selected = entries.getOrNull(selectedIndex)
    val parent = state.parentPath()
    val listState = rememberLazyListState()

    fun open(path: String) {
        ui.error = null
        ui.select(0)
        onList(path)
    }

    val back: () -> Unit = {
        when {
            ui.tab == FilesTab.QUEUE -> ui.tab = FilesTab.BROWSE
            parent != null -> open(parent)
            else -> onClose()
        }
    }
    BackHandler(onBack = back)

    LaunchedEffect(entries.size) { ui.clamp(entries.size) }
    LaunchedEffect(ui.index, entries.size) {
        if (entries.isNotEmpty()) {
            runCatching { listState.animateScrollToItem(ui.index.coerceIn(0, entries.lastIndex)) }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            DuoPageHeader(
                title = "文件传输",
                subtitle = state.path,
                onBack = back,
                trailing = transferSummary(state.transfer),
                compact = !wide,
            )
            DuoTabBar(
                items = FilesTab.entries.map { it.label },
                selectedIndex = ui.tab.ordinal,
                onSelect = { ui.tab = FilesTab.entries[it] },
                compact = !wide,
                focusIndex = ui.tab.ordinal,
            )
            val message = ui.error ?: state.error ?: state.notice
            if (message != null) {
                DuoBanner(
                    text = message,
                    tone = if (ui.error != null || state.error != null) BannerTone.Error else BannerTone.Info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }
            when (ui.tab) {
                FilesTab.BROWSE -> {
                    DuoActionBar(
                        actions = listOf(
                            DuoAction("上级", enabled = parent != null),
                            DuoAction("刷新"),
                            DuoAction("上传", primary = true),
                            DuoAction("下载", enabled = selected != null && !selected.isDirectory),
                        ),
                        onAction = { index ->
                            ui.error = null
                            when (index) {
                                0 -> parent?.let(::open)
                                1 -> onList(state.path)
                                2 -> onUpload()
                                3 -> if (selected == null) {
                                    ui.error = "请选择一个文件"
                                } else if (selected.isDirectory) {
                                    ui.error = "文件夹下载暂未支持"
                                } else {
                                    onDownload(selected)
                                }
                            }
                        },
                        height = 34,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                    )
                    FileList(
                        state = state,
                        entries = entries,
                        selectedIndex = selectedIndex,
                        listState = listState,
                        onSelect = ui::select,
                        onOpen = ::open,
                        modifier = Modifier.weight(1f),
                    )
                }

                FilesTab.QUEUE -> TransferQueue(
                    state = state,
                    onCancel = onCancel,
                    modifier = Modifier.weight(1f),
                )
            }
            FooterHint(ui.tab)
        }
    }
}

@Composable
private fun FileList(
    state: FileTransferState,
    entries: List<FileEntry>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSelect: (Int) -> Unit,
    onOpen: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    when {
        state.loading && entries.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = colors.accent,
                strokeWidth = 2.dp,
            )
        }

        entries.isEmpty() -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                if (state.loading) "正在读取…" else "此文件夹为空",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
        }

        else -> LazyColumn(
            state = listState,
            modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            itemsIndexed(
                items = entries,
                key = { index, entry -> "${entry.fullPath}:${entry.name}:$index" },
            ) { index, entry ->
                val selected = index == selectedIndex
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .gamepadFocusRing(selected, DuoRadius.sm)
                        .clip(RoundedCornerShape(DuoRadius.sm))
                        .background(if (selected) colors.accentSoft else colors.surface1)
                        .border(
                            1.dp,
                            if (selected) colors.accent.copy(alpha = 0.45f) else colors.outline,
                            RoundedCornerShape(DuoRadius.sm),
                        )
                        .clickable {
                            onSelect(index)
                            if (entry.isDirectory) {
                                onOpen(
                                    entry.fullPath.ifBlank {
                                        FileTransferState.childOf(state.path, entry.name)
                                    },
                                )
                            }
                        }
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (entry.isDirectory) Icons.Outlined.Folder else Icons.AutoMirrored.Outlined.InsertDriveFile,
                        contentDescription = null,
                        tint = if (entry.isDirectory) colors.accent else colors.textSecondary,
                        modifier = Modifier.size(17.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            entry.name,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontSize = 11.sp,
                                fontWeight = if (entry.isDirectory) FontWeight.Medium else FontWeight.Normal,
                            ),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            if (entry.isDirectory) "文件夹" else formatSize(entry.size),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = colors.textTertiary,
                            maxLines = 1,
                        )
                    }
                    Text(
                        formatTime(entry.modifiedTime),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun TransferQueue(
    state: FileTransferState,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val transfer = state.transfer
    if (transfer == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.Download,
                    contentDescription = null,
                    tint = colors.textTertiary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "暂无传输任务",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }
        }
        return
    }

    Column(modifier.fillMaxSize().padding(12.dp)) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DuoRadius.md))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.md))
                .padding(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (transfer.kind == TransferKind.UPLOAD) Icons.Outlined.Upload else Icons.Outlined.Download,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        transfer.name,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusLabel(transfer),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = when (transfer.status) {
                            TransferStatus.FAILED -> colors.error
                            TransferStatus.COMPLETED -> colors.online
                            else -> colors.textTertiary
                        },
                        maxLines = 1,
                    )
                }
                Text(
                    "${(transfer.progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = colors.accent,
                )
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { transfer.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp),
                color = colors.accent,
            )
            Spacer(Modifier.height(7.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${formatSize(transfer.done)} / ${formatSize(transfer.total)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.textTertiary,
                )
                Spacer(Modifier.weight(1f))
                if (transfer.status in ACTIVE_STATUSES) {
                    TextButton(onClick = onCancel) {
                        Text("取消", fontSize = 10.sp, color = colors.error)
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "同一时间只支持一个传输任务；断开连接会中止当前任务。",
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
        )
    }
}

@Composable
fun FilesTopPreview(
    state: FileTransferState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val transfer = state.transfer
    Column(
        modifier
            .fillMaxSize()
            .background(colors.bg)
            .padding(26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    "文件传输",
                    style = MaterialTheme.typography.headlineSmall.copy(fontSize = 23.sp, fontWeight = FontWeight.Bold),
                    color = colors.textPrimary,
                )
                Text(
                    "上屏只读 · 操作全部在下屏",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = colors.textTertiary,
                )
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(DuoRadius.lg))
                .background(colors.surface1)
                .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                .padding(16.dp),
        ) {
            Text(
                "当前目录",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
            Text(
                state.path,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(5.dp))
            Text(
                "${state.entries.size} 个项目" + if (state.loading) " · 正在读取" else "",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
            )
        }
        if (transfer != null) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(DuoRadius.lg))
                    .background(colors.surface1)
                    .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.lg))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        if (transfer.kind == TransferKind.UPLOAD) Icons.Outlined.Upload else Icons.Outlined.Download,
                        contentDescription = null,
                        tint = colors.accent,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(9.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            transfer.name,
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                            color = colors.textPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            statusLabel(transfer),
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                            color = colors.textTertiary,
                        )
                    }
                    Text(
                        "${(transfer.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.SemiBold),
                        color = colors.accent,
                    )
                }
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { transfer.progress },
                    modifier = Modifier.fillMaxWidth().height(5.dp),
                    color = colors.accent,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "${formatSize(transfer.done)} / ${formatSize(transfer.total)}",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    color = colors.textTertiary,
                )
            }
        } else {
            Text(
                "在下屏浏览远端目录，选择文件上传或下载。",
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = colors.textSecondary,
            )
        }
        val message = state.error ?: state.notice
        if (message != null) {
            DuoBanner(
                text = message,
                tone = if (state.error != null) BannerTone.Error else BannerTone.Info,
            )
        }
    }
}

@Composable
private fun FooterHint(tab: FilesTab) {
    val colors = LocalDuoColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .height(22.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (tab == FilesTab.BROWSE) {
                "B 打开 · A 返回 · X 上传 · Y 下载 · L1/R1 切换"
            } else {
                "X 取消 · L1/R1 切换 · A 返回"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

private fun statusLabel(transfer: ActiveTransfer): String = when (transfer.status) {
    TransferStatus.PREPARING -> "准备中"
    TransferStatus.TRANSFERRING -> "传输中"
    TransferStatus.WAITING -> "等待远端确认"
    TransferStatus.COMPLETED -> "已完成"
    TransferStatus.FAILED -> transfer.message ?: "失败"
    TransferStatus.CANCELED -> "已取消"
}

private fun transferSummary(transfer: ActiveTransfer?): String? {
    if (transfer == null) return null
    return when (transfer.status) {
        TransferStatus.PREPARING -> "准备中"
        TransferStatus.TRANSFERRING -> "${(transfer.progress * 100).toInt()}%"
        TransferStatus.WAITING -> "等待确认"
        TransferStatus.COMPLETED -> "已完成"
        TransferStatus.FAILED -> "传输失败"
        TransferStatus.CANCELED -> "已取消"
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes <= 0L) return "—"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

private fun formatTime(seconds: Long): String {
    if (seconds <= 0L) return "—"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
    }.getOrDefault("—")
}

private val ACTIVE_STATUSES = setOf(
    TransferStatus.PREPARING,
    TransferStatus.TRANSFERRING,
    TransferStatus.WAITING,
)
