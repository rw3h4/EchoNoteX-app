package org.rw3h4.echonotex.service.textgen.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import java.io.File

/**
 * A model that has been downloaded, verified, and persisted. This is the record
 * Phase 5's engine wiring will read to resolve a model id to an on-disk file
 * without knowing anything about the download machinery.
 */
@Serializable
data class InstalledModel(
    val id: String,
    val filePath: String,
    val sizeBytes: Long,
    val sha256: String,
    val installedAt: Long,
) {
    val file: File get() = File(filePath)
}

/**
 * Persistence for "which models are installed and verified?", backed by a
 * Preferences DataStore. Each installed model is stored as one JSON blob keyed
 * by `model_<id>`, which keeps the record atomic and lets [allInstalled] scan
 * by key prefix.
 *
 * The [DataStore] is injected so unit tests can supply a temp-file-backed store.
 */
class InstalledModelStore(private val dataStore: DataStore<Preferences>) {

    fun isInstalled(id: String): Flow<Boolean> =
        dataStore.data.map { it.contains(keyFor(id)) }

    fun getInstalled(id: String): Flow<InstalledModel?> =
        dataStore.data.map { prefs -> prefs[keyFor(id)]?.let(::decode) }

    fun allInstalled(): Flow<List<InstalledModel>> =
        dataStore.data.map { prefs ->
            prefs.asMap()
                .filterKeys { it.name.startsWith(KEY_PREFIX) }
                .values
                .mapNotNull { value -> (value as? String)?.let(::decode) }
        }

    suspend fun markInstalled(entry: ModelCatalogEntry, file: File) {
        val record = InstalledModel(
            id = entry.id,
            filePath = file.absolutePath,
            sizeBytes = file.length(),
            sha256 = entry.sha256,
            installedAt = System.currentTimeMillis(),
        )
        dataStore.edit { prefs -> prefs[keyFor(entry.id)] = json.encodeToString(record) }
    }

    suspend fun markUninstalled(id: String) {
        dataStore.edit { prefs -> prefs.remove(keyFor(id)) }
    }

    private fun decode(raw: String): InstalledModel? =
        runCatching { json.decodeFromString<InstalledModel>(raw) }.getOrNull()

    companion object {
        private const val KEY_PREFIX = "model_"
        private const val DATASTORE_NAME = "installed_models"

        private val json = Json { ignoreUnknownKeys = true }

        private fun keyFor(id: String) = stringPreferencesKey(KEY_PREFIX + id)

        private val Context.installedModelsDataStore by preferencesDataStore(DATASTORE_NAME)

        /** Builds a store backed by the app's real DataStore. */
        fun create(context: Context): InstalledModelStore =
            InstalledModelStore(context.applicationContext.installedModelsDataStore)
    }
}
