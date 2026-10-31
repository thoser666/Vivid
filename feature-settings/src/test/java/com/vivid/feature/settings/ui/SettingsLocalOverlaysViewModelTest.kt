package com.vivid.feature.settings.ui

import androidx.lifecycle.viewModelScope
import com.vivid.core.data.*
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsLocalOverlaysViewModelTest {
    @Test fun `rapid independent toggles preserve earlier edits and wait for loading`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = MutableStateFlow(AppSettings())
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns saved
            coEvery { updateLocalOverlays(any()) } coAnswers {
                val update = firstArg<(LocalOverlaySettings) -> LocalOverlaySettings>()
                saved.value = saved.value.copy(localOverlays = update(saved.value.localOverlays))
            }
        }
        val vm = SettingsLocalOverlaysViewModel(repository)
        try {
            vm.update { it.copy(showZoom = false) }
            coVerify(exactly = 0) { repository.updateLocalOverlays(any()) }
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.settings.collect {} }
            advanceUntilIdle()
            vm.update { it.copy(showZoom = false) }
            vm.update { it.copy(showFps = false) }
            vm.update { it.copy(showDuration = false) }
            vm.update { it.copy(showScenes = false) }
            vm.update { it.copy(showBitrate = false) }
            advanceUntilIdle()
            assertEquals(LocalOverlaySettings(false, false, false, false, false), vm.settings.value)
        } finally {
            vm.viewModelScope.cancel()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
}
