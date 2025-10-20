package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PrefKeys {
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")
        val OBS_HOST = stringPreferencesKey("obs_host")
        val OBS_PORT = stringPreferencesKey("obs_port")
        val OBS_PASSWORD = stringPreferencesKey("obs_password")
    }

    // WICHTIG: Dies ist jetzt der EINZIGE Flow, den das ViewModel braucht.
    // Er kombiniert die Daten für Stream und OBS.
    val appSettingsFlow: Flow<AppSettings> = combine(
        // Flow für Stream-Daten
        dataStore.data.map { prefs ->
            Pair(
                prefs[PrefKeys.STREAM_URL] ?: "",
                prefs[PrefKeys.STREAM_KEY] ?: ""
            )
        },
        // Flow für OBS-Daten
        dataStore.data.map { prefs ->
            Triple(
                prefs[PrefKeys.OBS_HOST] ?: "localhost",
                prefs[PrefKeys.OBS_PORT] ?: "4455",
                prefs[PrefKeys.OBS_PASSWORD] ?: ""
            )
        }
    ) { streamData, obsData ->
        // Baue das komplette AppSettings-Objekt zusammen
        AppSettings(
            streamUrl = streamData.first,
            streamKey = streamData.second,
            obsHost = obsData.first,
            obsPort = obsData.second,
            obsPassword = obsData.third
        )
    }

    // Update-Funktionen bleiben getrennt, das ist in Ordnung.
    suspend fun updateStreamSettings(url: String, key: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.STREAM_URL] = url
            prefs[PrefKeys.STREAM_KEY] = key
        }
    }

    suspend fun updateObsSettings(host: String, port: String, password: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.OBS_HOST] = host
            prefs[PrefKeys.OBS_PORT] = port
            prefs[PrefKeys.OBS_PASSWORD] = password
        }
    }
}