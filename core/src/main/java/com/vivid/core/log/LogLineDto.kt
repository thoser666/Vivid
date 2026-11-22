package com.vivid.core.log

/**
 * Rohes JSON-Lines-Abbild einer persistierten Log-Zeile — bewusst **nicht**
 * [LogEntry] und mit durchgehend nullbaren Feldern.
 *
 * Grund (Sentry VIVID-5H, Issue #433): `gson.fromJson(line, LogEntry::class.java)`
 * instanziiert per Reflection/Unsafe am Kotlin-Konstruktor vorbei. Die
 * Non-null-Typen von [LogEntry] sind danach nur noch eine Behauptung — Gson legt
 * für fehlende Keys, `"level": null` oder einen unbekannten Enum-Namen `null`
 * in ein statisch non-null typisiertes Feld, und ein Guard, der diesen Wert
 * prüft, ist für R8 ein unmöglicher Zustand (er faltet die Prüfung weg).
 *
 * Dieses DTO macht den echten Laufzeit-Zustand zur Typwahrheit: [level] ist der
 * rohe Name, alle Felder sind `…?`. Erst [toEntry] übersetzt ihn explizit in
 * [LogLevel] und verlangt dabei echte Werte — die Prüfungen laufen gegen
 * nullable Typen und können nicht als „unmöglich“ entfernt werden.
 */
internal data class LogLineDto(
    val timestampMillis: Long? = null,
    val level: String? = null,
    val tag: String? = null,
    val message: String? = null,
    val isCrash: Boolean? = null,
) {

    /**
     * Übersetzt die Zeile in einen invariantenfreien [LogEntry] oder `null`,
     * wenn ein Pflichtfeld fehlt bzw. der Level-Name unbekannt ist. Ein
     * unbekannter Name wird verworfen statt auf einen Default gemappt — sonst
     * stünde im Viewer ein erfundener Level.
     */
    fun toEntry(): LogEntry? {
        val levelName = level ?: return null
        val resolvedLevel = LogLevel.entries.firstOrNull { it.name == levelName } ?: return null
        val timestamp = timestampMillis ?: return null
        val tagValue = tag ?: return null
        val messageText = message ?: return null
        return LogEntry(
            timestampMillis = timestamp,
            level = resolvedLevel,
            tag = tagValue,
            message = messageText,
            isCrash = isCrash ?: false,
        )
    }
}
