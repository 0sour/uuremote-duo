package com.sour.duoremote.ui.components

import android.media.AudioManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback

data class DuoFeedback(
    val haptic: Boolean = false,
    val sound: Boolean = false,
)

val LocalDuoFeedback = staticCompositionLocalOf { DuoFeedback() }

/**
 * Wraps a click action with the optional touch feedback configured in settings.
 * Sound uses the system key-click effect so it follows the device's media volume.
 */
@Composable
fun feedbackAction(action: () -> Unit): () -> Unit {
    val config = LocalDuoFeedback.current
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    return {
        if (config.haptic) {
            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (config.sound) {
            runCatching {
                context.getSystemService(AudioManager::class.java)
                    ?.playSoundEffect(AudioManager.FX_KEY_CLICK)
            }
        }
        action()
    }
}
