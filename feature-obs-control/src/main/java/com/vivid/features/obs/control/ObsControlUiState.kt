// Datei: src/.../com/vivid/features/obs/control/ObsControlUiState.kt

package com.vivid.features.obs.control

/**
 * Definiert alle möglichen Zustände, in denen sich der ObsControlScreen befinden kann.
 * Dies ist eine "Sealed Interface", die es dem Compiler ermöglicht, in `when`-Blöcken
 * zu prüfen, ob alle Fälle abgedeckt sind.
 */
sealed interface ObsControlUiState {
    data object Idle : ObsControlUiState
    data object Connecting : ObsControlUiState
    data object Connected : ObsControlUiState
    data class Error(val message: String) : ObsControlUiState
}