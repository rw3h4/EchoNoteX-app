package org.rw3h4.echonotex.service.textgen.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogRepository
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

/**
 * Downloads a single GGUF model file in the background.
 *
 * The only input is the model id ([KEY_MODEL_ID]); the URL, filename, expected
 * SHA-256 and size are resolved from the [ModelCatalogRepository] so the worker's
 * inputs stay small and the catalog remains the single source of truth.
 *
 * Behaviour:
 * - Resumes an interrupted `.partial` via an HTTP `Range` request.
 * - Streams to the `.partial` file, publishing throttled progress.
 * - Verifies SHA-256, then atomically renames `.partial` -> final and records it
 *   in [InstalledModelStore].
 * - SHA-256 mismatch => [Result.failure] with [KEY_FAILURE_REASON] = "sha256_mismatch"
 *   and NO retry (corruption / wrong URL, not a network blip).
 * - [IOException] => [Result.retry] so WorkManager backs off; `.partial` is kept.
 */
class ModelDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    private val storage = ModelStorage(appContext)
    private val installedStore = InstalledModelStore.create(appContext)
    private val catalog = ModelCatalogRepository(appContext)

    override suspend fun doWork(): Result {
        val modelId = inputData.getString(KEY_MODEL_ID)
            ?: return Result.failure(reason("missing_model_id"))

        val entry = try {
            catalog.load().models.firstOrNull { it.id == modelId }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load catalog for '$modelId'", e)
            null
        } ?: return Result.failure(reason("unknown_model"))

        // If it is somehow already present and valid, treat as success.
        if (storage.exists(entry) && Sha256.matches(storage.fileFor(entry), entry.sha256)) {
            installedStore.markInstalled(entry, storage.fileFor(entry))
            return Result.success(successData(entry))
        }

        setForeground(makeForegroundInfo(entry, percent = 0, indeterminate = true))

        return try {
            download(entry)
        } catch (e: IOException) {
            // Network / IO problem — keep the .partial for resume and let
            // WorkManager schedule a backed-off retry.
            Log.w(TAG, "IO error downloading '$modelId', will retry", e)
            Result.retry()
        }
    }

    private suspend fun download(entry: ModelCatalogEntry): Result = withContext(Dispatchers.IO) {
        storage.ensureDirFor(entry)
        val partial = storage.partialFileFor(entry)

        var existingBytes = if (partial.exists()) partial.length() else 0L
        val connection = openConnection(entry.url, rangeStart = existingBytes)

        try {
            val code = connection.responseCode
            val append: Boolean
            val totalBytes: Long
            when {
                // Server honoured our Range request: continue appending.
                existingBytes > 0 && code == HttpURLConnection.HTTP_PARTIAL -> {
                    append = true
                    val remaining = connection.contentLengthLong.takeIf { it > 0 } ?: 0L
                    totalBytes = if (remaining > 0) existingBytes + remaining else entry.sizeBytes
                }
                // Range not satisfiable (partial >= full, or stale): start over.
                code == 416 -> {
                    partial.delete()
                    return@withContext Result.retry()
                }
                // Either a fresh download, or the server ignored our Range and
                // sent the whole file: overwrite from the start.
                code == HttpURLConnection.HTTP_OK -> {
                    append = false
                    existingBytes = 0L
                    totalBytes = connection.contentLengthLong.takeIf { it > 0 } ?: entry.sizeBytes
                }
                else -> {
                    Log.w(TAG, "Unexpected HTTP $code for '${entry.id}'")
                    return@withContext Result.retry()
                }
            }

            var downloaded = existingBytes
            var lastPublish = 0L
            connection.inputStream.use { input ->
                FileOutputStream(partial, append).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        // Cooperatively cancel: WorkManager cancels the coroutine,
                        // and we leave the .partial in place for a future resume.
                        coroutineContext.ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val now = System.currentTimeMillis()
                        if (now - lastPublish >= PROGRESS_INTERVAL_MS) {
                            lastPublish = now
                            publishProgress(entry, downloaded, totalBytes)
                        }
                    }
                    output.flush()
                }
            }

            // Signal 100% so the manager can render a distinct "Verifying" state.
            publishProgress(entry, totalBytes.coerceAtLeast(downloaded), totalBytes)
            setForeground(makeForegroundInfo(entry, percent = 100, verifying = true))

            if (!Sha256.matches(partial, entry.sha256)) {
                Log.e(TAG, "SHA256 mismatch for '${entry.id}', deleting partial")
                partial.delete()
                return@withContext Result.failure(reason(REASON_SHA_MISMATCH))
            }

            val finalFile = storage.fileFor(entry)
            finalFile.delete() // in case a stale final somehow lingers
            if (!partial.renameTo(finalFile)) {
                // Rename can fail across filesystems; fall back to copy.
                partial.copyTo(finalFile, overwrite = true)
                partial.delete()
            }

            installedStore.markInstalled(entry, finalFile)
            Result.success(successData(entry))
        } finally {
            connection.disconnect()
        }
    }

    private suspend fun publishProgress(entry: ModelCatalogEntry, downloaded: Long, total: Long) {
        setProgress(
            workDataOf(
                KEY_BYTES_DOWNLOADED to downloaded,
                KEY_TOTAL_BYTES to total,
            )
        )
        val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
        runCatching { setForeground(makeForegroundInfo(entry, percent = percent)) }
    }

    /**
     * Opens a connection to [url] following redirects manually so the `Range`
     * header is re-applied on each hop. [HttpURLConnection]'s automatic redirect
     * handling drops custom headers, which would silently break resume against
     * Hugging Face's CDN redirect.
     */
    private fun openConnection(url: String, rangeStart: Long): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val conn = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                requestMethod = "GET"
                if (rangeStart > 0) setRequestProperty("Range", "bytes=$rangeStart-")
            }
            val code = conn.responseCode
            if (code in REDIRECT_CODES) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location == null || ++redirects > MAX_REDIRECTS) {
                    throw IOException("Too many redirects (or missing Location) for $url")
                }
                current = URL(URL(current), location).toString()
                continue
            }
            return conn
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo =
        makeForegroundInfo(entry = null, percent = 0, indeterminate = true)

    private fun makeForegroundInfo(
        entry: ModelCatalogEntry?,
        percent: Int,
        indeterminate: Boolean = false,
        verifying: Boolean = false,
    ): ForegroundInfo {
        ensureChannel(applicationContext)

        val name = entry?.name ?: "model"
        val text = when {
            verifying -> "Verifying $name…"
            indeterminate -> "Starting download…"
            else -> "$percent%"
        }

        val cancelIntent = WorkManager.getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle("Downloading $name")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, percent, indeterminate || verifying)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancel",
                cancelIntent,
            )
            .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun successData(entry: ModelCatalogEntry) = workDataOf(
        KEY_FILE_PATH to storage.fileFor(entry).absolutePath,
        KEY_TOTAL_BYTES to storage.fileFor(entry).length(),
    )

    private fun reason(value: String) = workDataOf(KEY_FAILURE_REASON to value)

    companion object {
        private const val TAG = "ModelDownloadWorker"

        const val KEY_MODEL_ID = "model_id"
        const val KEY_BYTES_DOWNLOADED = "bytes_downloaded"
        const val KEY_TOTAL_BYTES = "total_bytes"
        const val KEY_FILE_PATH = "file_path"
        const val KEY_FAILURE_REASON = "failure_reason"

        const val REASON_SHA_MISMATCH = "sha256_mismatch"

        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_INTERVAL_MS = 500L
        private const val CONNECT_TIMEOUT_MS = 30_000
        private const val READ_TIMEOUT_MS = 30_000
        private const val MAX_REDIRECTS = 5
        private val REDIRECT_CODES = setOf(301, 302, 303, 307, 308)

        private const val NOTIFICATION_ID = 4711
        const val CHANNEL_ID = "model_downloads"

        /** Idempotent notification-channel creation (safe to call repeatedly). */
        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL_ID) != null) return
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Model downloads",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Progress of AI model downloads"
                    setShowBadge(false)
                }
            )
        }
    }
}
