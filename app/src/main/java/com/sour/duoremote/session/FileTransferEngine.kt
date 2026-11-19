package com.sour.duoremote.session

import com.sour.duoremote.protocol.file.FileEntry
import com.sour.duoremote.protocol.file.FileInfo
import com.sour.duoremote.protocol.file.FileRequest
import com.sour.duoremote.protocol.file.FileResponse
import com.sour.duoremote.protocol.file.FileTransferCodec
import com.sour.duoremote.protocol.file.TaskId
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import kotlin.random.Random

enum class TransferKind { UPLOAD, DOWNLOAD }

enum class TransferStatus { PREPARING, TRANSFERRING, WAITING, COMPLETED, FAILED, CANCELED }

data class ActiveTransfer(
    val id: Int,
    val name: String,
    val kind: TransferKind,
    val total: Long,
    val done: Long,
    val status: TransferStatus,
    val message: String? = null,
    /** 1-based index inside a batch; 0 when this is a single-file transfer. */
    val batchIndex: Int = 0,
    /** Total number of files in the batch; 0 when this is a single-file transfer. */
    val batchTotal: Int = 0,
) {
    val progress: Float
        get() = if (total <= 0L) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}

data class FileTransferState(
    val path: String = "C:\\",
    val entries: List<FileEntry> = emptyList(),
    val loading: Boolean = false,
    val error: String? = null,
    val notice: String? = null,
    val transfer: ActiveTransfer? = null,
) {
    fun parentPath(): String? = parentOf(path)

    companion object {
        fun parentOf(path: String): String? {
            val normalized = path.trimEnd('\\')
            if (normalized.length <= 2) return null
            val index = normalized.lastIndexOf('\\')
            if (index < 0) return null
            val parent = normalized.substring(0, index)
            return if (parent.length == 2 && parent[1] == ':') "$parent\\" else parent
        }

        fun childOf(base: String, name: String): String =
            if (base.endsWith("\\")) base + name else "$base\\$name"
    }
}

/**
 * Single-session FTP-over-SCTP state machine.
 *
 * One file is transferred at a time. Uploads are stop-and-wait on the host's block confirmations;
 * downloads acknowledge each host block. The engine is deliberately independent of Android so
 * the protocol flow can be unit tested with byte-array streams.
 */
class FileTransferEngine(
    private val send: (ByteArray) -> Boolean,
    private val onState: (FileTransferState) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis,
) {
    var state = FileTransferState()
        private set

    private var sequence = 1L
    private var pendingListId: Int? = null
    private var upload: UploadSession? = null
    private var download: DownloadSession? = null
    private var folderDownload: FolderDownloadSession? = null
    private var listStartedAt = 0L
    private var lastActivityAt = 0L

    @Synchronized
    fun list(path: String = state.path): Boolean {
        val target = path.trim().ifBlank { state.path }
        val id = if (pendingListId != null) pendingListId!! else nextTaskId()
        pendingListId = id
        listStartedAt = now()
        lastActivityAt = listStartedAt
        publish(
            state.copy(
                path = target,
                entries = if (target == state.path) state.entries else emptyList(),
                loading = true,
                error = null,
                notice = null,
            ),
        )
        val sent = sendRequest(
            FileRequest.ReadDir(TaskId(id, 1), target),
        )
        if (!sent) {
            pendingListId = null
            publish(state.copy(loading = false, error = "文件通道未就绪"))
        }
        return sent
    }

    @Synchronized
    fun upload(
        name: String,
        size: Long,
        modifiedTime: Long,
        input: InputStream,
        remoteDir: String = state.path,
        batchIndex: Int = 0,
        batchTotal: Int = 0,
    ): Boolean {
        if (state.transfer?.status in ACTIVE_STATUSES) {
            publish(state.copy(error = "请先等待当前传输结束"))
            return false
        }
        if (name.isBlank() || name.contains('\\') || name.contains('/') || size < 0L) {
            publish(state.copy(error = "文件名或大小无效"))
            return false
        }
        val id = nextTaskId()
        val files = listOf(FileInfo(name, size, modifiedTime))
        val listData = runCatching {
            FileTransferCodec.compress(FileTransferCodec.encodeFileList(files))
        }.getOrElse {
            publish(state.copy(error = "无法准备文件清单"))
            return false
        }
        val taskUniqueId = UUID.randomUUID().toString()
        upload = UploadSession(
            id = id,
            name = name,
            size = size,
            input = input,
        )
        publish(
            state.copy(
                transfer = ActiveTransfer(
                    id = id,
                    name = name,
                    kind = TransferKind.UPLOAD,
                    total = size,
                    done = 0L,
                    status = TransferStatus.PREPARING,
                    message = "准备上传",
                    batchIndex = batchIndex,
                    batchTotal = batchTotal,
                ),
                error = null,
                notice = null,
            ),
        )
        val sent = sendRequest(
            FileRequest.Receive(
                id = TaskId(id, 0),
                path = remoteDir,
                files = files,
                strategy = STRATEGY_SKIP,
                folderName = "",
                listData = listData,
                taskUniqueId = taskUniqueId,
            ),
        )
        if (!sent) failUpload("文件通道未就绪")
        return sent
    }

    @Synchronized
    fun download(
        remotePath: String,
        name: String,
        sizeHint: Long,
        output: OutputStream,
        batchIndex: Int = 0,
        batchTotal: Int = 0,
    ): Boolean {
        if (state.transfer?.status in ACTIVE_STATUSES) {
            publish(state.copy(error = "请先等待当前传输结束"))
            return false
        }
        val parent = parentRemote(remotePath)
        if (parent == null || name.isBlank()) {
            publish(state.copy(error = "远端路径无效"))
            return false
        }
        val id = nextTaskId()
        val files = listOf(FileInfo(name))
        val listData = runCatching {
            FileTransferCodec.compress(FileTransferCodec.encodeFileList(files))
        }.getOrElse {
            publish(state.copy(error = "无法准备下载清单"))
            return false
        }
        download = DownloadSession(
            id = id,
            name = name,
            total = sizeHint.coerceAtLeast(0L),
            output = output,
            nextBlock = 1,
        )
        publish(
            state.copy(
                transfer = ActiveTransfer(
                    id = id,
                    name = name,
                    kind = TransferKind.DOWNLOAD,
                    total = sizeHint.coerceAtLeast(0L),
                    done = 0L,
                    status = TransferStatus.PREPARING,
                    message = "准备下载",
                    batchIndex = batchIndex,
                    batchTotal = batchTotal,
                ),
                error = null,
                notice = null,
            ),
        )
        val sent = sendRequest(
            FileRequest.Send(
                id = TaskId(id, 0),
                path = parent,
                listData = listData,
            ),
        )
        if (!sent) failDownload("文件通道未就绪")
        return sent
    }

    /**
     * Downloads a remote directory. The host scans it and returns a manifest with relative
     * paths; each file then arrives through the normal Ask/Block/Complete sequence. The local
     * output factory is called lazily for each manifest entry.
     */
    @Synchronized
    fun downloadFolder(
        folderPath: String,
        folderName: String,
        outputFactory: (Int, FileInfo) -> OutputStream?,
    ): Boolean {
        if (state.transfer?.status in ACTIVE_STATUSES) {
            publish(state.copy(error = "请先等待当前传输结束"))
            return false
        }
        if (folderPath.isBlank() || folderName.isBlank()) {
            publish(state.copy(error = "远端文件夹路径无效"))
            return false
        }
        val id = nextTaskId()
        folderDownload = FolderDownloadSession(
            id = id,
            folderName = folderName,
            outputFactory = outputFactory,
        )
        publish(
            state.copy(
                transfer = ActiveTransfer(
                    id = id,
                    name = folderName,
                    kind = TransferKind.DOWNLOAD,
                    total = 0L,
                    done = 0L,
                    status = TransferStatus.PREPARING,
                    message = "正在扫描文件夹",
                ),
                error = null,
                notice = null,
            ),
        )
        val sent = sendRequest(
            FileRequest.Send(
                id = TaskId(id, 0),
                path = folderPath,
                listData = ByteArray(0),
            ),
        )
        if (!sent) failFolderDownload("文件通道未就绪")
        return sent
    }

    @Synchronized
    fun cancel() {
        val active = state.transfer ?: return
        if (active.status !in ACTIVE_STATUSES) return
        val id = active.id
        runCatching {
            sendRequest(FileRequest.Complete(TaskId(id, -1), FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED))
        }
        closeUpload()
        closeDownload()
        closeFolderDownload()
        publish(
            state.copy(
                transfer = active.copy(status = TransferStatus.CANCELED, message = "已取消"),
                notice = "已取消传输",
            ),
        )
    }

    @Synchronized
    fun reset() {
        closeUpload()
        closeDownload()
        closeFolderDownload()
        pendingListId = null
        publish(FileTransferState())
    }

    /** Fails a listing/transfer that has not seen progress within its timeout. */
    @Synchronized
    fun tick() {
        val current = now()
        if (pendingListId != null && current - listStartedAt > LIST_TIMEOUT_MS) {
            pendingListId = null
            publish(state.copy(loading = false, error = "读取远端目录超时"))
        }
        val active = state.transfer ?: return
        if (active.status !in ACTIVE_STATUSES || current - lastActivityAt <= TRANSFER_TIMEOUT_MS) return
        val id = active.id
        when {
            upload != null -> {
                sendRequest(
                    FileRequest.Complete(
                        TaskId(id, -1),
                        FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED,
                    ),
                )
                failUpload("传输超时")
            }

            download != null -> {
                sendRequest(
                    FileRequest.Complete(
                        TaskId(id, -1),
                        FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED,
                    ),
                )
                failDownload("传输超时")
            }

            folderDownload != null -> {
                sendRequest(
                    FileRequest.Complete(
                        TaskId(id, -1),
                        FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED,
                    ),
                )
                failFolderDownload("传输超时")
            }
        }
    }

    /** Consumes an inbound Main.Message. Returns false for non-file messages (e.g. clipboard). */
    @Synchronized
    fun onMessage(data: ByteArray): Boolean {
        val incoming = runCatching { FileTransferCodec.parseMain(data) }.getOrNull() ?: return false
        return when (incoming) {
            is FileTransferCodec.Incoming.Request -> {
                handleRequest(incoming.requestId, incoming.request)
                true
            }

            is FileTransferCodec.Incoming.Response -> {
                handleResponse(incoming.response)
                true
            }

            is FileTransferCodec.Incoming.Action,
            FileTransferCodec.Incoming.Other,
            -> false
        }
    }

    private fun handleResponse(response: FileResponse) {
        lastActivityAt = now()
        when (response) {
            is FileResponse.Directory -> handleDirectory(response)
            is FileResponse.ReceiveResponse -> {
                val session = upload ?: return
                if (response.id.taskId != session.id) return
                if (response.err != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                    failUpload("远端拒绝接收文件（${response.err}）")
                    return
                }
                val sent = sendRequest(
                    FileRequest.Ask(
                        id = TaskId(session.id, 1),
                        lastModified = 0L,
                        fileSize = session.size,
                    ),
                )
                if (sent) updateTransfer(TransferStatus.WAITING, session.size, "等待远端确认")
                else failUpload("文件通道未就绪")
            }

            is FileResponse.Confirm -> {
                val session = upload ?: return
                if (response.id.taskId != session.id) return
                if (response.err != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                    failUpload("远端文件确认失败（${response.err}）")
                    return
                }
                if (response.skip) {
                    completeUpload("已跳过同名文件")
                    return
                }
                if (response.resumePoint > session.size) {
                    failUpload("续传位置无效")
                    return
                }
                if (response.resumePoint > 0L && !skipFully(session.input, response.resumePoint)) {
                    failUpload("无法定位续传位置")
                    return
                }
                session.offset = response.resumePoint
                session.block = 0
                updateTransfer(TransferStatus.TRANSFERRING, session.offset, "上传中")
                sendNextBlock()
            }

            is FileResponse.BlockConfirm -> {
                val session = upload ?: return
                if (response.id.taskId != session.id) return
                if (response.err != FileTransferCodec.FILE_TRANSFER_ERROR_OK || response.blockId != session.block) {
                    failUpload("远端文件块确认失败")
                    return
                }
                val length = if (response.blockLen > 0) response.blockLen.toLong() else session.pendingBytes
                session.offset += length
                session.pendingBytes = 0L
                session.block += 1
                updateTransfer(TransferStatus.TRANSFERRING, session.offset, "上传中")
                sendNextBlock()
            }

            is FileResponse.SendResponse -> {
                val folder = folderDownload
                if (folder != null && response.id.taskId == folder.id) {
                    if (response.err != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                        failFolderDownload("远端拒绝发送文件夹（${response.err}）")
                        return
                    }
                    val files = runCatching {
                        if (response.listData.isNotEmpty()) {
                            FileTransferCodec.decodeFileList(
                                FileTransferCodec.decompress(response.listData),
                            )
                        } else {
                            response.files
                        }
                    }.getOrElse {
                        failFolderDownload("远端文件夹清单无效")
                        return
                    }
                    if (files.isEmpty()) {
                        closeFolderDownload()
                        publish(
                            state.copy(
                                transfer = state.transfer?.copy(
                                    status = TransferStatus.COMPLETED,
                                    message = "文件夹为空",
                                ),
                                notice = "文件夹为空",
                            ),
                        )
                        return
                    }
                    folder.files = files
                    folder.total = files.sumOf { it.size.coerceAtLeast(0L) }
                    folder.name = response.folderName.ifBlank { folder.folderName }
                    publish(
                        state.copy(
                            transfer = state.transfer?.copy(
                                name = folder.name,
                                total = folder.total,
                                status = TransferStatus.WAITING,
                                message = "等待远端数据",
                            ),
                            error = null,
                        ),
                    )
                    return
                }
                val session = download ?: return
                if (response.id.taskId != session.id) return
                if (response.err != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                    failDownload("远端拒绝发送文件（${response.err}）")
                    return
                }
                val info = response.files.singleOrNull()
                if (info == null) {
                    failDownload("远端返回的文件清单无效")
                    return
                }
                session.total = info.size
                updateTransfer(TransferStatus.WAITING, 0L, "等待远端数据")
            }

            is FileResponse.Result -> {
                val session = upload ?: return
                if (response.id.taskId != session.id) return
                if (response.fileError == FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                    completeUpload("已完成")
                } else {
                    failUpload(response.errMsg.ifBlank { "远端传输失败（${response.fileError}）" })
                }
            }
        }
    }

    private fun handleRequest(requestId: Long, request: FileRequest) {
        lastActivityAt = now()
        when (request) {
            is FileRequest.Ask -> {
                val folder = folderDownload
                if (folder != null && request.id.taskId == folder.id) {
                    val index = request.id.fileIndex
                    val info = folder.files.getOrNull(index - 1)
                    if (info == null || index <= 0) {
                        failFolderDownload("远端文件序号无效")
                        return
                    }
                    if (folder.output != null) {
                        failFolderDownload("远端文件顺序无效")
                        return
                    }
                    val output = runCatching { folder.outputFactory(index, info) }.getOrNull()
                    if (output == null) {
                        failFolderDownload("无法创建 ${info.relPath}")
                        return
                    }
                    folder.currentIndex = index
                    folder.current = info
                    folder.output = output
                    folder.currentOffset = 0L
                    folder.currentTotal = if (request.fileSize > 0L) request.fileSize else info.size
                    folder.nextBlock = 1
                    val sent = sendResponse(
                        requestId,
                        FileResponse.Confirm(
                            id = request.id,
                            skip = false,
                            err = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                            resumePoint = 0L,
                        ),
                    )
                    if (sent) {
                        updateTransfer(
                            TransferStatus.TRANSFERRING,
                            folder.done,
                            "下载 ${info.relPath}",
                        )
                    } else {
                        failFolderDownload("文件通道未就绪")
                    }
                    return
                }
                val session = download ?: return
                if (request.id.taskId != session.id) return
                if (request.fileSize > 0L) session.total = request.fileSize
                val sent = sendResponse(
                    requestId,
                    FileResponse.Confirm(
                        id = request.id,
                        skip = false,
                        err = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                        resumePoint = 0L,
                    ),
                )
                if (sent) updateTransfer(TransferStatus.TRANSFERRING, session.offset, "下载中")
                else failDownload("文件通道未就绪")
            }

            is FileRequest.Block -> {
                val folder = folderDownload
                if (folder != null && request.id.taskId == folder.id) {
                    val output = folder.output
                    val valid = output != null &&
                        request.blockId == folder.nextBlock &&
                        request.data.size <= MAX_BLOCK_BYTES &&
                        (folder.currentTotal <= 0L ||
                            folder.currentOffset + request.data.size <= folder.currentTotal)
                    if (!valid) {
                        sendResponse(
                            requestId,
                            FileResponse.BlockConfirm(
                                id = request.id,
                                blockId = request.blockId,
                                err = FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED,
                                blockLen = 0,
                            ),
                        )
                        failFolderDownload("远端文件块顺序或大小无效")
                        return
                    }
                    val written = runCatching {
                        output!!.write(request.data)
                        output.flush()
                    }.isSuccess
                    if (!written) {
                        failFolderDownload("写入本地文件失败")
                        return
                    }
                    folder.currentOffset += request.data.size
                    folder.nextBlock += 1
                    updateTransfer(
                        TransferStatus.TRANSFERRING,
                        folder.done + folder.currentOffset,
                        "下载 ${folder.current?.relPath ?: folder.name}",
                    )
                    sendResponse(
                        requestId,
                        FileResponse.BlockConfirm(
                            id = request.id,
                            blockId = request.blockId,
                            err = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                            blockLen = request.data.size,
                        ),
                    )
                    return
                }
                val session = download ?: return
                if (request.id.taskId != session.id) return
                val valid = request.blockId == session.nextBlock &&
                    request.data.size <= MAX_BLOCK_BYTES &&
                    (session.total <= 0L || session.offset + request.data.size <= session.total)
                if (!valid) {
                    sendResponse(
                        requestId,
                        FileResponse.BlockConfirm(
                            id = request.id,
                            blockId = request.blockId,
                            err = FileTransferCodec.FILE_TRANSFER_ERROR_CANCELED,
                            blockLen = 0,
                        ),
                    )
                    failDownload("远端文件块顺序或大小无效")
                    return
                }
                val written = runCatching {
                    session.output.write(request.data)
                    session.output.flush()
                }.isSuccess
                if (!written) {
                    failDownload("写入本地文件失败")
                    return
                }
                session.offset += request.data.size
                session.nextBlock += 1
                updateTransfer(TransferStatus.TRANSFERRING, session.offset, "下载中")
                sendResponse(
                    requestId,
                    FileResponse.BlockConfirm(
                        id = request.id,
                        blockId = request.blockId,
                        err = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                        blockLen = request.data.size,
                    ),
                )
            }

            is FileRequest.Complete -> {
                val folder = folderDownload
                if (folder != null && request.id.taskId == folder.id) {
                    if (request.error != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                        failFolderDownload("远端已取消下载")
                        return
                    }
                    if (request.id.fileIndex == -1) {
                        closeFolderDownload()
                        val total = folder.total.coerceAtLeast(folder.done)
                        publish(
                            state.copy(
                                transfer = state.transfer?.copy(
                                    done = total,
                                    total = total,
                                    status = TransferStatus.COMPLETED,
                                    message = "已完成",
                                ),
                                notice = "已下载文件夹 ${folder.name}",
                            ),
                        )
                        sendResponse(
                            requestId,
                            FileResponse.Result(
                                id = request.id,
                                fileError = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                                errMsg = "",
                            ),
                        )
                        return
                    }
                    if (folder.currentIndex != request.id.fileIndex) {
                        failFolderDownload("远端文件完成序号无效")
                        return
                    }
                    folder.output?.let { runCatching { it.close() } }
                    folder.output = null
                    folder.current = null
                    folder.done += folder.currentOffset
                    folder.currentOffset = 0L
                    updateTransfer(
                        TransferStatus.WAITING,
                        folder.done,
                        "等待下一个文件",
                    )
                    sendResponse(
                        requestId,
                        FileResponse.Result(
                            id = request.id,
                            fileError = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                            errMsg = "",
                        ),
                    )
                    return
                }
                val session = download ?: return
                if (request.id.taskId != session.id) return
                if (request.error != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
                    failDownload("远端已取消下载")
                    return
                }
                if (session.total > 0L && session.offset != session.total) {
                    failDownload("下载长度不完整")
                    return
                }
                closeDownload()
                publish(
                    state.copy(
                        transfer = state.transfer?.copy(
                            done = session.total,
                            status = TransferStatus.COMPLETED,
                            message = "已完成",
                        ),
                        notice = "已保存 ${session.name}",
                    ),
                )
                sendResponse(
                    requestId,
                    FileResponse.Result(
                        id = request.id,
                        fileError = FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                        errMsg = "",
                    ),
                )
            }

            else -> Unit
        }
    }

    private fun handleDirectory(response: FileResponse.Directory) {
        val pending = pendingListId ?: return
        if (response.id.taskId != pending) return
        pendingListId = null
        if (response.fileError != 0 && response.fileError != FileTransferCodec.FILE_TRANSFER_ERROR_OK) {
            publish(state.copy(loading = false, error = "无法读取远端目录（${response.fileError}）"))
            return
        }
        val path = response.path.ifBlank { state.path }
        val entries = response.entries
            .filter { it.name.isNotBlank() }
            .sortedWith(compareByDescending<FileEntry> { it.isDirectory }.thenBy { it.name.lowercase() })
        publish(
            state.copy(
                path = path,
                entries = entries,
                loading = false,
                error = null,
                notice = null,
            ),
        )
    }

    private fun sendNextBlock() {
        val session = upload ?: return
        if (session.offset >= session.size) {
            val fileDone = sendRequest(
                FileRequest.Complete(
                    TaskId(session.id, 1),
                    FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                ),
            )
            val taskDone = fileDone && sendRequest(
                FileRequest.Complete(
                    TaskId(session.id, -1),
                    FileTransferCodec.FILE_TRANSFER_ERROR_OK,
                ),
            )
            if (taskDone) {
                updateTransfer(TransferStatus.WAITING, session.offset, "等待远端确认")
            } else {
                failUpload("文件通道未就绪")
            }
            return
        }
        val length = minOf(BLOCK_BYTES.toLong(), session.size - session.offset).toInt()
        val data = ByteArray(length)
        var read = 0
        while (read < length) {
            val count = runCatching { session.input.read(data, read, length - read) }.getOrElse { -1 }
            if (count <= 0) {
                failUpload("读取本地文件失败")
                return
            }
            read += count
        }
        session.pendingBytes = length.toLong()
        val sent = sendRequest(
            FileRequest.Block(
                id = TaskId(session.id, 1),
                blockId = session.block,
                data = data,
            ),
        )
        if (!sent) {
            failUpload("文件通道未就绪")
            return
        }
        updateTransfer(TransferStatus.TRANSFERRING, session.offset, "上传中")
    }

    private fun completeUpload(message: String) {
        val session = upload ?: return
        closeUpload()
        publish(
            state.copy(
                transfer = state.transfer?.copy(
                    done = session.offset.coerceAtMost(session.size),
                    status = TransferStatus.COMPLETED,
                    message = message,
                ),
                notice = message,
            ),
        )
    }

    private fun failUpload(message: String) {
        closeUpload()
        failTransfer(message)
    }

    private fun failDownload(message: String) {
        closeDownload()
        failTransfer(message)
    }

    private fun failTransfer(message: String) {
        publish(
            state.copy(
                error = message,
                transfer = state.transfer?.copy(
                    status = TransferStatus.FAILED,
                    message = message,
                ),
            ),
        )
    }

    private fun updateTransfer(status: TransferStatus, done: Long, message: String?) {
        val current = state.transfer ?: return
        publish(
            state.copy(
                transfer = current.copy(
                    done = done.coerceAtLeast(0L),
                    status = status,
                    message = message,
                ),
            ),
        )
    }

    private fun publish(next: FileTransferState) {
        state = next
        onState(next)
    }

    private fun sendRequest(request: FileRequest): Boolean = runCatching {
        lastActivityAt = now()
        send(
            FileTransferCodec.encodeMainRequest(
                seq = nextSequence(),
                timestamp = now(),
                requestId = requestIdFor(request),
                request = request,
            ),
        )
    }.getOrDefault(false)

    private fun requestIdFor(request: FileRequest): Long = when (request) {
        is FileRequest.Receive -> request.id.taskId.toLong()
        is FileRequest.Send -> request.id.taskId.toLong()
        is FileRequest.Ask -> request.id.taskId.toLong()
        is FileRequest.Block -> request.id.taskId.toLong()
        is FileRequest.Complete -> request.id.taskId.toLong()
        is FileRequest.ReadDir -> request.id.taskId.toLong()
    }

    private fun sendResponse(requestId: Long, response: FileResponse): Boolean = runCatching {
        lastActivityAt = now()
        send(
            FileTransferCodec.encodeMainResponse(
                seq = nextSequence(),
                timestamp = now(),
                requestId = requestId,
                response = response,
            ),
        )
    }.getOrDefault(false)

    private fun nextSequence(): Long = sequence++

    private fun nextTaskId(): Int {
        while (true) {
            val id = Random.nextInt(1000, 1_000_000_000)
            if (id != upload?.id && id != download?.id) return id
        }
    }

    private fun parentRemote(path: String): String? {
        val normalized = path.trimEnd('\\', '/')
        val index = normalized.lastIndexOfAny(charArrayOf('\\', '/'))
        if (index <= 0) return null
        // The host's SendRequest expects the containing directory with a trailing separator.
        return normalized.substring(0, index) + "\\"
    }

    private fun skipFully(input: InputStream, count: Long): Boolean {
        var remaining = count
        while (remaining > 0L) {
            val skipped = runCatching { input.skip(remaining) }.getOrDefault(0L)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (runCatching { input.read() }.getOrDefault(-1) < 0) {
                return false
            } else {
                remaining -= 1L
            }
        }
        return true
    }

    private fun closeUpload() {
        upload?.let { runCatching { it.input.close() } }
        upload = null
    }

    private fun closeDownload() {
        download?.let { runCatching { it.output.close() } }
        download = null
    }

    private fun closeFolderDownload() {
        folderDownload?.output?.let { runCatching { it.close() } }
        folderDownload = null
    }

    private fun failFolderDownload(message: String) {
        closeFolderDownload()
        failTransfer(message)
    }

    private data class UploadSession(
        val id: Int,
        val name: String,
        val size: Long,
        val input: InputStream,
        var offset: Long = 0L,
        var block: Int = 0,
        var pendingBytes: Long = 0L,
    )

    private data class DownloadSession(
        val id: Int,
        val name: String,
        var total: Long,
        val output: OutputStream,
        var offset: Long = 0L,
        var nextBlock: Int = 1,
    )

    private data class FolderDownloadSession(
        val id: Int,
        val folderName: String,
        val outputFactory: (Int, FileInfo) -> OutputStream?,
        var name: String = folderName,
        var files: List<FileInfo> = emptyList(),
        var total: Long = 0L,
        var done: Long = 0L,
        var currentIndex: Int = 0,
        var current: FileInfo? = null,
        var output: OutputStream? = null,
        var currentOffset: Long = 0L,
        var currentTotal: Long = 0L,
        var nextBlock: Int = 1,
    )

    private companion object {
        const val BLOCK_BYTES = 64 * 1024
        const val MAX_BLOCK_BYTES = 1024 * 1024
        const val STRATEGY_SKIP = 3
        const val LIST_TIMEOUT_MS = 15_000L
        const val TRANSFER_TIMEOUT_MS = 30_000L
        val ACTIVE_STATUSES = setOf(
            TransferStatus.PREPARING,
            TransferStatus.TRANSFERRING,
            TransferStatus.WAITING,
        )
    }
}
