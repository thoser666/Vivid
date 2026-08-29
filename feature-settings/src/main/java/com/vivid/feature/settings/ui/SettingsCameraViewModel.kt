package com.vivid.feature.settings.ui

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * ViewModel für die manuelle Kamera-Steuerung.
 *
 * Ermöglicht dem Nutzer:
 * - Den Fokusabstand manuell einzustellen (0.0 = Unendlich, höhere Werte = näher)
 * - Zwischen verfügbaren Kameras/Linsen zu wechseln
 *
 * Die tatsächliche Kamera-Steuerung erfolgt über die StreamingEngine,
 * dieses ViewModel verwaltet nur die UI-StateFlows.
 */
@HiltViewModel
class SettingsCameraViewModel @Inject constructor() : ViewModel() {

    // --- Manual Focus ---

    private val _focusDistance = MutableStateFlow(0.0f)
    val focusDistance: StateFlow<Float> = _focusDistance.asStateFlow()

    private val _hasManualFocus = MutableStateFlow(false)
    val hasManualFocus: StateFlow<Boolean> = _hasManualFocus.asStateFlow()

    /** Fokusabstand setzen (0.0 = Unendlich, höhere Werte = näher). */
    fun setFocusDistance(distance: Float) {
        _focusDistance.value = distance.coerceIn(0f, 10f)
    }

    // --- Lens Selection ---

    data class LensUiState(
        val id: String,
        val displayName: String,
        val isActive: Boolean,
    )

    private val _availableLenses = MutableStateFlow<List<LensUiState>>(emptyList())
    val availableLenses: StateFlow<List<LensUiState>> = _availableLenses.asStateFlow()

    private val _currentLensId = MutableStateFlow("")
    val currentLensId: StateFlow<String> = _currentLensId.asStateFlow()

    /** Linse auswählen. */
    fun selectLens(lensId: String) {
        _currentLensId.value = lensId
        _availableLenses.value = _availableLenses.value.map {
            it.copy(isActive = it.id == lensId)
        }
    }

    // --- Update from Engine ---

    /**
     * Aktualisiert den internen State mit Werten aus der StreamingEngine.
     * Wird aufgerufen, wenn sich der Kamera-Zustand ändert.
     */
    fun updateFromEngine(
        focusDistance: Float,
        hasManualFocus: Boolean,
        availableLenses: List<Triple<String, String, Boolean>>, // id, name, isActive
        currentLensId: String,
    ) {
        _focusDistance.value = focusDistance
        _hasManualFocus.value = hasManualFocus
        _availableLenses.value = availableLenses.map { (id, name, isActive) ->
            LensUiState(id = id, displayName = name, isActive = isActive)
        }
        _currentLensId.value = currentLensId
    }
}
