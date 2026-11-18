package com.sour.duoremote.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sour.duoremote.ui.host.ScreenMode
import com.sour.duoremote.ui.host.SingleHost
import com.sour.duoremote.ui.input.GamepadStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

data class DuoSettings(
    val screenMode: ScreenMode = ScreenMode.AUTO,
    val singleHost: SingleHost = SingleHost.AUTO,
    val resolution: String = "1920x1080",
    val fps: Int = 60,
    val quality: String = "hd",
    val bitrateMbps: Int = 0,
    val videoScale: String = "fit",
    val audio: Boolean = true,
    val autoReconnect: Boolean = true,
    val gamepadStyle: GamepadStyle = GamepadStyle.NORMAL,
    val gamepadPassthrough: Boolean = true,
    val vibration: Boolean = true,
    val gamepadDeadzone: Float = 0.16f,
    val invertLeftY: Boolean = false,
    val invertRightY: Boolean = false,
    val keyboardDefault: String = "builtin",
    val candidateCount: Int = 7,
    val clipboardSync: Boolean = false,
    val viewOnly: Boolean = false,
    val hapticFeedback: Boolean = true,
    val soundFeedback: Boolean = false,
    val touchpadSensitivity: Float = 1.0f,
    val directTouch: Boolean = false,
    val theme: String = "dark",
    val focusRingStyle: String = "white",
    val diagnostics: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "duo_settings")

class SettingsStore(private val context: Context) {

    val settings: Flow<DuoSettings> = context.settingsDataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            DuoSettings(
                screenMode = prefs[KEY_SCREEN_MODE].toScreenMode(),
                singleHost = prefs[KEY_SINGLE_HOST].toSingleHost(),
                resolution = prefs[KEY_RESOLUTION] ?: "1920x1080",
                fps = prefs[KEY_FPS] ?: 60,
                quality = prefs[KEY_QUALITY] ?: "hd",
                bitrateMbps = prefs[KEY_BITRATE] ?: 0,
                videoScale = prefs[KEY_VIDEO_SCALE] ?: "fit",
                audio = prefs[KEY_AUDIO] ?: true,
                autoReconnect = prefs[KEY_AUTO_RECONNECT] ?: true,
                gamepadStyle = prefs[KEY_GAMEPAD_STYLE].toGamepadStyle(),
                gamepadPassthrough = prefs[KEY_GAMEPAD_PASSTHROUGH] ?: true,
                vibration = prefs[KEY_VIBRATION] ?: true,
                gamepadDeadzone = prefs[KEY_GAMEPAD_DEADZONE] ?: 0.16f,
                invertLeftY = prefs[KEY_INVERT_LEFT_Y] ?: false,
                invertRightY = prefs[KEY_INVERT_RIGHT_Y] ?: false,
                keyboardDefault = prefs[KEY_KEYBOARD_DEFAULT] ?: "builtin",
                candidateCount = (prefs[KEY_CANDIDATE_COUNT] ?: 7).coerceIn(5, 9),
                clipboardSync = prefs[KEY_CLIPBOARD_SYNC] ?: false,
                viewOnly = prefs[KEY_VIEW_ONLY] ?: false,
                hapticFeedback = prefs[KEY_HAPTIC] ?: true,
                soundFeedback = prefs[KEY_SOUND] ?: false,
                touchpadSensitivity = prefs[KEY_TOUCHPAD_SENSITIVITY] ?: 1.0f,
                directTouch = prefs[KEY_DIRECT_TOUCH] ?: false,
                theme = prefs[KEY_THEME] ?: "dark",
                focusRingStyle = prefs[KEY_FOCUS_RING] ?: "white",
                diagnostics = prefs[KEY_DIAGNOSTICS] ?: false,
            )
        }

    suspend fun save(settings: DuoSettings) {
        context.settingsDataStore.edit { prefs ->
            prefs[KEY_SCREEN_MODE] = settings.screenMode.name
            prefs[KEY_SINGLE_HOST] = settings.singleHost.name
            prefs[KEY_RESOLUTION] = settings.resolution
            prefs[KEY_FPS] = settings.fps
            prefs[KEY_QUALITY] = settings.quality
            prefs[KEY_BITRATE] = settings.bitrateMbps
            prefs[KEY_VIDEO_SCALE] = settings.videoScale
            prefs[KEY_AUDIO] = settings.audio
            prefs[KEY_AUTO_RECONNECT] = settings.autoReconnect
            prefs[KEY_GAMEPAD_STYLE] = settings.gamepadStyle.name
            prefs[KEY_GAMEPAD_PASSTHROUGH] = settings.gamepadPassthrough
            prefs[KEY_VIBRATION] = settings.vibration
            prefs[KEY_GAMEPAD_DEADZONE] = settings.gamepadDeadzone.coerceIn(0f, 0.9f)
            prefs[KEY_INVERT_LEFT_Y] = settings.invertLeftY
            prefs[KEY_INVERT_RIGHT_Y] = settings.invertRightY
            prefs[KEY_KEYBOARD_DEFAULT] = settings.keyboardDefault
            prefs[KEY_CANDIDATE_COUNT] = settings.candidateCount
            prefs[KEY_CLIPBOARD_SYNC] = settings.clipboardSync
            prefs[KEY_VIEW_ONLY] = settings.viewOnly
            prefs[KEY_HAPTIC] = settings.hapticFeedback
            prefs[KEY_SOUND] = settings.soundFeedback
            prefs[KEY_TOUCHPAD_SENSITIVITY] = settings.touchpadSensitivity.coerceIn(0.4f, 2.5f)
            prefs[KEY_DIRECT_TOUCH] = settings.directTouch
            prefs[KEY_THEME] = settings.theme
            prefs[KEY_FOCUS_RING] = settings.focusRingStyle
            prefs[KEY_DIAGNOSTICS] = settings.diagnostics
        }
    }

    suspend fun setScreenMode(mode: ScreenMode) {
        context.settingsDataStore.edit { it[KEY_SCREEN_MODE] = mode.name }
    }

    suspend fun setSingleHost(host: SingleHost) {
        context.settingsDataStore.edit { it[KEY_SINGLE_HOST] = host.name }
    }

    suspend fun setPlayback(resolution: String, fps: Int, quality: String) {
        context.settingsDataStore.edit {
            it[KEY_RESOLUTION] = resolution
            it[KEY_FPS] = fps
            it[KEY_QUALITY] = quality
        }
    }

    suspend fun setAudio(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUDIO] = enabled }
    }

    suspend fun setAutoReconnect(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_AUTO_RECONNECT] = enabled }
    }

    suspend fun setGamepad(style: GamepadStyle, passthrough: Boolean) {
        context.settingsDataStore.edit {
            it[KEY_GAMEPAD_STYLE] = style.name
            it[KEY_GAMEPAD_PASSTHROUGH] = passthrough
        }
    }

    suspend fun setTouchpadSensitivity(value: Float) {
        context.settingsDataStore.edit { it[KEY_TOUCHPAD_SENSITIVITY] = value.coerceIn(0.4f, 2.5f) }
    }

    private fun String?.toScreenMode(): ScreenMode =
        runCatching { ScreenMode.valueOf(this ?: "") }.getOrDefault(ScreenMode.AUTO)

    private fun String?.toSingleHost(): SingleHost =
        runCatching { SingleHost.valueOf(this ?: "") }.getOrDefault(SingleHost.AUTO)

    private fun String?.toGamepadStyle(): GamepadStyle =
        runCatching { GamepadStyle.valueOf(this ?: "") }.getOrDefault(GamepadStyle.NORMAL)

    private companion object {
        val KEY_SCREEN_MODE = stringPreferencesKey("screen_mode")
        val KEY_SINGLE_HOST = stringPreferencesKey("single_host")
        val KEY_RESOLUTION = stringPreferencesKey("resolution")
        val KEY_FPS = intPreferencesKey("fps")
        val KEY_QUALITY = stringPreferencesKey("quality")
        val KEY_BITRATE = intPreferencesKey("bitrate_mbps")
        val KEY_VIDEO_SCALE = stringPreferencesKey("video_scale")
        val KEY_AUDIO = booleanPreferencesKey("audio")
        val KEY_AUTO_RECONNECT = booleanPreferencesKey("auto_reconnect")
        val KEY_GAMEPAD_STYLE = stringPreferencesKey("gamepad_style")
        val KEY_GAMEPAD_PASSTHROUGH = booleanPreferencesKey("gamepad_passthrough")
        val KEY_VIBRATION = booleanPreferencesKey("vibration")
        val KEY_GAMEPAD_DEADZONE = floatPreferencesKey("gamepad_deadzone")
        val KEY_INVERT_LEFT_Y = booleanPreferencesKey("invert_left_y")
        val KEY_INVERT_RIGHT_Y = booleanPreferencesKey("invert_right_y")
        val KEY_KEYBOARD_DEFAULT = stringPreferencesKey("keyboard_default")
        val KEY_CANDIDATE_COUNT = intPreferencesKey("candidate_count")
        val KEY_CLIPBOARD_SYNC = booleanPreferencesKey("clipboard_sync")
        val KEY_VIEW_ONLY = booleanPreferencesKey("view_only")
        val KEY_HAPTIC = booleanPreferencesKey("haptic_feedback")
        val KEY_SOUND = booleanPreferencesKey("sound_feedback")
        val KEY_TOUCHPAD_SENSITIVITY = floatPreferencesKey("touchpad_sensitivity")
        val KEY_DIRECT_TOUCH = booleanPreferencesKey("direct_touch")
        val KEY_THEME = stringPreferencesKey("theme")
        val KEY_FOCUS_RING = stringPreferencesKey("focus_ring")
        val KEY_DIAGNOSTICS = booleanPreferencesKey("diagnostics")
    }
}
