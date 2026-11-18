package com.sour.duoremote.protocol.keys

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Arrays
import java.util.zip.ZipFile

/**
 * Extracts the 24-byte UU TV signing material from an official TV 3.5.0 APK.
 *
 * The key is a string in the DEX string table. We identify it by SHA-256, never by
 * a hard-coded value, and never log it. Callers must encrypt the returned bytes
 * immediately (see SecureStore).
 *
 * This is intentionally a pure JVM class so it can be unit-tested without Android.
 */
object DexKeyExtractor {

    data class Result(
        val key: ByteArray,
        val fingerprintHex: String,
    )

    sealed class Failure(message: String) : Exception(message) {
        class MissingApk : Failure("APK not found")
        class NoDex : Failure("No classes*.dex in APK")
        class KeyNotFound : Failure("Signing material not found")
        class ReadError : Failure("Could not read APK")
    }

    fun extract(apk: File, expectedFingerprintHex: String): Result {
        if (!apk.isFile) throw Failure.MissingApk()
        val expected = expectedFingerprintHex.lowercase()
        var foundDex = false

        try {
            ZipFile(apk).use { zip ->
                val dexEntries = zip.entries().asSequence()
                    .filter { entry ->
                        entry.name.matches(Regex("classes(\\d*)\\.dex")) && !entry.isDirectory
                    }
                    .sortedBy { it.name }
                    .toList()

                if (dexEntries.isEmpty()) throw Failure.NoDex()
                foundDex = true

                for (entry in dexEntries) {
                    val bytes = zip.getInputStream(entry).use { it.readBytes() }
                    try {
                        val key = scanDex(bytes, expected)
                        if (key != null) {
                            return Result(key, expected)
                        }
                    } finally {
                        Arrays.fill(bytes, 0)
                    }
                }
            }
        } catch (e: Failure) {
            throw e
        } catch (e: Exception) {
            throw Failure.ReadError()
        }

        if (!foundDex) throw Failure.NoDex()
        throw Failure.KeyNotFound()
    }

    private fun scanDex(dex: ByteArray, expectedFingerprintHex: String): ByteArray? {
        if (dex.size < 0x70) return null
        val magic = String(dex, 0, 4, Charsets.US_ASCII)
        if (magic != "dex\n") return null

        val buffer = ByteBuffer.wrap(dex).order(ByteOrder.LITTLE_ENDIAN)
        val stringIdsSize = buffer.getInt(0x38).toLong() and 0xffffffffL
        val stringIdsOff = buffer.getInt(0x3c).toLong() and 0xffffffffL
        if (stringIdsSize <= 0 || stringIdsOff <= 0) return null
        if (stringIdsOff + stringIdsSize * 4 > dex.size) return null

        val digest = MessageDigest.getInstance("SHA-256")
        val candidate = ByteArray(24)
        val stringIds = stringIdsOff.toInt()

        for (i in 0 until stringIdsSize.toInt()) {
            val dataOff = buffer.getInt(stringIds + i * 4).toLong() and 0xffffffffL
            if (dataOff >= dex.size) continue

            var p = dataOff.toInt()
            p = skipUleb128(dex, p)
            if (p < 0 || p >= dex.size) continue

            val start = p
            while (p < dex.size && dex[p] != 0.toByte()) p++
            if (p >= dex.size) continue
            if (p - start != candidate.size) continue

            System.arraycopy(dex, start, candidate, 0, candidate.size)
            digest.reset()
            val hash = digest.digest(candidate)
            if (toHex(hash) == expectedFingerprintHex) {
                return candidate.copyOf()
            }
            Arrays.fill(candidate, 0)
        }
        return null
    }

    private fun skipUleb128(bytes: ByteArray, offset: Int): Int {
        var p = offset
        var shift = 0
        while (p < bytes.size && shift < 35) {
            val b = bytes[p].toInt() and 0xff
            p++
            if (b and 0x80 == 0) return p
            shift += 7
        }
        return -1
    }

    private fun toHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        val digits = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xff
            out[i * 2] = digits[v ushr 4]
            out[i * 2 + 1] = digits[v and 0x0f]
        }
        return String(out)
    }
}
