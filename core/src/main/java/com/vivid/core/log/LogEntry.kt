package com.vivid.core.log

/**
 * Eine einzelne Log-Zeile im In-App-Log (Ringpuffer + täglicher Store).
 *
 * Die [message] ist bereits durch den [LogRedactor] geschwärzt — Stream-Keys,
 * Tokens und Passwörter landen nie unverschlüsselt im Puffer/Export.
 *
 * [isCrash] markiert einen Absturz (Default-Uncaught-Exception-Handler): Diese
 * Einträge werden im Viewer deutlich hervorgehoben, damit der Streamer (oder
 * die Diagnose) Fehlerursachen gezielt auswerten kann.
 */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
    val isCrash: Boolean = false,
) {
    /** Kompakte, menschenlesbare Darstellung für die Anzeige/den Export. */
    fun format(): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(timestampMillis))
        val crash = if (isCrash) "💥 " else ""
        return "$time ${level.name.padEnd(5)} [$tag] $crash$message"
    }
}

/**
 * Datums-Helfer für die tägliche Log-Rotation (minSdk 24 — bewusst
 * `java.util.Calendar`/`SimpleDateFormat` statt `java.time`, kein Desugaring).
 */
object LogDates {

    private val DAY_FORMAT = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)

    /** Tages-Schlüssel `yyyy-MM-dd` (lokale Zeitzone) für einen Zeitstempel. */
    fun dayKey(timestampMillis: Long): String = DAY_FORMAT.format(java.util.Date(timestampMillis))

    /** Tages-Schlüssel für „heute“ (lokal). */
    fun todayKey(): String = dayKey(System.currentTimeMillis())

    /** Tages-Schlüssel von [dayKey] um [days] Tage verschoben (negativ = Vergangenheit). */
    fun shiftDays(dayKey: String, days: Int): String {
        val cal = java.util.Calendar.getInstance()
        val date = DAY_FORMAT.parse(dayKey) ?: return dayKey
        cal.time = date
        cal.add(java.util.Calendar.DAY_OF_YEAR, days)
        return DAY_FORMAT.format(cal.time)
    }

    /** Tages-Schlüssel von [timestampMillis] um [days] Tage in die Vergangenheit verschoben. */
    fun daysAgoKey(timestampMillis: Long, days: Int): String = shiftDays(dayKey(timestampMillis), -days)

    /** Ist [dayKey] der heutige Tag? (für die lokalisierte „Heute“-Sektion) */
    fun isToday(dayKey: String): Boolean = dayKey == todayKey()

    /** Ist [dayKey] der gestrige Tag? (für die lokalisierte „Gestern“-Sektion) */
    fun isYesterday(dayKey: String): Boolean = dayKey == shiftDays(todayKey(), -1)

    /** Anzeige-Datum `dd.MM.yyyy` für die Tages-Sektion (Fallback: roher Schlüssel). */
    fun formatDate(dayKey: String): String {
        val cal = java.util.Calendar.getInstance()
        val date = DAY_FORMAT.parse(dayKey) ?: return dayKey
        cal.time = date
        return java.text.SimpleDateFormat("dd.MM.yyyy", java.util.Locale.getDefault()).format(cal.time)
    }
}