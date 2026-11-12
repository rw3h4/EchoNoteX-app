package org.rw3h4.echonotex.service.textgen.catalog

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One model entry in the catalog. All fields are required in schema v1.
 * The serialized representation uses snake_case to match the JSON file;
 * the Kotlin properties use camelCase.
 */
@Serializable
data class ModelCatalogEntry(
    val id: String,
    val name: String,
    val description: String,
    val url: String,
    val filename: String,
    @SerialName("size_bytes")
    val sizeBytes: Long,
    val sha256: String,
    val quant: String,
    @SerialName("min_ram_mb")
    val minRamMb: Int,
    @SerialName("context_default")
    val contextDefault: Int,
    @SerialName("chat_template_id")
    val chatTemplateId: String,
)
