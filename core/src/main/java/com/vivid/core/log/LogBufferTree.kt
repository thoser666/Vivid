package com.vivid.core.log

import timber.log.Timber

/**
 * Timber-Tree, der jede Log-Zeile (geschwärzt durch [LogRedactor]) in den
 * [LogBuffer] schreibt — damit werden die vorhandenen `Timber.*`-Aufrufe der
 * App erstmals wirksam und im In-App-Log sichtbar.
 */
class LogBufferTree(
    private val buffer: LogBuffer,
    private val minLevel: LogLevel = LogLevel.DEBUG,
) : Timber.Tree() {

    override fun isLoggable(tag: String?, priority: Int): Boolean =
        priority >= minLevel.priority

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        buffer.add(
            LogEntry(
                timestampMillis = System.currentTimeMillis(),
                level = LogLevel.fromPriority(priority),
                tag = tag ?: "Vivid",
                message = LogRedactor.redact(message),
            ),
        )
    }
}