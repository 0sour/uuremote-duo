package com.sour.duoremote.ui.settings

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class SettingsCategory(val label: String) {
    PICTURE("画面"),
    SCREEN("屏幕"),
    PERIPHERAL("外设"),
    SECURITY("安全"),
    NETWORK("网络"),
    APPEARANCE("外观"),
    ABOUT("关于"),
}

/**
 * Activity-scoped settings focus. Hoisted out of the composable so the Activity window and the
 * bottom Presentation drive exactly the same category/row, including L1/R1 and left/right.
 */
class SettingsUiState {
    var category by mutableStateOf(SettingsCategory.PICTURE)
    var rowIndex by mutableStateOf(0)
    var confirmReset by mutableStateOf(false)
    var confirmSelection by mutableStateOf(0)

    fun open(initial: SettingsCategory) {
        category = initial
        rowIndex = 0
        confirmReset = false
        confirmSelection = 0
    }

    fun moveCategory(delta: Int) {
        val values = SettingsCategory.entries
        category = values[(category.ordinal + delta + values.size) % values.size]
        rowIndex = 0
        confirmReset = false
        confirmSelection = 0
    }

    fun selectCategory(index: Int) {
        val values = SettingsCategory.entries
        if (index !in values.indices) return
        category = values[index]
        rowIndex = 0
        confirmReset = false
        confirmSelection = 0
    }

    fun moveRow(delta: Int, count: Int) {
        if (count <= 0) return
        rowIndex = (rowIndex + delta).coerceIn(0, count - 1)
    }

    fun selectConfirm(next: Int) {
        confirmSelection = next.coerceIn(0, 1)
    }

    fun toggleConfirm() {
        confirmSelection = 1 - confirmSelection
    }
}
