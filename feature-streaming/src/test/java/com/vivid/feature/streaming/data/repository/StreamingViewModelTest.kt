// In app/src/test/java/com/vivid/feature/streaming/data/repository/StreamingViewModelTest.kt

package com.vivid.feature.streaming.data.repository

import app.cash.turbine.test
import com.vivid.core.data.SettingsRepository
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.ui.StreamingViewModel
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@ExperimentalCoroutinesApi
class StreamingViewModelTest {

    // 1. Mocks für alle Abhängigkeiten erstellen
    private lateinit var streamingEngine: StreamingEngine
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var obsClient: OBSWebSocketClient

    // Die zu testende Klasse
    private lateinit var viewModel: StreamingViewModel

    // Test-Dispatcher für Coroutinen
    private val testDispatcher = StandardTestDispatcher()

    // MutableStateFlows für die Mocks, damit wir ihren Zustand ändern können
    private val mockIsStreamingFlow = MutableStateFlow(false)
    private val mockStreamingErrorFlow = MutableStateFlow<String?>(null)
    private val mockObsConnectionStateFlow = MutableStateFlow(OBSWebSocketClient.ConnectionState.DISCONNECTED)
    private val mockRtmpUrlFlow = MutableStateFlow("rtmp://default.url/live")

    @Before
    fun setUp() {
        // Main-Dispatcher für Tests setzen, damit viewModelScope vorhersagbar ist
        Dispatchers.setMain(testDispatcher)

        // Mocks instanziieren
        streamingEngine = mockk(relaxed = true) // relaxed = true erlaubt uns, nur das zu mocken, was wir brauchen
        settingsRepository = mockk()
        obsClient = mockk(relaxed = true)

        // 2. Verhalten der Mocks definieren
        // Wenn der ViewModel die Flows abfragt, soll er unsere Test-Flows erhalten
        every { streamingEngine.isStreaming } returns mockIsStreamingFlow
        every { streamingEngine.streamingError } returns mockStreamingErrorFlow
        every { obsClient.connectionState } returns mockObsConnectionStateFlow

        // Mocken des SettingsRepository
        val mockSettingsFlow = flowOf(SettingsRepository.StreamSettings(url = "rtmp://mock.url/live", key = "1234"))
        every { settingsRepository.streamSettingsFlow } returns mockSettingsFlow

        // 3. ViewModel mit den Mocks initialisieren
        viewModel = StreamingViewModel(streamingEngine, settingsRepository, obsClient)
    }

    @After
    fun tearDown() {
        // Main-Dispatcher nach dem Test zurücksetzen
        Dispatchers.resetMain()
    }

    // In der StreamingViewModelTest-Klasse

    @Test
    fun `isStreaming state should reflect streamingEngine's state`() = runTest {
        // Turbine's 'test' sammelt alle Emissionen des Flows
        viewModel.isStreaming.test {
            // Initialer Zustand
            assertEquals(false, awaitItem())

            // Simulieren, dass die Engine mit dem Streaming beginnt
            mockIsStreamingFlow.value = true
            assertEquals(true, awaitItem())

            // Simulieren, dass die Engine stoppt
            mockIsStreamingFlow.value = false
            assertEquals(false, awaitItem())
        }
    }

    @Test
    fun `rtmpUrl state should be collected from settingsRepository`() = runTest {
        viewModel.rtmpUrl.test {
            // Initialer Wert aus dem StateIn
            assertEquals("", awaitItem())
            // Wert vom gemockten Flow
            assertEquals("rtmp://mock.url/live", awaitItem())
        }
    }


    @Test
    fun `connectToOBS should call obsClient with correct config`() {
        // Arrange
        val host = "192.168.1.10"
        val port = 4455
        val password = "supersecret"

        // Act
        viewModel.connectToOBS(host, port, password)

        // Assert
        // coVerify, da obsClient.connect eine suspend-Funktion sein könnte oder in einer Coroutine aufgerufen wird
        coVerify {
            obsClient.connect(OBSWebSocketClient.OBSConfig(host, port, password))
        }
    }

    @Test
    fun `onCleared should release engine and disconnect client`() {
        // Act
        viewModel.onCleared()

        // Assert
        coVerify { streamingEngine.release() }
        coVerify { obsClient.disconnect() }
    }
}