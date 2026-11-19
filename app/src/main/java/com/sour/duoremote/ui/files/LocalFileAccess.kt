package com.sour.duoremote.ui.files

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream

/**
 * Local storage access for the in-app browser.
 *
 * Two roots are supported:
 *  - APP: the app-specific external files directory, writable with no runtime permission.
 *  - AUTHORIZED: one folder chosen once through ACTION_OPEN_DOCUMENT_TREE and kept through a
 *    persisted read/write grant. All later browsing uses DocumentsContract directly, so the
 *    system file manager is never opened per transfer.
 */
object LocalFileAccess {

    private const val APP_FOLDER = "DuoRemote"
    private const val MIME_FALLBACK = "application/octet-stream"

    fun appRoot(context: Context): LocalLocation {
        val base = context.getExternalFilesDir(null) ?: context.filesDir
        val dir = File(base, APP_FOLDER).apply { mkdirs() }
        return LocalLocation(
            root = LocalRootKind.APP,
            label = "应用目录",
            path = dir.absolutePath,
        )
    }

    fun hasTreePermission(context: Context, treeUri: Uri): Boolean =
        context.contentResolver.persistedUriPermissions.any {
            it.uri == treeUri && it.isReadPermission && it.isWritePermission
        }

    fun authorizedRoot(context: Context, treeUri: Uri): LocalLocation? {
        if (!hasTreePermission(context, treeUri)) return null
        val documentId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
            ?: return null
        val label = runCatching {
            queryDisplayName(context, DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId))
        }.getOrNull().orEmpty().ifBlank { "授权文件夹" }
        return LocalLocation(
            root = LocalRootKind.AUTHORIZED,
            label = label,
            path = treeUri.toString(),
            treeUri = treeUri.toString(),
            documentId = documentId,
        )
    }

    fun list(context: Context, location: LocalLocation): List<LocalEntry> {
        val entries = when (location.root) {
            LocalRootKind.APP -> listApp(File(location.path))
            LocalRootKind.AUTHORIZED -> listTree(context, location)
        }
        return entries.sortedWith(
            compareByDescending<LocalEntry> { it.isDirectory }
                .thenBy { it.name.lowercase() },
        )
    }

    fun openInput(context: Context, entry: LocalEntry): InputStream? {
        if (entry.isDirectory) return null
        return runCatching {
            if (entry.documentId == null) {
                File(entry.path).inputStream()
            } else {
                context.contentResolver.openInputStream(Uri.parse(entry.path))
            }
        }.getOrNull()
    }

    fun sizeOf(context: Context, entry: LocalEntry): Long {
        if (entry.size >= 0L) return entry.size
        return runCatching {
            if (entry.documentId == null) {
                File(entry.path).length()
            } else {
                context.contentResolver.openAssetFileDescriptor(Uri.parse(entry.path), "r")
                    ?.use { it.length }
                    ?: -1L
            }
        }.getOrDefault(-1L)
    }

    /**
     * Creates a new file in [location], never overwriting an existing name. The returned delete
     * action removes the empty/partial file if the transfer cannot be started.
     */
    fun createOutput(
        context: Context,
        location: LocalLocation,
        requestedName: String,
    ): LocalOutput? {
        val safeName = requestedName.trim().ifBlank { "download.bin" }
        return runCatching {
            when (location.root) {
                LocalRootKind.APP -> createAppOutput(File(location.path), safeName)
                LocalRootKind.AUTHORIZED -> createTreeOutput(context, location, safeName)
            }
        }.getOrNull()
    }

    private fun listApp(dir: File): List<LocalEntry> {
        val files = dir.listFiles() ?: return emptyList()
        return files.map { file ->
            LocalEntry(
                name = file.name,
                isDirectory = file.isDirectory,
                size = if (file.isDirectory) -1L else file.length(),
                modifiedTime = file.lastModified() / 1000L,
                path = file.absolutePath,
            )
        }
    }

    private fun listTree(context: Context, location: LocalLocation): List<LocalEntry> {
        val treeUri = Uri.parse(location.treeUri)
        val documentId = location.documentId
            ?: DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val entries = mutableListOf<LocalEntry>()
        context.contentResolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
            while (cursor.moveToNext()) {
                val childId = cursor.getString(0) ?: continue
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(2)
                val isDirectory = mime == DocumentsContract.Document.MIME_TYPE_DIR
                val size = if (cursor.isNull(3)) -1L else cursor.getLong(3)
                val modified = if (cursor.isNull(4)) 0L else cursor.getLong(4) / 1000L
                val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId)
                entries += LocalEntry(
                    name = name,
                    isDirectory = isDirectory,
                    size = if (isDirectory) -1L else size,
                    modifiedTime = modified,
                    path = documentUri.toString(),
                    documentId = childId,
                )
            }
        }
        return entries
    }

    private fun createAppOutput(dir: File, requestedName: String): LocalOutput? {
        if (!dir.isDirectory && !dir.mkdirs()) return null
        val existing = dir.list()?.toSet().orEmpty()
        val name = uniqueName(existing, requestedName)
        val target = File(dir, name)
        val stream = FileOutputStream(target)
        return LocalOutput(
            name = name,
            stream = stream,
            delete = {
                runCatching { stream.close() }
                runCatching { target.delete() }
            },
        )
    }

    private fun createTreeOutput(
        context: Context,
        location: LocalLocation,
        requestedName: String,
    ): LocalOutput? {
        val treeUri = Uri.parse(location.treeUri)
        val documentId = location.documentId
            ?: DocumentsContract.getTreeDocumentId(treeUri)
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val existing = runCatching { list(context, location).map { it.name }.toSet() }
            .getOrDefault(emptySet())
        val name = uniqueName(existing, requestedName)
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            mimeFor(name),
            name,
        ) ?: return null
        val stream = context.contentResolver.openOutputStream(created, "w") ?: run {
            runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
            return null
        }
        val displayName = runCatching { queryDisplayName(context, created) }
            .getOrNull()
            .orEmpty()
            .ifBlank { name }
        return LocalOutput(
            name = displayName,
            stream = stream,
            delete = {
                runCatching { stream.close() }
                runCatching { DocumentsContract.deleteDocument(context.contentResolver, created) }
            },
        )
    }

    private fun queryDisplayName(context: Context, documentUri: Uri): String? {
        return context.contentResolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun uniqueName(existing: Set<String>, requestedName: String): String {
        if (requestedName !in existing) return requestedName
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var counter = 1
        while ("$base ($counter)$extension" in existing) counter++
        return "$base ($counter)$extension"
    }

    private fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: MIME_FALLBACK
    }
}

data class LocalOutput(
    val name: String,
    val stream: OutputStream,
    val delete: () -> Unit,
)
