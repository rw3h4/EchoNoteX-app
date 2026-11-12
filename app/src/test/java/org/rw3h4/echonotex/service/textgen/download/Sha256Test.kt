package org.rw3h4.echonotex.service.textgen.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class Sha256Test {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun fileWith(content: String): File =
        tmp.newFile().apply { writeText(content) }

    @Test
    fun computesKnownHashOfEmptyFile() {
        // SHA-256 of the empty input is a well-known constant.
        val file = tmp.newFile()
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.of(file)
        )
    }

    @Test
    fun computesKnownHashOfAbc() {
        // SHA-256("abc") is a standard test vector.
        val file = fileWith("abc")
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.of(file)
        )
    }

    @Test
    fun matchesReturnsTrueForCorrectHash() {
        val file = fileWith("hello world")
        val hash = Sha256.of(file)
        assertTrue(Sha256.matches(file, hash))
        assertTrue("comparison is case-insensitive", Sha256.matches(file, hash.uppercase()))
    }

    @Test
    fun tamperedContentDoesNotMatch() {
        val original = fileWith("the quick brown fox")
        val expected = Sha256.of(original)

        val tampered = fileWith("the quick brown fox.")
        assertFalse(Sha256.matches(tampered, expected))
    }
}
