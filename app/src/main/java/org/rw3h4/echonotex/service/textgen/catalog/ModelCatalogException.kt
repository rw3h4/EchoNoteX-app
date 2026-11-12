package org.rw3h4.echonotex.service.textgen.catalog

/**
 * Errors produced while loading or validating the model catalog.
 * Subclasses identify the failure mode so callers can react meaningfully.
 */
sealed class ModelCatalogException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause) {

    class Malformed(message: String, cause: Throwable? = null) :
        ModelCatalogException(message, cause)

    class UnsupportedVersion(found: Int, supported: Int) :
        ModelCatalogException("Unsupported catalog version: found=$found supported=$supported")

    class Empty : ModelCatalogException("Catalog contains no model entries")

    class InvalidField(entryId: String?, field: String, reason: String) :
        ModelCatalogException(
            "Invalid field '$field' on entry '${entryId ?: "<unknown>"}': $reason"
        )

    class DuplicateId(id: String) :
        ModelCatalogException("Duplicate model id in catalog: '$id'")
}
