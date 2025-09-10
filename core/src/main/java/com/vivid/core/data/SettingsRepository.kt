package com.vivid.core.data // Sie können dies in einem passenden 'core'-Modul ablegen

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

// Erstellt eine Erweiterungseigenschaft, um den DataStore-Singleton zu verwalten
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class StreamSettings(
    val url: String = "",
    val key: String = ""
)

@Singleton
class SettingsRepository @Inject constructor(@ApplicationContext private val context: Context) {

    private object PreferencesKeys {
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")
    }

    // Ein Flow, der bei jeder Einstellungsänderung die neuen Werte ausgibt
    val streamSettingsFlow: Flow<StreamSettings> = context.dataStore.data.map { preferences ->
        val url = preferences[PreferencesKeys.STREAM_URL] ?: "rtmp://a.rtmp.youtube.com/live2" // Standardwert
        val key = preferences[PreferencesKeys.STREAM_KEY] ?: "" // Standardwert
        StreamSettings(url, key)
    }

    // Eine Suspend-Funktion zum sicheren Speichern der Einstellungen
    suspend fun updateStreamSettings(url: String, key: String) {
        context.dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_URL] = url
            preferences[PreferencesKeys.STREAM_KEY] = key
        }
    }
}