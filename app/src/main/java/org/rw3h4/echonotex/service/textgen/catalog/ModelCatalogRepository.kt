package org.rw3h4.echonotex.service.textgen.catalog

import android.content.Context
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException

/**
 * Loads the model catalog from app assets, validates it, and caches the
 * parsed result in memory. All validation failures surface as
 * [ModelCatalogException] subclasses — never silent.
 */
class ModelCatalogRepository(
    private val context: Context,
    private val assetPath: String = DEFAULT_ASSET_PATH,
) {
    @Volatile
    private var cached: ModelCatalog? = null

    fun load(): ModelCatalog {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val json = try {
                context.assets.open(assetPath).bufferedReader().use { it.readText() }
            } catch (e: IOException) {
                throw ModelCatalogException.Malformed(
                    "Failed to read catalog asset '$assetPath'", e
                )
            }
            val parsed = parseAndValidate(json)
            cached = parsed
            return parsed
        }
    }

    companion object {
        const val DEFAULT_ASSET_PATH = "model_catalog.json"
        const val SUPPORTED_VERSION = 1

        private val SHA256_HEX = Regex("^[0-9a-f]{64}$")
        private const val SHA256_TODO_MARKER = "TODO"

        private val json = Json {
            ignoreUnknownKeys = false
            isLenient = false
        }

        /**
         * Parse a catalog JSON string and validate every entry. Pure function —
         * has no Android dependencies so it can be exercised in unit tests.
         */
        fun parseAndValidate(rawJson: String): ModelCatalog {
            val catalog = try {
                json.decodeFromString(ModelCatalog.serializer(), rawJson)
            } catch (e: SerializationException) {
                throw ModelCatalogException.Malformed(
                    "Catalog JSON is malformed or missing required fields: ${e.message}", e
                )
            } catch (e: IllegalArgumentException) {
                throw ModelCatalogException.Malformed(
                    "Catalog JSON is malformed: ${e.message}", e
                )
            }

            if (catalog.version != SUPPORTED_VERSION) {
                throw ModelCatalogException.UnsupportedVersion(
                    found = catalog.version, supported = SUPPORTED_VERSION
                )
            }
            if (catalog.models.isEmpty()) {
                throw ModelCatalogException.Empty()
            }

            val seenIds = HashSet<String>(catalog.models.size)
            for (entry in catalog.models) {
                validateEntry(entry)
                if (!seenIds.add(entry.id)) {
                    throw ModelCatalogException.DuplicateId(entry.id)
                }
            }

            return catalog
        }

        private fun validateEntry(entry: ModelCatalogEntry) {
            requireNonBlank(entry, "id", entry.id)
            requireNonBlank(entry, "name", entry.name)
            requireNonBlank(entry, "description", entry.description)
            requireNonBlank(entry, "filename", entry.filename)
            requireNonBlank(entry, "quant", entry.quant)
            requireNonBlank(entry, "chat_template_id", entry.chatTemplateId)

            if (!entry.url.startsWith("https://")) {
                throw ModelCatalogException.InvalidField(
                    entry.id, "url", "must be an https:// URL (got '${entry.url}')"
                )
            }
            if (entry.sizeBytes <= 0) {
                throw ModelCatalogException.InvalidField(
                    entry.id, "size_bytes", "must be > 0 (got ${entry.sizeBytes})"
                )
            }
            if (entry.minRamMb <= 0) {
                throw ModelCatalogException.InvalidField(
                    entry.id, "min_ram_mb", "must be > 0 (got ${entry.minRamMb})"
                )
            }
            if (entry.contextDefault <= 0) {
                throw ModelCatalogException.InvalidField(
                    entry.id, "context_default", "must be > 0 (got ${entry.contextDefault})"
                )
            }

            // Skip the SHA256 format check for entries marked with the explicit
            // TODO placeholder. Real entries must be 64 lowercase hex chars.
            if (!entry.sha256.startsWith(SHA256_TODO_MARKER) && !SHA256_HEX.matches(entry.sha256)) {
                throw ModelCatalogException.InvalidField(
                    entry.id, "sha256", "must be 64 lowercase hex chars (got '${entry.sha256}')"
                )
            }
        }

        private fun requireNonBlank(entry: ModelCatalogEntry, field: String, value: String) {
            if (value.isBlank()) {
                throw ModelCatalogException.InvalidField(entry.id, field, "must not be blank")
            }
        }
    }
}
