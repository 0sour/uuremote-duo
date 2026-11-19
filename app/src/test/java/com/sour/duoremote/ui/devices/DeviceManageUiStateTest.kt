package com.sour.duoremote.ui.devices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceManageUiStateTest {

    @Test
    fun beginActionCarriesCurrentNameAndCancelClearsIt() {
        val state = DeviceManageUiState()
        state.begin(DeviceManageAction.REMOVE, "DESKTOP")
        assertEquals(DeviceManageAction.REMOVE, state.action)
        assertEquals("DESKTOP", state.draftName)
        assertEquals(0, state.confirmIndex)

        state.moveConfirm(1)
        assertEquals(1, state.confirmIndex)
        state.cancelAction()
        assertNull(state.action)
        assertEquals("", state.draftName)
        assertEquals(0, state.confirmIndex)
    }

    @Test
    fun busyActionCannotBeCancelledUntilFinished() {
        val state = DeviceManageUiState()
        state.begin(DeviceManageAction.RENAME, "PC")
        state.updateBusy(true)
        state.cancelAction()
        assertEquals(DeviceManageAction.RENAME, state.action)

        state.finishAction("已重命名")
        assertNull(state.action)
        assertEquals("已重命名", state.notice)
        assertTrue(!state.busy)
    }

    @Test
    fun indexWrapsAndClamps() {
        val state = DeviceManageUiState()
        state.moveIndex(-1, 3)
        assertEquals(2, state.index)
        state.moveIndex(1, 3)
        assertEquals(0, state.index)
        state.moveIndex(1, 0)
        assertEquals(0, state.index)
        state.select(9)
        state.clamp(3)
        assertEquals(2, state.index)
    }
}
