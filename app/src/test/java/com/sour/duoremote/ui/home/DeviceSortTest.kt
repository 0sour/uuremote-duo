package com.sour.duoremote.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceSortTest {

    private fun device(id: String, name: String, online: Boolean) =
        DemoDevice(id, name, "Windows", online, "—", "—")

    @Test
    fun onlineSortPutsOnlineDevicesFirstThenName() {
        val devices = listOf(
            device("b", "Beta", false),
            device("a", "Alpha", true),
            device("c", "Charlie", true),
        )
        val sorted = sortDevices(devices, DeviceSort.ONLINE, "")
        assertEquals(listOf("a", "c", "b"), sorted.map { it.id })
    }

    @Test
    fun recentSortUsesPersistedHistoryThenOnline() {
        val devices = listOf(
            device("a", "Alpha", true),
            device("b", "Beta", true),
            device("c", "Charlie", false),
        )
        val sorted = sortDevices(devices, DeviceSort.RECENT, "c,b")
        assertEquals(listOf("c", "b", "a"), sorted.map { it.id })
    }

    @Test
    fun nameSortIgnoresCase() {
        val devices = listOf(
            device("b", "beta", true),
            device("a", "Alpha", false),
        )
        val sorted = sortDevices(devices, DeviceSort.NAME, "")
        assertEquals(listOf("a", "b"), sorted.map { it.id })
    }
}
