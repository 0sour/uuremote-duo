package com.sour.duoremote.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Which part of the home page owns the pad focus. The top screen never participates. */
enum class HomeFocus { LIST, ACTIONS }

/**
 * Activity-scoped focus state for the home action bar. Device selection stays in
 * MainActivity because it drives the top detail card; only the focus zone lives here.
 */
class HomeUiState {
    var focus by mutableStateOf(HomeFocus.LIST)
    var actionIndex by mutableStateOf(0)

    /** 0 = 连接, 1 = 偏好, 2 = 设置, 3 = 账号. */
    val actionCount: Int = 4

    fun reset(hasDevices: Boolean) {
        focus = if (hasDevices) HomeFocus.LIST else HomeFocus.ACTIONS
        actionIndex = 0
    }

    fun moveAction(delta: Int) {
        actionIndex = (actionIndex + delta + actionCount) % actionCount
    }

    fun toActions() {
        focus = HomeFocus.ACTIONS
        actionIndex = 0
    }

    fun toList() {
        focus = HomeFocus.LIST
    }
}
