package com.vivid.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Private Erweiterungseigenschaft, um eine einzige Instanz von DataStore zu gewährleisten.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/**
 * Datenklasse zur Kapselung aller Stream- und OBS-Einstellungen.
 */
data class AppSettings(
    val streamUrl: String = "",
    val streamKey: String = "",
    val obsHost: String = "localhost",
    val obsPort: String = "4455",
    val obsPassword: String = ""
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    private companion object {
        // Schlüssel für Stream-Einstellungen
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")

        // Schlüssel für OBS-Einstellungen
        val OBS_HOST = stringPreferencesKey("obs_host")
        val OBS_PORT = stringPreferencesKey("obs_port")
        val OBS_PASSWORD = stringPreferencesKey("obs_password")
    }

    /**
     * Ein Flow, der bei jeder Änderung die gesamten App-Einstellungen ausgibt.
     */
    val appSettingsFlow: Flow<AppSettings> = context.dataStore.data.map { preferences ->
        AppSettings(
            streamUrl = preferences[STREAM_URL] ?: "rtmp://a.rtmp.youtube.com/live2",
            streamKey = preferences[STREAM_KEY] ?: "",
            obsHost = preferences[OBS_HOST] ?: "localhost",
            obsPort = preferences[OBS_PORT] ?: "4455",
            obsPassword = preferences[OBS_PASSWORD] ?: ""
        )
    }

    /**
     * Speichert die Stream-URL und den Stream-Schlüssel sicher.
     */
    suspend fun updateStreamSettings(url: String, key: String) {
        context.dataStore.edit { preferences ->
            preferences[STREAM_URL] = url
            preferences[STREAM_KEY] = key
        }
    }

    /**
     * Speichert die OBS-Verbindungseinstellungen sicher.
     */
    suspend fun updateObsSettings(host: String, port: String, password: String) {
        context.dataStore.edit { preferences ->
            preferences[OBS_HOST] = host
            preferences[OBS_PORT] = port
            preferences[OBS_PASSWORD] = password
        }
    }
}