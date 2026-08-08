package com.vivid.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `appSettingsFlow should return saved values`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Arrange
        val testUrl = "rtmp://test.url/live"
        val testKey = "test_key_123"
        
        // Act
        repository.updateStreamSettings(testUrl, testKey)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(testUrl, settings.streamUrl)
        assertEquals(testKey, settings.streamKey)
    }

    @Test
    fun `appSettingsFlow should return default values if nothing is saved`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("", settings.streamUrl)
        assertEquals("localhost", settings.obsHost)
    }

    @Test
    fun `appSettingsFlow should return saved obs settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_obs.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateObsSettings("192.168.1.100", "4456", "obs-secret")
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("192.168.1.100", settings.obsHost)
        assertEquals("4456", settings.obsPort)
        assertEquals("obs-secret", settings.obsPassword)
    }

    @Test
    fun `appSettingsFlow should default obsUseTls to false (plain ws)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_tls_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(false, settings.obsUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved obs tls flag`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_tls.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateObsSettings("192.168.1.100", "4455", "obs-secret", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(true, settings.obsUseTls)
    }
}
