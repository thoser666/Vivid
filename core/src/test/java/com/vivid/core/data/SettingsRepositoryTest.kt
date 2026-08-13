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
    fun `appSettingsFlow should default streamUseTls to false (plain rtmp)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_stream_tls_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(false, settings.streamUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved stream tls flag`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_stream_tls.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateStreamSettings("rtmp://live.example/app", "key-1", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(true, settings.streamUseTls)
        assertEquals("rtmp://live.example/app", settings.streamUrl)
        assertEquals("key-1", settings.streamKey)
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
    fun `appSettingsFlow should default secondary stream settings to empty (disabled)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("", settings.secondaryStreamUrl)
        assertEquals("", settings.secondaryStreamKey)
        assertEquals(false, settings.secondaryStreamUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved secondary stream settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateSecondaryStreamSettings("rtmp://secondary.example/app", "key-2", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("rtmp://secondary.example/app", settings.secondaryStreamUrl)
        assertEquals("key-2", settings.secondaryStreamKey)
        assertEquals(true, settings.secondaryStreamUseTls)
        // Das primäre Ziel bleibt unberührt.
        assertEquals("", settings.streamUrl)
    }

    @Test
    fun `appSettingsFlow should keep secondary settings untouched when updating the primary`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary_independent.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        repository.updateStreamSettings("rtmp://primary.example/app", "key-1")
        val settings = repository.appSettingsFlow.first()

        assertEquals("rtmp://primary.example/app", settings.streamUrl)
        assertEquals("", settings.secondaryStreamUrl)
        assertEquals("", settings.secondaryStreamKey)
        assertEquals(false, settings.secondaryStreamUseTls)
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
