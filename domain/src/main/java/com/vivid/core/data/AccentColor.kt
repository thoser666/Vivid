package com.vivid.core.data

/**
 * Kuratierte Akzentfarben (Settings-Kategorie „Darstellung“, PARITY-Zusatz
 * „UI-Farbschemata“ — Stufe 2). Jede Option ist ein HCT-Seed, aus dem die
 * Material-3-Palette (TonalSpot, Chroma 40) generiert wird; die konkreten
 * Scheme-Farben liegen in `app/.../ui/theme/Theme.kt` (Key = Enum-Name).
 *
 * [seedHex] ist der reine Anzeige-/Swatch-Wert (sRGB-Hex ohne Alpha) — er
 * dient dem Farb-Kreis in den Settings und der Dokumentation.
 */
enum class AccentColor(val seedHex: String) {
    VIVID_GREEN("#3DDC84"),
    OCEAN_BLUE("#42A5F5"),
    ROYAL_PURPLE("#9575CD"),
    SUNSET_ORANGE("#FFA726"),
    ROSE_PINK("#EC407A"),
    TEAL("#26A69A"),
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [VIVID_GREEN]). */
        fun fromName(name: String?): AccentColor =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: VIVID_GREEN
    }
}
