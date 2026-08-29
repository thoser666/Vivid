package com.vivid.feature.streaming

/**
 * Steuert die Auswahl der Kamera-Linse (Rückkamera: Ultraweit/Weit/Tele).
 *
 * Nutzt die [CameraControls]-Schnittstelle, um verfügbare Kameras abzufragen
 * und zwischen ihnen zu wechseln. Funktioniert auch in Unit-Tests ohne
 * echte Kamera.
 */
class CameraLensController(
    private val controls: CameraControls,
) {
    /** Enum für die verfügbaren Linsen-Typen. */
    enum class LensType(val displayName: String) {
        ULTRA_WIDE("Ultraweit"),
        WIDE("Weit"),
        TELE("Tele"),
        UNKNOWN("Unbekannt")
    }

    /** Aktuell ausgewählte Linse. */
    private var currentLens: LensType = LensType.WIDE

    /**
     * Gibt die verfügbaren Kameras als Liste zurück.
     * Jeder Eintrag enthält die Kamera-ID und einen angezeigten Namen.
     */
    fun getAvailableLenses(): List<LensInfo> {
        val cameraIds = controls.getAvailableCameraIds()
        return cameraIds.mapIndexed { index, id ->
            LensInfo(
                id = id,
                type = guessLensType(id, index),
                isActive = id == controls.getCurrentCameraId()
            )
        }
    }

    /**
     * Wechselt auf die Linse mit der angegebenen ID.
     *
     * @return true, wenn der Wechsel erfolgreich war.
     */
    fun selectLens(lensId: String): Boolean {
        val success = controls.selectCamera(lensId)
        if (success) {
            currentLens = getAvailableLenses()
                .firstOrNull { it.id == lensId }?.type
                ?: LensType.UNKNOWN
        }
        return success
    }

    /** Aktueller Linsen-Typ. */
    fun getCurrentLens(): LensType = currentLens

    /**
     * Versucht, die Linsen-Typen anhand der Kamera-ID zu erraten.
     * Dies ist eine Heuristik — die tatsächliche Kamera-Konfiguration
     * hängt vom Gerät ab.
     */
    private fun guessLensType(cameraId: String, index: Int): LensType {
        // Standard-Heuristik für die meisten Android-Geräte:
        // - Kamera 0 = Rückseite Weit (Standard)
        // - Kamera 1 = Rückseite Ultraweit (wenn vorhanden)
        // - Kamera 2 = Rückseite Tele (wenn vorhanden)
        // - Frontkamera hat typischerweise höhere IDs
        return when {
            cameraId.contains("ultra") || cameraId.contains("wide_angle") ->
                LensType.ULTRA_WIDE
            cameraId.contains("tele") || cameraId.contains("zoom") ->
                LensType.TELE
            index == 0 -> LensType.WIDE
            index == 1 -> LensType.ULTRA_WIDE
            index == 2 -> LensType.TELE
            else -> LensType.UNKNOWN
        }
    }
}

/**
 * Information über eine verfügbare Kamera-Linse.
 */
data class LensInfo(
    val id: String,
    val type: CameraLensController.LensType,
    val isActive: Boolean,
)
