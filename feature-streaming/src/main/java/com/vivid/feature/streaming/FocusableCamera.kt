package com.vivid.feature.streaming

/**
 * Abstraktion über das RootEncoder-Kamera-API, das nur die Fokus-Steuerung
 * exponiert. Dadurch ist [CameraFocusController] ohne echte Kamera bzw.
 * Android-Klassen unit-testbar.
 */
interface FocusableCamera {
    /** Aktiviert den Autofokus; false, wenn die Kamera das ablehnt. */
    fun enableAutoFocus(): Boolean

    /** Deaktiviert den Autofokus; false, wenn die Kamera das ablehnt. */
    fun disableAutoFocus(): Boolean

    /** true, wenn der Autofokus aktiv ist. */
    fun isAutoFocusEnabled(): Boolean

    /** Setzt die Fokus-Distanz in Dioptrien (0 = Unendlich). */
    fun setFocusDistance(distance: Float)
}
