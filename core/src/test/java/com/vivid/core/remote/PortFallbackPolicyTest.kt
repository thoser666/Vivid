package com.vivid.core.remote

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Reine Fallback-Kette der Web-Remote-Control (ohne Netz/Android):
 *
 *  1. Bevorzugter Port frei → er gewinnt (keine Probe-Fehler).
 *  2. Bevorzugter belegt → deterministische Kette +1 → +2 → +3 (für 8080:
 *     8081 → 8082 → 8083).
 *  3. Komplette Kette belegt → ephemeraler Port (0).
 *  4. Probe wirft generische Exception → ebenfalls Überspringen (robust).
 */
class PortFallbackPolicyTest {

    /** Probe, die die gegebenen Ports als belegt meldet (wirft). */
    private fun busy(vararg ports: Int): (Int) -> Unit = { candidate ->
        if (candidate in ports) throw java.net.BindException("EADDRINUSE $candidate")
    }

    @Test
    fun `bevorzugter Port frei - er gewinnt`() {
        assertEquals(8080, PortFallbackPolicy.selectPort(8080, busy()))
    }

    @Test
    fun `bevorzugter belegt - Kette in fester Reihenfolge`() {
        assertEquals(8081, PortFallbackPolicy.selectPort(8080, busy(8080)))
        assertEquals(8082, PortFallbackPolicy.selectPort(8080, busy(8080, 8081)))
        assertEquals(8083, PortFallbackPolicy.selectPort(8080, busy(8080, 8081, 8082)))
    }

    @Test
    fun `komplette Kette belegt - ephemeral als letzter Ausweg`() {
        assertEquals(
            PortFallbackPolicy.EPHEMERAL_PORT,
            PortFallbackPolicy.selectPort(8080, busy(8080, 8081, 8082, 8083)),
        )
    }

    @Test
    fun `ephemeral wird nie probiert - Probe darf fuer 0 nicht aufgerufen werden`() {
        val probedPorts = mutableListOf<Int>()
        val probe: (Int) -> Unit = { p ->
            probedPorts.add(p)
            throw java.net.BindException("busy")
        }
        val chosen = PortFallbackPolicy.selectPort(8080, probe)
        assertEquals(PortFallbackPolicy.EPHEMERAL_PORT, chosen)
        // Der ephemerale Kandidat (0) taucht nie in der Probe-Liste auf.
        assertTrue(0 !in probedPorts)
    }

    @Test
    fun `generische Probe-Exception wird ebenfalls uebersprungen`() {
        val probe: (Int) -> Unit = { candidate ->
            if (candidate == 8080) throw IllegalStateException("kaputte Probe")
        }
        assertEquals(8081, PortFallbackPolicy.selectPort(8080, probe))
    }

    @Test
    fun `Kette ist dokumentationsstabil - konstante Offsets relativ zum Preferred`() {
        assertEquals(
            intArrayOf(8080, 8081, 8082, 8083, 0).toList(),
            PortFallbackPolicy.chain(8080).toList(),
        )
        // Relativ: Ein Test-Preferred (z. B. freier Portbereich) verhaelt sich identisch.
        assertEquals(
            intArrayOf(20000, 20001, 20002, 20003, 0).toList(),
            PortFallbackPolicy.chain(20000).toList(),
        )
        assertTrue(PortFallbackPolicy.isEphemeral(0))
        assertFalse(PortFallbackPolicy.isEphemeral(8080))
        assertFalse(PortFallbackPolicy.isEphemeral(8081))
    }
}
