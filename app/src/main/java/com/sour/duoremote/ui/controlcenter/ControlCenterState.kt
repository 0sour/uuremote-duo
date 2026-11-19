package com.sour.duoremote.ui.controlcenter

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.sour.duoremote.data.DuoSettings
import com.sour.duoremote.ui.input.GamepadEmulationMode
import com.sour.duoremote.ui.input.GamepadStyle
import java.util.Locale

enum class ControlGroup(val label: String, val hint: String) {
    PICTURE("画面与显示", "分辨率、码率与缩放"),
    PERIPHERAL("外设", "手柄、触摸板与键盘"),
    SECURITY("安全", "剪贴板与观看权限"),
    MORE("更多", "统计、指南与后续功能"),
}

enum class ControlField {
    RESOLUTION,
    FPS,
    QUALITY,
    BITRATE,
    VIDEO_SCALE,
    HDR,
    GAMEPAD_MODE,
    GAMEPAD_EMULATION,
    VIBRATION,
    GAMEPAD_STYLE,
    GAMEPAD_DEADZONE,
    INVERT_LEFT_Y,
    INVERT_RIGHT_Y,
    TOUCHPAD_SENSITIVITY,
    DIRECT_TOUCH,
    KEYBOARD_DEFAULT,
    CANDIDATE_COUNT,
    AUDIO,
    CLIPBOARD_SYNC,
    VIEW_ONLY,
    LOCK_SCREEN,
    PRIVACY_SCREEN,
    FINGERPRINT,
    DIAGNOSTICS,
    GUIDE,
    FILES,
    DISPLAYS,
    TOOLS,
}

data class PlaybackDraft(
    val resolution: String = "1920x1080",
    val fps: Int = 60,
    val quality: String = "hd",
    val bitrateMbps: Int = 0,
) {
    fun applyTo(settings: DuoSettings): DuoSettings = settings.copy(
        resolution = resolution,
        fps = fps,
        quality = quality,
        bitrateMbps = bitrateMbps,
    )

    companion object {
        fun from(settings: DuoSettings): PlaybackDraft = PlaybackDraft(
            resolution = settings.resolution,
            fps = settings.fps,
            quality = settings.quality,
            bitrateMbps = settings.bitrateMbps,
        )
    }
}

data class ControlRow(
    val field: ControlField?,
    val title: String,
    val description: String,
    val value: String,
    val enabled: Boolean = true,
    val pending: Boolean = false,
    val danger: Boolean = false,
    val tag: String? = null,
)

data class ControlAdjustment(
    val draft: PlaybackDraft? = null,
    val settings: DuoSettings? = null,
    val openHelp: Boolean = false,
    val openFiles: Boolean = false,
) {
    val handled: Boolean
        get() = draft != null || settings != null || openHelp || openFiles
}

/**
 * Activity-scoped control-center state. Playback rows are edited into [draft] and only committed
 * by "应用并重连"; peripheral/security rows are applied immediately through updateSettings.
 */
class ControlCenterState {
    var group by mutableStateOf(ControlGroup.PICTURE)
        private set
    var subTab by mutableStateOf(0)
        private set
    var rowIndex by mutableStateOf(0)
        private set
    var draft by mutableStateOf(PlaybackDraft())
        private set

    fun open(settings: DuoSettings) {
        group = ControlGroup.PICTURE
        subTab = 0
        rowIndex = 0
        draft = PlaybackDraft.from(settings)
    }

    fun close() {
        rowIndex = 0
    }

    fun syncAfterApply(settings: DuoSettings) {
        draft = PlaybackDraft.from(settings)
        rowIndex = 0
    }

    fun updateDraft(next: PlaybackDraft) {
        draft = next
    }

    fun cancel(settings: DuoSettings) {
        draft = PlaybackDraft.from(settings)
        rowIndex = 0
    }

    fun pendingCount(settings: DuoSettings): Int {
        var count = 0
        if (draft.resolution != settings.resolution) count++
        if (draft.fps != settings.fps) count++
        if (draft.quality != settings.quality) count++
        if (draft.bitrateMbps != settings.bitrateMbps) count++
        return count
    }

    fun applyTo(settings: DuoSettings): DuoSettings = draft.applyTo(settings)

    fun rows(settings: DuoSettings): List<ControlRow> = controlRows(group, subTab, draft, settings)

    fun subTabCount(): Int = when (group) {
        ControlGroup.PICTURE -> 2
        ControlGroup.PERIPHERAL -> 3
        ControlGroup.SECURITY, ControlGroup.MORE -> 0
    }

    fun moveGroup(delta: Int) {
        val values = ControlGroup.entries
        group = values[(group.ordinal + delta + values.size) % values.size]
        subTab = 0
        rowIndex = 0
    }

    fun moveSubTab(delta: Int) {
        val count = subTabCount()
        if (count <= 1) return
        subTab = (subTab + delta + count) % count
        rowIndex = 0
    }

    fun moveRow(delta: Int, count: Int) {
        if (count <= 0) return
        rowIndex = (rowIndex + delta).coerceIn(0, count - 1)
    }

    fun selectRow(index: Int) {
        if (index >= 0) rowIndex = index
    }

    fun adjust(field: ControlField, delta: Int, settings: DuoSettings): ControlAdjustment {
        val step = if (delta == 0) 1 else delta
        fun next(value: String, values: List<String>): String {
            val index = values.indexOf(value).let { if (it < 0) 0 else it }
            return values[(index + step + values.size) % values.size]
        }
        fun <T> next(value: T, values: List<T>): T {
            val index = values.indexOf(value).let { if (it < 0) 0 else it }
            return values[(index + step + values.size) % values.size]
        }
        return when (field) {
            ControlField.RESOLUTION -> ControlAdjustment(
                draft = draft.copy(resolution = next(draft.resolution, listOf("1920x1080", "1280x720", "window"))),
            )

            ControlField.FPS -> ControlAdjustment(
                draft = draft.copy(fps = next(draft.fps, listOf(30, 60))),
            )

            ControlField.QUALITY -> ControlAdjustment(
                draft = draft.copy(quality = next(draft.quality, listOf("smooth", "standard", "hd"))),
            )

            ControlField.BITRATE -> ControlAdjustment(
                draft = draft.copy(bitrateMbps = next(draft.bitrateMbps, listOf(0, 20, 50, 100))),
            )

            // Local rendering only: no video renegotiation, so apply it immediately.
            ControlField.VIDEO_SCALE -> ControlAdjustment(
                settings = settings.copy(
                    videoScale = next(settings.videoScale, listOf("fit", "fill", "original")),
                ),
            )

            ControlField.GAMEPAD_MODE -> ControlAdjustment(
                settings = settings.copy(gamepadPassthrough = !settings.gamepadPassthrough),
            )

            ControlField.GAMEPAD_EMULATION -> {
                val values = GamepadEmulationMode.entries
                val current = GamepadEmulationMode.fromStorage(settings.gamepadEmulation)
                ControlAdjustment(
                    settings = settings.copy(
                        gamepadEmulation = values[(current.ordinal + step + values.size) % values.size].storage,
                    ),
                )
            }

            ControlField.VIBRATION -> ControlAdjustment(
                settings = settings.copy(vibration = !settings.vibration),
            )

            ControlField.GAMEPAD_STYLE -> ControlAdjustment(
                settings = settings.copy(gamepadStyle = next(settings.gamepadStyle, GamepadStyle.entries)),
            )

            ControlField.GAMEPAD_DEADZONE -> {
                val values = listOf(0.08f, 0.16f, 0.25f)
                val current = values.indexOfFirst { kotlin.math.abs(it - settings.gamepadDeadzone) < 0.001f }
                    .coerceAtLeast(0)
                ControlAdjustment(
                    settings = settings.copy(
                        gamepadDeadzone = values[(current + step + values.size) % values.size],
                    ),
                )
            }

            ControlField.INVERT_LEFT_Y -> ControlAdjustment(
                settings = settings.copy(invertLeftY = !settings.invertLeftY),
            )

            ControlField.INVERT_RIGHT_Y -> ControlAdjustment(
                settings = settings.copy(invertRightY = !settings.invertRightY),
            )

            ControlField.TOUCHPAD_SENSITIVITY -> {
                val values = listOf(0.6f, 0.8f, 1.0f, 1.2f, 1.5f, 2.0f, 2.5f)
                val current = values.indexOfFirst { it == settings.touchpadSensitivity }.coerceAtLeast(0)
                ControlAdjustment(
                    settings = settings.copy(
                        touchpadSensitivity = values[(current + step + values.size) % values.size],
                    ),
                )
            }

            ControlField.DIRECT_TOUCH -> ControlAdjustment(
                settings = settings.copy(directTouch = !settings.directTouch),
            )

            ControlField.KEYBOARD_DEFAULT -> ControlAdjustment(
                settings = settings.copy(
                    keyboardDefault = if (settings.keyboardDefault == "system") "builtin" else "system",
                ),
            )

            ControlField.CANDIDATE_COUNT -> ControlAdjustment(
                settings = settings.copy(
                    candidateCount = if (step < 0) {
                        if (settings.candidateCount <= 5) 9 else settings.candidateCount - 1
                    } else {
                        if (settings.candidateCount >= 9) 5 else settings.candidateCount + 1
                    },
                ),
            )

            ControlField.AUDIO -> ControlAdjustment(
                settings = settings.copy(audio = !settings.audio),
            )

            ControlField.CLIPBOARD_SYNC -> ControlAdjustment(
                settings = settings.copy(clipboardSync = !settings.clipboardSync),
            )

            ControlField.VIEW_ONLY -> ControlAdjustment(
                settings = settings.copy(viewOnly = !settings.viewOnly),
            )

            ControlField.DIAGNOSTICS -> ControlAdjustment(
                settings = settings.copy(diagnostics = !settings.diagnostics),
            )

            ControlField.GUIDE -> ControlAdjustment(openHelp = true)

            ControlField.FILES -> ControlAdjustment(openFiles = true)

            ControlField.HDR,
            ControlField.LOCK_SCREEN,
            ControlField.PRIVACY_SCREEN,
            ControlField.FINGERPRINT,
            ControlField.DISPLAYS,
            ControlField.TOOLS,
            -> ControlAdjustment()
        }
    }
}

fun controlRows(
    group: ControlGroup,
    subTab: Int,
    draft: PlaybackDraft,
    settings: DuoSettings,
): List<ControlRow> = when (group) {
    ControlGroup.PICTURE -> if (subTab == 0) {
        listOf(
            ControlRow(
                ControlField.RESOLUTION,
                "分辨率",
                "修改后需要重新协商视频",
                resolutionLabel(draft.resolution),
                pending = draft.resolution != settings.resolution,
            ),
            ControlRow(
                ControlField.FPS,
                "帧率上限",
                "30 / 60 FPS",
                "${draft.fps} FPS",
                pending = draft.fps != settings.fps,
            ),
            ControlRow(
                ControlField.QUALITY,
                "画质",
                "流畅 / 标准 / 高清",
                qualityLabel(draft.quality),
                pending = draft.quality != settings.quality,
            ),
            ControlRow(
                ControlField.BITRATE,
                "码率上限",
                "自动或手动上限",
                bitrateLabel(draft.bitrateMbps),
                pending = draft.bitrateMbps != settings.bitrateMbps,
            ),
        )
    } else {
        listOf(
            ControlRow(
                ControlField.VIDEO_SCALE,
                "画面缩放",
                "适应 / 铺满 / 原始比例；即时生效",
                scaleLabel(settings.videoScale),
            ),
            ControlRow(
                ControlField.HDR,
                "HDR 色彩",
                "需要被控端与编码器支持",
                "后续",
                enabled = false,
                tag = "后续",
            ),
            ControlRow(
                ControlField.DISPLAYS,
                "多显示器",
                "在控制中心切换远端屏幕",
                "后续",
                enabled = false,
                tag = "后续",
            ),
        )
    }

    ControlGroup.PERIPHERAL -> when (subTab) {
        0 -> listOf(
            ControlRow(
                ControlField.GAMEPAD_MODE,
                "手柄模式",
                if (settings.viewOnly) "仅观看时暂停远端输入" else "直通电脑或本地操作",
                if (settings.gamepadPassthrough) "直通电脑" else "本地操作",
            ),
            ControlRow(
                ControlField.GAMEPAD_EMULATION,
                "手柄映射",
                "实体手柄转键鼠模板",
                if (settings.gamepadEmulation == "off") {
                    "关闭"
                } else {
                    "映射 · ${GamepadEmulationMode.fromStorage(settings.gamepadEmulation).label}"
                },
            ),
            ControlRow(
                ControlField.VIBRATION,
                "振动",
                "远端 gpd_vibration",
                onOff(settings.vibration),
            ),
            ControlRow(
                ControlField.GAMEPAD_STYLE,
                "手柄风格",
                "入口归一化为标准 Xbox",
                gamepadStyleLabel(settings.gamepadStyle),
            ),
            ControlRow(
                ControlField.GAMEPAD_DEADZONE,
                "摇杆死区",
                "中心不动区",
                "${(settings.gamepadDeadzone * 100).toInt()}%",
            ),
            ControlRow(
                ControlField.INVERT_LEFT_Y,
                "左摇杆 Y 反向",
                "仅影响远端直通",
                onOff(settings.invertLeftY),
            ),
            ControlRow(
                ControlField.INVERT_RIGHT_Y,
                "右摇杆 Y 反向",
                "仅影响远端直通",
                onOff(settings.invertRightY),
            ),
        )

        1 -> listOf(
            ControlRow(
                ControlField.TOUCHPAD_SENSITIVITY,
                "触摸板灵敏度",
                "0.6×–2.5×",
                String.format(Locale.US, "%.1f×", settings.touchpadSensitivity),
            ),
            ControlRow(
                ControlField.DIRECT_TOUCH,
                "直接触摸视频",
                "点按画面即移动远端光标",
                onOff(settings.directTouch),
            ),
            ControlRow(
                ControlField.KEYBOARD_DEFAULT,
                "默认输入",
                "内置拼音或系统输入法",
                if (settings.keyboardDefault == "system") "系统 IME" else "内置拼音",
            ),
            ControlRow(
                ControlField.CANDIDATE_COUNT,
                "候选数",
                "5–9 个候选",
                "${settings.candidateCount}",
            ),
        )

        else -> listOf(
            ControlRow(
                ControlField.AUDIO,
                "远端音频",
                "播放电脑声音，可随时静音",
                onOff(settings.audio),
            ),
        )
    }

    ControlGroup.SECURITY -> listOf(
        ControlRow(
            ControlField.CLIPBOARD_SYNC,
            "同步剪贴板",
            "文字双向同步；默认关闭",
            onOff(settings.clipboardSync),
        ),
        ControlRow(
            ControlField.VIEW_ONLY,
            "仅观看",
            "本机不发送键鼠、手柄和文字；保持到手动关闭",
            onOff(settings.viewOnly),
            danger = settings.viewOnly,
        ),
        ControlRow(
            ControlField.LOCK_SCREEN,
            "锁屏保护",
            "需要被控端支持",
            "后续",
            enabled = false,
            tag = "后续",
        ),
        ControlRow(
            ControlField.PRIVACY_SCREEN,
            "防窥屏",
            "需要被控端支持",
            "后续",
            enabled = false,
            tag = "后续",
        ),
        ControlRow(
            ControlField.FINGERPRINT,
            "指纹确认",
            "本机生物识别绑定",
            "后续",
            enabled = false,
            tag = "后续",
        ),
    )

    ControlGroup.MORE -> listOf(
        ControlRow(
            ControlField.DIAGNOSTICS,
            "网络统计",
            "在工具箱状态页显示 FPS / 码率 / RTT / 丢包",
            onOff(settings.diagnostics),
        ),
        ControlRow(
            ControlField.GUIDE,
            "手柄说明",
            "查看方向键、B/A、L1/R1、L2/R2 与 START",
            "打开",
        ),
        ControlRow(
            ControlField.FILES,
            "文件传输",
            if (settings.viewOnly) "仅观看模式下不可用" else "浏览远端目录，上传或下载单个文件",
            if (settings.viewOnly) "仅观看" else "打开",
            enabled = !settings.viewOnly,
        ),
        ControlRow(
            ControlField.DISPLAYS,
            "多屏 / 超级屏",
            "需要 STREAMER 多流与电脑端支持",
            "后续",
            enabled = false,
            tag = "后续",
        ),
        ControlRow(
            ControlField.TOOLS,
            "开机 / 终端 / 端口",
            "需要电脑端 RPC 与端口映射",
            "后续",
            enabled = false,
            tag = "后续",
        ),
    )
}

fun resolutionLabel(value: String): String = when (value) {
    "1280x720" -> "720p"
    "window" -> "跟随窗口"
    else -> "1080p"
}

fun qualityLabel(value: String): String = when (value) {
    "smooth" -> "流畅"
    "standard" -> "标准"
    else -> "高清"
}

fun scaleLabel(value: String): String = when (value) {
    "fill" -> "铺满"
    "original" -> "原始"
    else -> "适应"
}

fun bitrateLabel(value: Int): String =
    if (value <= 0) "自动" else "$value Mbps"

fun gamepadStyleLabel(value: GamepadStyle): String = when (value) {
    GamepadStyle.NORMAL -> "标准"
    GamepadStyle.RETRO -> "Retro"
}

fun onOff(value: Boolean): String = if (value) "开" else "关"
