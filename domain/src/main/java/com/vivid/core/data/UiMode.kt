package com.vivid.core.data

/**
 * Bedienmodus der App-Oberfläche.
 *
 * - [EASY]: reduzierte Oberfläche für den direkten Stream-Start.
 * - [STUDIO]: vollständige Oberfläche mit Szenen, Quellen und OBS-Steuerung.
 */
enum class UiMode {
    EASY,
    STUDIO,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [EASY]). */
        fun fromName(name: String?): UiMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: EASY
    }
}
