package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View

/** Unterstützter Zoom-Bereich der Kamera (1.0 = ungezoomt). */
data class ZoomRange(val min: Float, val max: Float)

/**
 * Abstraktion über die Kamera-Kontrollen der Streaming-Kamera (RootEncoder).
 *
 * Exponiert Tap-to-Focus, Pinch-Zoom und Stabilisierung ohne direkte Abhängigkeit
 * von [com.pedro.library.base.Camera2Base] — dadurch in reinen Unit-Tests ohne
 * echte Kamera bzw. Android-Framework-Klassen prüfbar.
 */
interface CameraControls {
    /** Aktueller Zoom-Faktor (≥ 1.0). */
    fun getZoom(): Float

    /** Unterstützter Zoom-Bereich oder null, wenn die Kamera keinen Zoom bietet. */
    fun getZoomRange(): ZoomRange?

    /** Setzt den Zoom-Faktor absolut. */
    fun setZoom(value: Float)

    /** Fokussiert auf die Stelle des [event] (Tap-to-Focus). */
    fun tapToFocus(view: View, event: MotionEvent)

    /** true, wenn die Kamera optische Bildstabilisierung (OIS) unterstützt. */
    fun hasOpticalStabilization(): Boolean

    /** true, wenn (optische oder digitale) Stabilisierung aktiv ist. */
    fun isStabilizationEnabled(): Boolean

    /** Aktiviert Stabilisierung (optisch bevorzugt, sonst digital). */
    fun enableStabilization(): Boolean

    /** Deaktiviert optische und digitale Stabilisierung. */
    fun disableStabilization(): Boolean

    /** true, wenn das Gerät eine Taschenlampe (Lantern/Torch) unterstützt. */
    fun hasTorch(): Boolean

    /** true, wenn die Taschenlampe aktuell eingeschaltet ist. */
    fun isTorchEnabled(): Boolean

    /** Schaltet die Taschenlampe ein. */
    fun enableTorch(): Boolean

    /** Schaltet die Taschenlampe aus. */
    fun disableTorch(): Boolean

    // --- Manuelle Kamera-Steuerung ---

    /** true, wenn der manuelle Fokusabstand unterstützt wird. */
    fun hasManualFocus(): Boolean

    /** Aktueller Fokusabstand (0.0 = Unendlich, höhere Werte = näher). */
    fun getFocusDistance(): Float

    /** Setzt den Fokusabstand (0.0 = Unendlich, höhere Werte = näher). */
    fun setFocusDistance(distance: Float)

    /** Verfügbare Kamera-IDs (z.B. Rückkamera(s), Frontkamera). */
    fun getAvailableCameraIds(): List<String>

    /** Aktuelle Kamera-ID. */
    fun getCurrentCameraId(): String

    /** Wechselt auf die Kamera mit der angegebenen ID. */
    fun selectCamera(cameraId: String): Boolean
}
