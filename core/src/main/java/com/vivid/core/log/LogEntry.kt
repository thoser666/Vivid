package com.vivid.core.log

/**
 * Eine einzelne Log-Zeile im In-App-Log (Ringpuffer).
 *
 * Die [message] ist bereits durch den [LogRedactor] geschwärzt — Stream-Keys,
 * Tokens und Passwörter landen nie unverschlüsselt im Puffer.
 */
data class LogEntry(
    val timestampMillis: Long,
    val level: LogLevel,
    val tag: String,
    val message: String,
) {
    /** Kompakte, menschenlesbare Darstellung für die Anzeige/den Export. */
    fun format(): String {
        val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
            .format(java.util.Date(timestampMillis))
        return "$time ${level.name.padEnd(5)} [$tag] $message"
    }
}