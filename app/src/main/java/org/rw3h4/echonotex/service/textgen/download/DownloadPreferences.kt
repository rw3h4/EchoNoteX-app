package org.rw3h4.echonotex.service.textgen.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * User-facing download settings. Currently just the "download over Wi-Fi only"
 * toggle, persisted so the choice survives across sessions. Defaults to ON —
 * models are large and metered-connection downloads are a nasty surprise.
 */
class DownloadPreferences(private val dataStore: DataStore<Preferences>) {

    val wifiOnly: Flow<Boolean> =
        dataStore.data.map { it[WIFI_ONLY] ?: DEFAULT_WIFI_ONLY }

    suspend fun setWifiOnly(enabled: Boolean) {
        dataStore.edit { it[WIFI_ONLY] = enabled }
    }

    companion object {
        const val DEFAULT_WIFI_ONLY = true
        private const val DATASTORE_NAME = "download_settings"

        private val WIFI_ONLY = booleanPreferencesKey("wifi_only")

        private val Context.downloadSettingsDataStore by preferencesDataStore(DATASTORE_NAME)

        fun create(context: Context): DownloadPreferences =
            DownloadPreferences(context.applicationContext.downloadSettingsDataStore)
    }
}
