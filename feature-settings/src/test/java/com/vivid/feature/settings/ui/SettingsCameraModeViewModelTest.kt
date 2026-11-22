package com.vivid.feature.settings.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.CameraMode
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.streaming.CameraModeStatus
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCameraModeViewModelTest {
    @Test fun `selection persists only when idle and availability follows the selected lens`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        var model: SettingsCameraModeViewModel? = null
        var subscriber: Job? = null
        try {
            val saved = MutableStateFlow(AppSettings())
            val status = MutableStateFlow(CameraModeStatus())
            val lens = MutableStateFlow(0)
            var allowed = true
            var supported = true
            val repository = mockk<SettingsRepository> {
                every { appSettingsFlow } returns saved
                coEvery { updateCameraMode(any()) } coAnswers { saved.value = saved.value.copy(cameraMode = firstArg()) }
            }
            val engine = mockk<StreamingEngine> {
                every { cameraModeStatus } returns status
                every { streamingState } returns MutableStateFlow<StreamingState>(StreamingState.Idle)
                every { replayState } returns MutableStateFlow<ReplayState>(ReplayState.Idle)
                every { cameraSelectionVersion } returns lens
                every { measuredEncoderFps } returns MutableStateFlow(null)
                every { canChangeCameraMode() } answers { allowed }
                every { supportsExperimentalCamera(any(), any()) } answers { supported }
                every { configureCameraMode(any()) } answers { allowed }
            }
            val activeModel = SettingsCameraModeViewModel(repository, engine)
            model = activeModel
            subscriber = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { activeModel.uiState.collect {} }
            advanceUntilIdle()
            activeModel.selectMode(CameraMode.EXPERIMENTAL_60_FPS)
            advanceUntilIdle()
            assertEquals(CameraMode.EXPERIMENTAL_60_FPS, saved.value.cameraMode)
            allowed = false
            status.value = CameraModeStatus(preparing = true)
            advanceUntilIdle()
            assertTrue(activeModel.uiState.value.locked)
            activeModel.selectMode(CameraMode.STANDARD)
            coVerify(exactly = 0) { repository.updateCameraMode(CameraMode.STANDARD) }
            allowed = true
            supported = false
            status.value = CameraModeStatus()
            lens.value++
            advanceUntilIdle()
            assertFalse(activeModel.uiState.value.experimentalSupported)
            activeModel.selectMode(CameraMode.EXPERIMENTAL_60_FPS)
            coVerify(exactly = 1) { repository.updateCameraMode(CameraMode.EXPERIMENTAL_60_FPS) }
        } finally {
            subscriber?.cancel()
            model?.viewModelScope?.cancel()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
}
