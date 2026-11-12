package org.rw3h4.echonotex.service.textgen.download

import java.io.File
import java.security.MessageDigest

/**
 * Pure SHA-256 helper. No Android dependencies, so it is exercised directly in
 * unit tests. Streams the file in fixed chunks so it never loads a multi-hundred-MB
 * GGUF into memory.
 */
object Sha256 {

    private const val BUFFER_SIZE = 64 * 1024

    /** Lowercase hex SHA-256 of [file]'s contents. */
    fun of(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return toHex(digest.digest())
    }

    /** True when [file]'s SHA-256 equals [expected] (case-insensitive). */
    fun matches(file: File, expected: String): Boolean =
        of(file).equals(expected, ignoreCase = true)

    private fun toHex(bytes: ByteArray): String {
        val sb = StringBuilder(bytes.size * 2)
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(HEX[v ushr 4])
            sb.append(HEX[v and 0x0F])
        }
        return sb.toString()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
