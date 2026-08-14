package com.vivid.feature.streaming

/**
 * Steuert die Video-Stabilisierung der Streaming-Kamera.
 *
 * Optische Stabilisierung (OIS) wird bevorzugt, wenn die Kamera sie unterstützt,
 * sonst fällt sie auf die digitale Stabilisierung (EIS) zurück. Der Zustand
 * wechselt nur, wenn die Kamera die Operation tatsächlich bestätigt — schlägt
 * das Kamera-API fehl, bleibt der vorherige Zustand erhalten.
 */
class CameraStabilizationController(
    private val camera: CameraControls,
) {
    /** Aktueller Zustand, initial aus der Kamera gelesen. */
    var isEnabled: Boolean = camera.isStabilizationEnabled()
        private set

    /**
     * Schaltet die Stabilisierung um.
     *
     * @return true, wenn der neue Zustand von der Kamera übernommen wurde.
     */
    fun toggle(): Boolean {
        val target = !isEnabled
        if (apply(target)) {
            isEnabled = target
            return true
        }
        return false
    }

    private fun apply(enabled: Boolean): Boolean =
        if (enabled) camera.enableStabilization() else camera.disableStabilization()
}
