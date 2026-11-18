package com.sour.duoremote.ui.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoTouchMapperTest {

    @Test
    fun mapsExactFit() {
        val point = VideoTouchMapper.map(960f, 540f, 1920f, 1080f, 1920, 1080)!!
        assertEquals(0.5f, point.x)
        assertEquals(0.5f, point.y)
        assertEquals(0f, VideoTouchMapper.map(0f, 0f, 1920f, 1080f, 1920, 1080)!!.x)
        assertEquals(1f, VideoTouchMapper.map(1920f, 1080f, 1920f, 1080f, 1920, 1080)!!.y)
    }

    @Test
    fun mapsPillarboxedVideo() {
        // 16:9 video in a 21:9 view: bars left/right, content 1920x1080 centered.
        val point = VideoTouchMapper.map(1260f, 540f, 2520f, 1080f, 1920, 1080)!!
        assertEquals(0.5f, point.x)
        assertEquals(0.5f, point.y)
        assertNull(VideoTouchMapper.map(100f, 540f, 2520f, 1080f, 1920, 1080))
    }

    @Test
    fun mapsLetterboxedVideo() {
        // 16:9 video in a 4:3 view: bars top/bottom, content 1920x1080 centered.
        val point = VideoTouchMapper.map(960f, 720f, 1920f, 1440f, 1920, 1080)!!
        assertEquals(0.5f, point.x)
        assertEquals(0.5f, point.y)
        assertNull(VideoTouchMapper.map(960f, 50f, 1920f, 1440f, 1920, 1080))
    }

    @Test
    fun mapsCroppedFillWithoutLetterbox() {
        // Fill crops the 16:9 video into a 21:9 view; there are no dead bars.
        val point = VideoTouchMapper.map(
            1260f, 540f, 2520f, 1080f, 1920, 1080, scaleMode = "fill",
        )!!
        assertEquals(0.5f, point.x)
        assertEquals(0.5f, point.y)
        assertTrue(
            VideoTouchMapper.map(100f, 540f, 2520f, 1080f, 1920, 1080, scaleMode = "fill") != null,
        )
    }

    @Test
    fun mapsOriginalAtOneToOneUntilItMustFit() {
        // 1920x1080 in a 2520x1080 view stays 1:1 and is centered with side bars.
        val center = VideoTouchMapper.map(
            1260f, 540f, 2520f, 1080f, 1920, 1080, scaleMode = "original",
        )!!
        assertEquals(0.5f, center.x)
        assertNull(
            VideoTouchMapper.map(100f, 540f, 2520f, 1080f, 1920, 1080, scaleMode = "original"),
        )
        // A larger video falls back to fit instead of overflowing at 1:1.
        assertEquals(
            0.5f,
            VideoTouchMapper.map(960f, 540f, 1920f, 1080f, 3840, 2160, scaleMode = "original")!!.x,
        )
    }

    @Test
    fun rejectsInvalidSizes() {
        assertNull(VideoTouchMapper.map(10f, 10f, 0f, 100f, 1920, 1080))
        assertNull(VideoTouchMapper.map(10f, 10f, 100f, 100f, 0, 0))
    }
}
