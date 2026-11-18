package com.sour.duoremote.ui.streaming

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.webrtc.VideoTrack

enum class ToolTab(val label: String) {
    TOUCHPAD("触摸板"),
    KEYBOARD("键盘"),
    SHORTCUTS("快捷键"),
    STATS("状态"),
}

enum class ImeMode {
    HIDDEN,
    BUILTIN,
    SYSTEM,
}

/**
 * Activity-scoped streaming UI state shared by the Activity and Presentation.
 * Input content itself is not persisted.
 */
class StreamingUiState {
    var tab by mutableStateOf(ToolTab.TOUCHPAD)
    var imeMode by mutableStateOf(ImeMode.HIDDEN)
    var localMenu by mutableStateOf(false)
    var localMenuIndex by mutableStateOf(0)
    var drawerExpanded by mutableStateOf(true)
    var drawerFraction by mutableStateOf(0.42f)
    var muted by mutableStateOf(false)
    var dragLock by mutableStateOf(false)
    var englishMode by mutableStateOf(false)
    var symbolPage by mutableStateOf(false)
    var composition by mutableStateOf("")
    var candidates by mutableStateOf<List<String>>(emptyList())
    var candidateIndex by mutableStateOf(0)
    var previewText by mutableStateOf("")
    var modifiers by mutableStateOf<Set<Int>>(emptySet())
    var cursorX by mutableStateOf(0.5f)
    var cursorY by mutableStateOf(0.5f)

    /** 0..3 = tabs, 4 = system IME button. Only drawn while the pad owns the toolkit. */
    var toolkitFocusIndex by mutableStateOf(0)

    /** 0 = tab row, 1 = content row (touchpad buttons, shortcut cards, stats actions). */
    var toolkitFocusRow by mutableStateOf(0)
    var toolkitBodyIndex by mutableStateOf(0)

    /**
     * Keeps the touchpad's notion of the remote cursor in sync with direct touches on the video.
     * Without this a later touchpad click would first jump the cursor back to its stale position.
     */
    fun moveCursorTo(x: Float, y: Float) {
        cursorX = x.coerceIn(0f, 1f)
        cursorY = y.coerceIn(0f, 1f)
    }

    fun selectTab(next: ToolTab, keyboardDefault: ImeMode = ImeMode.BUILTIN) {
        tab = next
        toolkitFocusIndex = next.ordinal
        toolkitFocusRow = 0
        toolkitBodyIndex = 0
        imeMode = when {
            next != ToolTab.KEYBOARD -> ImeMode.HIDDEN
            imeMode == ImeMode.HIDDEN && keyboardDefault == ImeMode.SYSTEM -> ImeMode.SYSTEM
            else -> imeMode
        }
    }

    fun moveTab(delta: Int, keyboardDefault: ImeMode = ImeMode.BUILTIN) {
        val values = ToolTab.entries
        selectTab(values[(tab.ordinal + delta + values.size) % values.size], keyboardDefault)
    }

    fun bodyItemCount(next: ToolTab = tab): Int = when (next) {
        ToolTab.TOUCHPAD -> 5
        ToolTab.SHORTCUTS -> 12
        ToolTab.STATS -> 2
        ToolTab.KEYBOARD -> 0
    }

    fun moveToolkitFocus(delta: Int) {
        if (toolkitFocusRow == 0) {
            toolkitFocusIndex = (toolkitFocusIndex + delta + 5) % 5
        } else {
            val count = bodyItemCount()
            if (count > 0) toolkitBodyIndex = (toolkitBodyIndex + delta + count) % count
        }
    }

    /** Returns true when the vertical move was consumed. */
    fun moveToolkitRow(down: Boolean): Boolean {
        if (down) {
            if (toolkitFocusRow == 0 && bodyItemCount() > 0) {
                toolkitFocusRow = 1
                toolkitBodyIndex = 0
                return true
            }
        } else if (toolkitFocusRow == 1) {
            toolkitFocusRow = 0
            toolkitFocusIndex = tab.ordinal
            return true
        }
        return false
    }

    fun activateToolkitFocus(
        onSystemIme: () -> Unit,
        keyboardDefault: ImeMode = ImeMode.BUILTIN,
    ) {
        when (val index = toolkitFocusIndex) {
            in 0..3 -> selectTab(ToolTab.entries[index], keyboardDefault)
            else -> onSystemIme()
        }
    }

    fun resetText() {
        composition = ""
        candidates = emptyList()
        candidateIndex = 0
        previewText = ""
        symbolPage = false
    }

    fun resetForNewSession() {
        tab = ToolTab.TOUCHPAD
        toolkitFocusIndex = 0
        toolkitFocusRow = 0
        toolkitBodyIndex = 0
        imeMode = ImeMode.HIDDEN
        localMenu = false
        localMenuIndex = 0
        drawerExpanded = true
        dragLock = false
        resetText()
        modifiers = emptySet()
        cursorX = 0.5f
        cursorY = 0.5f
    }
}

data class StreamUiModel(
    val track: VideoTrack,
    val title: String,
    val status: String?,
    val stats: com.sour.duoremote.session.StreamStats,
)
