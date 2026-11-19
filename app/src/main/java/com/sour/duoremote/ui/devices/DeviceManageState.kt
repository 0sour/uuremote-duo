package com.sour.duoremote.ui.devices

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class DeviceManageAction { RENAME, REBOOT, REMOVE }

/**
 * Activity-scoped selection and confirmation state for device management. Network mutations live
 * in [com.sour.duoremote.MainActivity]; this class only holds UI state so both the Activity and the
 * bottom Presentation show the same dialog.
 */
class DeviceManageUiState {
    var index by mutableStateOf(0)
        private set
    var action by mutableStateOf<DeviceManageAction?>(null)
        private set
    var draftName by mutableStateOf("")
        private set
    /** 0 = confirm, 1 = cancel. */
    var confirmIndex by mutableStateOf(0)
        private set
    var busy by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    var notice by mutableStateOf<String?>(null)

    fun reset() {
        index = 0
        action = null
        draftName = ""
        confirmIndex = 0
        busy = false
        error = null
        notice = null
    }

    fun select(index: Int) {
        if (index >= 0) this.index = index
    }

    fun moveIndex(delta: Int, count: Int) {
        if (count <= 0) {
            index = 0
            return
        }
        index = (index + delta + count) % count
    }

    fun clamp(count: Int) {
        index = index.coerceIn(0, maxOf(0, count - 1))
    }

    fun begin(action: DeviceManageAction, name: String) {
        this.action = action
        draftName = name
        confirmIndex = 0
        error = null
        notice = null
    }

    fun moveConfirm(delta: Int) {
        confirmIndex = if (delta < 0) 0 else 1
    }

    fun updateDraftName(value: String) {
        draftName = value
    }

    fun updateBusy(value: Boolean) {
        busy = value
    }

    fun cancelAction() {
        if (busy) return
        action = null
        draftName = ""
        confirmIndex = 0
        error = null
    }

    fun finishAction(message: String? = null) {
        action = null
        draftName = ""
        confirmIndex = 0
        busy = false
        error = null
        notice = message
    }

    fun failAction(message: String) {
        busy = false
        error = message
    }
}
