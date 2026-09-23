package com.vivid.core.startup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reine Error-Replay-Entscheidung des Sentry Session Replay:
 *
 *  1. Reporting an → Error-Rate 1.0, Session-Rate 0.0, Buffering starten
 *  2. Reporting aus → beide Rates 0, kein Buffering (nichts wird geschnitten)
 *  3. Die Rate-Konstanten sind dokumentationsstabil (Error-only-Vertrag)
 *  4. Laufzeit: Start/Stop folgt dem Toggle; gleichbleibender Stand = NONE
 *     (kein Flackern beim Settings-Recollect)
 */
class SentryReplayPolicyTest {

    // --- Konfiguration (plan) ---

    @Test
    fun `reporting an - error replay aktiv mit session replay deaktiviert`() {
        val plan = SentryReplayPolicy.plan(sentryEnabled = true)
        assertEquals(1.0, plan.errorSampleRate)
        assertEquals(0.0, plan.sessionSampleRate)
        assertTrue(plan.startBuffering)
    }

    @Test
    fun `reporting aus - nichts wird geschnitten oder gepuffert`() {
        val plan = SentryReplayPolicy.plan(sentryEnabled = false)
        assertEquals(0.0, plan.errorSampleRate)
        assertEquals(0.0, plan.sessionSampleRate)
        assertFalse(plan.startBuffering)
    }

    @Test
    fun `rate-konstanten sind dokumentationsstabil - error-only-vertrag`() {
        // Der Feature-Vertrag „Error-Replay ohne Dauer-Recording“ ist bewusst
        // als Konstante fixiert; eine Änderung muss die Doku (PRIVACY.md,
        // Settings-String) mit anfassen.
        assertEquals(1.0, SentryReplayPolicy.ERROR_SAMPLE_RATE)
        assertEquals(0.0, SentryReplayPolicy.SESSION_SAMPLE_RATE)
    }

    // --- Laufzeit (runtimeAction) ---

    @Test
    fun `erster stand ohne vorherigen - an startet buffering`() {
        assertEquals(
            SentryReplayPolicy.RuntimeAction.REPLAY_START,
            SentryReplayPolicy.runtimeAction(sentryEnabled = true, previousEnabled = null),
        )
    }

    @Test
    fun `erster stand ohne vorherigen - aus stoppt buffering`() {
        assertEquals(
            SentryReplayPolicy.RuntimeAction.REPLAY_STOP,
            SentryReplayPolicy.runtimeAction(sentryEnabled = false, previousEnabled = null),
        )
    }

    @Test
    fun `umschalten erzeugt start bzw stop`() {
        assertEquals(
            SentryReplayPolicy.RuntimeAction.REPLAY_START,
            SentryReplayPolicy.runtimeAction(sentryEnabled = true, previousEnabled = false),
        )
        assertEquals(
            SentryReplayPolicy.RuntimeAction.REPLAY_STOP,
            SentryReplayPolicy.runtimeAction(sentryEnabled = false, previousEnabled = true),
        )
    }

    @Test
    fun `gleichbleibender stand - keine aktion kein flackern`() {
        assertEquals(
            SentryReplayPolicy.RuntimeAction.NONE,
            SentryReplayPolicy.runtimeAction(sentryEnabled = true, previousEnabled = true),
        )
        assertEquals(
            SentryReplayPolicy.RuntimeAction.NONE,
            SentryReplayPolicy.runtimeAction(sentryEnabled = false, previousEnabled = false),
        )
    }
}
