package com.sour.duoremote.session

import com.sour.duoremote.protocol.file.FileEntry
import com.sour.duoremote.protocol.file.FileInfo
import com.sour.duoremote.protocol.file.FileRequest
import com.sour.duoremote.protocol.file.FileResponse
import com.sour.duoremote.protocol.file.FileTransferCodec
import com.sour.duoremote.protocol.file.TaskId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

class FileTransferEngineTest {

    @Test
    fun directoryListingUpdatesPathAndSortsFoldersFirst() {
        val sent = mutableListOf<ByteArray>()
        val engine = FileTransferEngine(send = { sent += it; true }, now = { 1L })

        assertTrue(engine.list("C:\\Users"))
        val request = lastRequest(sent) as FileRequest.ReadDir
        assertEquals("C:\\Users", request.path)

        val entries = listOf(
            FileEntry(4, "z.txt", 10, 0, "C:\\Users\\z.txt"),
            FileEntry(0, "Desktop", 0, 0, "C:\\Users\\Desktop"),
            FileEntry(4, "a.txt", 20, 0, "C:\\Users\\a.txt"),
        )
        engine.onMessage(
            response(
                FileResponse.Directory(
                    id = TaskId(request.id.taskId),
                    path = "C:\\Users",
                    entries = entries,
                    fileError = 1,
                ),
            ),
        )

        assertEquals("C:\\Users", engine.state.path)
        assertEquals(listOf("Desktop", "a.txt", "z.txt"), engine.state.entries.map { it.name })
        assertEquals(false, engine.state.loading)
        assertNull(engine.state.error)
    }

    @Test
    fun uploadUsesStopAndWaitAndCompletesAfterResult() {
        val payload = ByteArray(150_000) { (it % 251).toByte() }
        val sent = mutableListOf<ByteArray>()
        val engine = FileTransferEngine(send = { sent += it; true }, now = { 1L })

        assertTrue(
            engine.upload(
                name = "sample.bin",
                size = payload.size.toLong(),
                modifiedTime = 0L,
                input = ByteArrayInputStream(payload),
                batchIndex = 2,
                batchTotal = 5,
            ),
        )
        assertEquals(2, engine.state.transfer?.batchIndex)
        assertEquals(5, engine.state.transfer?.batchTotal)
        val receive = lastRequest(sent) as FileRequest.Receive
        val taskId = receive.id.taskId
        assertEquals(taskId.toLong(), lastRequestEnvelope(sent).requestId)
        assertEquals("sample.bin", receive.files.single().relPath)
        assertEquals(3, receive.strategy)

        engine.onMessage(
            response(FileResponse.ReceiveResponse(TaskId(taskId), emptyList(), 1)),
        )
        val ask = lastRequest(sent) as FileRequest.Ask
        assertEquals(payload.size.toLong(), ask.fileSize)

        engine.onMessage(
            response(FileResponse.Confirm(TaskId(taskId, 1), skip = false, err = 1, resumePoint = 0)),
        )

        val blocks = ArrayList<ByteArray>()
        var blockCount = 0
        while (blockCount < 20) {
            when (val request = lastRequest(sent)) {
                is FileRequest.Block -> {
                    blocks += request.data
                    blockCount++
                    assertEquals(blockCount - 1, request.blockId)
                    engine.onMessage(
                        response(
                            FileResponse.BlockConfirm(
                                id = TaskId(taskId, 1),
                                blockId = request.blockId,
                                err = 1,
                                blockLen = request.data.size,
                            ),
                        ),
                    )
                }

                is FileRequest.Complete -> {
                    val finals = sent.takeLast(2).map {
                        (FileTransferCodec.parseMain(it) as FileTransferCodec.Incoming.Request).request
                    }
                    assertTrue(finals.all { it is FileRequest.Complete })
                    assertEquals(1, (finals[0] as FileRequest.Complete).id.fileIndex)
                    assertEquals(-1, (finals[1] as FileRequest.Complete).id.fileIndex)
                    break
                }

                else -> error("Unexpected request $request")
            }
        }

        assertEquals(3, blockCount)
        assertEquals(payload.toList(), blocks.fold(ByteArray(0)) { acc, bytes -> acc + bytes }.toList())

        engine.onMessage(
            response(FileResponse.Result(TaskId(taskId), fileError = 1, errMsg = "")),
        )
        assertEquals(TransferStatus.COMPLETED, engine.state.transfer?.status)
        assertEquals(payload.size.toLong(), engine.state.transfer?.done)
    }

    @Test
    fun downloadWritesBlocksAndEchoesHostRequestIds() {
        val payload = ByteArray(140_000) { (it % 239).toByte() }
        val output = ByteArrayOutputStream()
        val sent = mutableListOf<ByteArray>()
        val engine = FileTransferEngine(send = { sent += it; true }, now = { 1L })

        assertTrue(engine.download("C:\\dir\\sample.bin", "sample.bin", 0L, output))
        val send = lastRequest(sent) as FileRequest.Send
        val taskId = send.id.taskId
        assertEquals(taskId.toLong(), lastRequestEnvelope(sent).requestId)
        assertEquals("C:\\dir\\", send.path)

        engine.onMessage(
            response(
                FileResponse.SendResponse(
                    id = TaskId(taskId),
                    err = 1,
                    files = listOf(FileInfo("sample.bin", payload.size.toLong())),
                    folderName = "",
                    listData = ByteArray(0),
                ),
            ),
        )

        val askId = 800L
        engine.onMessage(
            hostRequest(
                FileRequest.Ask(TaskId(taskId, 1), 0L, payload.size.toLong()),
                requestId = askId,
            ),
        )
        val confirm = lastResponse(sent)
        assertEquals(askId, confirm.requestId)
        assertTrue(confirm.response is FileResponse.Confirm)

        var blockId = 1
        var offset = 0
        while (offset < payload.size) {
            val length = minOf(64 * 1024, payload.size - offset)
            val chunk = payload.copyOfRange(offset, offset + length)
            val requestId = 900L + blockId
            engine.onMessage(
                hostRequest(
                    FileRequest.Block(TaskId(taskId, 1), blockId, chunk),
                    requestId = requestId,
                ),
            )
            val ack = lastResponse(sent)
            assertEquals(requestId, ack.requestId)
            val blockConfirm = ack.response as FileResponse.BlockConfirm
            assertEquals(1, blockConfirm.err)
            assertEquals(length, blockConfirm.blockLen)
            offset += length
            blockId++
        }

        val completeId = 999L
        engine.onMessage(
            hostRequest(
                FileRequest.Complete(TaskId(taskId, 1), 1),
                requestId = completeId,
            ),
        )
        val result = lastResponse(sent)
        assertEquals(completeId, result.requestId)
        assertEquals(1, (result.response as FileResponse.Result).fileError)

        assertArrayEquals(payload, output.toByteArray())
        assertEquals(TransferStatus.COMPLETED, engine.state.transfer?.status)
    }

    @Test
    fun sendFailureSurfacesError() {
        val engine = FileTransferEngine(send = { false }, now = { 1L })
        assertEquals(false, engine.list("C:\\"))
        assertEquals("文件通道未就绪", engine.state.error)
        assertEquals(false, engine.state.loading)
    }

    @Test
    fun directoryListingTimesOutWithoutAResponse() {
        var clock = 0L
        val engine = FileTransferEngine(send = { true }, now = { clock })
        assertTrue(engine.list("C:\\"))
        clock = 20_000L
        engine.tick()
        assertEquals("读取远端目录超时", engine.state.error)
        assertFalse(engine.state.loading)
    }

    @Test
    fun parentPathStopsAtDriveRoot() {
        assertEquals("C:\\", FileTransferState.parentOf("C:\\Users"))
        assertEquals("C:\\dir", FileTransferState.parentOf("C:\\dir\\file.txt"))
        assertNull(FileTransferState.parentOf("C:\\"))
        assertEquals("C:\\dir\\file.txt", FileTransferState.childOf("C:\\dir", "file.txt"))
    }

    private fun lastRequest(sent: List<ByteArray>): FileRequest =
        lastRequestEnvelope(sent).request

    private fun lastRequestEnvelope(sent: List<ByteArray>): FileTransferCodec.Incoming.Request =
        FileTransferCodec.parseMain(sent.last()) as FileTransferCodec.Incoming.Request

    private fun lastResponse(sent: List<ByteArray>): FileTransferCodec.Incoming.Response =
        FileTransferCodec.parseMain(sent.last()) as FileTransferCodec.Incoming.Response

    private fun response(response: FileResponse): ByteArray =
        FileTransferCodec.encodeMainResponse(seq = 1, timestamp = 2, requestId = 3, response = response)

    private fun hostRequest(request: FileRequest, requestId: Long): ByteArray =
        FileTransferCodec.encodeMainRequest(
            seq = 10,
            timestamp = 20,
            requestId = requestId,
            request = request,
        )
}
