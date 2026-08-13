package com.vivid.feature.obscontrol

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.model.ObsQrCodeData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsViewModelTest {

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads obs settings from repository into ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(obsHost = "192.168.1.5", obsPort = "4456", obsPassword = "obs-secret", obsUseTls = true),
            )
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        assertEquals("192.168.1.5", viewModel.uiState.value.host)
        assertEquals("4456", viewModel.uiState.value.port)
        assertEquals("obs-secret", viewModel.uiState.value.password)
        assertEquals(true, viewModel.uiState.value.useTls)
    }

    @Test
    fun `input changes update the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onHostChanged("obs.example.com")
        viewModel.onPortChanged("4460")
        viewModel.onPasswordChanged("new-secret")

        assertEquals("obs.example.com", viewModel.uiState.value.host)
        assertEquals("4460", viewModel.uiState.value.port)
        assertEquals("new-secret", viewModel.uiState.value.password)
    }

    @Test
    fun `saveObsSettings persists the current values`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onHostChanged("obs.example.com")
        viewModel.onPortChanged("4460")
        viewModel.onPasswordChanged("new-secret")
        viewModel.onUseTlsChanged(true)

        viewModel.saveObsSettings()
        advanceUntilIdle()

        coVerify { repository.updateObsSettings("obs.example.com", "4460", "new-secret", true) }
    }

    @Test
    fun `importFromQrCode applies valid obsws data to the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        val result = viewModel.importFromQrCode("obsws://192.168.1.50:4456/obs-secret")

        assertEquals("192.168.1.50", viewModel.uiState.value.host)
        assertEquals("4456", viewModel.uiState.value.port)
        assertEquals("obs-secret", viewModel.uiState.value.password)
        assertTrue(result is QrImportResult.Success)
        assertEquals(ObsQrCodeData("192.168.1.50", 4456, "obs-secret"), (result as QrImportResult.Success).data)
    }

    @Test
    fun `importFromQrCode decodes percent-encoded password`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        val result = viewModel.importFromQrCode("obsws://obs.local:4455/pa%20ss%2Fwort")

        assertTrue(result is QrImportResult.Success)
        assertEquals("pa ss/wort", viewModel.uiState.value.password)
    }

    @Test
    fun `importFromQrCode keeps existing values on invalid input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings(obsHost = "keep.me", obsPort = "4455"))
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        val result = viewModel.importFromQrCode("kein gültiger qr code")

        assertTrue(result is QrImportResult.Error)
        assertTrue((result as QrImportResult.Error).message.isNotBlank())
        // Bestehende Werte bleiben unverändert
        assertEquals("keep.me", viewModel.uiState.value.host)
    }

    @Test
    fun `importFromQrCode accepts legacy obswebsocket format`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()

        val result = viewModel.importFromQrCode("obswebsocket|[192.168.1.7]:[4455]|[oldpw]")

        assertTrue(result is QrImportResult.Success)
        assertEquals("192.168.1.7", viewModel.uiState.value.host)
        assertEquals("4455", viewModel.uiState.value.port)
        assertEquals("oldpw", viewModel.uiState.value.password)
    }

    @Test
    fun `saving flag is reset after the repository re-emits the saved settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settingsFlow = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns settingsFlow
            // Note: use literals instead of firstArg()/secondArg()/thirdArg() here —
            // mockk 1.14.9 throws inside `coEvery { } answers { }` for those matchers.
            coEvery { updateObsSettings(any(), any(), any(), any()) } answers {
                // Simulate the DataStore re-emitting the freshly saved settings.
                settingsFlow.value = AppSettings(obsHost = "saved-host", obsPort = "4456", obsPassword = "saved-pw")
            }
        }

        val viewModel = SettingsViewModel(repository)
        advanceUntilIdle()
        assertEquals(false, viewModel.uiState.value.isSaving)

        viewModel.saveObsSettings()
        advanceUntilIdle()

        // The re-emitted settings cleared the saving flag and updated the state.
        assertEquals(false, viewModel.uiState.value.isSaving)
        assertEquals("saved-host", viewModel.uiState.value.host)
    }
}
