package com.sour.duoremote.ui.input

import kotlin.math.max
import kotlin.math.min

data class VideoTouchPoint(val x: Float, val y: Float)

/**
 * Maps a touch on the rendered video surface to normalized remote coordinates.
 *
 * - fit: letterboxed, touches on bars return null.
 * - fill: cropped, every touch inside the view maps to the visible crop.
 * - original: 1:1 when the video fits, otherwise same as fit (RendererCommon BALANCED).
 */
object VideoTouchMapper {

    fun map(
        x: Float,
        y: Float,
        viewWidth: Float,
        viewHeight: Float,
        videoWidth: Int,
        videoHeight: Int,
        scaleMode: String = "fit",
    ): VideoTouchPoint? {
        if (viewWidth <= 0f || viewHeight <= 0f || videoWidth <= 0 || videoHeight <= 0) return null
        val fitsAtOneToOne = videoWidth <= viewWidth && videoHeight <= viewHeight
        val scale = when (scaleMode) {
            "fill" -> max(viewWidth / videoWidth, viewHeight / videoHeight)
            "original" -> if (fitsAtOneToOne) 1f
            else min(viewWidth / videoWidth, viewHeight / videoHeight)

            else -> min(viewWidth / videoWidth, viewHeight / videoHeight)
        }
        val contentWidth = videoWidth * scale
        val contentHeight = videoHeight * scale
        val left = (viewWidth - contentWidth) / 2f
        val top = (viewHeight - contentHeight) / 2f
        if (x < left || x > left + contentWidth || y < top || y > top + contentHeight) return null
        return VideoTouchPoint(
            x = ((x - left) / contentWidth).coerceIn(0f, 1f),
            y = ((y - top) / contentHeight).coerceIn(0f, 1f),
        )
    }
}
