package org.rw3h4.echonotex

import android.app.Application
import android.util.Log
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogRepository
import org.rw3h4.echonotex.service.textgen.download.DownloadPreferences
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadManager
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadWorker

class EchoNoteApplication : Application() {

    /** App-wide model catalog, loaded lazily and cached. */
    val catalogRepository: ModelCatalogRepository by lazy { ModelCatalogRepository(this) }

    /** Public façade for the model download pipeline (Phase 3). */
    val downloadManager: ModelDownloadManager by lazy {
        ModelDownloadManager(this, catalog = catalogRepository)
    }

    /** Persisted download settings (Wi-Fi-only toggle). */
    val downloadPreferences: DownloadPreferences by lazy { DownloadPreferences.create(this) }

    override fun onCreate() {
        super.onCreate()
        ModelDownloadWorker.ensureChannel(this)
        try {
            val catalog = catalogRepository.load()
            Log.i(TAG, "Catalog loaded: ${catalog.models.size} models")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model catalog: ${e.message}", e)
        }
    }

    companion object {
        private const val TAG = "ModelCatalog"
    }
}
