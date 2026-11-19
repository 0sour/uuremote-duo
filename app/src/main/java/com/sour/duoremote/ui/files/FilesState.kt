package com.sour.duoremote.ui.files

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class FilesTab(val label: String) {
    BROWSE("浏览"),
    QUEUE("队列"),
}

/**
 * Activity-scoped focus/tab state for the file page. The remote listing and transfer progress
 * live in [com.sour.duoremote.session.FileTransferState]; only presentation state lives here so
 * the bottom Presentation can be rebuilt without losing the selected row.
 */
class FilesUiState {
    var tab by mutableStateOf(FilesTab.BROWSE)
    var index by mutableStateOf(0)
    var requestUpload by mutableStateOf(false)
    var requestDownload by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)

    fun reset() {
        tab = FilesTab.BROWSE
        index = 0
        requestUpload = false
        requestDownload = false
        error = null
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

    fun moveTab(delta: Int) {
        val values = FilesTab.entries
        tab = values[(tab.ordinal + delta + values.size) % values.size]
        requestUpload = false
        requestDownload = false
    }

    fun clamp(count: Int) {
        index = index.coerceIn(0, maxOf(0, count - 1))
    }
}
