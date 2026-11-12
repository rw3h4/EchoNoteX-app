package org.rw3h4.echonotex.service.textgen.catalog

import kotlinx.serialization.Serializable

/**
 * Top-level catalog document. `version` allows future schema migrations;
 * loaders should reject any version they do not understand.
 */
@Serializable
data class ModelCatalog(
    val version: Int,
    val models: List<ModelCatalogEntry>,
)
