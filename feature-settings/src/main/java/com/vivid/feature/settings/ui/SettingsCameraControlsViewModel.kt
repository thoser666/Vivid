package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vivid.core.data.CameraControlSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.StabilizationMode
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.source.VideoSourceKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CameraControlsUiState(
    val settings: CameraControlSettings = CameraControlSettings(),
    val loaded: Boolean = false,
    val stabilizationModes: Set<StabilizationMode> = setOf(StabilizationMode.OFF),
)

@HiltViewModel
class SettingsCameraControlsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val engine: StreamingEngine,
) : ViewModel() {
    val uiState = combine(repository.appSettingsFlow, engine.cameraSelectionVersion,
        engine.cameraModeStatus, engine.activeSourceKind) { settings, _, _, source ->
        CameraControlsUiState(settings.cameraControls, true,
            if (source == VideoSourceKind.CAMERA) engine.supportedStabilizationModes() else setOf(StabilizationMode.OFF))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CameraControlsUiState())

    init {
        viewModelScope.launch {
            repository.appSettingsFlow.map { it.cameraControls }.distinctUntilChanged().collect {
                engine.configureCameraControls(it)
            }
        }
    }

    fun update(change: (CameraControlSettings) -> CameraControlSettings) {
        if (!uiState.value.loaded) return
        viewModelScope.launch { repository.updateCameraControls(change) }
    }

}
