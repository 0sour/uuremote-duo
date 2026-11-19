package com.sour.duoremote.ui.controlcenter

import com.sour.duoremote.data.DuoSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ControlCenterStateTest {

    private val base = DuoSettings()

    @Test
    fun openSnapshotsPlaybackWithoutPendingChanges() {
        val state = ControlCenterState()
        state.open(base)

        assertEquals(PlaybackDraft.from(base), state.draft)
        assertEquals(0, state.pendingCount(base))
        assertEquals(ControlGroup.PICTURE, state.group)
        assertEquals(0, state.subTab)
        assertEquals(0, state.rowIndex)
    }

    @Test
    fun playbackEditsStayPendingUntilApplied() {
        val state = ControlCenterState()
        state.open(base)

        state.adjust(ControlField.RESOLUTION, 1, base).draft?.let(state::updateDraft)
        state.adjust(ControlField.FPS, 1, base).draft?.let(state::updateDraft)
        assertEquals("1280x720", state.draft.resolution)
        assertEquals(30, state.draft.fps)
        assertEquals(2, state.pendingCount(base))
        assertEquals("1920x1080", base.resolution)
        assertEquals(60, base.fps)

        val applied = state.applyTo(base)
        assertEquals("1280x720", applied.resolution)
        assertEquals(30, applied.fps)
    }

    @Test
    fun cancelDiscardsDraftAndKeepsSettings() {
        val state = ControlCenterState()
        state.open(base)
        state.adjust(ControlField.QUALITY, 1, base).draft?.let(state::updateDraft)
        assertTrue(state.pendingCount(base) > 0)

        state.cancel(base)
        assertEquals(0, state.pendingCount(base))
        assertEquals(PlaybackDraft.from(base), state.draft)
    }

    @Test
    fun immediateSettingsAreReturnedSeparately() {
        val state = ControlCenterState()
        state.open(base)

        val viewOnly = state.adjust(ControlField.VIEW_ONLY, 1, base)
        assertEquals(true, viewOnly.settings?.viewOnly)
        assertTrue(viewOnly.draft == null)

        // Scaling is local-only and must not trigger a reconnect.
        val scale = state.adjust(ControlField.VIDEO_SCALE, 1, base)
        assertEquals("fill", scale.settings?.videoScale)
        assertTrue(scale.draft == null)
        assertEquals(0, state.pendingCount(base))
    }

    @Test
    fun gamepadEmulationCyclesThroughTemplates() {
        val state = ControlCenterState()
        state.open(base)

        val desktop = state.adjust(ControlField.GAMEPAD_EMULATION, 1, base).settings
        assertEquals("desktop", desktop?.gamepadEmulation)
        val fps = state.adjust(ControlField.GAMEPAD_EMULATION, 1, desktop!!).settings
        assertEquals("fps", fps?.gamepadEmulation)
        val emulator = state.adjust(ControlField.GAMEPAD_EMULATION, 1, fps!!).settings
        assertEquals("emulator", emulator?.gamepadEmulation)
        val off = state.adjust(ControlField.GAMEPAD_EMULATION, 1, emulator!!).settings
        assertEquals("off", off?.gamepadEmulation)
    }

    @Test
    fun groupsSubtabsAndRowsClamp() {
        val state = ControlCenterState()
        state.open(base)

        state.moveGroup(-1)
        assertEquals(ControlGroup.MORE, state.group)
        assertEquals(0, state.subTabCount())

        state.moveGroup(1)
        assertEquals(ControlGroup.PICTURE, state.group)
        assertEquals(2, state.subTabCount())

        state.moveSubTab(1)
        assertEquals(1, state.subTab)
        state.moveRow(5, 3)
        assertEquals(2, state.rowIndex)
        state.moveRow(-9, 3)
        assertEquals(0, state.rowIndex)
    }

    @Test
    fun moreGuideOpensHelpAndFilesOpensBrowser() {
        val state = ControlCenterState()
        state.open(base)

        assertTrue(state.adjust(ControlField.GUIDE, 1, base).openHelp)
        assertTrue(state.adjust(ControlField.FILES, 1, base).openFiles)
        assertFalse(state.adjust(ControlField.HDR, 1, base).handled)
    }

    @Test
    fun rowsExposePendingAndPlaceholderTags() {
        val state = ControlCenterState()
        state.open(base)
        state.adjust(ControlField.BITRATE, 1, base).draft?.let(state::updateDraft)

        val picture = state.rows(base)
        assertTrue(picture.first { it.field == ControlField.BITRATE }.pending)

        state.moveGroup(3)
        assertEquals(ControlGroup.MORE, state.group)
        val more = state.rows(base)
        assertTrue(more.first { it.field == ControlField.FILES }.enabled)
        assertEquals("打开", more.first { it.field == ControlField.FILES }.value)
        assertTrue(more.first { it.field == ControlField.DISPLAYS }.tag == "后续")
        assertFalse(more.first { it.field == ControlField.DISPLAYS }.enabled)
    }
}
