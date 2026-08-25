package com.vivid.core.log

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import timber.log.Timber

/**
 * Testet den Timber-Tree über die echte Timber-API (plant/uproot): Er schreibt
 * Log-Zeilen (geschwärzt) in den Puffer, respektiert die Mindeststufe und
 * verarbeitet Throwables.
 */
class LogBufferTreeTest {

    private fun withTree(
        minLevel: LogLevel = LogLevel.DEBUG,
        block: (LogBuffer) -> Unit,
    ) {
        val buffer = LogBuffer()
        val tree = LogBufferTree(buffer, minLevel = minLevel)
        Timber.plant(tree)
        try {
            block(buffer)
        } finally {
            Timber.uproot(tree)
        }
    }

    @Test
    fun `log schreibt einen Eintrag in den Puffer`() = withTree { buffer ->
        Timber.i("hello world")

        val entries = buffer.snapshot()
        assertEquals(1, entries.size)
        assertEquals(LogLevel.INFO, entries[0].level)
        assertEquals("Vivid", entries[0].tag)
        assertEquals("hello world", entries[0].message)
    }

    @Test
    fun `log schwaerzt sensible Werte vor dem Schreiben`() = withTree { buffer ->
        Timber.d("key=0123456789abcdef0123456789abcdef")

        val message = buffer.snapshot().single().message
        assertFalse(message.contains("0123456789abcdef"))
        assertEquals("key=***", message)
    }

    @Test
    fun `unterhalb der Mindeststufe wird nicht geloggt`() = withTree(minLevel = LogLevel.WARN) { buffer ->
        Timber.d("debug line")
        Timber.e("error line")

        val messages = buffer.snapshot().map { it.message }
        assertEquals(listOf("error line"), messages)
    }

    @Test
    fun `Throwable wird mitgeloggt`() = withTree { buffer ->
        val cause = RuntimeException("kaputt")
        Timber.e(cause, "boom")

        val entry = buffer.snapshot().single()
        assertEquals(LogLevel.ERROR, entry.level)
        // Timber hängt den Stacktrace an die Message an — nur der Anfang ist die Meldung.
        assertTrue(entry.message.startsWith("boom"))
    }
}