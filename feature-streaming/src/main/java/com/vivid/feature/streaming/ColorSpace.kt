package com.vivid.feature.streaming

import androidx.annotation.StringRes
import com.vivid.feature.streaming.R

/**
 * Unterstützte Color-Spaces für die Stream-Pipeline.
 *
 * Die Auswahl bestimmt den Gamma-Korrektur-Modus des 3D-LUT-Filters:
 * - sRGB: Standard-Gamma (2.2) — der Normalfall für die meisten Streams
 * - Display P3: breiterer Farbraum (DCI-P3 Primaries + sRGB Gamma) — iPhones, Macs
 * - Apple Log: logarithmisches Encodierung — für professionelle Post-Production
 *
 * Moblin-Parität: „Color-Spaces (sRGB/P3/Log) + 3D-LUTs"
 */
enum class ColorSpace(
    /** String-Ressource für den Anzeigenamen im UI (i18n de/en/fr). */
    @StringRes val labelRes: Int,
    /** Eindeutiger interner Name (für Persistenz und Bot-Kommunikation). */
    val id: String,
    /** Gamma-Wert für den 3D-LUT-Decoder (1.0 = Linear, 2.2 = sRGB-Gamma). */
    val gamma: Float,
) {
    SRGB(R.string.color_space_srgb, "srgb", 2.2f),
    DISPLAY_P3(R.string.color_space_p3, "p3", 2.2f),
    APPLE_LOG(R.string.color_space_apple_log, "apple_log", 1.0f),
}
