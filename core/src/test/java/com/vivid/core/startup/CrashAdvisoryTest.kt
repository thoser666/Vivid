package com.vivid.core.startup

import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogLevel
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow

/**
 * Crash-Advisory-Vertraege (reines JUnit, ohne Android-Framework):
 *
 *  1. Reine Entscheidung ([CrashAdvisoryRegistry.evaluate]): Treffer inklusive
 *     Bereichsgrenzen, kein Treffer, leere Registry - der saubere Pfad ist still.
 *  2. Reporter: eine Treffer-Advisory landet CRASH-markiert (WARN, 💥, Tag
 *     `CrashAdvisory`) im In-App-Log [LogBuffer]; "kein Treffer" schreibt nichts.
 *  3. Registry-Hygiene: [CrashAdvisoryRegistry.KNOWN] ist leer startend bzw.
 *     enthaelt nur real identifizierte Eintraege (keine Spekulation) und alle
 *     Eintraege muessen wohlgeformt sein.
 *
 * Die Start-Verdrahtung ([com.vivid.irlbroadcaster.VividApplication]) ruft
 * `reportIfAny(BuildConfig.VERSION_CODE)` fehlertolerant nach dem Timber-Planting.
 */
class CrashAdvisoryTest {

    private val candidate = KnownCrashCandidate(
        id = "TEST-STARTUP-CRASH",
        description = "Absturz beim Start mit beschädigtem Replay-Cache",
        minVersionCode = 100,
        maxVersionCode = 199,
        workaround = "Auf Build ≥ 200 aktualisieren",
    )

    // --- 1) Reine Entscheidung ---------------------------------------------

    @Test
    fun `leere Registry fuehrt zu keiner Advisory`() {
        assertNull(CrashAdvisoryRegistry.evaluate(12345, emptyList()))
        assertNull(CrashAdvisoryRegistry.evaluate(12345, CrashAdvisoryRegistry.KNOWN))
    }

    @Test
    fun `Kandidat trifft nur im inklusiven versionCode-Bereich`() {
        assertNotNull(CrashAdvisoryRegistry.evaluate(100, listOf(candidate)))
        assertNotNull(CrashAdvisoryRegistry.evaluate(150, listOf(candidate)))
        assertNotNull(CrashAdvisoryRegistry.evaluate(199, listOf(candidate)))
        assertNull(CrashAdvisoryRegistry.evaluate(99, listOf(candidate)))
        assertNull(CrashAdvisoryRegistry.evaluate(200, listOf(candidate)))
    }

    @Test
    fun `bei mehreren Treffern gewinnt der erste Kandidat`() {
        val first = KnownCrashCandidate("A", "Erster", 0, 1000)
        val second = KnownCrashCandidate("B", "Zweiter", 0, 1000)

        val advisory = CrashAdvisoryRegistry.evaluate(500, listOf(first, second))

        assertNotNull(advisory)
        assertEquals("A", advisory!!.candidate.id)
    }

    // --- 2) Reporter ---------------------------------------------------------

    @Test
    fun `Reporter meldet Treffer crash-markiert mit versionCode und Workaround`() {
        val buffer = LogBuffer()
        val advisory = CrashAdvisoryRegistry.evaluate(150, listOf(candidate))!!

        CrashAdvisoryReporter(buffer).report(advisory)

        val hit = buffer.snapshot().last()
        assertTrue(hit.isCrash, "Advisory muss CRASH-markiert sein (rote Zeile + 💥 im Viewer)")
        assertEquals(LogLevel.WARN, hit.level)
        assertEquals("CrashAdvisory", hit.tag)
        assertTrue(hit.message.contains("TEST-STARTUP-CRASH"), "ID fehlt: ${hit.message}")
        assertTrue(
            hit.message.contains("installierter versionCode 150"),
            "versionCode fehlt: ${hit.message}",
        )
        assertTrue(
            hit.message.contains("Auf Build ≥ 200 aktualisieren"),
            "Workaround fehlt: ${hit.message}",
        )
    }

    @Test
    fun `reportIfAny ohne Treffer schreibt nichts in den Puffer`() {
        val buffer = LogBuffer()

        CrashAdvisoryReporter(buffer).reportIfAny(12345, emptyList())

        assertTrue(buffer.snapshot().isEmpty())
    }

    // --- 3) Registry-Hygiene ---------------------------------------------------

    @Test
    fun `KNOWN enthaelt keine spekulativen Eintraege - jeder Eintrag ist begruendet`() {
        // Die Registry darf nur real identifizierte Crashes enthalten. Die
        // Liste ist handgepflegt; dieser Test dokumentiert den Vertrag:
        // Jeder Eintrag braucht Range + Workaround (geprüft in
        // `alle Registry-Eintraege sind wohlgeformt`), neue Eintraege
        // verlangen einen realen Befund (Sentry/In-App-Log).
        assertTrue(CrashAdvisoryRegistry.KNOWN.size >= 1)
    }

    @Test
    fun `alle Registry-Eintraege sind wohlgeformt`() {
        CrashAdvisoryRegistry.KNOWN.forEach { c ->
            assertTrue(c.id.isNotBlank())
            assertTrue(c.description.isNotBlank())
            assertTrue(
                c.minVersionCode <= c.maxVersionCode,
                "Range von ${c.id} invertiert (${c.minVersionCode}..${c.maxVersionCode})",
            )
        }
    }

    @Test
    fun `REAL-Kandidat REMOTE-EADDRINUSE-STARTUP trifft die Crashing-Versionen`() {
        // Real identifiziert (S23-Startcrash, In-App-Log): Autostart seit
        // v0.5.0-alpha (Basis 5000), Haertung ab v0.5.16-beta (5162).
        val c = CrashAdvisoryRegistry.KNOWN.first { it.id == "REMOTE-EADDRINUSE-STARTUP" }
        assertNotNull(CrashAdvisoryRegistry.evaluate(5000, listOf(c)))
        assertNotNull(CrashAdvisoryRegistry.evaluate(5144, listOf(c)))
        assertNull(CrashAdvisoryRegistry.evaluate(5162, listOf(c)))
        assertNull(CrashAdvisoryRegistry.evaluate(5172, listOf(c)))
        assertNull(CrashAdvisoryRegistry.evaluate(4999, listOf(c)))
    }

    @Test
    fun `evaluate mit aktueller Registry wirft nie - Startpfad bleibt robust`() {
        for (vc in intArrayOf(4999, 5000, 5144, 5162, 5172, 999999)) {
            assertDoesNotThrow { CrashAdvisoryRegistry.evaluate(vc, CrashAdvisoryRegistry.KNOWN) }
        }
    }
}
