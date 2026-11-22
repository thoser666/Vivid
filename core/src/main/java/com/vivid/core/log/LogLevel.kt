package com.vivid.core.log

/**
 * Log-Stufe einer [LogEntry]. Die `priority`-Werte entsprechen den
 * Android-Log-Prioritäten (2=VERBOSE … 7=ASSERT), damit ein Timber-Tree die
 * Stufe direkt übernehmen kann.
 */
enum class LogLevel(val priority: Int) {
    VERBOSE(2),
    DEBUG(3),
    INFO(4),
    WARN(5),
    ERROR(6),
    ASSERT(7),
    ;

    companion object {
        /** Ordnet eine Android/Timber-Priorität (2–7) der Stufe zu. */
        fun fromPriority(priority: Int): LogLevel =
            entries.firstOrNull { it.priority == priority } ?: DEBUG
    }
}

/**
 * Null-toleranter Levelvergleich — `null` ist nie „mindestens [min]“.
 *
 * Der Receiver ist absichtlich `LogLevel?` typisiert: Einträge, die den
 * Kotlin-Konstruktor umgangen haben (Gson/Unsafe, siehe `LogLineDto`), tragen
 * zur Laufzeit `null`, obwohl das Feld statisch non-null ist. Der Vergleich
 * läuft über [LogLevel.priority] (Int) statt über `Comparable` —
 * `it.level >= LogLevel.ERROR` crashte mit `Enum.compareTo` (Sentry VIVID-5H,
 * #433, `SettingsLogsViewModel`-errorsOnly-Filter).
 */
fun LogLevel?.isAtLeast(min: LogLevel): Boolean =
    (this?.priority ?: Int.MIN_VALUE) >= min.priority

/**
 * Anzeigename der Stufe für Log-Zeilen und Export — null-tolerant.
 *
 * `null` (Gson/Unsafe-Artefakt, siehe `LogLineDto`) wird als `?` gezeigt
 * statt `level.name` zu lesen: an genau dieser Dereferenzierung crashte
 * `LogEntry.format()` (VIVID-5H, #433, Setting-Viewer + Export).
 * Der Platzhalter hält die Level-Spalte schmal und macht die kaputte Zeile
 * sichtbar, statt sie zu verstecken.
 */
fun LogLevel?.displayName(): String = this?.name ?: "?"