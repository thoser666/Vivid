package com.vivid.core.remote

import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.net.BindException
import java.net.ServerSocket

/**
 * Tests für die deterministische Port-Probe der Web-Remote-Control
 * ([RemoteControlServer.probePort], Companion — ohne Instanz testbar).
 *
 * Hintergrund: Ktor wirft Bind-Fehler asynchron im acceptJob — das
 * `runCatching` beim Application-Start kann sie nicht fangen, und der
 * unbehandelte Fehler im SupervisorJob crasht den Prozess. Die Probe
 * scheitert stattdessen SYNCHRON mit klarer Ursache, bevor der Ktor-Bind
 * überhaupt beginnt.
 */
class RemoteControlServerProbeTest {

    @Test
    fun `probePort succeeds on a free port`() {
        // Port 0 = vom Stack vergebener freier Port — die Probe muss durchgehen.
        assertDoesNotThrow { RemoteControlServer.probePort(port = 0) }
    }

    @Test
    fun `probePort throws BindException when the port is occupied`() {
        // Echter, gebundener Socket = deterministisch belegter Port.
        // Bewusst OHNE SO_REUSEADDR an der Probe: Windows behandelt das Flag
        // wie REUSEPORT (Port-Klau möglich) — der strikte Bind schlägt auf
        // allen Plattformen deterministisch fehl.
        ServerSocket(0).use { occupied ->
            val boundPort = occupied.localPort
            assertThrows(BindException::class.java) {
                RemoteControlServer.probePort(port = boundPort)
            }
        }
    }

    @Test
    fun `probePort frees the port after returning`() {
        // Die Probe darf den Port nicht beansprucht lassen (try-with-resources
        // im Kotlin-use): Nach dem erfolgreichen Durchgang ist der Port wieder frei.
        var probed = 0
        ServerSocket(0).use { socket ->
            probed = socket.localPort
        }
        assertDoesNotThrow { RemoteControlServer.probePort(port = probed) }
    }
}
