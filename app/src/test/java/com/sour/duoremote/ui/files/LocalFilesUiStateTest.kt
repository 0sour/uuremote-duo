package com.sour.duoremote.ui.files

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalFilesUiStateTest {

    private fun location(label: String) = LocalLocation(
        root = LocalRootKind.APP,
        label = label,
        path = "C:\\$label",
    )

    private fun entry(name: String, directory: Boolean = false) = LocalEntry(
        name = name,
        isDirectory = directory,
        size = if (directory) -1L else 42L,
        modifiedTime = 100L,
        path = "C:\\$name",
    )

    @Test
    fun openUploadCapturesRemoteDirectoryAndResetsNavigation() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.UPLOAD, "C:\\Users\\Public", emptyList())

        assertTrue(state.visible)
        assertEquals(LocalPickerMode.UPLOAD, state.mode)
        assertEquals("C:\\Users\\Public", state.remoteDir)
        assertNull(state.pendingDownload)
        assertTrue(state.stack.isEmpty())
        assertFalse(state.authPrompt)
        assertEquals(0, state.index)
    }

    @Test
    fun pushPopAndSelectionUseOneStack() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.UPLOAD, "C:\\", emptyList())
        val root = location("root")
        val child = location("child")
        state.beginAt(root)
        val rootToken = state.loadToken
        state.loaded(rootToken, root, listOf(entry("a.txt"), entry("folder", true)))

        state.select(1)
        assertEquals("folder", state.selected?.name)

        state.push(child)
        assertTrue(state.loading)
        assertEquals(2, state.stack.size)
        assertEquals(child, state.current)

        val back = state.pop()
        assertEquals(root, back)
        assertEquals(1, state.stack.size)
        assertEquals(root, state.current)
        assertTrue(state.loading)
    }

    @Test
    fun staleListingCannotReplaceNewerNavigation() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.DOWNLOAD, "C:\\", listOf(PendingDownload("a.txt", "C:\\a.txt", 4)))
        val root = location("root")
        val child = location("child")
        state.beginAt(root)
        val rootToken = state.loadToken
        state.push(child)
        val childToken = state.loadToken

        state.loaded(rootToken, root, listOf(entry("old.txt")))
        assertTrue(state.entries.isEmpty())
        assertTrue(state.loading)

        state.loaded(childToken, child, listOf(entry("new.txt")))
        assertEquals(listOf("new.txt"), state.entries.map { it.name })
        assertFalse(state.loading)
    }

    @Test
    fun authPromptKeepsPendingDownloadAndMode() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.DOWNLOAD, "C:\\", listOf(PendingDownload("a.txt", "C:\\a.txt", 4)))
        state.showAuthPrompt("not granted")

        assertTrue(state.authPrompt)
        assertTrue(state.stack.isEmpty())
        assertEquals(LocalPickerMode.DOWNLOAD, state.mode)
        assertEquals("a.txt", state.pendingDownload?.name)

        state.beginAt(location("authorized"))
        assertFalse(state.authPrompt)
        assertNull(state.notice)
        assertEquals("a.txt", state.pendingDownload?.name)
    }

    @Test
    fun marksSurviveFolderNavigationAndIgnoreDirectories() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.UPLOAD, "C:\\", emptyList())
        val root = location("root")
        state.beginAt(root)
        val first = entry("a.txt")
        val second = entry("b.txt")
        state.toggleMarked(first)
        state.toggleMarked(entry("folder", true))
        state.push(location("child"))
        state.toggleMarked(second)

        assertEquals(setOf("a.txt", "b.txt"), state.markedEntries.map { it.name }.toSet())
        assertEquals(2, state.marked.size)

        state.toggleMarked(first)
        assertEquals(listOf("b.txt"), state.markedEntries.map { it.name })

        state.clearMarked()
        assertTrue(state.markedEntries.isEmpty())
    }

    @Test
    fun closeClearsPendingDownloads() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.DOWNLOAD, "C:\\", listOf(
            PendingDownload("a.txt", "C:\\a.txt", 4),
            PendingDownload("b.txt", "C:\\b.txt", 5),
        ))
        assertEquals(2, state.pendingDownloads.size)
        state.close()
        assertTrue(state.pendingDownloads.isEmpty())
    }

    @Test
    fun closeClearsPickerState() {
        val state = LocalFilesUiState()
        state.open(LocalPickerMode.UPLOAD, "C:\\", emptyList())
        state.beginAt(location("root"))
        state.close()

        assertFalse(state.visible)
        assertTrue(state.stack.isEmpty())
        assertTrue(state.entries.isEmpty())
        assertNull(state.pendingDownload)
        assertEquals("", state.remoteDir)
    }
}
