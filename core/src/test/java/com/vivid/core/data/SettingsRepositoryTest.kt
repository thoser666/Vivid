package com.vivid.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import app.cash.turbine.test
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.io.File

@ExperimentalCoroutinesApi
class SettingsRepositoryTest {

    // Ein spezieller Dispatcher für Tests, um die Ausführung von Coroutinen zu steuern
    private val testDispatcher: TestDispatcher = StandardTestDispatcher()

    // Ein temporärer Ordner, der von JUnit5 bereitgestellt wird, um eine Test-DataStore-Datei zu erstellen
    @TempDir
    lateinit var tempFile: File

    // Die zu testende Klasse
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var testDataStore: DataStore<Preferences>

    // Mocken des Android Context, da wir ihn nicht wirklich brauchen
    private val mockContext: Context = mock()

    @BeforeEach
    fun setUp() {
        // Richten Sie den Haupt-Dispatcher für Coroutinen ein, um unseren Test-Dispatcher zu verwenden
        Dispatchers.setMain(testDispatcher)

        // Erstellen einer In-Memory-DataStore-Instanz für diesen Test
        testDataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(testDispatcher + Job()),
            produceFile = { tempFile.resolve("test_settings.preferences_pb") },
        )
        // Erstellen Sie die Repository-Instanz mit der Test-DataStore
        settingsRepository = SettingsRepository(testDataStore)
    }

    @AfterEach
    fun tearDown() {
        // Setzen Sie den Haupt-Dispatcher nach dem Test zurück
        Dispatchers.resetMain()
    }

    @Test
    @DisplayName("streamSettingsFlow should emit default values when DataStore is empty")
    fun `streamSettingsFlow emits default values initially`() = runTest {
        // Act & Assert
        settingsRepository.streamSettingsFlow.test {
            val defaultSettings = awaitItem()
            assertEquals("rtmp://a.rtmp.youtube.com/live2", defaultSettings.url)
            assertEquals("", defaultSettings.key)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    @DisplayName("updateStreamSettings should correctly update values and be emitted by the flow")
    fun `updateStreamSettings updates the flow correctly`() = runTest {
        // Arrange
        val newUrl = "rtmp://test.url/live"
        val newKey = "test_key_12345"

        settingsRepository.streamSettingsFlow.test {
            // Ignorieren Sie den ersten, standardmäßigen Emissionswert
            awaitItem()

            // Act: Rufen Sie die Methode auf, die wir testen wollen
            settingsRepository.updateStreamSettings(newUrl, newKey)

            // Assert: Überprüfen Sie, ob der Flow die neuen Werte ausgibt
            val updatedSettings = awaitItem()
            assertEquals(newUrl, updatedSettings.url)
            assertEquals(newKey, updatedSettings.key)
        }
    }
}
