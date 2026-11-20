package com.vivid.core.log

import com.google.gson.Gson
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regression zu Sentry VIVID-5H (Issue #433): `LogEntry.level` ist statisch
 * non-null, kann durch die Gson-Reflexion aber zur Laufzeit `null` tragen.
 * Der Level-Vergleich (`it.level >= LogLevel.ERROR`) crashte dort mit
 * `Enum.compareTo` (NPE) im errorsOnly-Filter des Log-Viewers, obwohl
 * `LogStore.parseLine` die Zeile hätte verwerfen sollen.
 */
class LogLevelNullSafetyTest {

    /** Baut exakt das Produktions-Artefakt: Gson instanziiert ohne Konstruktor. */
    private fun entryWithNullLevel(): LogEntry = Gson().fromJson(
        """{"timestampMillis":1,"level":null,"tag":"T","message":"gift","isCrash":false}""",
        LogEntry::class.java,
    )

    @Test
    fun `gson erzeugt den null-level im non-null typisierten feld`() {
        assertNull(entryWithNullLevel().level)
    }

    @Test
    fun `null-level ist nie mindestens ERROR`() {
        assertFalse(entryWithNullLevel().level.isAtLeast(LogLevel.ERROR))
    }

    @Test
    fun `level-vergleich bleibt fuer echte werte unveraendert`() {
        assertTrue(LogLevel.ERROR.isAtLeast(LogLevel.ERROR))
        assertTrue(LogLevel.ASSERT.isAtLeast(LogLevel.ERROR))
        assertFalse(LogLevel.WARN.isAtLeast(LogLevel.ERROR))
        assertFalse(LogLevel.INFO.isAtLeast(LogLevel.ERROR))
        assertTrue(LogLevel.DEBUG.isAtLeast(LogLevel.VERBOSE))
    }
}
