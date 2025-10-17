package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Umbenannt für Konsistenz mit dem ViewModel
data class AppSettings(
    val obsHost: String,
    val obsPort: String, // Wir verwenden String, um konsistent mit dem UI-State zu sein
    val obsPassword: String
)

@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object PreferencesKeys {
        // Wir benennen die Keys um, damit sie zur AppSettings-Klasse passen
        val OBS_HOST = stringPreferencesKey("obs_host")
        val OBS_PORT = stringPreferencesKey("obs_port") // Speichern als String ist hier einfacher
        val OBS_PASSWORD = stringPreferencesKey("obs_password")
    }

    // HIER DIE WICHTIGSTE ÄNDERUNG: Flow umbenennen
    val appSettingsFlow: Flow<AppSettings> = dataStore.data
        .map { preferences ->
            AppSettings(
                // Properties umbenennen (ip -> obsHost)
                obsHost = preferences[PreferencesKeys.OBS_HOST] ?: "localhost",
                obsPort = preferences[PreferencesKeys.OBS_PORT] ?: "4455",
                obsPassword = preferences[PreferencesKeys.OBS_PASSWORD] ?: ""
            )
        }

    suspend fun updateObsSettings(host: String, port: String, password: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.OBS_HOST] = host
            preferences[PreferencesKeys.OBS_PORT] = port
            preferences[PreferencesKeys.OBS_PASSWORD] = password
        }
    }
}