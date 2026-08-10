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
                    streamUseTls = true,
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
        assertEquals(true, viewModel.uiState.value.streamUseTls)
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
        viewModel.onStreamUseTlsChange(true)
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")
        viewModel.onObsUseTlsChange(true)

        assertEquals("rtmp://new/app", viewModel.uiState.value.streamUrl)
        assertEquals("new-key", viewModel.uiState.value.streamKey)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
        assertEquals("obs.example.com", viewModel.uiState.value.obsHost)
        assertEquals("4455", viewModel.uiState.value.obsPort)
        assertEquals("pw", viewModel.uiState.value.obsPassword)
        assertEquals(true, viewModel.uiState.value.obsUseTls)
    }

    @Test
    fun `applying a platform preset fills the ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.applyPlatformPreset(StreamPlatform.Kick)

        assertEquals("rtmp://live.kick.com/app", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying the youtube preset fills the youtube ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.applyPlatformPreset(StreamPlatform.YouTube)

        assertEquals("rtmp://a.rtmp.youtube.com/live2", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying the twitch preset fills the twitch ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.applyPlatformPreset(StreamPlatform.Twitch)

        assertEquals("rtmp://live.twitch.tv/app", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying a preset keeps an already entered stream key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        viewModel.onStreamKeyChange("live_12345_secret")
        viewModel.applyPlatformPreset(StreamPlatform.Twitch)

        assertEquals("rtmp://live.twitch.tv/app", viewModel.uiState.value.streamUrl)
        assertEquals("live_12345_secret", viewModel.uiState.value.streamKey)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `saveSettings persists stream and obs settings and emits the save event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onStreamUrlChange("rtmp://live/app")
        viewModel.onStreamKeyChange("key-9")
        viewModel.onStreamUseTlsChange(true)
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")

        val events = mutableListOf<Unit>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveEvent.collect { events.add(it) }
        }

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { repository.updateStreamSettings("rtmp://live/app", "key-9", true) }
        coVerify { repository.updateObsSettings("obs.example.com", "4455", "pw", false) }
        assertEquals(1, events.size)
        collector.cancel()
    }
}
