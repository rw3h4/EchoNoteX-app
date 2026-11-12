package org.rw3h4.echonotex.service.textgen.download

import java.io.File

/**
 * The observable state of a single model, from the picker UI's point of view.
 * Produced by [ModelDownloadManager] by folding WorkManager's [androidx.work.WorkInfo]
 * together with the persisted [InstalledModelStore] record.
 */
sealed interface ModelDownloadState {

    /** Nothing is enqueued for this model and it is not installed. Tapping starts a download. */
    data object NotStarted : ModelDownloadState

    /** Enqueued in WorkManager, waiting for its constraints (e.g. Wi-Fi) to be met. */
    data object Queued : ModelDownloadState

    /** Actively transferring bytes. */
    data class Downloading(
        val bytesDownloaded: Long,
        val totalBytes: Long,
        val progressPercent: Int,
    ) : ModelDownloadState

    /** Bytes are all down; SHA256 verification is in progress. */
    data object Verifying : ModelDownloadState

    /** Verified and persisted; [file] is the final on-disk location. */
    data class Installed(
        val file: File,
        val sizeBytes: Long,
    ) : ModelDownloadState

    /**
     * The download failed. [canRetry] is false for failures that a retry cannot
     * fix (SHA256 mismatch, insufficient storage), true for transient/network ones.
     */
    data class Failed(
        val reason: String,
        val canRetry: Boolean,
    ) : ModelDownloadState

    /** The user cancelled the download. Treated by the UI as revertible to [NotStarted]. */
    data object Cancelled : ModelDownloadState
}
