// SettingsRepositoryTest.kt

package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map // Wichtig für .map auf einem Flow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach // Modernes JUnit 5 (ersetzt @Before)
import org.junit.jupiter.api.Test
import java.io.File


data class StreamSettings(val url: String, val key: String)

class SettingsRepository(private val dataStore: DataStore<Preferences>) {
    private object PreferencesKeys {
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")
    }

    val streamSettingsFlow = dataStore.data
        .map { preferences ->
            StreamSettings(
                url = preferences[PreferencesKeys.STREAM_URL] ?: "",
                key = preferences[PreferencesKeys.STREAM_KEY] ?: ""
            )
        }

    suspend fun updateStreamSettings(url: String, key: String) {
        dataStore.edit { preferences ->
            preferences[PreferencesKeys.STREAM_URL] = url
            preferences[PreferencesKeys.STREAM_KEY] = key
        }
    }
}


// --- HIER BEGINNT DER TEST ---
class SettingsRepositoryTest {

    // Erstellen Sie eine Test-DataStore im Speicher
    private val testDataStore: DataStore<Preferences> = PreferenceDataStoreFactory.create(
        produceFile = { File("test.preferences_pb") }
    )

    private lateinit var repository: SettingsRepository

    @BeforeEach // KORREKTUR: @BeforeEach für JUnit 5
    fun setup() {
        // Initialisieren Sie das Repository mit der Test-DataStore
        repository = SettingsRepository(testDataStore)
    }

    @Test
    fun `streamSettingsFlow should return saved values`() = runTest {
        // 1. Arrange: Speichern Sie Testdaten
        val testUrl = "rtmp://test.url/live"
        val testKey = "test_key_123"
        repository.updateStreamSettings(testUrl, testKey)

        // 2. Act: Rufen Sie den Flow auf und sammeln Sie das erste Ergebnis
        val settings = repository.streamSettingsFlow.first()

        // 3. Assert: Überprüfen Sie, ob die Daten korrekt sind
        assertEquals(testUrl, settings.url)
        assertEquals(testKey, settings.key)
    }

    @Test
    fun `streamSettingsFlow should return default values if nothing is saved`() = runTest {
        // Act: Rufen Sie den Flow ohne vorherige Daten auf
        val settings = repository.streamSettingsFlow.first()

        // Assert: Überprüfen Sie die Standardwerte (leere Strings)
        assertEquals("", settings.url)
        assertEquals("", settings.key)
    }
}