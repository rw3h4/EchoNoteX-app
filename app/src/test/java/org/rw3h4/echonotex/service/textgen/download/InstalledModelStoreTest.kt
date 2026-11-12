package org.rw3h4.echonotex.service.textgen.download

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class InstalledModelStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun entry(id: String, filename: String = "$id.gguf", sha: String = "a".repeat(64)) =
        ModelCatalogEntry(
            id = id,
            name = "Model $id",
            description = "d",
            url = "https://example.com/$filename",
            filename = filename,
            sizeBytes = 1_000L,
            sha256 = sha,
            quant = "Q4_K_M",
            minRamMb = 3072,
            contextDefault = 2048,
            chatTemplateId = "qwen2",
        )

    private fun newStore(scope: CoroutineScope): InstalledModelStore {
        val file = File(tmp.newFolder(), "installed.preferences_pb")
        val dataStore: DataStore<Preferences> =
            PreferenceDataStoreFactory.create(scope = scope) { file }
        return InstalledModelStore(dataStore)
    }

    private fun installedFile(bytes: Int): File =
        tmp.newFile().apply { writeBytes(ByteArray(bytes)) }

    @Test
    fun markInstalledThenGetInstalledReturnsRecord() = runTest {
        val store = newStore(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        val e = entry("qwen", sha = "b".repeat(64))
        val file = installedFile(42)

        store.markInstalled(e, file)

        val record = store.getInstalled("qwen").first()
        assertEquals("qwen", record?.id)
        assertEquals(file.absolutePath, record?.filePath)
        assertEquals(42L, record?.sizeBytes)
        assertEquals("b".repeat(64), record?.sha256)
        assertTrue((record?.installedAt ?: 0L) > 0L)
        assertTrue(store.isInstalled("qwen").first())
    }

    @Test
    fun markUninstalledClearsRecord() = runTest {
        val store = newStore(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        val e = entry("qwen")
        store.markInstalled(e, installedFile(10))
        assertTrue(store.isInstalled("qwen").first())

        store.markUninstalled("qwen")

        assertFalse(store.isInstalled("qwen").first())
        assertNull(store.getInstalled("qwen").first())
    }

    @Test
    fun allInstalledReflectsState() = runTest {
        val store = newStore(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        assertTrue(store.allInstalled().first().isEmpty())

        store.markInstalled(entry("a"), installedFile(1))
        store.markInstalled(entry("b"), installedFile(2))

        val ids = store.allInstalled().first().map { it.id }.toSet()
        assertEquals(setOf("a", "b"), ids)

        store.markUninstalled("a")
        assertEquals(setOf("b"), store.allInstalled().first().map { it.id }.toSet())
    }

    @Test
    fun getInstalledIsNullForUnknownId() = runTest {
        val store = newStore(CoroutineScope(UnconfinedTestDispatcher(testScheduler) + Job()))
        assertNull(store.getInstalled("missing").first())
        assertFalse(store.isInstalled("missing").first())
    }
}
