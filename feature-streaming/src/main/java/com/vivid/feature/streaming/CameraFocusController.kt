package com.vivid.feature.streaming

/**
 * Steuert den Fokusmodus der Streaming-Kamera (Moblin [#377](https://github.com/eerimoq/moblin/issues/377), „Focus Lock").
 *
 * Hält den aktuellen [FocusMode] und wendet ihn über die [FocusableCamera]-Abstraktion
 * auf die Kamera an. Der Modus wechselt nur, wenn die Kamera die Operation
 * tatsächlich bestätigt — schlägt das Kamera-API fehl (z. B. Hardware ohne
 * Fokussteuerung), bleibt der vorherige Modus erhalten.
 */
class CameraFocusController(
    private val camera: FocusableCamera,
) {
    var mode: FocusMode = FocusMode.AUTO
        private set

    /** true, wenn der Fokus aktuell gelockt (nicht AUTO) ist. */
    val isFocusLocked: Boolean get() = mode != FocusMode.AUTO

    /**
     * Schaltet zwischen Autofokus und Fokus-Lock (Unendlich) um.
     *
     * @return true, wenn der neue Modus übernommen wurde.
     */
    fun toggleFocusLock(): Boolean {
        val newMode = if (mode == FocusMode.AUTO) FocusMode.LOCKED_INFINITY else FocusMode.AUTO
        if (apply(newMode)) {
            mode = newMode
            return true
        }
        return false
    }

    /**
     * Wendet [newMode] auf die Kamera an.
     *
     * @return true, wenn die Kamera den Modus übernommen hat.
     */
    fun apply(newMode: FocusMode): Boolean = when (newMode) {
        FocusMode.AUTO -> camera.enableAutoFocus()
        FocusMode.LOCKED_INFINITY -> {
            // AF deaktivieren (sonst übernimmt er den Fokus wieder) und die
            // Distanz auf Unendlich stellen (0 Dioptrien). Die Distanz wird nur
            // gesetzt, wenn das Deaktivieren tatsächlich geklappt hat.
            val disabled = camera.disableAutoFocus()
            if (disabled) {
                camera.setFocusDistance(FOCUS_DISTANCE_INFINITY)
            }
            disabled
        }
    }

    companion object {
        /** Fokus-Distanz in Dioptrien: 0 = Unendlich. */
        const val FOCUS_DISTANCE_INFINITY = 0f
    }
}
