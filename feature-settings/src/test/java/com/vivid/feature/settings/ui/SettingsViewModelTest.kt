package com.vivid.feature.settings.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings from repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(
                    streamUrl = "rtmp://live.example/app",
                    streamKey = "key-1",
                    obsHost = "192.168.1.5",
                    obsPort = "4456",
                    obsPassword = "obs-secret",
                    obsUseTls = true,
                ),
            )
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        assertEquals("rtmp://live.example/app", viewModel.uiState.value.streamUrl)
        assertEquals("key-1", viewModel.uiState.value.streamKey)
        assertEquals("192.168.1.5", viewModel.uiState.value.obsHost)
        assertEquals("4456", viewModel.uiState.value.obsPort)
        assertEquals("obs-secret", viewModel.uiState.value.obsPassword)
        assertEquals(true, viewModel.uiState.value.obsUseTls)
    }

    @Test
    fun `input changes update the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onStreamUrlChange("rtmp://new/app")
        viewModel.onStreamKeyChange("new-key")
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")
        viewModel.onObsUseTlsChange(true)

        assertEquals("rtmp://new/app", viewModel.uiState.value.streamUrl)
        assertEquals("new-key", viewModel.uiState.value.streamKey)
        assertEquals("obs.example.com", viewModel.uiState.value.obsHost)
        assertEquals("4455", viewModel.uiState.value.obsPort)
        assertEquals("pw", viewModel.uiState.value.obsPassword)
        assertEquals(true, viewModel.uiState.value.obsUseTls)
    }

    @Test
    fun `saveSettings persists stream and obs settings and emits the save event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onStreamUrlChange("rtmp://live/app")
        viewModel.onStreamKeyChange("key-9")
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")

        val events = mutableListOf<Unit>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveEvent.collect { events.add(it) }
        }

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { repository.updateStreamSettings("rtmp://live/app", "key-9") }
        coVerify { repository.updateObsSettings("obs.example.com", "4455", "pw", false) }
        assertEquals(1, events.size)
        collector.cancel()
    }
}
