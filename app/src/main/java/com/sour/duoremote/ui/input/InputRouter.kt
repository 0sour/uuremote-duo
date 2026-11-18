package com.sour.duoremote.ui.input

import android.view.KeyEvent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class GamepadStyle {
    NORMAL,
    RETRO,
}

/**
 * Entry point for local gamepad navigation.
 *
 * Physical Duo labels are B/A/Y/X (bottom/right/left/top). Android may deliver either the
 * standard keycodes or the Retro-swapped set; normalize to canonical Xbox and map canonical
 * A/B/X/Y to local UI actions. Remote passthrough will consume the same normalized state later.
 *
 * Home follows the design's B-confirm layout (B connect, A back, Y refresh, X account), while
 * login and forms keep the platform-natural A confirm / B back pair.
 */
class InputRouter {
    var style by mutableStateOf(GamepadStyle.NORMAL)

    fun normalizeFaceKey(keyCode: Int): Int {
        if (style != GamepadStyle.RETRO) return keyCode
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
            KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
            KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
            KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
            else -> keyCode
        }
    }

    fun handleLocalKey(
        event: KeyEvent,
        onConfirm: () -> Unit,
        onBack: () -> Unit,
        onRefresh: () -> Unit,
        onAccount: () -> Unit,
        onSettings: () -> Unit,
        confirmOnB: Boolean = false,
        onDirection: (dx: Int, dy: Int) -> Unit = { _, _ -> },
    ): Boolean {
        if (event.action != KeyEvent.ACTION_UP) return false
        // Key events are normalized once at the window entry point, so this handler only sees
        // canonical Xbox keycodes. Never normalize again or Retro mode would swap twice.
        when (event.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP -> {
                onDirection(0, -1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_DOWN -> {
                onDirection(0, 1)
                return true
            }

            KeyEvent.KEYCODE_DPAD_LEFT -> {
                onDirection(-1, 0)
                return true
            }

            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                onDirection(1, 0)
                return true
            }
        }
        val key = event.keyCode
        val action = if (confirmOnB) {
            when (key) {
                KeyEvent.KEYCODE_BUTTON_A -> KeyEvent.KEYCODE_BUTTON_B
                KeyEvent.KEYCODE_BUTTON_B -> KeyEvent.KEYCODE_BUTTON_A
                KeyEvent.KEYCODE_BUTTON_X -> KeyEvent.KEYCODE_BUTTON_Y
                KeyEvent.KEYCODE_BUTTON_Y -> KeyEvent.KEYCODE_BUTTON_X
                else -> key
            }
        } else {
            key
        }
        return when (action) {
            KeyEvent.KEYCODE_BUTTON_B -> {
                onConfirm()
                true
            }

            KeyEvent.KEYCODE_BUTTON_A -> {
                onBack()
                true
            }

            KeyEvent.KEYCODE_BUTTON_Y -> {
                onRefresh()
                true
            }

            KeyEvent.KEYCODE_BUTTON_X -> {
                onAccount()
                true
            }

            KeyEvent.KEYCODE_BUTTON_START -> {
                onSettings()
                true
            }

            else -> false
        }
    }
}
