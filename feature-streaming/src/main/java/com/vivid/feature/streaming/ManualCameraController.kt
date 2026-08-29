package com.vivid.feature.streaming

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Steuert die manuellen Kamera-Einstellungen (Fokus, Linse).
 *
 * Ermöglicht dem Nutzer, den Fokusabstand manuell einzustellen
 * und zwischen verfügbaren Kameras/Linsen zu wechseln.
 * Aktualisiert die UI-StateFlows für eine reaktive Darstellung
 * im Settings-Screen.
 */
class ManualCameraController(
    private val controls: CameraControls,
    private val lensController: CameraLensController,
) {
    // --- Manual Focus ---

    private val _focusDistance = MutableStateFlow(0.0f)
    val focusDistance: StateFlow<Float> = _focusDistance.asStateFlow()

    /** Setzt den manuellen Fokusabstand (0.0 = Unendlich, höhere Werte = näher). */
    fun setFocusDistance(distance: Float) {
        controls.setFocusDistance(distance)
        _focusDistance.value = distance
    }

    // --- Lens Selection ---

    private val _currentLens = MutableStateFlow(CameraLensController.LensType.WIDE)
    val currentLens: StateFlow<CameraLensController.LensType> = _currentLens.asStateFlow()

    /** Wechselt auf die angegebene Linse. */
    fun selectLens(lensId: String): Boolean {
        val success = lensController.selectLens(lensId)
        if (success) {
            _currentLens.value = lensController.getCurrentLens()
        }
        return success
    }

    // --- Capability Queries ---

    /** true, wenn die Kamera manuellen Fokus unterstützt. */
    fun hasManualFocus(): Boolean = controls.hasManualFocus()

    /** Verfügbare Linsen. */
    fun getAvailableLenses(): List<LensInfo> = lensController.getAvailableLenses()

    // --- Synchronize Initial State ---

    /** Synchronisiert die internen States mit dem tatsächlichen Kamera-Zustand. */
    fun syncState() {
        _focusDistance.value = controls.getFocusDistance()
        _currentLens.value = lensController.getCurrentLens()
    }
}
