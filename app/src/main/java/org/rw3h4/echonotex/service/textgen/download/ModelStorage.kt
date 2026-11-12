package org.rw3h4.echonotex.service.textgen.download

import android.content.Context
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import java.io.File

/**
 * Owns the on-disk layout for downloaded models.
 *
 * Files live under `<external-files>/models/<model-id>/<filename>`. External
 * app-specific storage is used so the user can reclaim the space via system
 * settings, and the per-model-id subdirectory keeps final files and their
 * `.partial` companions grouped and easy to reason about.
 *
 * The primary constructor takes the models root directory directly so the class
 * can be unit-tested against a temp folder with no Android [Context].
 */
class ModelStorage(private val modelsRoot: File) {

    constructor(context: Context) : this(
        // Fall back to internal storage if external is unavailable (removable
        // media ejected). Correctness never depends on external being present.
        File(context.getExternalFilesDir(null) ?: context.filesDir, MODELS_DIR)
    )

    /** Per-model directory (not guaranteed to exist until [ensureDirFor]). */
    fun dirFor(entry: ModelCatalogEntry): File = File(modelsRoot, entry.id)

    /** Final destination path for the verified GGUF. */
    fun fileFor(entry: ModelCatalogEntry): File = File(dirFor(entry), entry.filename)

    /** In-progress download path; atomically renamed to [fileFor] on successful verify. */
    fun partialFileFor(entry: ModelCatalogEntry): File =
        File(dirFor(entry), entry.filename + PARTIAL_SUFFIX)

    /** Creates the per-model directory if needed and returns it. */
    fun ensureDirFor(entry: ModelCatalogEntry): File = dirFor(entry).apply { mkdirs() }

    /** True when the final (verified) file is present. */
    fun exists(entry: ModelCatalogEntry): Boolean = fileFor(entry).isFile

    /**
     * Removes both the final and partial files (and prunes the now-empty
     * per-model directory). Returns true if anything was actually deleted.
     */
    fun delete(entry: ModelCatalogEntry): Boolean {
        // Non-short-circuiting: always attempt both deletions.
        val finalDeleted = fileFor(entry).delete()
        val partialDeleted = partialFileFor(entry).delete()
        dirFor(entry).delete() // no-op unless the directory is now empty
        return finalDeleted || partialDeleted
    }

    /** Total bytes used by everything under the models root (final + partial). */
    fun totalUsedBytes(): Long {
        if (!modelsRoot.exists()) return 0L
        return modelsRoot.walkTopDown().filter { it.isFile }.sumOf { it.length() }
    }

    companion object {
        const val MODELS_DIR = "models"
        const val PARTIAL_SUFFIX = ".partial"
    }
}
