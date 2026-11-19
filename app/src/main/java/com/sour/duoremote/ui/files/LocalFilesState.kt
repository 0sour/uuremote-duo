package com.sour.duoremote.ui.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class LocalPickerMode { UPLOAD, DOWNLOAD }

enum class LocalRootKind { APP, AUTHORIZED }

/**
 * One entry in the in-app local browser.
 *
 * [documentId] is non-null for SAF tree entries; app-directory entries use only [path] (an
 * absolute file path). Tree entries store their content URI in [path] so opening a stream never
 * needs another picker.
 */
data class LocalEntry(
    val name: String,
    val isDirectory: Boolean,
    val size: Long,
    val modifiedTime: Long,
    val path: String,
    val documentId: String? = null,
)

/**
 * A folder in the local browser. The stack in [LocalFilesUiState] is the navigation history, so
 * app directories and SAF tree directories share one breadcrumb/up model.
 */
data class LocalLocation(
    val root: LocalRootKind,
    val label: String,
    val path: String,
    val treeUri: String = "",
    val documentId: String? = null,
)

data class PendingDownload(
    val name: String,
    val remotePath: String,
    val size: Long,
)

/**
 * Activity-scoped state for the app-built local file browser. It deliberately contains no
 * Android IO: [com.sour.duoremote.MainActivity] loads listings and creates streams, so this state
 * can be unit-tested and shared by the Activity and bottom Presentation.
 */
class LocalFilesUiState {
    var visible by mutableStateOf(false)
        private set
    var mode by mutableStateOf(LocalPickerMode.UPLOAD)
        private set
    var stack by mutableStateOf<List<LocalLocation>>(emptyList())
        private set
    var entries by mutableStateOf<List<LocalEntry>>(emptyList())
        private set
    var index by mutableStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
        private set
    var remoteDir by mutableStateOf("")
        private set
    var pendingDownload by mutableStateOf<PendingDownload?>(null)
        private set
    var treeAvailable by mutableStateOf(false)
        private set
    var authPrompt by mutableStateOf(false)
        private set

    /** Incremented on every navigation/load so a slow tree listing cannot replace a newer one. */
    var loadToken = 0
        private set

    val current: LocalLocation?
        get() = stack.lastOrNull()

    val selected: LocalEntry?
        get() = entries.getOrNull(index.coerceIn(0, maxOf(0, entries.size - 1)))

    fun open(mode: LocalPickerMode, remoteDir: String, pending: PendingDownload?) {
        this.mode = mode
        this.remoteDir = remoteDir
        pendingDownload = pending
        visible = true
        stack = emptyList()
        entries = emptyList()
        index = 0
        loading = false
        error = null
        notice = null
        authPrompt = false
        loadToken++
    }

    fun updateTreeAvailable(available: Boolean) {
        treeAvailable = available
    }

    fun beginAt(location: LocalLocation) {
        stack = listOf(location)
        entries = emptyList()
        index = 0
        loading = true
        error = null
        notice = null
        authPrompt = false
        loadToken++
    }

    fun push(location: LocalLocation) {
        stack = stack + location
        entries = emptyList()
        index = 0
        loading = true
        error = null
        notice = null
        authPrompt = false
        loadToken++
    }

    /** Removes the current folder and returns the folder that should be loaded next. */
    fun pop(): LocalLocation? {
        if (stack.size <= 1) return null
        stack = stack.dropLast(1)
        entries = emptyList()
        index = 0
        loading = true
        error = null
        notice = null
        authPrompt = false
        loadToken++
        return stack.lastOrNull()
    }

    fun retry() {
        if (current == null) return
        entries = emptyList()
        index = 0
        loading = true
        error = null
        notice = null
        loadToken++
    }

    fun showAuthPrompt(message: String? = null) {
        stack = emptyList()
        entries = emptyList()
        index = 0
        loading = false
        error = null
        notice = message
        authPrompt = true
        loadToken++
    }

    fun loaded(token: Int, location: LocalLocation, list: List<LocalEntry>) {
        if (token != loadToken || current != location) return
        entries = list
        index = 0
        loading = false
        error = null
    }

    fun failed(token: Int, message: String) {
        if (token != loadToken) return
        entries = emptyList()
        index = 0
        loading = false
        error = message
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

    fun reportError(message: String?) {
        error = message
    }

    fun note(message: String?) {
        notice = message
    }

    fun close() {
        visible = false
        stack = emptyList()
        entries = emptyList()
        index = 0
        loading = false
        error = null
        notice = null
        authPrompt = false
        remoteDir = ""
        pendingDownload = null
        loadToken++
    }
}
