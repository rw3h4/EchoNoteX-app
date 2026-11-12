package org.rw3h4.echonotex.service.textgen.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadManager.Companion.computeState
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadManager.WorkPhase

/**
 * Exercises the pure state-fold that [ModelDownloadManager] applies to a
 * WorkManager job. WorkInfo itself is decoded into [WorkPhase] + progress/output
 * primitives at the boundary, so the interesting logic is fully unit-testable
 * here without WorkManager on the classpath.
 */
class ModelDownloadManagerTest {

    private fun state(
        phase: WorkPhase?,
        downloaded: Long = 0L,
        total: Long = 0L,
        failureReason: String? = null,
        outputPath: String? = null,
        outputSize: Long = 0L,
        installed: InstalledModel? = null,
    ) = computeState(
        phase = phase,
        bytesDownloaded = downloaded,
        totalBytes = total,
        failureReason = failureReason,
        outputFilePath = outputPath,
        outputSizeBytes = outputSize,
        installed = installed,
    )

    private fun installed(id: String = "m", path: String = "/tmp/m.gguf", size: Long = 100L) =
        InstalledModel(id = id, filePath = path, sizeBytes = size, sha256 = "a".repeat(64), installedAt = 1L)

    @Test
    fun noWorkAndNotInstalledIsNotStarted() {
        assertEquals(ModelDownloadState.NotStarted, state(phase = null))
    }

    @Test
    fun noWorkButInstalledIsInstalled() {
        val result = state(phase = null, installed = installed(size = 555L))
        assertTrue(result is ModelDownloadState.Installed)
        assertEquals(555L, (result as ModelDownloadState.Installed).sizeBytes)
    }

    @Test
    fun enqueuedIsQueued() {
        assertEquals(ModelDownloadState.Queued, state(phase = WorkPhase.QUEUED))
    }

    @Test
    fun runningWithProgressIsDownloadingWithPercent() {
        val result = state(phase = WorkPhase.RUNNING, downloaded = 250L, total = 1000L)
        assertTrue(result is ModelDownloadState.Downloading)
        result as ModelDownloadState.Downloading
        assertEquals(250L, result.bytesDownloaded)
        assertEquals(1000L, result.totalBytes)
        assertEquals(25, result.progressPercent)
    }

    @Test
    fun runningWithNoTotalIsDownloadingAtZeroPercent() {
        val result = state(phase = WorkPhase.RUNNING, downloaded = 100L, total = 0L)
        assertTrue(result is ModelDownloadState.Downloading)
        assertEquals(0, (result as ModelDownloadState.Downloading).progressPercent)
    }

    @Test
    fun runningAtFullBytesIsVerifying() {
        val result = state(phase = WorkPhase.RUNNING, downloaded = 1000L, total = 1000L)
        assertEquals(ModelDownloadState.Verifying, result)
    }

    @Test
    fun succeededResolvesToInstalledFromRegistry() {
        val result = state(phase = WorkPhase.SUCCEEDED, installed = installed(size = 900L))
        assertTrue(result is ModelDownloadState.Installed)
        assertEquals(900L, (result as ModelDownloadState.Installed).sizeBytes)
    }

    @Test
    fun succeededFallsBackToOutputDataWhenRegistryLags() {
        val result = state(
            phase = WorkPhase.SUCCEEDED,
            outputPath = "/tmp/out.gguf",
            outputSize = 777L,
        )
        assertTrue(result is ModelDownloadState.Installed)
        result as ModelDownloadState.Installed
        assertEquals("/tmp/out.gguf", result.file.path)
        assertEquals(777L, result.sizeBytes)
    }

    @Test
    fun failedNetworkCanRetry() {
        val result = state(phase = WorkPhase.FAILED, failureReason = "download_failed")
        assertTrue(result is ModelDownloadState.Failed)
        assertTrue((result as ModelDownloadState.Failed).canRetry)
    }

    @Test
    fun failedShaMismatchCannotRetry() {
        val result = state(
            phase = WorkPhase.FAILED,
            failureReason = ModelDownloadWorker.REASON_SHA_MISMATCH,
        )
        assertTrue(result is ModelDownloadState.Failed)
        result as ModelDownloadState.Failed
        assertEquals(ModelDownloadWorker.REASON_SHA_MISMATCH, result.reason)
        assertTrue("sha mismatch must not be retryable", !result.canRetry)
    }

    @Test
    fun cancelledIsCancelledWhenNotInstalled() {
        assertEquals(ModelDownloadState.Cancelled, state(phase = WorkPhase.CANCELLED))
    }

    @Test
    fun fullQueuedToRunningToSucceededTransition() {
        // queued -> running -> verifying -> installed, the happy path.
        assertEquals(ModelDownloadState.Queued, state(phase = WorkPhase.QUEUED))

        val running = state(phase = WorkPhase.RUNNING, downloaded = 500L, total = 1000L)
        assertTrue(running is ModelDownloadState.Downloading)

        val verifying = state(phase = WorkPhase.RUNNING, downloaded = 1000L, total = 1000L)
        assertEquals(ModelDownloadState.Verifying, verifying)

        val installed = state(phase = WorkPhase.SUCCEEDED, installed = installed())
        assertTrue(installed is ModelDownloadState.Installed)
    }
}
