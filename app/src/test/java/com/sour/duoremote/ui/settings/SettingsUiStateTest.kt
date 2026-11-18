package com.sour.duoremote.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsUiStateTest {

    @Test
    fun categoriesWrapAndResetTheRow() {
        val state = SettingsUiState()
        state.open(SettingsCategory.PICTURE)
        state.rowIndex = 4

        state.moveCategory(-1)
        assertEquals(SettingsCategory.ABOUT, state.category)
        assertEquals(0, state.rowIndex)

        state.moveCategory(1)
        assertEquals(SettingsCategory.PICTURE, state.category)
        assertEquals(0, state.rowIndex)
    }

    @Test
    fun rowSelectionClampsToAvailableRows() {
        val state = SettingsUiState()
        state.open(SettingsCategory.PERIPHERAL)
        state.moveRow(99, 5)
        assertEquals(4, state.rowIndex)
        state.moveRow(-99, 5)
        assertEquals(0, state.rowIndex)
        state.moveRow(1, 0)
        assertEquals(0, state.rowIndex)
    }

    @Test
    fun confirmSelectionTogglesAndResetsOnCategoryChange() {
        val state = SettingsUiState()
        state.open(SettingsCategory.SECURITY)
        state.confirmReset = true
        state.selectConfirm(1)
        assertEquals(1, state.confirmSelection)

        state.toggleConfirm()
        assertEquals(0, state.confirmSelection)

        state.confirmReset = true
        state.moveCategory(1)
        assertFalse(state.confirmReset)
        assertEquals(0, state.confirmSelection)
        assertTrue(state.category == SettingsCategory.NETWORK)
    }

    @Test
    fun rowsCoverAllSevenCategories() {
        val settings = com.sour.duoremote.data.DuoSettings()
        SettingsCategory.entries.forEach { category ->
            val rows = settingsRows(category, settings, {}, {})
            assertTrue("$category should have rows", rows.isNotEmpty())
        }
        assertEquals(7, SettingsCategory.entries.size)
    }

    @Test
    fun restoreDefaultsRowIsDangerousAndHasNoAdjust() {
        val settings = com.sour.duoremote.data.DuoSettings()
        val rows = settingsRows(SettingsCategory.ABOUT, settings, {}, {})
        val restore = rows.first { it.title == "恢复默认" }
        assertTrue(restore.danger)
        assertEquals(null, restore.adjust)
    }
}
