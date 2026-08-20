package com.vivid.core.data

/**
 * Design-Modus der App (Settings-Kategorie „Darstellung“, PARITY-Zusatz
 * „UI-Farbschemata“ — Stufe 2).
 *
 * - [SYSTEM]: folgt dem System-Dark-Mode (`isSystemInDarkTheme`), wie bisher.
 * - [LIGHT]: immer hell.
 * - [DARK]: immer dunkel.
 * - [AMOLED]: immer dunkel mit rein-schwarzen Flächen (spart auf OLED-
 *   Displays Energie und maximiert den Kontrast).
 */
enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [SYSTEM]). */
        fun fromName(name: String?): ThemeMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Reine Entscheidungslogik (testbar): ob der Dunkel-Modus aktiv ist.
 *
 * [AMOLED] ist wie [DARK] ein Dunkel-Modus — der Unterschied (schwarze
 * Flächen) wird beim Aufbau des ColorSchemes entschieden, nicht hier.
 */
fun ThemeMode.resolveDark(systemDark: Boolean): Boolean = when (this) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK, ThemeMode.AMOLED -> true
}
