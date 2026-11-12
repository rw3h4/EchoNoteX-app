package org.rw3h4.echonotex.viewmodel

import android.app.ActivityManager
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rw3h4.echonotex.EchoNoteApplication
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalog
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogEntry
import org.rw3h4.echonotex.service.textgen.catalog.ModelCatalogException
import org.rw3h4.echonotex.service.textgen.download.DownloadPreferences
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadManager
import org.rw3h4.echonotex.service.textgen.download.ModelDownloadState

/**
 * How comfortably a catalog model fits in this device's total RAM.
 * Computed once per load in the ViewModel, never in composition.
 */
enum class ModelCompatibility {
    /** Total RAM >= 1.5x the model's minimum — comfortable headroom. */
    WORKS,

    /** Total RAM meets the minimum but with little headroom. */
    TIGHT,

    /** Total RAM is below the model's stated minimum. */
    TOO_BIG,
}

/** A catalog entry paired with its pre-computed compatibility bucket and live download state. */
data class CatalogModelUi(
    val entry: ModelCatalogEntry,
    val compatibility: ModelCompatibility,
    val downloadState: ModelDownloadState = ModelDownloadState.NotStarted,
)

sealed interface ModelPickerUiState {
    data object Loading : ModelPickerUiState
    data class Success(
        val models: List<CatalogModelUi>,
        val deviceRamMb: Long,
        val wifiOnly: Boolean = DownloadPreferences.DEFAULT_WIFI_ONLY,
    ) : ModelPickerUiState

    data class Error(val message: String) : ModelPickerUiState
}

/**
 * Loads the model catalog, grades each entry against the device's total RAM,
 * and overlays the live per-model download state from [ModelDownloadManager].
 *
 * The catalog loader and RAM source are injected so unit tests can substitute
 * fakes. [downloadManager] and [downloadPreferences] are optional so the
 * catalog/compatibility logic remains testable without WorkManager or DataStore.
 */
class ModelPickerViewModel(
    private val loadCatalog: () -> ModelCatalog,
    private val readDeviceRamMb: () -> Long,
    private val downloadManager: ModelDownloadManager? = null,
    private val downloadPreferences: DownloadPreferences? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ModelPickerUiState>(ModelPickerUiState.Loading)
    val uiState: StateFlow<ModelPickerUiState> = _uiState.asStateFlow()

    // Latest inputs to the composed Success state.
    private var baseModels: List<CatalogModelUi> = emptyList()
    private var deviceRamMb: Long = 0L
    private var baseLoaded: Boolean = false
    private var latestDownloads: Map<String, ModelDownloadState> = emptyMap()
    private var latestWifiOnly: Boolean = DownloadPreferences.DEFAULT_WIFI_ONLY

    init {
        load()
        downloadManager?.let { manager ->
            viewModelScope.launch {
                manager.observeAllDownloads().collect { downloads ->
                    latestDownloads = downloads
                    recomposeIfLoaded()
                }
            }
        }
        downloadPreferences?.let { prefs ->
            viewModelScope.launch {
                prefs.wifiOnly.collect { wifiOnly ->
                    latestWifiOnly = wifiOnly
                    recomposeIfLoaded()
                }
            }
        }
    }

    fun load() {
        _uiState.value = ModelPickerUiState.Loading
        baseLoaded = false
        viewModelScope.launch {
            try {
                withContext(ioDispatcher) {
                    val catalog = loadCatalog()
                    val ram = readDeviceRamMb()
                    deviceRamMb = ram
                    baseModels = catalog.models.map { entry ->
                        CatalogModelUi(entry, computeCompatibility(ram, entry.minRamMb))
                    }
                }
                baseLoaded = true
                _uiState.value = buildSuccess()
            } catch (e: ModelCatalogException) {
                _uiState.value = ModelPickerUiState.Error(messageFor(e))
            } catch (e: Exception) {
                _uiState.value = ModelPickerUiState.Error(
                    "Couldn't load the model catalog: ${e.message ?: e.javaClass.simpleName}"
                )
            }
        }
    }

    /** Starts (or re-enqueues) a download for [entry], honouring the Wi-Fi-only setting. */
    fun startDownload(entry: ModelCatalogEntry) {
        downloadManager?.startDownload(entry, latestWifiOnly)
    }

    /** Retry is just a fresh enqueue; the previous failed job is complete so KEEP allows it. */
    fun retryDownload(entry: ModelCatalogEntry) = startDownload(entry)

    fun cancelDownload(id: String) {
        downloadManager?.cancelDownload(id)
    }

    fun deleteInstalled(id: String) {
        val manager = downloadManager ?: return
        viewModelScope.launch { manager.deleteInstalled(id) }
    }

    fun setWifiOnly(enabled: Boolean) {
        val prefs = downloadPreferences
        if (prefs != null) {
            viewModelScope.launch { prefs.setWifiOnly(enabled) }
        } else {
            // No persistence available (e.g. tests): keep the value in memory.
            latestWifiOnly = enabled
            recomposeIfLoaded()
        }
    }

    private fun buildSuccess(): ModelPickerUiState.Success = ModelPickerUiState.Success(
        models = baseModels.map { model ->
            model.copy(downloadState = latestDownloads[model.entry.id] ?: ModelDownloadState.NotStarted)
        },
        deviceRamMb = deviceRamMb,
        wifiOnly = latestWifiOnly,
    )

    private fun recomposeIfLoaded() {
        if (baseLoaded) _uiState.value = buildSuccess()
    }

    companion object {
        /** Multiplier over min_ram_mb that counts as comfortable headroom. */
        const val WORKS_HEADROOM = 1.5

        fun computeCompatibility(deviceRamMb: Long, minRamMb: Int): ModelCompatibility = when {
            deviceRamMb.toDouble() >= minRamMb * WORKS_HEADROOM -> ModelCompatibility.WORKS
            deviceRamMb >= minRamMb -> ModelCompatibility.TIGHT
            else -> ModelCompatibility.TOO_BIG
        }

        private fun messageFor(e: ModelCatalogException): String = when (e) {
            is ModelCatalogException.Malformed ->
                "The model catalog could not be read. It may be corrupted."
            is ModelCatalogException.UnsupportedVersion ->
                "The model catalog format is not supported by this app version. Please update the app."
            is ModelCatalogException.Empty ->
                "The model catalog has no models in it."
            is ModelCatalogException.InvalidField ->
                "A model in the catalog has invalid data: ${e.message}"
            is ModelCatalogException.DuplicateId ->
                "The model catalog contains duplicate entries: ${e.message}"
        }

        /**
         * Total device RAM in MB. Total (not available) RAM is used for
         * compatibility gating: it is a stable per-device property, whereas
         * available RAM fluctuates with whatever else is running.
         */
        fun readTotalRamMb(context: Context): Long {
            val activityManager =
                context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val memoryInfo = ActivityManager.MemoryInfo()
            activityManager.getMemoryInfo(memoryInfo)
            return memoryInfo.totalMem / (1024L * 1024L)
        }

        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext as EchoNoteApplication
            return viewModelFactory {
                initializer {
                    ModelPickerViewModel(
                        loadCatalog = { app.catalogRepository.load() },
                        readDeviceRamMb = { readTotalRamMb(app) },
                        downloadManager = app.downloadManager,
                        downloadPreferences = app.downloadPreferences,
                    )
                }
            }
        }
    }
}
