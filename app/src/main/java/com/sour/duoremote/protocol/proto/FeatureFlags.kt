package com.sour.duoremote.protocol.proto

/**
 * `gv_pb.FeatureFlag` carried by ConnectOptions field 11 and by the SimpleAction echo handshake.
 *
 * The host only exposes optional RPC families after the controller advertises them. Values
 * mirror the capability levels used by the reference controller: file transfer v1/v2, clipboard
 * text changes and capture settings. Unknown families stay disabled.
 */
data class FeatureFlags(
    val captureSetting: Int = 4,
    val simpleAction: Int = 3,
    val systemMetrics: Int = 3,
    val privateScreen: Int = 0,
    val updateAcquire: Int = 0,
    val fileTransferFtp: Int = 2,
    val fileTransferFtp2: Int = 2,
    val clipboard: Int = 3,
    val qosStat: Int = 1,
    val mumuControl: Int = 0,
    val virtualMouse: Int = 0,
) {
    fun encode(): ByteArray = ProtoWriter().apply {
        if (captureSetting != 0) integer(1, captureSetting.toLong())
        if (simpleAction != 0) integer(2, simpleAction.toLong())
        if (systemMetrics != 0) integer(3, systemMetrics.toLong())
        if (privateScreen != 0) integer(4, privateScreen.toLong())
        if (updateAcquire != 0) integer(5, updateAcquire.toLong())
        if (fileTransferFtp != 0) integer(6, fileTransferFtp.toLong())
        if (fileTransferFtp2 != 0) integer(7, fileTransferFtp2.toLong())
        if (clipboard != 0) integer(8, clipboard.toLong())
        if (qosStat != 0) integer(9, qosStat.toLong())
        if (mumuControl != 0) integer(10, mumuControl.toLong())
        if (virtualMouse != 0) integer(11, virtualMouse.toLong())
    }.toByteArray()

    companion object {
        fun parse(data: ByteArray): FeatureFlags {
            var result = FeatureFlags(
                captureSetting = 0,
                simpleAction = 0,
                systemMetrics = 0,
                privateScreen = 0,
                updateAcquire = 0,
                fileTransferFtp = 0,
                fileTransferFtp2 = 0,
                clipboard = 0,
                qosStat = 0,
                mumuControl = 0,
                virtualMouse = 0,
            )
            val reader = ProtoReader(data)
            while (true) {
                val field = reader.readField() ?: break
                if (field.wire != 0) continue
                val value = field.integer.toInt()
                result = when (field.number) {
                    1 -> result.copy(captureSetting = value)
                    2 -> result.copy(simpleAction = value)
                    3 -> result.copy(systemMetrics = value)
                    4 -> result.copy(privateScreen = value)
                    5 -> result.copy(updateAcquire = value)
                    6 -> result.copy(fileTransferFtp = value)
                    7 -> result.copy(fileTransferFtp2 = value)
                    8 -> result.copy(clipboard = value)
                    9 -> result.copy(qosStat = value)
                    10 -> result.copy(mumuControl = value)
                    11 -> result.copy(virtualMouse = value)
                    else -> result
                }
            }
            return result
        }
    }
}
