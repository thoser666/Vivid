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

    // --- Belichtung und Weißabgleich ---

    private val _exposure = MutableStateFlow(0)
    val exposure: StateFlow<Int> = _exposure.asStateFlow()

    private val _exposureRange = MutableStateFlow<IntRange?>(null)
    val exposureRange: StateFlow<IntRange?> = _exposureRange.asStateFlow()

    private val _autoExposureEnabled = MutableStateFlow(true)
    val autoExposureEnabled: StateFlow<Boolean> = _autoExposureEnabled.asStateFlow()

    private val _autoWhiteBalanceEnabled = MutableStateFlow(true)
    val autoWhiteBalanceEnabled: StateFlow<Boolean> = _autoWhiteBalanceEnabled.asStateFlow()

    /** Setzt die Belichtungsstufe, wenn die Kamera den Wert unterstützt. */
    fun setExposure(value: Int): Boolean {
        if (!controls.setExposure(value)) return false
        _exposure.value = value
        return true
    }

    /** Schaltet die automatische Belichtung aus oder wieder ein. */
    fun setAutoExposure(enabled: Boolean): Boolean {
        val changed = if (enabled) controls.enableAutoExposure() else controls.disableAutoExposure()
        if (changed) _autoExposureEnabled.value = enabled
        return changed
    }

    /** Schaltet den automatischen Weißabgleich aus oder wieder ein. */
    fun setAutoWhiteBalance(enabled: Boolean): Boolean {
        val changed = if (enabled) controls.enableAutoWhiteBalance() else controls.disableAutoWhiteBalance()
        if (changed) _autoWhiteBalanceEnabled.value = enabled
        return changed
    }

    /** true, wenn ISO auf dem aktiven Kamera-Backend separat steuerbar ist. */
    fun hasIsoControl(): Boolean = false

    /** true, wenn EV als separater Parameter verfügbar ist; Belichtung nutzt stattdessen [exposureRange]. */
    fun hasEvControl(): Boolean = controls.hasExposureControl()

    /** true, wenn der automatische Weißabgleich über die Kamera steuerbar ist. */
    fun hasWhiteBalanceControl(): Boolean = controls.hasWhiteBalanceControl()

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
        _exposureRange.value = controls.getExposureRange()
        _exposure.value = controls.getExposure()
        _autoExposureEnabled.value = controls.isAutoExposureEnabled()
        _autoWhiteBalanceEnabled.value = controls.isAutoWhiteBalanceEnabled()
    }
}
