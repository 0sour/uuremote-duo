package com.sour.duoremote.protocol.file

import com.sour.duoremote.protocol.proto.FeatureFlags
import com.sour.duoremote.protocol.proto.ProtoReader
import com.sour.duoremote.protocol.proto.ProtoWriter
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.InflaterInputStream

/** `gv_pb.TaskId`; `fileIndex` is 0 for a task, 1 for its file and -1 for task completion. */
data class TaskId(val taskId: Int, val fileIndex: Int = 0)

data class FileInfo(
    val relPath: String,
    val size: Long = 0L,
    val modifiedTime: Long = 0L,
)

data class FileEntry(
    val entryType: Int,
    val name: String,
    val size: Long,
    val modifiedTime: Long,
    val fullPath: String,
) {
    val isDirectory: Boolean
        get() = entryType == 0 || entryType == 2 || entryType == 3
}

sealed interface FileRequest {
    data class Receive(
        val id: TaskId,
        val path: String,
        val files: List<FileInfo>,
        val strategy: Int,
        val folderName: String,
        val listData: ByteArray,
        val taskUniqueId: String,
    ) : FileRequest

    data class Send(
        val id: TaskId,
        val path: String,
        val listData: ByteArray,
    ) : FileRequest

    data class Ask(
        val id: TaskId,
        val lastModified: Long,
        val fileSize: Long,
    ) : FileRequest

    data class Block(
        val id: TaskId,
        val blockId: Int,
        val data: ByteArray,
    ) : FileRequest

    data class Complete(
        val id: TaskId,
        val error: Int,
    ) : FileRequest

    data class ReadDir(
        val id: TaskId,
        val path: String,
    ) : FileRequest
}

sealed interface FileResponse {
    data class ReceiveResponse(
        val id: TaskId,
        val files: List<FileInfo>,
        val err: Int,
    ) : FileResponse

    data class SendResponse(
        val id: TaskId,
        val err: Int,
        val files: List<FileInfo>,
        val folderName: String,
        val listData: ByteArray,
    ) : FileResponse

    data class Confirm(
        val id: TaskId,
        val skip: Boolean,
        val err: Int,
        val resumePoint: Long,
    ) : FileResponse

    data class BlockConfirm(
        val id: TaskId,
        val blockId: Int,
        val err: Int,
        val blockLen: Int,
    ) : FileResponse

    data class Result(
        val id: TaskId,
        val fileError: Int,
        val errMsg: String,
    ) : FileResponse

    data class Directory(
        val id: TaskId,
        val path: String,
        val entries: List<FileEntry>,
        val fileError: Int,
        val dirData: ByteArray = ByteArray(0),
    ) : FileResponse
}

/**
 * Wire codec for the official FTP-over-data-channel RPC. File payloads use the same
 * `Main.Message` envelope as text input; only the RPC oneofs and compressed file lists differ.
 */
object FileTransferCodec {

    const val FILE_TRANSFER_ERROR_OK = 1
    const val FILE_TRANSFER_ERROR_CANCELED = 7

    sealed interface Incoming {
        data class Action(
            val seq: Long,
            val timestamp: Long,
            val action: Int,
        ) : Incoming

        data class Request(
            val requestId: Long,
            val request: FileRequest,
        ) : Incoming

        data class Response(
            val requestId: Long,
            val response: FileResponse,
        ) : Incoming

        data object Other : Incoming
    }

    fun encodeMainRequest(
        seq: Long,
        timestamp: Long,
        requestId: Long,
        request: FileRequest,
    ): ByteArray {
        val header = ProtoWriter().apply { integer(1, requestId) }.toByteArray()
        val rpc = ProtoWriter().apply {
            message(1, header)
            message(8, encodeRequest(request))
        }.toByteArray()
        return ProtoWriter().apply {
            integer(1, seq)
            integer(2, timestamp)
            message(21, rpc)
        }.toByteArray()
    }

    fun encodeMainResponse(
        seq: Long,
        timestamp: Long,
        requestId: Long,
        response: FileResponse,
    ): ByteArray {
        val header = ProtoWriter().apply { integer(1, requestId) }.toByteArray()
        val rpc = ProtoWriter().apply {
            message(1, header)
            message(4, encodeResponse(response))
        }.toByteArray()
        return ProtoWriter().apply {
            integer(1, seq)
            integer(2, timestamp)
            message(22, rpc)
        }.toByteArray()
    }

    fun encodeAction(
        seq: Long,
        timestamp: Long,
        action: Int,
        flags: FeatureFlags? = null,
    ): ByteArray {
        val actionMessage = ProtoWriter().apply {
            integer(1, action.toLong())
            if (flags != null) message(4, flags.encode())
        }.toByteArray()
        return ProtoWriter().apply {
            integer(1, seq)
            integer(2, timestamp)
            message(3, actionMessage)
        }.toByteArray()
    }

    fun parseMain(data: ByteArray): Incoming? {
        var seq = 0L
        var timestamp = 0L
        var action: ByteArray? = null
        var request: ByteArray? = null
        var response: ByteArray? = null
        var sawField = false
        runCatching {
            val reader = ProtoReader(data)
            while (true) {
                val field = reader.readField() ?: break
                sawField = true
                when (field.number) {
                    1 -> if (field.wire == 0) seq = field.integer
                    2 -> if (field.wire == 0) timestamp = field.integer
                    3 -> if (field.wire == 2) action = field.bytes
                    21 -> if (field.wire == 2) request = field.bytes
                    22 -> if (field.wire == 2) response = field.bytes
                }
            }
        }.getOrElse { return null }
        if (!sawField) return null
        action?.let { return Incoming.Action(seq, timestamp, parseAction(it)) }
        request?.let {
            val rpc = parseRpcRequest(it) ?: return Incoming.Other
            return Incoming.Request(rpc.first, rpc.second)
        }
        response?.let {
            val rpc = parseRpcResponse(it) ?: return Incoming.Other
            return Incoming.Response(rpc.first, rpc.second)
        }
        return Incoming.Other
    }

    fun encodeRequest(request: FileRequest): ByteArray = ProtoWriter().apply {
        when (request) {
            is FileRequest.Receive -> message(1, receiveBody(request))
            is FileRequest.Send -> message(2, sendBody(request))
            is FileRequest.Ask -> message(3, ProtoWriter().apply {
                message(1, encodeTask(request.id))
                if (request.lastModified != 0L) integer(2, request.lastModified)
                integer(3, request.fileSize)
            }.toByteArray())
            is FileRequest.Block -> message(4, ProtoWriter().apply {
                message(1, encodeTask(request.id))
                integer(2, request.blockId.toLong())
                message(3, request.data)
            }.toByteArray())
            is FileRequest.Complete -> message(9, ProtoWriter().apply {
                message(1, encodeTask(request.id))
                integer(2, request.error.toLong())
            }.toByteArray())
            is FileRequest.ReadDir -> message(10, ProtoWriter().apply {
                message(1, encodeTask(request.id))
                text(2, request.path)
            }.toByteArray())
        }
    }.toByteArray()

    fun encodeResponse(response: FileResponse): ByteArray = ProtoWriter().apply {
        when (response) {
            is FileResponse.ReceiveResponse -> message(1, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                response.files.forEach { message(2, encodeFileInfo(it)) }
                integer(3, response.err.toLong())
            }.toByteArray())
            is FileResponse.SendResponse -> message(2, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                integer(2, response.err.toLong())
                response.files.forEach { message(3, encodeFileInfo(it)) }
                if (response.folderName.isNotEmpty()) text(4, response.folderName)
                if (response.listData.isNotEmpty()) message(5, response.listData)
            }.toByteArray())
            is FileResponse.Confirm -> message(3, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                boolean(2, response.skip)
                integer(3, response.err.toLong())
                if (response.resumePoint != 0L) integer(4, response.resumePoint)
            }.toByteArray())
            is FileResponse.BlockConfirm -> message(4, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                integer(2, response.blockId.toLong())
                integer(3, response.err.toLong())
                integer(4, response.blockLen.toLong())
            }.toByteArray())
            is FileResponse.Result -> message(6, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                integer(2, response.fileError.toLong())
                if (response.errMsg.isNotEmpty()) text(3, response.errMsg)
            }.toByteArray())
            is FileResponse.Directory -> message(7, ProtoWriter().apply {
                message(1, encodeTask(response.id))
                text(2, response.path)
                response.entries.forEach { message(3, encodeFileEntry(it)) }
                integer(4, response.fileError.toLong())
                if (response.entries.isEmpty() && response.dirData.isNotEmpty()) {
                    message(5, response.dirData)
                }
            }.toByteArray())
        }
    }.toByteArray()

    fun encodeFileList(files: List<FileInfo>): ByteArray = ProtoWriter().apply {
        files.forEach { message(1, encodeFileInfo(it)) }
    }.toByteArray()

    fun decodeFileList(data: ByteArray): List<FileInfo> {
        val files = ArrayList<FileInfo>()
        readFields(data) { field ->
            if (field.number == 1 && field.wire == 2) files += parseFileInfo(field.bytes)
        }
        return files
    }

    fun encodeDirectoryData(entries: List<FileEntry>): ByteArray = ProtoWriter().apply {
        entries.forEach { message(1, encodeFileEntry(it)) }
    }.toByteArray()

    fun compress(data: ByteArray): ByteArray {
        val output = ByteArrayOutputStream()
        val deflater = Deflater(Deflater.BEST_SPEED)
        try {
            DeflaterOutputStream(output, deflater).use { it.write(data) }
        } finally {
            deflater.end()
        }
        return output.toByteArray()
    }

    fun decompress(data: ByteArray, limit: Int = 16 * 1024 * 1024): ByteArray {
        val output = ByteArrayOutputStream()
        InflaterInputStream(ByteArrayInputStream(data)).use { input ->
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= limit) { "Inflated payload is too large" }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun receiveBody(request: FileRequest.Receive): ByteArray = ProtoWriter().apply {
        message(1, encodeTask(request.id))
        text(2, request.path)
        request.files.forEach { message(3, encodeFileInfo(it)) }
        integer(4, request.strategy.toLong())
        if (request.folderName.isNotEmpty()) text(5, request.folderName)
        if (request.listData.isNotEmpty()) message(6, request.listData)
        if (request.taskUniqueId.isNotEmpty()) text(7, request.taskUniqueId)
    }.toByteArray()

    private fun sendBody(request: FileRequest.Send): ByteArray = ProtoWriter().apply {
        message(1, encodeTask(request.id))
        text(2, request.path)
        if (request.listData.isNotEmpty()) message(3, request.listData)
    }.toByteArray()

    private fun encodeTask(id: TaskId): ByteArray = ProtoWriter().apply {
        integer(1, id.taskId.toLong())
        if (id.fileIndex != 0) integer(2, id.fileIndex.toLong())
    }.toByteArray()

    private fun encodeFileInfo(info: FileInfo): ByteArray = ProtoWriter().apply {
        text(1, info.relPath)
        if (info.size != 0L) integer(2, info.size)
        if (info.modifiedTime != 0L) integer(3, info.modifiedTime)
    }.toByteArray()

    private fun encodeFileEntry(entry: FileEntry): ByteArray = ProtoWriter().apply {
        integer(1, entry.entryType.toLong())
        text(2, entry.name)
        if (entry.size != 0L) integer(3, entry.size)
        if (entry.modifiedTime != 0L) integer(4, entry.modifiedTime)
        if (entry.fullPath.isNotEmpty()) text(5, entry.fullPath)
    }.toByteArray()

    private fun parseAction(data: ByteArray): Int {
        var action = 0
        readFields(data) { field ->
            if (field.number == 1 && field.wire == 0) action = field.integer.toInt()
        }
        return action
    }

    private fun parseRpcRequest(data: ByteArray): Pair<Long, FileRequest>? {
        var requestId = 0L
        var file: ByteArray? = null
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) requestId = parseHeader(field.bytes)
                8 -> if (field.wire == 2) file = field.bytes
            }
        }
        val request = file?.let(::parseRequest) ?: return null
        return requestId to request
    }

    private fun parseRpcResponse(data: ByteArray): Pair<Long, FileResponse>? {
        var requestId = 0L
        var file: ByteArray? = null
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) requestId = parseHeader(field.bytes)
                4 -> if (field.wire == 2) file = field.bytes
            }
        }
        val response = file?.let(::parseResponse) ?: return null
        return requestId to response
    }

    private fun parseHeader(data: ByteArray): Long {
        var id = 0L
        readFields(data) { field ->
            if (field.number == 1 && field.wire == 0) id = field.integer
        }
        return id
    }

    private fun parseRequest(data: ByteArray): FileRequest? {
        var result: FileRequest? = null
        readFields(data) { field ->
            if (field.wire != 2) return@readFields
            result = when (field.number) {
                1 -> parseReceive(field.bytes)
                2 -> parseSend(field.bytes)
                3 -> parseAsk(field.bytes)
                4 -> parseBlock(field.bytes)
                9 -> parseComplete(field.bytes)
                10 -> parseReadDir(field.bytes)
                else -> result
            }
        }
        return result
    }

    private fun parseResponse(data: ByteArray): FileResponse? {
        var result: FileResponse? = null
        readFields(data) { field ->
            if (field.wire != 2) return@readFields
            result = when (field.number) {
                1 -> parseReceiveResponse(field.bytes)
                2 -> parseSendResponse(field.bytes)
                3 -> parseConfirm(field.bytes)
                4 -> parseBlockConfirm(field.bytes)
                6 -> parseResult(field.bytes)
                7 -> parseDirectory(field.bytes)
                else -> result
            }
        }
        return result
    }

    private fun parseReceive(data: ByteArray): FileRequest.Receive {
        var id = TaskId(0)
        var path = ""
        val files = ArrayList<FileInfo>()
        var strategy = 0
        var folderName = ""
        var listData = ByteArray(0)
        var taskUniqueId = ""
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
                3 -> if (field.wire == 2) files += parseFileInfo(field.bytes)
                4 -> if (field.wire == 0) strategy = field.integer.toInt()
                5 -> if (field.wire == 2) folderName = field.bytes.toString(Charsets.UTF_8)
                6 -> if (field.wire == 2) listData = field.bytes
                7 -> if (field.wire == 2) taskUniqueId = field.bytes.toString(Charsets.UTF_8)
            }
        }
        return FileRequest.Receive(id, path, files, strategy, folderName, listData, taskUniqueId)
    }

    private fun parseSend(data: ByteArray): FileRequest.Send {
        var id = TaskId(0)
        var path = ""
        var listData = ByteArray(0)
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
                3 -> if (field.wire == 2) listData = field.bytes
            }
        }
        return FileRequest.Send(id, path, listData)
    }

    private fun parseAsk(data: ByteArray): FileRequest.Ask {
        var id = TaskId(0)
        var lastModified = 0L
        var size = 0L
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) lastModified = field.integer
                3 -> if (field.wire == 0) size = field.integer
            }
        }
        return FileRequest.Ask(id, lastModified, size)
    }

    private fun parseBlock(data: ByteArray): FileRequest.Block {
        var id = TaskId(0)
        var blockId = 0
        var payload = ByteArray(0)
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) blockId = field.integer.toInt()
                3 -> if (field.wire == 2) payload = field.bytes
            }
        }
        return FileRequest.Block(id, blockId, payload)
    }

    private fun parseComplete(data: ByteArray): FileRequest.Complete {
        var id = TaskId(0)
        var error = 0
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) error = field.integer.toInt()
            }
        }
        return FileRequest.Complete(id, error)
    }

    private fun parseReadDir(data: ByteArray): FileRequest.ReadDir {
        var id = TaskId(0)
        var path = ""
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
            }
        }
        return FileRequest.ReadDir(id, path)
    }

    private fun parseReceiveResponse(data: ByteArray): FileResponse.ReceiveResponse {
        var id = TaskId(0)
        val files = ArrayList<FileInfo>()
        var err = 0
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 2) files += parseFileInfo(field.bytes)
                3 -> if (field.wire == 0) err = field.integer.toInt()
            }
        }
        return FileResponse.ReceiveResponse(id, files, err)
    }

    private fun parseSendResponse(data: ByteArray): FileResponse.SendResponse {
        var id = TaskId(0)
        var err = 0
        var files = emptyList<FileInfo>()
        var folderName = ""
        var listData = ByteArray(0)
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) err = field.integer.toInt()
                3 -> if (field.wire == 2) files = files + parseFileInfo(field.bytes)
                4 -> if (field.wire == 2) folderName = field.bytes.toString(Charsets.UTF_8)
                5 -> if (field.wire == 2) listData = field.bytes
            }
        }
        if (files.isEmpty() && listData.isNotEmpty()) {
            files = runCatching { decodeFileList(decompress(listData)) }.getOrDefault(emptyList())
        }
        return FileResponse.SendResponse(id, err, files, folderName, listData)
    }

    private fun parseConfirm(data: ByteArray): FileResponse.Confirm {
        var id = TaskId(0)
        var skip = false
        var err = 0
        var resume = 0L
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) skip = field.integer != 0L
                3 -> if (field.wire == 0) err = field.integer.toInt()
                4 -> if (field.wire == 0) resume = field.integer
            }
        }
        return FileResponse.Confirm(id, skip, err, resume)
    }

    private fun parseBlockConfirm(data: ByteArray): FileResponse.BlockConfirm {
        var id = TaskId(0)
        var blockId = 0
        var err = 0
        var length = 0
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) blockId = field.integer.toInt()
                3 -> if (field.wire == 0) err = field.integer.toInt()
                4 -> if (field.wire == 0) length = field.integer.toInt()
            }
        }
        return FileResponse.BlockConfirm(id, blockId, err, length)
    }

    private fun parseResult(data: ByteArray): FileResponse.Result {
        var id = TaskId(0)
        var error = 0
        var message = ""
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 0) error = field.integer.toInt()
                3 -> if (field.wire == 2) message = field.bytes.toString(Charsets.UTF_8)
            }
        }
        return FileResponse.Result(id, error, message)
    }

    private fun parseDirectory(data: ByteArray): FileResponse.Directory {
        var id = TaskId(0)
        var path = ""
        var entries = emptyList<FileEntry>()
        var error = 0
        var compressed = ByteArray(0)
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) id = parseTask(field.bytes)
                2 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
                3 -> if (field.wire == 2) entries = entries + parseFileEntry(field.bytes)
                4 -> if (field.wire == 0) error = field.integer.toInt()
                5 -> if (field.wire == 2) compressed = field.bytes
            }
        }
        if (entries.isEmpty() && compressed.isNotEmpty()) {
            entries = runCatching {
                val plain = decompress(compressed)
                val result = ArrayList<FileEntry>()
                readFields(plain) { field ->
                    if (field.number == 1 && field.wire == 2) result += parseFileEntry(field.bytes)
                }
                result
            }.getOrDefault(emptyList())
        }
        return FileResponse.Directory(id, path, entries, error)
    }

    private fun parseTask(data: ByteArray): TaskId {
        var id = 0
        var index = 0
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 0) id = field.integer.toInt()
                2 -> if (field.wire == 0) index = field.integer.toInt()
            }
        }
        return TaskId(id, index)
    }

    private fun parseFileInfo(data: ByteArray): FileInfo {
        var path = ""
        var size = 0L
        var modified = 0L
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
                2 -> if (field.wire == 0) size = field.integer
                3 -> if (field.wire == 0) modified = field.integer
            }
        }
        return FileInfo(path, size, modified)
    }

    private fun parseFileEntry(data: ByteArray): FileEntry {
        var type = 0
        var name = ""
        var size = 0L
        var modified = 0L
        var path = ""
        readFields(data) { field ->
            when (field.number) {
                1 -> if (field.wire == 0) type = field.integer.toInt()
                2 -> if (field.wire == 2) name = field.bytes.toString(Charsets.UTF_8)
                3 -> if (field.wire == 0) size = field.integer
                4 -> if (field.wire == 0) modified = field.integer
                5 -> if (field.wire == 2) path = field.bytes.toString(Charsets.UTF_8)
            }
        }
        return FileEntry(type, name, size, modified, path)
    }

    private inline fun readFields(data: ByteArray, block: (ProtoReader.Field) -> Unit) {
        val reader = ProtoReader(data)
        while (true) {
            val field = reader.readField() ?: return
            block(field)
        }
    }
}
