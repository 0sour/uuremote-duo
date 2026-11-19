package com.sour.duoremote.ui.files

import com.sour.duoremote.protocol.file.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilesUiStateTest {

    private fun file(name: String, path: String = "C:\\$name") =
        FileEntry(4, name, 10, 0, path)

    private fun folder(name: String) = FileEntry(0, name, 0, 0, "C:\\$name")

    @Test
    fun marksOnlyFilesAndTogglesByStablePath() {
        val state = FilesUiState()
        val a = file("a.txt")
        val b = file("b.txt")
        state.toggleMarked(a)
        state.toggleMarked(folder("folder"))
        state.toggleMarked(b)

        assertTrue(state.isMarked(a))
        assertEquals(setOf("a.txt", "b.txt"), state.markedEntries.map { it.name }.toSet())

        state.toggleMarked(a)
        assertFalse(state.isMarked(a))
        assertEquals(listOf("b.txt"), state.markedEntries.map { it.name })
    }

    @Test
    fun resetClearsMarks() {
        val state = FilesUiState()
        state.toggleMarked(file("a.txt"))
        state.reset()
        assertTrue(state.markedEntries.isEmpty())
    }
}
