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
    private const val MAX_TREE_DEPTH = 64
    private const val MAX_TREE_FILES = 5_000

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
     * Recursively collects a local folder for upload. Relative paths use `/` so they can be
     * turned into remote paths without ambiguity. Returns null when the tree is unreadable or
     * exceeds the file-count guard.
     */
    fun collectTree(
        context: Context,
        entry: LocalEntry,
        treeUri: String? = null,
    ): LocalTreeUpload? {
        if (!entry.isDirectory) return null
        val files = ArrayList<LocalTreeFile>()
        val folders = ArrayList<String>()
        val appContext = context.applicationContext
        if (entry.documentId == null) {
            val root = File(entry.path)
            val walk = runCatching {
                root.walkTopDown().maxDepth(MAX_TREE_DEPTH).forEach { file ->
                    if (file == root) return@forEach
                    val relative = file.relativeTo(root).invariantSeparatorsPath
                    if (relative.isBlank()) return@forEach
                    if (file.isDirectory) {
                        folders += relative
                    } else {
                        require(files.size < MAX_TREE_FILES) { "too many files" }
                        files += LocalTreeFile(
                            relativePath = relative,
                            name = file.name,
                            size = file.length(),
                            modifiedTime = file.lastModified() / 1000L,
                            open = { runCatching { file.inputStream() }.getOrNull() },
                        )
                    }
                }
            }
            return if (walk.isSuccess) LocalTreeUpload(files, folders) else null
        }

        val uri = treeUri?.let(Uri::parse) ?: return null
        if (!hasTreePermission(appContext, uri)) return null
        val location = LocalLocation(
            root = LocalRootKind.AUTHORIZED,
            label = entry.name,
            path = uri.toString(),
            treeUri = uri.toString(),
            documentId = entry.documentId,
        )
        val walk = runCatching {
            collectTreeFolder(appContext, uri, location, "", 0, files, folders)
        }
        return if (walk.isSuccess && walk.getOrDefault(false)) LocalTreeUpload(files, folders) else null
    }

    private fun collectTreeFolder(
        context: Context,
        treeUri: Uri,
        location: LocalLocation,
        prefix: String,
        depth: Int,
        files: MutableList<LocalTreeFile>,
        folders: MutableList<String>,
    ): Boolean {
        if (depth > MAX_TREE_DEPTH) return false
        for (child in list(context, location)) {
            val relative = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
            if (child.isDirectory) {
                folders += relative
                val childId = child.documentId ?: return false
                if (!collectTreeFolder(
                        context,
                        treeUri,
                        location.copy(label = child.name, documentId = childId),
                        relative,
                        depth + 1,
                        files,
                        folders,
                    )
                ) {
                    return false
                }
            } else {
                if (files.size >= MAX_TREE_FILES) return false
                files += LocalTreeFile(
                    relativePath = relative,
                    name = child.name,
                    size = child.size.coerceAtLeast(0L),
                    modifiedTime = child.modifiedTime,
                    open = { openInput(context, child) },
                )
            }
        }
        return true
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

    internal fun uniqueName(existing: Set<String>, requestedName: String): String {
        if (requestedName !in existing) return requestedName
        val dot = requestedName.lastIndexOf('.')
        val base = if (dot > 0) requestedName.substring(0, dot) else requestedName
        val extension = if (dot > 0) requestedName.substring(dot) else ""
        var counter = 1
        while ("$base ($counter)$extension" in existing) counter++
        return "$base ($counter)$extension"
    }

    internal fun mimeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: MIME_FALLBACK
    }

    /**
     * Creates the destination folder for a remote-folder download. Files are then opened lazily
     * with [LocalFolderWriter.open] as the host manifest arrives.
     */
    fun createFolderWriter(
        context: Context,
        base: LocalLocation,
        requestedName: String,
    ): LocalFolderWriter? {
        val folderName = safeSegment(requestedName.substringAfterLast('/').substringAfterLast('\\'))
            ?: return null
        return runCatching {
            when (base.root) {
                LocalRootKind.APP -> {
                    val parent = File(base.path)
                    if (!parent.isDirectory && !parent.mkdirs()) return null
                    val name = uniqueName(parent.list()?.toSet().orEmpty(), folderName)
                    val root = File(parent, name)
                    if (!root.mkdirs()) return null
                    LocalFolderWriter.app(root, name)
                }

                LocalRootKind.AUTHORIZED -> {
                    val treeUri = Uri.parse(base.treeUri)
                    val parentId = base.documentId
                        ?: DocumentsContract.getTreeDocumentId(treeUri)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
                    val existing = runCatching {
                        list(context, base).map { it.name }.toSet()
                    }.getOrDefault(emptySet())
                    val name = uniqueName(existing, folderName)
                    val created = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        DocumentsContract.Document.MIME_TYPE_DIR,
                        name,
                    ) ?: return null
                    val docId = DocumentsContract.getDocumentId(created)
                    LocalFolderWriter.tree(context, treeUri, docId, name)
                }
            }
        }.getOrNull()
    }
}

/** Writes one remote folder tree into either the app directory or the authorized SAF tree. */
class LocalFolderWriter private constructor(
    val folderName: String,
    private val appRoot: File?,
    private val context: Context?,
    private val treeUri: Uri?,
    private val rootDocId: String?,
) {

    fun open(relativePath: String): LocalOutput? {
        val segments = safePath(relativePath) ?: return null
        return runCatching {
            if (appRoot != null) openApp(segments) else openTree(segments)
        }.getOrNull()
    }

    fun deleteRoot() {
        val localRoot = appRoot
        if (localRoot != null) {
            runCatching { localRoot.deleteRecursively() }
            return
        }
        val uri = treeUri ?: return
        val docId = rootDocId ?: return
        runCatching {
            DocumentsContract.deleteDocument(
                context!!.contentResolver,
                DocumentsContract.buildDocumentUriUsingTree(uri, docId),
            )
        }
    }

    private fun openApp(segments: List<String>): LocalOutput? {
        val root = appRoot ?: return null
        var parent = root
        for (segment in segments.dropLast(1)) {
            parent = File(parent, segment)
            if (!parent.isDirectory && !parent.mkdirs()) return null
        }
        val name = LocalFileAccess.uniqueName(
            parent.list()?.toSet().orEmpty(),
            segments.last(),
        )
        val target = File(parent, name)
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

    private fun openTree(segments: List<String>): LocalOutput? {
        val ctx = context ?: return null
        val tree = treeUri ?: return null
        var parentId = rootDocId ?: return null
        for (segment in segments.dropLast(1)) {
            parentId = findOrCreateDirectory(ctx, tree, parentId, segment) ?: return null
        }
        val name = LocalFileAccess.uniqueName(
            childNames(ctx, tree, parentId),
            segments.last(),
        )
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
        val created = DocumentsContract.createDocument(
            ctx.contentResolver,
            parentUri,
            LocalFileAccess.mimeFor(name),
            name,
        ) ?: return null
        val stream = ctx.contentResolver.openOutputStream(created, "w") ?: run {
            runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, created) }
            return null
        }
        return LocalOutput(
            name = name,
            stream = stream,
            delete = {
                runCatching { stream.close() }
                runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, created) }
            },
        )
    }

    private fun findOrCreateDirectory(
        context: Context,
        treeUri: Uri,
        parentId: String,
        name: String,
    ): String? {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        context.contentResolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == name &&
                    cursor.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR
                ) {
                    return cursor.getString(0)
                }
            }
        }
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentId)
        val created = DocumentsContract.createDocument(
            context.contentResolver,
            parentUri,
            DocumentsContract.Document.MIME_TYPE_DIR,
            name,
        ) ?: return null
        return DocumentsContract.getDocumentId(created)
    }

    private fun childNames(context: Context, treeUri: Uri, parentId: String): Set<String> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentId)
        val names = mutableSetOf<String>()
        context.contentResolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                cursor.getString(0)?.let(names::add)
            }
        }
        return names
    }

    companion object {
        internal fun app(root: File, name: String) = LocalFolderWriter(
            folderName = name,
            appRoot = root,
            context = null,
            treeUri = null,
            rootDocId = null,
        )

        internal fun tree(
            context: Context,
            treeUri: Uri,
            rootDocId: String,
            name: String,
        ) = LocalFolderWriter(
            folderName = name,
            appRoot = null,
            context = context,
            treeUri = treeUri,
            rootDocId = rootDocId,
        )
    }
}

private fun safeSegment(value: String): String? {
    val name = value.trim()
    if (name.isEmpty() || name == "." || name == "..") return null
    if (name.contains('/') || name.contains('\\')) return null
    return name
}

private fun safePath(value: String): List<String>? {
    val parts = value.replace('\\', '/').split('/').filter { it.isNotBlank() }
    if (parts.isEmpty()) return null
    val segments = parts.map { safeSegment(it) ?: return null }
    return segments
}

data class LocalTreeFile(
    val relativePath: String,
    val name: String,
    val size: Long,
    val modifiedTime: Long,
    val open: () -> InputStream?,
)

data class LocalTreeUpload(
    val files: List<LocalTreeFile>,
    val folders: List<String>,
)

data class LocalOutput(
    val name: String,
    val stream: OutputStream,
    val delete: () -> Unit,
)
