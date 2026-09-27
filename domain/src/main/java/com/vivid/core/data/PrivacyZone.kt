package com.vivid.core.data

import kotlinx.serialization.Serializable

/**
 * Eine manuelle Anonymisierungs-Zone (Stufe 1 der Skizze
 * docs/architecture/privacy-anonymization.md): eine Ellipse in
 * **normalisierten Koordinaten 0..1**, quellrelativ zur Vorschau angegeben —
 * (0, 0) ist oben links, (1, 1) unten rechts. Ein Wechsel der Videoquelle
 * (Kamera/Screen-Capture/Replay) verschiebt die Zonen relativ zur Bildmitte
 * (P3 prüft eine Persistenz je Quelle).
 *
 * Das Modell lebt bewusst im Domain-Modul (`com.vivid.core.data`, wie
 * [StreamScene]): [com.vivid.core.data.ZoneRepository] serialisiert es als
 * JSON in die Preferences-DataStore, die UI und die Engine übernehmen die
 * Werte unverändert (Mapping zu den Textur-Koordinaten des Privacy-Shaders
 * passiert in feature-streaming).
 *
 * Serialisierbar (kotlinx.serialization, `encodeDefaults = true` im
 * Repository-Muster), damit neue Felder abwärtskompatibel ergänzt werden
 * können (decode mit `ignoreUnknownKeys`).
 */
@Serializable
data class PrivacyZone(
    /** Ellipsen-Zentrum X (0..1, quellrelativ, 0 = linker Bildrand). */
    val centerX: Float,
    /** Ellipsen-Zentrum Y (0..1, quellrelativ, 0 = oberer Bildrand). */
    val centerY: Float,
    /** Horizontaler Radius (0..1, Anteil der Bildbreite). */
    val radiusX: Float,
    /** Vertikaler Radius (0..1, Anteil der Bildhöhe). */
    val radiusY: Float,
) {
    init {
        require(centerX in 0f..1f && centerY in 0f..1f) {
            "Zonen-Zentrum außerhalb 0..1: $centerX/$centerY"
        }
        require(radiusX in 0f..1f && radiusY in 0f..1f) {
            "Zonen-Radius außerhalb 0..1: $radiusX/$radiusY"
        }
    }

    companion object {
        /** Max. manuelle Zonen (Skizze §5 P1; Shader trägt 8 Slots für Zonen + Gesichter). */
        const val MAX_ZONES = 4

        /**
         * Verschiebt eine Koordinate relativ zur Bildmitte (0,5): [delta] ist
         * die Änderung in normalisierten Einheiten. Nur bei Drag/Resize der
         * Zonen sinnvoll — hier zentral, damit Editor und Tests dieselbe
         * Semantik verwenden.
         */
        fun shift(value: Float, delta: Float): Float = (value + delta).coerceIn(0f, 1f)
    }
}
