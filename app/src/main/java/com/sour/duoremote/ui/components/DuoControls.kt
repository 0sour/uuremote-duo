package com.sour.duoremote.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowLeft
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors

/**
 * Shared controls for the control center, settings and home action bar.
 * All values mirror docs/ui/mockup.css; gamepad focus is state-driven so the Activity and the
 * bottom Presentation render the same selection.
 */

@Composable
fun DuoTabBar(
    items: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true,
    focusIndex: Int? = null,
) {
    val colors = LocalDuoColors.current
    val listState = rememberLazyListState()
    LaunchedEffect(selectedIndex) {
        runCatching { listState.animateScrollToItem(selectedIndex.coerceAtLeast(0)) }
    }
    LazyRow(
        modifier = modifier.fillMaxWidth().height(if (compact) 38.dp else 42.dp),
        state = listState,
        contentPadding = PaddingValues(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        itemsIndexed(items) { index, label ->
            val selected = index == selectedIndex
            Box(
                Modifier
                    .height(30.dp)
                    .gamepadFocusRing(index == focusIndex, DuoRadius.sm)
                    .clip(RoundedCornerShape(DuoRadius.sm))
                    .background(if (selected) colors.accentSoft else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = if (compact) 10.dp else 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        fontSize = if (compact) 11.sp else 12.sp,
                    ),
                    color = if (selected) colors.accent else colors.textSecondary,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
fun DuoPageHeader(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    compact: Boolean = true,
) {
    val colors = LocalDuoColors.current
    Row(
        modifier
            .fillMaxWidth()
            .height(if (compact) 44.dp else 46.dp)
            .padding(horizontal = if (compact) 12.dp else 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(9.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.KeyboardArrowLeft,
                contentDescription = "返回",
                tint = colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = if (compact) 14.sp else 16.sp,
                ),
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun DuoSettingRow(
    title: String,
    description: String,
    value: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    pending: Boolean = false,
    danger: Boolean = false,
    tag: String? = null,
    height: Int = 54,
) {
    val colors = LocalDuoColors.current
    val feedback = feedbackAction(onClick)
    Row(
        modifier
            .fillMaxWidth()
            .height(height.dp)
            .gamepadFocusRing(selected, DuoRadius.md)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(
                when {
                    !enabled -> colors.surface1.copy(alpha = 0.55f)
                    selected -> colors.accentSoft
                    else -> colors.surface1
                },
            )
            .border(
                1.dp,
                when {
                    danger -> colors.error.copy(alpha = 0.26f)
                    !enabled -> colors.outline.copy(alpha = 0.55f)
                    selected -> colors.accent.copy(alpha = 0.45f)
                    else -> colors.outline
                },
                RoundedCornerShape(DuoRadius.md),
            )
            .clickable(enabled = enabled, onClick = feedback)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 12.sp),
                    color = when {
                        danger -> colors.error
                        !enabled -> colors.textTertiary
                        else -> colors.textPrimary
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (pending) {
                    Text(
                        "待应用",
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, fontWeight = FontWeight.Medium),
                        color = colors.warning,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(DuoRadius.pill))
                            .background(colors.warning.copy(alpha = 0.10f))
                            .padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                }
                if (tag != null) {
                    Text(
                        tag,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                        color = colors.textTertiary,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(DuoRadius.pill))
                            .border(1.dp, colors.outline, RoundedCornerShape(DuoRadius.pill))
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    )
                }
            }
            Text(
                description,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                color = colors.textTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 1.dp),
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp, fontWeight = FontWeight.Medium),
            color = when {
                danger -> colors.error
                !enabled -> colors.textTertiary
                else -> colors.accent
            },
            maxLines = 1,
        )
    }
}

@Composable
fun DuoBanner(
    text: String,
    modifier: Modifier = Modifier,
    tone: BannerTone = BannerTone.Warning,
    trailing: String? = null,
) {
    val colors = LocalDuoColors.current
    val fg = when (tone) {
        BannerTone.Warning -> colors.warning
        BannerTone.Info -> colors.accent
        BannerTone.Error -> colors.error
    }
    Row(
        modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(fg.copy(alpha = 0.09f))
            .border(1.dp, fg.copy(alpha = 0.24f), RoundedCornerShape(DuoRadius.md))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier
                .size(5.dp)
                .background(fg, RoundedCornerShape(DuoRadius.pill)),
        )
        Text(
            text,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = fg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (trailing != null) {
            Text(
                trailing,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                color = colors.textTertiary,
                maxLines = 1,
            )
        }
    }
}

enum class BannerTone { Warning, Info, Error }

data class DuoAction(
    val label: String,
    val icon: ImageVector? = null,
    val primary: Boolean = false,
    val enabled: Boolean = true,
)

@Composable
fun DuoActionBar(
    actions: List<DuoAction>,
    onAction: (Int) -> Unit,
    modifier: Modifier = Modifier,
    focusIndex: Int? = null,
    height: Int = 42,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        actions.forEachIndexed { index, action ->
            DuoActionButton(
                action = action,
                selected = focusIndex == index,
                onClick = { onAction(index) },
                modifier = Modifier.weight(1f),
                height = height,
            )
        }
    }
}

@Composable
fun DuoActionButton(
    action: DuoAction,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Int = 42,
) {
    val colors = LocalDuoColors.current
    val feedback = feedbackAction(onClick)
    Row(
        modifier
            .height(height.dp)
            .gamepadFocusRing(selected, DuoRadius.md)
            .clip(RoundedCornerShape(DuoRadius.md))
            .background(if (action.primary) colors.accent else colors.surface2)
            .border(
                1.dp,
                when {
                    !action.enabled -> colors.outline.copy(alpha = 0.5f)
                    action.primary -> colors.accent
                    selected -> colors.accent.copy(alpha = 0.45f)
                    else -> colors.outline
                },
                RoundedCornerShape(DuoRadius.md),
            )
            .clickable(enabled = action.enabled, onClick = feedback)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (action.icon != null) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = when {
                    !action.enabled -> colors.textTertiary
                    action.primary -> colors.onAccent
                    else -> colors.textSecondary
                },
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            action.label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
            ),
            color = when {
                !action.enabled -> colors.textTertiary
                action.primary -> colors.onAccent
                else -> colors.textPrimary
            },
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
