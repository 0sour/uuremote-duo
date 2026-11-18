package com.sour.duoremote.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sour.duoremote.ui.theme.DuoRadius
import com.sour.duoremote.ui.theme.LocalDuoColors

/**
 * High-contrast selection ring for gamepad-driven UI.
 *
 * Drawn outside the component bounds, so the parent must leave a few dp of padding.
 * The dark gap keeps the ring readable on both the accent button and dark cards.
 */
@Composable
fun Modifier.gamepadFocusRing(
    selected: Boolean,
    radius: Dp = DuoRadius.lg,
    color: Color = LocalDuoColors.current.focusRing,
): Modifier {
    val glow = LocalDuoColors.current.focusRingGlow
    return drawWithContent {
        drawContent()
        if (!selected) return@drawWithContent

        val gap = 2.dp.toPx()
        val ring = 2.dp.toPx()
        val expand = gap + ring
        if (glow) {
            drawRoundRect(
                color = color.copy(alpha = 0.24f),
                topLeft = Offset(-expand, -expand),
                size = Size(size.width + expand * 2, size.height + expand * 2),
                cornerRadius = CornerRadius(radius.toPx() + expand),
                style = Stroke(width = ring),
            )
        }
        drawRoundRect(
            color = color,
            topLeft = Offset(-gap, -gap),
            size = Size(size.width + gap * 2, size.height + gap * 2),
            cornerRadius = CornerRadius(radius.toPx() + gap),
            style = Stroke(width = ring),
        )
    }
}
