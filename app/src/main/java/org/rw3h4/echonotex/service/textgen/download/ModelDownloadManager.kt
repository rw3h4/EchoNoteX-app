package org.rw3h4.echonotex.service.textgen.download

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogRepository
import java.io.File
import java.util.UUID

/**
 * Public façade for the download pipeline. The picker UI talks only to this.
 *
 * It enqueues [ModelDownloadWorker] jobs (one unique job per model id), and
 * translates WorkManager's [WorkInfo] plus the [InstalledModelStore] record into
 * the UI-facing [ModelDownloadState].
 */
class ModelDownloadManager(
    context: Context,
    private val catalog: ModelCatalogRepository,
    private val storage: ModelStorage = ModelStorage(context),
    private val installedStore: InstalledModelStore = InstalledModelStore.create(context),
    private val workManager: WorkManager = WorkManager.getInstance(context),
) {

    /** Enqueues a download (no-op if one is already pending). Returns the work id. */
    fun startDownload(entry: ModelCatalogEntry, wifiOnly: Boolean): UUID {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ModelDownloadWorker>()
            .setConstraints(constraints)
            .setInputData(workDataOf(ModelDownloadWorker.KEY_MODEL_ID to entry.id))
            .addTag(TAG_ALL)
            .addTag(tagFor(entry.id))
            .build()

        // KEEP: re-tapping a model that is already downloading does nothing; a
        // finished (succeeded/failed/cancelled) job is not "pending", so a fresh
        // tap after those states starts a new download.
        workManager.enqueueUniqueWork(uniqueName(entry.id), ExistingWorkPolicy.KEEP, request)
        return request.id
    }

    fun observeDownload(id: String): Flow<ModelDownloadState> =
        combine(
            workManager.getWorkInfosForUniqueWorkFlow(uniqueName(id)),
            installedStore.getInstalled(id),
        ) { infos, installed ->
            mapWorkInfo(infos.firstOrNull(), installed)
        }

    /** Observes every catalog model's state as a map keyed by model id. */
    fun observeAllDownloads(): Flow<Map<String, ModelDownloadState>> {
        val ids = runCatching { catalog.load().models.map { it.id } }.getOrDefault(emptyList())
        if (ids.isEmpty()) return flowOf(emptyMap())
        val flows = ids.map { id -> observeDownload(id).map { id to it } }
        return combine(flows) { pairs -> pairs.toMap() }
    }

    fun cancelDownload(id: String) {
        workManager.cancelUniqueWork(uniqueName(id))
    }

    /**
     * Frees space for an installed model. Files are removed first, then the
     * registry entry — so a failed registry write can never leave a record that
     * points at a file that is already gone.
     */
    suspend fun deleteInstalled(id: String) {
        val entry = runCatching { catalog.load().models.firstOrNull { it.id == id } }.getOrNull()
        if (entry != null) {
            storage.delete(entry)
        }
        installedStore.markUninstalled(id)
    }

    private fun mapWorkInfo(info: WorkInfo?, installed: InstalledModel?): ModelDownloadState {
        val phase = when (info?.state) {
            null -> null
            WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> WorkPhase.QUEUED
            WorkInfo.State.RUNNING -> WorkPhase.RUNNING
            WorkInfo.State.SUCCEEDED -> WorkPhase.SUCCEEDED
            WorkInfo.State.FAILED -> WorkPhase.FAILED
            WorkInfo.State.CANCELLED -> WorkPhase.CANCELLED
        }
        val progress = info?.progress
        val output = info?.outputData
        return computeState(
            phase = phase,
            bytesDownloaded = progress?.getLong(ModelDownloadWorker.KEY_BYTES_DOWNLOADED, 0L) ?: 0L,
            totalBytes = progress?.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L) ?: 0L,
            failureReason = output?.getString(ModelDownloadWorker.KEY_FAILURE_REASON),
            outputFilePath = output?.getString(ModelDownloadWorker.KEY_FILE_PATH),
            outputSizeBytes = output?.getLong(ModelDownloadWorker.KEY_TOTAL_BYTES, 0L) ?: 0L,
            installed = installed,
        )
    }

    /** Coarse phase decoupled from [WorkInfo] so [computeState] is JVM-unit-testable. */
    internal enum class WorkPhase { QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

    companion object {
        private const val UNIQUE_PREFIX = "model_download_"
        const val TAG_ALL = "model_download"

        fun uniqueName(id: String) = UNIQUE_PREFIX + id
        fun tagFor(id: String) = "model_download_id_$id"

        /**
         * Pure fold of a model's state. Precedence: an active job (queued /
         * running) is shown as such; otherwise an installed record wins; then
         * terminal work states.
         */
        internal fun computeState(
            phase: WorkPhase?,
            bytesDownloaded: Long,
            totalBytes: Long,
            failureReason: String?,
            outputFilePath: String?,
            outputSizeBytes: Long,
            installed: InstalledModel?,
        ): ModelDownloadState {
            fun installedState(): ModelDownloadState? = when {
                installed != null -> ModelDownloadState.Installed(installed.file, installed.sizeBytes)
                outputFilePath != null ->
                    ModelDownloadState.Installed(File(outputFilePath), outputSizeBytes)
                else -> null
            }

            return when (phase) {
                null -> installedState() ?: ModelDownloadState.NotStarted

                WorkPhase.QUEUED -> ModelDownloadState.Queued

                WorkPhase.RUNNING -> {
                    if (totalBytes > 0 && bytesDownloaded >= totalBytes) {
                        ModelDownloadState.Verifying
                    } else {
                        val percent =
                            if (totalBytes > 0) {
                                ((bytesDownloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                            } else 0
                        ModelDownloadState.Downloading(bytesDownloaded, totalBytes, percent)
                    }
                }

                WorkPhase.SUCCEEDED ->
                    installedState() ?: ModelDownloadState.Verifying

                WorkPhase.FAILED -> {
                    val reason = failureReason ?: "download_failed"
                    ModelDownloadState.Failed(
                        reason = reason,
                        canRetry = reason != ModelDownloadWorker.REASON_SHA_MISMATCH,
                    )
                }

                WorkPhase.CANCELLED -> installedState() ?: ModelDownloadState.Cancelled
            }
        }
    }
}
