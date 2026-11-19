package com.sour.duoremote.ui.home

enum class DeviceSort(val storage: String, val label: String) {
    ONLINE("online", "在线优先"),
    NAME("name", "按名称"),
    RECENT("recent", "最近连接");

    companion object {
        fun fromStorage(value: String?): DeviceSort =
            entries.firstOrNull { it.storage == value } ?: ONLINE
    }
}

/**
 * Orders the device list for the home/manager pages. [recentIds] is the comma-separated history
 * persisted in settings; unknown or empty IDs simply rank after the recent ones.
 */
fun sortDevices(
    devices: List<DemoDevice>,
    sort: DeviceSort,
    recentIds: String,
): List<DemoDevice> {
    if (devices.size < 2) return devices
    val recentRank = recentIds
        .split(',')
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .withIndex()
        .associate { it.value to it.index }
    return when (sort) {
        DeviceSort.ONLINE -> devices.sortedWith(
            compareByDescending<DemoDevice> { it.online }
                .thenBy { it.name.lowercase() },
        )

        DeviceSort.NAME -> devices.sortedBy { it.name.lowercase() }

        DeviceSort.RECENT -> devices.sortedWith(
            compareBy<DemoDevice> { recentRank[it.id] ?: Int.MAX_VALUE }
                .thenByDescending { it.online }
                .thenBy { it.name.lowercase() },
        )
    }
}
