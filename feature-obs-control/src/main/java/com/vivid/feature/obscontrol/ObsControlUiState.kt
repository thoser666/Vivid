package com.vivid.feature.obscontrol

sealed interface ObsControlUiState {
    data object Idle : ObsControlUiState
    data object Connecting : ObsControlUiState
    data object Connected : ObsControlUiState
    data class Error(val message: String) : ObsControlUiState
}