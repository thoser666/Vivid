package com.vivid.feature.obscontrol

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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class ObsControlViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state is Disconnected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
        }

        val viewModel = ObsControlViewModel(repository)

        assertEquals(ConnectionState.Disconnected, viewModel.uiState.value)
    }

    @Test
    fun `connect with valid port delegates to repository and shows Connecting`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { connectToObs("secret", "127.0.0.1", 4455) } just runs
        }

        val viewModel = ObsControlViewModel(repository)
        viewModel.connect("secret", "127.0.0.1", "4455")

        assertEquals(ConnectionState.Connecting, viewModel.uiState.value)
        verify { repository.connectToObs("secret", "127.0.0.1", 4455) }
    }

    @Test
    fun `connect with invalid port sets error state and does not connect`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
        }

        val viewModel = ObsControlViewModel(repository)
        viewModel.connect("secret", "127.0.0.1", "not-a-port")

        assertEquals(ConnectionState.Error("Invalid port number"), viewModel.uiState.value)
        verify(exactly = 0) { repository.connectToObs(any(), any(), any()) }
    }

    @Test
    fun `connect propagates repository exceptions as error state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(false)
            every { connectToObs("secret", "127.0.0.1", 4455) } throws RuntimeException("connection refused")
        }

        val viewModel = ObsControlViewModel(repository)
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

        val viewModel = ObsControlViewModel(repository)
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

        val viewModel = ObsControlViewModel(repository)
        viewModel.disconnect()

        verify { repository.disconnectFromObs() }
    }
}
