package com.vivid.feature.settings.ui

import androidx.lifecycle.viewModelScope
import com.vivid.core.data.*
import com.vivid.feature.streaming.*
import com.vivid.feature.streaming.source.VideoSourceKind
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsCameraControlsViewModelTest {
    @Test fun `saved controls reach engine and atomic edits preserve the other camera presets`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val saved = MutableStateFlow(AppSettings())
        val version = MutableStateFlow(0)
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns saved
            coEvery { updateCameraControls(any()) } coAnswers {
                val update = firstArg<(CameraControlSettings) -> CameraControlSettings>()
                saved.value = saved.value.copy(cameraControls = update(saved.value.cameraControls))
            }
        }
        val engine = mockk<StreamingEngine>(relaxed = true) {
            every { cameraSelectionVersion } returns version
            every { cameraModeStatus } returns MutableStateFlow(CameraModeStatus())
            every { activeSourceKind } returns MutableStateFlow(VideoSourceKind.CAMERA)
            every { supportedStabilizationModes() } returns StabilizationMode.entries.toSet()
        }
        val model = SettingsCameraControlsViewModel(repository, engine)
        val subscriber = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.uiState.collect {} }
        try {
            advanceUntilIdle()
            model.update { it.copy(frontZoomPresets = listOf(1f, 3f)) }
            model.update { it.copy(zoomSpeed = 2f) }
            advanceUntilIdle()
            assertEquals(listOf(1f, 3f), saved.value.cameraControls.frontZoomPresets)
            assertEquals(listOf(1f, 1.5f, 2f), saved.value.cameraControls.rearZoomPresets)
            verify { engine.configureCameraControls(saved.value.cameraControls) }
            assertEquals(StabilizationMode.entries.toSet(), model.uiState.value.stabilizationModes)
            every { engine.supportedStabilizationModes() } returns setOf(StabilizationMode.OFF)
            version.value++
            advanceUntilIdle()
            assertEquals(setOf(StabilizationMode.OFF), model.uiState.value.stabilizationModes)
        } finally {
            subscriber.cancel()
            model.viewModelScope.cancel()
            advanceUntilIdle()
            Dispatchers.resetMain()
        }
    }
}
