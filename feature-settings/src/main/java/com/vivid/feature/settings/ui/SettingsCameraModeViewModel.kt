package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.CameraMode
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.streaming.CameraModeStatus
import com.vivid.feature.streaming.StreamingEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraModeUiState(
    val selected: CameraMode = CameraMode.STANDARD,
    val locked: Boolean = false,
    val experimentalSupported: Boolean = false,
    val status: CameraModeStatus = CameraModeStatus(),
    val measuredFps: Int? = null,
)

@HiltViewModel
class SettingsCameraModeViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val engine: StreamingEngine,
) : ViewModel() {
    val uiState: StateFlow<CameraModeUiState> = combine(
        repository.appSettingsFlow,
        combine(engine.cameraModeStatus, engine.streamingState, engine.replayState, engine.cameraSelectionVersion) { status, _, _, _ -> status },
        engine.measuredEncoderFps,
    ) { settings, status, fps ->
        CameraModeUiState(settings.cameraMode, !engine.canChangeCameraMode(),
            engine.supportsExperimentalCamera(settings.encoderPreset, settings.videoCodecPreference), status, fps)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraModeUiState())

    fun selectMode(mode: CameraMode) {
        val state = uiState.value
        if (state.locked || (mode == CameraMode.EXPERIMENTAL_60_FPS && !state.experimentalSupported)) return
        if (!engine.configureCameraMode(mode)) return
        viewModelScope.launch { repository.updateCameraMode(mode) }
    }
}
