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
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import com.sour.duoremote.ui.components.BannerTone
import com.sour.duoremote.ui.components.DuoAction
import com.sour.duoremote.ui.components.DuoActionBar
import com.sour.duoremote.ui.components.DuoBanner
import com.sour.duoremote.ui.components.DuoPageHeader
import com.sour.duoremote.ui.components.gamepadFocusRing
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * App-built local file picker. It is shown in place of [FilesScreen] while the user chooses an
 * upload source or a download destination; the system picker is only used for the one-time
 * folder grant.
 */
@Composable
fun LocalPickerScreen(
    state: LocalFilesUiState,
    onBack: () -> Unit,
    onSelectRoot: (LocalRootKind) -> Unit,
    onGrantFolder: () -> Unit,
    onRevokeFolder: () -> Unit,
    onOpen: (LocalEntry) -> Unit,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onSelect: (Int) -> Unit,
    onToggleMark: (LocalEntry) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    val listState = rememberLazyListState()
    val currentPath = state.stack.joinToString(" / ") { it.label }
    val selectedIndex = state.index.coerceIn(0, maxOf(0, state.entries.size - 1))
    val selected = state.entries.getOrNull(selectedIndex)
    val markedCount = state.marked.size

    BackHandler(onBack = onBack)
    LaunchedEffect(state.entries.size) { state.clamp(state.entries.size) }
    LaunchedEffect(state.index, state.entries.size) {
        if (state.entries.isNotEmpty()) {
            runCatching {
                listState.animateScrollToItem(state.index.coerceIn(0, state.entries.lastIndex))
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(colors.bg)) {
        val wide = maxWidth >= 700.dp
        Column(Modifier.fillMaxSize()) {
            DuoPageHeader(
                title = if (state.mode == LocalPickerMode.UPLOAD) "选择本地文件" else "选择保存位置",
                subtitle = when {
                    state.authPrompt -> "需要授权一个本机文件夹"
                    currentPath.isNotBlank() -> currentPath
                    else -> "本机文件"
                },
                onBack = onBack,
                trailing = if (state.mode == LocalPickerMode.UPLOAD) "上传" else "下载",
                compact = !wide,
            )
            if (!state.authPrompt) {
                RootSelector(
                    state = state,
                    onSelectRoot = onSelectRoot,
                )
            }

            val message = state.error ?: state.notice
            if (message != null) {
                DuoBanner(
                    text = message,
                    tone = if (state.error != null) BannerTone.Error else BannerTone.Info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
            }

            if (state.authPrompt) {
                AuthPrompt(
                    state = state,
                    onGrantFolder = onGrantFolder,
                    onUseApp = { onSelectRoot(LocalRootKind.APP) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                PathBar(
                    path = currentPath,
                    canGoUp = state.stack.size > 1,
                    canRevoke = state.current?.root == LocalRootKind.AUTHORIZED,
                    onUp = onUp,
                    onRefresh = onRefresh,
                    onRevoke = onRevokeFolder,
                )
                when {
                    state.loading && state.entries.isEmpty() -> Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = colors.accent,
                            strokeWidth = 2.dp,
                        )
                    }

                    state.entries.isEmpty() -> EmptyLocalFolder(
                        state = state,
                        modifier = Modifier.weight(1f),
                    )

                    else -> LocalEntryList(
                        entries = state.entries,
                        selectedIndex = selectedIndex,
                        listState = listState,
                        showMarks = state.mode == LocalPickerMode.UPLOAD,
                        isMarked = state::isMarked,
                        onSelect = onSelect,
                        onOpen = onOpen,
                        onToggleMark = onToggleMark,
                        modifier = Modifier.weight(1f),
                    )
                }
                DuoBanner(
                    text = when (state.mode) {
                        LocalPickerMode.UPLOAD ->
                            (if (markedCount > 0) "已选 $markedCount 个 · " else "") +
                                "上传到 ${state.remoteDir.ifBlank { "当前远端目录" }}"

                        LocalPickerMode.DOWNLOAD ->
                            if (state.pendingDownloads.size > 1) {
                                "保存 ${state.pendingDownloads.size} 个文件到此文件夹"
                            } else {
                                "保存“${state.pendingDownload?.name ?: "文件"}”到此文件夹"
                            }
                    },
                    tone = BannerTone.Info,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                )
                DuoActionBar(
                    actions = when (state.mode) {
                        LocalPickerMode.UPLOAD -> listOf(
                            DuoAction(
                                if (markedCount > 0) "上传已选 $markedCount 个" else "上传选中文件",
                                primary = true,
                                enabled = (markedCount > 0 || (selected != null && !selected.isDirectory)) &&
                                    !state.loading,
                            ),
                            DuoAction("关闭"),
                        )

                        LocalPickerMode.DOWNLOAD -> listOf(
                            DuoAction(
                                "保存到此文件夹",
                                primary = true,
                                enabled = state.current != null && !state.loading,
                            ),
                            DuoAction("关闭"),
                        )
                    },
                    onAction = { index ->
                        when (index) {
                            0 -> onConfirm()
                            else -> onBack()
                        }
                    },
                    height = 38,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp),
                )
            }
            LocalFooter(state)
        }
    }
}

@Composable
private fun RootSelector(
    state: LocalFilesUiState,
    onSelectRoot: (LocalRootKind) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RootChip(
            label = "应用目录",
            detail = "免授权",
            selected = state.current?.root == LocalRootKind.APP,
            onClick = { onSelectRoot(LocalRootKind.APP) },
            modifier = Modifier.weight(1f),
        )
        RootChip(
            label = "授权文件夹",
            detail = if (state.treeAvailable) "已授权" else "点此授权",
            selected = state.current?.root == LocalRootKind.AUTHORIZED,
            onClick = { onSelectRoot(LocalRootKind.AUTHORIZED) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun RootChip(
    label: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(DuoRadius.sm))
            .background(if (selected) colors.accentSoft else colors.surface1)
            .border(
                1.dp,
                if (selected) colors.accent.copy(alpha = 0.45f) else colors.outline,
                RoundedCornerShape(DuoRadius.sm),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.textSecondary,
            modifier = Modifier.size(15.dp),
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
            ),
            color = if (selected) colors.accent else colors.textPrimary,
            maxLines = 1,
        )
        Text(
            detail,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

@Composable
private fun PathBar(
    path: String,
    canGoUp: Boolean,
    canRevoke: Boolean,
    onUp: () -> Unit,
    onRefresh: () -> Unit,
    onRevoke: () -> Unit,
) {
    val colors = LocalDuoColors.current
    Row(
        Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            path.ifBlank { "本机" },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = colors.textTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onUp, enabled = canGoUp) {
            Text(
                "上级",
                fontSize = 9.sp,
                color = if (canGoUp) colors.accent else colors.textTertiary,
            )
        }
        TextButton(onClick = onRefresh) {
            Text("刷新", fontSize = 9.sp, color = colors.accent)
        }
        if (canRevoke) {
            TextButton(onClick = onRevoke) {
                Text("撤销授权", fontSize = 9.sp, color = colors.error)
            }
        }
    }
}

@Composable
private fun EmptyLocalFolder(
    state: LocalFilesUiState,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.textTertiary,
                modifier = Modifier.size(28.dp),
            )
            Text(
                "此文件夹为空",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                color = colors.textTertiary,
            )
            if (state.mode == LocalPickerMode.UPLOAD && state.current?.root == LocalRootKind.APP) {
                Text(
                    state.current?.path.orEmpty(),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                    color = colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 30.dp),
                )
            }
        }
    }
}

@Composable
private fun AuthPrompt(
    state: LocalFilesUiState,
    onGrantFolder: () -> Unit,
    onUseApp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Outlined.Folder,
                contentDescription = null,
                tint = colors.accent,
                modifier = Modifier.size(34.dp),
            )
            Text(
                "授权一个文件夹",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
                color = colors.textPrimary,
            )
            Text(
                "只需选择一次，之后浏览、上传、下载都在应用内完成。建议新建一个专用文件夹，只放需要传输的文件。首次授权会短暂打开系统页面；不授权也可以使用无需权限的应用目录。",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textSecondary,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            DuoBanner(
                text = if (state.mode == LocalPickerMode.DOWNLOAD) {
                    "下载将保存到所选文件夹"
                } else {
                    "上传可从所选文件夹读取"
                },
                tone = BannerTone.Info,
            )
            DuoActionBar(
                actions = listOf(
                    DuoAction("选择文件夹授权", primary = true),
                    DuoAction("使用应用目录"),
                ),
                onAction = { index ->
                    if (index == 0) onGrantFolder() else onUseApp()
                },
                height = 38,
            )
        }
    }
}

@Composable
private fun LocalEntryList(
    entries: List<LocalEntry>,
    selectedIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    showMarks: Boolean,
    isMarked: (LocalEntry) -> Boolean,
    onSelect: (Int) -> Unit,
    onOpen: (LocalEntry) -> Unit,
    onToggleMark: (LocalEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalDuoColors.current
    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxWidth().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(
            items = entries,
            key = { index, entry -> "${entry.path}:${entry.name}:$index" },
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
                        if (entry.isDirectory) onOpen(entry)
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
                        when {
                            entry.isDirectory -> "文件夹"
                            entry.size < 0 -> "大小未知"
                            else -> formatLocalSize(entry.size)
                        },
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                        color = colors.textTertiary,
                        maxLines = 1,
                    )
                }
                Text(
                    formatLocalTime(entry.modifiedTime),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = colors.textTertiary,
                    maxLines = 1,
                )
                if (showMarks && !entry.isDirectory) {
                    Spacer(Modifier.width(6.dp))
                    val marked = isMarked(entry)
                    Box(
                        Modifier
                            .size(26.dp)
                            .clickable { onToggleMark(entry) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (marked) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                            contentDescription = if (marked) "取消选择" else "加入上传列表",
                            tint = if (marked) colors.accent else colors.textTertiary,
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocalFooter(state: LocalFilesUiState) {
    val colors = LocalDuoColors.current
    Row(
        Modifier.fillMaxWidth().height(22.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            when {
                state.authPrompt -> "B 授权 · A 返回 · X 使用应用目录"
                state.mode == LocalPickerMode.UPLOAD ->
                    "B 打开/上传 · A 返回 · X 刷新 · Y 多选 · L1/R1 切换目录"

                else -> "B 打开文件夹/保存 · A 返回 · X 刷新 · L1/R1 切换目录"
            },
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = colors.textTertiary,
            maxLines = 1,
        )
    }
}

private fun formatLocalSize(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val kb = bytes / 1024.0
    if (kb < 1.0) return "$bytes B"
    if (kb < 1024.0) return String.format(Locale.US, "%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format(Locale.US, "%.1f MB", mb)
    return String.format(Locale.US, "%.1f GB", mb / 1024.0)
}

private fun formatLocalTime(seconds: Long): String {
    if (seconds <= 0L) return "—"
    return runCatching {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(seconds * 1000))
    }.getOrDefault("—")
}
