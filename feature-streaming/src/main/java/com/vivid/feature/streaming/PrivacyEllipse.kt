package com.vivid.feature.streaming

/**
 * Eine Ellipsen-Zone der Anonymisierung in **normalisierten Textur-Koordinaten**
 * (0..1). P0: nur Modell + Validierung — Produzenten (Zonen-Repository P1,
 * Face-Analyzer P2) füllen sie später.
 */
data class PrivacyEllipse(
    val centerX: Float,
    val centerY: Float,
    val radiusX: Float,
    val radiusY: Float,
) {
    init {
        require(centerX in 0f..1f && centerY in 0f..1f) {
            "Zentrum außerhalb 0..1: $centerX/$centerY"
        }
        require(radiusX in 0f..1f && radiusY in 0f..1f) {
            "Radius außerhalb 0..1: $radiusX/$radiusY"
        }
    }

    companion object {
        /** Deaktivierter Slot (Radius 0 — der Shader maskiert dann nicht). */
        val DISABLED = PrivacyEllipse(centerX = 0f, centerY = 0f, radiusX = 0f, radiusY = 0f)
    }
}
