package com.vivid.feature.settings.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteControlServer
import com.vivid.core.remote.RemoteControlTokenStore
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    private fun repository() = mockk<SettingsRepository> {
        every { appSettingsFlow } returns MutableStateFlow(AppSettings())
    }

    private fun tokenStore(token: String = "test-token"): RemoteControlTokenStore = mockk {
        coEvery { getOrCreateToken() } returns token
    }

    private fun createViewModel(
        repository: SettingsRepository = repository(),
        checker: UpdateChecker = mockk(relaxed = true),
        tokenStore: RemoteControlTokenStore = tokenStore(),
        remoteControlServer: RemoteControlServer = mockk(relaxed = true),
    ) = SettingsViewModel(repository, checker, tokenStore, remoteControlServer)

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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

        val viewModel = createViewModel(repository)
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

    // --- Web-Remote-Control ---

    @Test
    fun `loads the remote control token and default port`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(tokenStore = tokenStore("abc-123"))
        advanceUntilIdle()

        assertEquals(RemoteControlServer.DEFAULT_PORT, viewModel.remoteControl.value.port)
        assertEquals("abc-123", viewModel.remoteControl.value.token)
    }

    @Test
    fun `restartRemoteControlServer stops and starts the server`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = mockk<RemoteControlServer> {
            coEvery { stop() } just runs
            coEvery { start() } just runs
        }
        val viewModel = createViewModel(remoteControlServer = server)
        advanceUntilIdle()

        viewModel.restartRemoteControlServer()
        advanceUntilIdle()

        coVerify(exactly = 1) { server.stop() }
        coVerify(exactly = 1) { server.start() }
    }

    // --- Update-Indikator (Obtainium-Test) ---

    @Test
    fun `checkForUpdates reports an available update`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check("0.2.0-nightly.93") } returns UpdateCheckResult.UpdateAvailable(
            latestVersion = "0.2.0-nightly.97",
            releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118",
        )
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        assertFalse(viewModel.updateState.value.checking)
        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.97", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118"),
            viewModel.updateState.value.result,
        )
    }

    @Test
    fun `checkForUpdates reports up to date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.UpToDate(latestVersion = "0.2.0-nightly.97")
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.97")
        advanceUntilIdle()

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), viewModel.updateState.value.result)
    }

    @Test
    fun `checkForUpdates maps errors without touching the settings state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.Error("Update-Check fehlgeschlagen: network down")
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        assertEquals(UpdateCheckResult.Error("Update-Check fehlgeschlagen: network down"), viewModel.updateState.value.result)
    }

    @Test
    fun `checkForUpdates ignores a blank version`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("")
        advanceUntilIdle()

        coVerify(exactly = 0) { checker.check(any()) }
        assertEquals(null, viewModel.updateState.value.result)
    }

    @Test
    fun `checkForUpdates only runs once per version`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.UpToDate("0.2.0-nightly.97")
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        coVerify(exactly = 1) { checker.check("0.2.0-nightly.93") }
    }

    @Test
    fun `checkForUpdates is ignored while a check is running`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<UpdateCheckResult>()
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle() // läuft bis zum gate
        assertTrue(viewModel.updateState.value.checking)

        viewModel.checkForUpdates("0.2.0-nightly.93") // muss ignoriert werden

        gate.complete(UpdateCheckResult.UpToDate("0.2.0-nightly.97"))
        advanceUntilIdle()
        assertFalse(viewModel.updateState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), viewModel.updateState.value.result)
    }
}
