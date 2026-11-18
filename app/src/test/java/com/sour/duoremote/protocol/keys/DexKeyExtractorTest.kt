package com.sour.duoremote.protocol.keys

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class DexKeyExtractorTest {

    private val fingerprint =
        "40cb3a32f9f6f90d9962fb9736685bb881979e848b5956649b6dae3cc957528c"

    @Test
    fun extractsKeyFromOfficialTvApkWhenPresent() {
        val apk = findTvApk()
        assumeTrue("official TV APK not present in this checkout", apk.isFile)

        val result = DexKeyExtractor.extract(apk, fingerprint)
        assertEquals(24, result.key.size)
        assertEquals(fingerprint, sha256Hex(result.key))
        // Never print result.key in test output.
        assertNotNull(result.fingerprintHex)
    }

    @Test
    fun rejectsWrongFingerprint() {
        val apk = findTvApk()
        assumeTrue("official TV APK not present in this checkout", apk.isFile)

        val failure = runCatching {
            DexKeyExtractor.extract(apk, "00".repeat(32))
        }.exceptionOrNull()
        assertEquals(DexKeyExtractor.Failure.KeyNotFound::class.java, failure?.javaClass)
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun findTvApk(): File {
        val candidates = listOf(
            File("../research/apk/uuremote-tv-3.5.0.apk"),
            File("research/apk/uuremote-tv-3.5.0.apk"),
        )
        return candidates.firstOrNull { it.isFile } ?: candidates.first()
    }
}
