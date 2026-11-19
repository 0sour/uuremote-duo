package com.sour.duoremote.protocol.file

import com.sour.duoremote.protocol.proto.FeatureFlags
import com.sour.duoremote.protocol.proto.ProtoReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FileTransferCodecTest {

    @Test
    fun readDirRequestRoundTripsThroughMainEnvelope() {
        val bytes = FileTransferCodec.encodeMainRequest(
            seq = 7,
            timestamp = 99,
            requestId = 42,
            request = FileRequest.ReadDir(TaskId(5, 1), "C:\\Users"),
        )
        val incoming = FileTransferCodec.parseMain(bytes)
        assertTrue(incoming is FileTransferCodec.Incoming.Request)
        val request = (incoming as FileTransferCodec.Incoming.Request).request
        assertEquals(42L, incoming.requestId)
        assertEquals(FileRequest.ReadDir(TaskId(5, 1), "C:\\Users"), request)
    }

    @Test
    fun receiveRequestCarriesCompressedFileList() {
        val files = listOf(FileInfo("报告.docx", 1_234, 1_700_000_000))
        val listData = FileTransferCodec.compress(FileTransferCodec.encodeFileList(files))
        val request = FileRequest.Receive(
            id = TaskId(11, 0),
            path = "C:\\Downloads",
            files = files,
            strategy = 3,
            folderName = "",
            listData = listData,
            taskUniqueId = "task-1",
        )
        val incoming = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainRequest(1, 2, 3, request),
        ) as FileTransferCodec.Incoming.Request
        val parsed = incoming.request as FileRequest.Receive
        assertEquals(11, parsed.id.taskId)
        assertEquals("C:\\Downloads", parsed.path)
        assertEquals(files, parsed.files)
        assertEquals("task-1", parsed.taskUniqueId)
        assertArrayEquals(listData, parsed.listData)
        assertEquals(files, FileTransferCodec.decodeFileList(FileTransferCodec.decompress(parsed.listData)))
    }

    @Test
    fun dirCreateAndRenameRoundTripAsOperationRpc() {
        val dir = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainRequest(1, 2, 77, FileRequest.DirCreate(0, "C:/")),
        ) as FileTransferCodec.Incoming.Request
        assertEquals(77L, dir.requestId)
        val create = dir.request as FileRequest.DirCreate
        assertEquals("C:/", create.path)

        val rename = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainRequest(
                3,
                4,
                88,
                FileRequest.Rename(0, "C:/新建文件夹", "C:/Projects"),
            ),
        ) as FileTransferCodec.Incoming.Request
        assertEquals(88L, rename.requestId)
        val moved = rename.request as FileRequest.Rename
        assertEquals("C:/新建文件夹", moved.path)
        assertEquals("C:/Projects", moved.newName)

        val result = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainResponse(
                5,
                6,
                88,
                FileResponse.OperationResult(1, "", "C:/Projects"),
            ),
        ) as FileTransferCodec.Incoming.Response
        assertEquals(88L, result.requestId)
        assertEquals(FileResponse.OperationResult(1, "", "C:/Projects"), result.response)
    }

    @Test
    fun directoryResponseDecodesDirectAndCompressedEntries() {
        val entries = listOf(
            FileEntry(0, "文件夹", 0, 0, "C:\\文件夹"),
            FileEntry(4, "a.txt", 12, 100, "C:\\a.txt"),
        )
        val direct = FileResponse.Directory(TaskId(7), "C:\\", entries, 1)
        val parsedDirect = parseResponse(FileResponse.Directory::class.java, direct)
        assertEquals(entries, parsedDirect.entries)

        val dirData = FileTransferCodec.compress(FileTransferCodec.encodeDirectoryData(entries))
        val compressed = FileResponse.Directory(TaskId(7), "C:\\", emptyList(), 1, dirData)
        val parsedCompressed = parseResponse(FileResponse.Directory::class.java, compressed)
        assertEquals(entries, parsedCompressed.entries)
    }

    @Test
    fun blockConfirmKeepsTaskAndBlockIdentity() {
        val response = FileResponse.BlockConfirm(TaskId(21, 0), 4, 1, 65_536)
        val incoming = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainResponse(1, 2, 777, response),
        ) as FileTransferCodec.Incoming.Response
        assertEquals(777L, incoming.requestId)
        assertEquals(response, incoming.response)
    }

    @Test
    fun actionMessageAndFeatureFlagsRoundTrip() {
        val action = FileTransferCodec.parseMain(
            FileTransferCodec.encodeAction(1, 2, 0),
        ) as FileTransferCodec.Incoming.Action
        assertEquals(0, action.action)
        assertEquals(1L, action.seq)

        val flags = FeatureFlags(
            captureSetting = 4,
            simpleAction = 3,
            systemMetrics = 3,
            fileTransferFtp = 2,
            fileTransferFtp2 = 2,
            clipboard = 3,
            qosStat = 1,
        )
        val decoded = FeatureFlags.parse(flags.encode())
        assertEquals(flags, decoded)
    }

    @Test
    fun zlibRejectsOversizedPayload() {
        val compressed = FileTransferCodec.compress(ByteArray(4096))
        val failure = runCatching {
            FileTransferCodec.decompress(compressed, limit = 1024)
        }.exceptionOrNull()
        assertNotNull(failure)
    }

    @Test
    fun fieldsOfEnvelopeUseExpectedNumbers() {
        val bytes = FileTransferCodec.encodeMainRequest(
            1,
            2,
            3,
            FileRequest.Complete(TaskId(9, -1), 7),
        )
        val main = fields(bytes)
        assertEquals(21, main.first { it.number == 21 }.number)
        val rpc = fields(main.first { it.number == 21 }.bytes)
        assertEquals(1, fields(rpc.first { it.number == 1 }.bytes).first { it.number == 1 }.number)
        val ftp = fields(rpc.first { it.number == 8 }.bytes)
        assertEquals(9, ftp.single().number)
    }

    private fun <T : FileResponse> parseResponse(type: Class<T>, response: T): T {
        val incoming = FileTransferCodec.parseMain(
            FileTransferCodec.encodeMainResponse(1, 2, 3, response),
        ) as FileTransferCodec.Incoming.Response
        return type.cast(incoming.response) ?: error("Unexpected response type")
    }

    private data class Field(
        val number: Int,
        val wire: Int,
        val bytes: ByteArray = ByteArray(0),
    )

    private fun fields(data: ByteArray): List<Field> {
        val result = ArrayList<Field>()
        val reader = ProtoReader(data)
        while (true) {
            val field = reader.readField() ?: return result
            result += Field(field.number, field.wire, field.bytes)
        }
    }
}
