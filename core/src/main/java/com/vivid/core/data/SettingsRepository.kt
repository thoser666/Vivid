package com.vivid.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// Diese Erweiterungseigenschaft ist der "Single Point of Truth" für die Erstellung von DataStore.
// Sie ist privat für diese Datei, was gut ist.
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class StreamSettings(
    val url: String = "",
    val key: String = ""
)

@Singleton
class SettingsRepository @Inject constructor(
    // Wir injizieren den ApplicationContext, um Zugriff auf die dataStore-Erweiterung zu erhalten.
    @ApplicationContext private val context: Context
) {

    // Das 'companion object' ist besser für Konstanten geeignet als 'private object'.
    private companion object {
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")
    }

    // Ein Flow, der bei jeder Einstellungsänderung die neuen Werte ausgibt.
    // Wir verwenden direkt context.dataStore.
    val streamSettingsFlow: Flow<StreamSettings> = context.dataStore.data.map { preferences ->
        val url = preferences[STREAM_URL] ?: "rtmp://a.rtmp.youtube.com/live2" // Standardwert
        val key = preferences[STREAM_KEY] ?: "" // Standardwert
        StreamSettings(url, key)
    }

    // Eine Suspend-Funktion zum sicheren Speichern der Einstellungen.
    // Auch hier verwenden wir direkt context.dataStore.
    suspend fun updateStreamSettings(url: String, key: String) {
        context.dataStore.edit { preferences ->
            preferences[STREAM_URL] = url
            preferences[STREAM_KEY] = key
        }
    }
}

// Das Hilt-Modul war innerhalb der Klasse, was nicht ideal ist.
// Es sollte auf Top-Ebene stehen, um die Verantwortlichkeiten klar zu trennen.
// Dieses Modul ist hier jetzt redundant, da wir Hilt nicht mehr benötigen,
// um DataStore zu injizieren, aber es ist eine gute Praxis, es für zukünftige
// 'core'-Abhängigkeiten beizubehalten.
@Module
@InstallIn(SingletonComponent::class)
object CoreDataModule {
    // Diese @Provides-Funktion ist nicht mehr notwendig, da SettingsRepository
    // jetzt direkt Context injiziert und DataStore selbst auflöst.
    // Wir lassen das Modul für zukünftige Erweiterungen stehen.
}