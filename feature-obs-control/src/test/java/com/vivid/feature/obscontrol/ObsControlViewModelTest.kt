package com.vivid.feature.obscontrol

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.repository.StreamingRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ObsControlViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun settingsRepository(settings: AppSettings = AppSettings()): SettingsRepository =
        mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(settings)
        }

    @Test
    fun `initial state is Disconnected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())

        assertEquals(ConnectionState.Disconnected, viewModel.uiState.value)
    }

    @Test
    fun `connect with valid port delegates to repository and shows Connecting`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { connectToObs("secret", "127.0.0.1", 4455, false) } just runs
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455")

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        verify { repository.connectToObs("secret", "127.0.0.1", 4455, false) }
    }

    @Test
    fun `connect forwards the tls flag to the repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { connectToObs("secret", "127.0.0.1", 4455, true) } just runs
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455", useTls = true)

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        verify { repository.connectToObs("secret", "127.0.0.1", 4455, true) }
    }

    @Test
    fun `connect with invalid port sets error state and does not connect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "not-a-port")

        assertEquals(
            ConnectionState.Error(messageRes = R.string.obs_invalid_port_message),
            viewModel.uiState.value,
        )
        verify(exactly = 0) { repository.connectToObs(any(), any(), any(), any()) }
    }

    @Test
    fun `connect propagates repository exceptions as error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { connectToObs("secret", "127.0.0.1", 4455, false) } throws RuntimeException("connection refused")
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.connect("secret", "127.0.0.1", "4455")

        assertEquals(ConnectionState.Error("connection refused"), viewModel.uiState.value)
    }

    @Test
    fun `uiState follows the repository connection flow`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val connectedFlow = MutableStateFlow(false)
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns connectedFlow
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        connectedFlow.value = true
        advanceUntilIdle()

        assertEquals(ConnectionState.Connected, viewModel.uiState.value)
    }

    @Test
    fun `disconnect delegates to repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { disconnectFromObs() } just runs
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository())
        viewModel.disconnect()

        verify { repository.disconnectFromObs() }
    }

    @Test
    fun `savedUseTls follows the persisted settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
        }

        val viewModel = ObsControlViewModel(repository, settingsRepository(AppSettings(obsUseTls = true)))
        advanceUntilIdle()

        assertEquals(true, viewModel.savedUseTls.value)
    }
}
