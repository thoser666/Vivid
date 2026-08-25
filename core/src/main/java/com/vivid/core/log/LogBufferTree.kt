package com.vivid.core.log

import timber.log.Timber

/**
 * Timber-Tree, der jede Log-Zeile (geschwärzt durch [LogRedactor]) in den
 * [LogBuffer] schreibt — damit werden die vorhandenen `Timber.*`-Aufrufe der
 * App erstmals wirksam und im In-App-Log sichtbar. Ist ein [LogStore]
 * hinterlegt, wird jeder Eintrag zusätzlich in die tägliche Log-Datei
 * persistiert (Rotation + Vorhaltezeit).
 */
class LogBufferTree(
    private val buffer: LogBuffer,
    private val minLevel: LogLevel = LogLevel.DEBUG,
    private val store: LogStore? = null,
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= minLevel.priority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.fromPriority(priority),
                tag = tag ?: "Vivid",
                message = LogRedactor.redact(message),
            ),
        )
    }

    /**
     * Markiert einen Absturz im In-App-Log ([LogEntry.isCrash] = true, Stufe
     * ASSERT) — aufgerufen vom Default-Uncaught-Exception-Handler, bevor der
     * Fehler an Sentry/den vorherigen Handler weitergereicht wird.
     */
    fun crash(tag: String, throwable: Throwable) {
        add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.ASSERT,
                tag = tag,
                message = LogRedactor.redact(throwable.stackTraceToString()),
                isCrash = true,
            ),
        )
    }

    private fun add(entry: LogEntry) {
        buffer.add(entry)
        store?.add(entry)
    }
}