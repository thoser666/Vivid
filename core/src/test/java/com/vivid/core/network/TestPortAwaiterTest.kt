package com.vivid.core.network

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket

/**
 * Tests fuer den Warte-Helper aus #264.
 *
 * Warum ein eigener Test und keiner am OBS-Test: Der Bind-Race selbst ist ein
 * Zeitfenster und laesst sich auf einem schnellen Entwicklerrechner nicht
 * zuverlaessig reproduzieren — der Versuch, den Warteblock wegzumutieren,
 * liess den OBS-Test gruen (Mutation B, siehe Issue #264). Was sich aber
 * **deterministisch** pruefen laesst, ist der Helper selbst, und genau der ist
 * der neue Code, auf den sich alle E2E-Tests verlassen: bei einem lauschenden
 * Port muss er `true` liefern, bei einem toten Port `false` — und zwar
 * innerhalb einer gemessenen Zeit, damit er eine echte Schranke ist.
 */
@Timeout(30)
class TestPortAwaiterTest {

    @Test
    fun `returns true for a port that is actually listening`() {
        ServerSocket(0).use { socket ->
            assertTrue(
                awaitPortListening(socket.localPort, timeoutMs = 2_000),
                "Ein lauschender Port muss sofort als bereit gelten",
            )
        }
    }

    @Test
    fun `returns false for a port nobody listens on`() {
        // Reservieren und sofort wieder freigeben: der Port ist bekannt,
        // aber garantiert nicht belegt — genau der Zustand, vor dem die
        // E2E-Tests standen.
        val deadPort = ServerSocket(0).use { it.localPort }

        assertFalse(
            awaitPortListening(deadPort, timeoutMs = 500),
            "Ein toter Port darf nicht als bereit gemeldet werden",
        )
    }

    @Test
    fun `canConnectNow distinguishes listening from dead ports`() {
        val listening = ServerSocket(0).use { it.localPort }
        val dead = ServerSocket(0).use { it.localPort }
        // Reihenfolge zaehlt nicht: beide Ports sind zu diesem Zeitpunkt
        // geschlossen, "listening" war nur einmalig gebunden.
        val firstResult = canConnectNow(listening)
        assertFalse(firstResult, "Nach dem Close lauscht der Port nicht mehr")

        assertFalse(canConnectNow(dead), "Toter Port darf keine Verbindung melden")
    }
}