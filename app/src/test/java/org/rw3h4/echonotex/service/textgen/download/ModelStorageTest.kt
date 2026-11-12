package org.rw3h4.echonotex.service.textgen.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import java.io.File

class ModelStorageTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(id: String = "qwen2.5-1.5b", filename: String = "qwen.gguf") =
        ModelCatalogEntry(
            id = id,
            name = "Qwen",
            description = "d",
            url = "https://example.com/$filename",
            filename = filename,
            sizeBytes = 1_000L,
            sha256 = "a".repeat(64),
            quant = "Q4_K_M",
            minRamMb = 3072,
            contextDefault = 2048,
            chatTemplateId = "qwen2",
        )

    private fun storage(): Pair<ModelStorage, File> {
        val root = tmp.newFolder("models")
        return ModelStorage(root) to root
    }

    @Test
    fun fileForProducesIdAndFilenameNestedPath() {
        val (storage, root) = storage()
        val entry = entry(id = "m1", filename = "m1.gguf")

        val expected = File(File(root, "m1"), "m1.gguf")
        assertEquals(expected, storage.fileFor(entry))
    }

    @Test
    fun partialFileForAppendsPartialSuffix() {
        val (storage, root) = storage()
        val entry = entry(id = "m1", filename = "m1.gguf")

        val expected = File(File(root, "m1"), "m1.gguf.partial")
        assertEquals(expected, storage.partialFileFor(entry))
    }

    @Test
    fun existsReflectsRealityForFinalFileOnly() {
        val (storage, _) = storage()
        val entry = entry()

        assertFalse(storage.exists(entry))

        storage.ensureDirFor(entry)
        // A partial alone does not count as "installed".
        storage.partialFileFor(entry).writeText("half")
        assertFalse(storage.exists(entry))

        storage.fileFor(entry).writeText("done")
        assertTrue(storage.exists(entry))
    }

    @Test
    fun deleteRemovesBothFinalAndPartial() {
        val (storage, _) = storage()
        val entry = entry()
        storage.ensureDirFor(entry)
        storage.fileFor(entry).writeText("done")
        storage.partialFileFor(entry).writeText("half")

        val deleted = storage.delete(entry)

        assertTrue(deleted)
        assertFalse(storage.fileFor(entry).exists())
        assertFalse(storage.partialFileFor(entry).exists())
    }

    @Test
    fun deleteReturnsFalseWhenNothingPresent() {
        val (storage, _) = storage()
        assertFalse(storage.delete(entry()))
    }

    @Test
    fun totalUsedBytesSumsAllModelFiles() {
        val (storage, _) = storage()
        val a = entry(id = "a", filename = "a.gguf")
        val b = entry(id = "b", filename = "b.gguf")
        storage.ensureDirFor(a)
        storage.ensureDirFor(b)
        storage.fileFor(a).writeText("12345")       // 5 bytes
        storage.partialFileFor(b).writeText("123")  // 3 bytes

        assertEquals(8L, storage.totalUsedBytes())
    }

    @Test
    fun totalUsedBytesIsZeroWhenRootMissing() {
        val root = File(tmp.root, "does-not-exist")
        assertEquals(0L, ModelStorage(root).totalUsedBytes())
    }
}
