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