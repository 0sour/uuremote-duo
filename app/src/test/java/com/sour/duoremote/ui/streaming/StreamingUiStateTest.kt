package com.sour.duoremote.ui.streaming

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingUiStateTest {

    @Test
    fun bodyCountsMatchVisibleControls() {
        val ui = StreamingUiState()
        assertEquals(5, ui.bodyItemCount(ToolTab.TOUCHPAD))
        assertEquals(12, ui.bodyItemCount(ToolTab.SHORTCUTS))
        assertEquals(2, ui.bodyItemCount(ToolTab.STATS))
        assertEquals(0, ui.bodyItemCount(ToolTab.KEYBOARD))
    }

    @Test
    fun tabRowWrapsAndBodyRowIsTwoLevel() {
        val ui = StreamingUiState()
        ui.moveToolkitFocus(-1)
        assertEquals(4, ui.toolkitFocusIndex)
        ui.moveToolkitFocus(1)
        assertEquals(0, ui.toolkitFocusIndex)

        assertTrue(ui.moveToolkitRow(down = true))
        assertEquals(1, ui.toolkitFocusRow)
        assertEquals(0, ui.toolkitBodyIndex)

        ui.moveToolkitFocus(-1)
        assertEquals(4, ui.toolkitBodyIndex)
        ui.moveToolkitFocus(1)
        assertEquals(0, ui.toolkitBodyIndex)

        assertTrue(ui.moveToolkitRow(down = false))
        assertEquals(0, ui.toolkitFocusRow)
        assertEquals(ToolTab.TOUCHPAD.ordinal, ui.toolkitFocusIndex)
    }

    @Test
    fun keyboardTabHasNoContentRow() {
        val ui = StreamingUiState()
        ui.selectTab(ToolTab.KEYBOARD)
        assertFalse(ui.moveToolkitRow(down = true))
        assertEquals(0, ui.toolkitFocusRow)
    }

    @Test
    fun switchingTabsResetsBodyFocus() {
        val ui = StreamingUiState()
        ui.moveToolkitRow(down = true)
        ui.moveToolkitFocus(1)
        ui.selectTab(ToolTab.SHORTCUTS)
        assertEquals(0, ui.toolkitFocusRow)
        assertEquals(0, ui.toolkitBodyIndex)
        assertEquals(ToolTab.SHORTCUTS.ordinal, ui.toolkitFocusIndex)
    }

    @Test
    fun keyboardDefaultOpensSystemImeOnlyWhenSelected() {
        val ui = StreamingUiState()
        ui.selectTab(ToolTab.TOUCHPAD, ImeMode.SYSTEM)
        assertEquals(ImeMode.HIDDEN, ui.imeMode)
        ui.selectTab(ToolTab.KEYBOARD, ImeMode.SYSTEM)
        assertEquals(ImeMode.SYSTEM, ui.imeMode)
        ui.selectTab(ToolTab.TOUCHPAD, ImeMode.SYSTEM)
        assertEquals(ImeMode.HIDDEN, ui.imeMode)
        ui.selectTab(ToolTab.KEYBOARD, ImeMode.BUILTIN)
        assertEquals(ImeMode.HIDDEN, ui.imeMode)
    }

    @Test
    fun symbolPageResetsWithSession() {
        val ui = StreamingUiState()
        ui.symbolPage = true
        ui.resetForNewSession()
        assertFalse(ui.symbolPage)
    }
}
