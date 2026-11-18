package com.sour.duoremote.ui.host

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class ScreenMode {
    AUTO,
    DUAL,
    SINGLE,
}

enum class SingleHost {
    AUTO,
    TOP,
    BOTTOM,
}

enum class HostLayout {
    DUAL,
    SINGLE_TOP,
    SINGLE_BOTTOM,
}

/**
 * Single source of truth for where the UI lives.
 *
 * AUTO prefers dual when a secondary display is present, otherwise single top.
 * SINGLE + BOTTOM requires a secondary display; without one it falls back to top.
 */
class HostCoordinator {
    var screenMode by mutableStateOf(ScreenMode.AUTO)
    var singleHost by mutableStateOf(SingleHost.AUTO)
    var hasSecondaryDisplay by mutableStateOf(false)

    val layout: HostLayout
        get() {
            if (hasSecondaryDisplay) {
                return when (screenMode) {
                    ScreenMode.AUTO, ScreenMode.DUAL -> HostLayout.DUAL
                    ScreenMode.SINGLE -> if (singleHost == SingleHost.BOTTOM) {
                        HostLayout.SINGLE_BOTTOM
                    } else {
                        HostLayout.SINGLE_TOP
                    }
                }
            }
            return HostLayout.SINGLE_TOP
        }

    val label: String
        get() = when (layout) {
            HostLayout.DUAL -> "双屏"
            HostLayout.SINGLE_TOP -> "单屏 · 上屏"
            HostLayout.SINGLE_BOTTOM -> "单屏 · 下屏"
        }

    fun useDual() {
        screenMode = ScreenMode.DUAL
        singleHost = SingleHost.AUTO
    }

    fun useSingleTop() {
        screenMode = ScreenMode.SINGLE
        singleHost = SingleHost.TOP
    }

    fun useSingleBottom() {
        screenMode = ScreenMode.SINGLE
        singleHost = SingleHost.BOTTOM
    }
}
