package com.vivid.core.startup

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Reine Crash-Schleifen-Entscheidung (Startup-Safe-Mode):
 *
 * Semantik laut Doku: Der Zähler zählt **gescheiterte** Starts in Folge.
 * Zwei gescheiterte Starts erlauben den nächsten Start nur noch im
 * Safe-Mode (Start Nr. 3 nach zwei Crashes); der Safe-Mode verlässt sich
 * nie selbst (>= statt ==).
 */
class CrashLoopPolicyTest {

    // --- decide ---------------------------------------------------------------

    @Test
    fun `frischer Start startet normal`() {
        assertEquals(CrashLoopPolicy.Decision.NORMAL, CrashLoopPolicy.decide(0))
    }

    @Test
    fun `ein gescheiterter Start startet noch normal`() {
        assertEquals(CrashLoopPolicy.Decision.NORMAL, CrashLoopPolicy.decide(1))
    }

    @Test
    fun `zwei gescheiterte Starts fuehren zum Safe-Mode`() {
        assertEquals(CrashLoopPolicy.Decision.SAFE_MODE, CrashLoopPolicy.decide(2))
    }

    @Test
    fun `Safe-Mode verlaesst sich nie selbst`() {
        assertEquals(CrashLoopPolicy.Decision.SAFE_MODE, CrashLoopPolicy.decide(3))
        assertEquals(CrashLoopPolicy.Decision.SAFE_MODE, CrashLoopPolicy.decide(99))
    }

    // --- Transitions ------------------------------------------------------------

    @Test
    fun `Startversuch inkrementiert`() {
        assertEquals(1, CrashLoopPolicy.onStartupAttempt(0))
        assertEquals(2, CrashLoopPolicy.onStartupAttempt(1))
    }

    @Test
    fun `UI erreicht setzt vollstaendig zurueck`() {
        assertEquals(CrashLoopPolicy.HEALTHY_RESET, CrashLoopPolicy.onUiReached())
        assertEquals(0, CrashLoopPolicy.HEALTHY_RESET)
    }

    // --- Konstanten-Vertrag -------------------------------------------------------

    @Test
    fun `Schwellwert ist zwei`() {
        assertEquals(2, CrashLoopPolicy.SAFE_MODE_THRESHOLD)
    }
}
