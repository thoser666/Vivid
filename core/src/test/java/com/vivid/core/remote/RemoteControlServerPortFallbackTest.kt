package com.vivid.core.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vivid.core.log.LogStore
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * Integrationstests der Port-Fallback-Kette ([RemoteControlServer.start] mit
 * [PortFallbackPolicy]) — echte ServerSockets, kein Mock des Netzes:
 *
 *  1. Port frei → Server lauscht auf dem Preferred-Port, `activePort` meldet ihn.
 *  2. Preferred belegt (echter Socket-Halter) → Ausweichport aus der Kette,
 *     `activePort` folgt; `stop()` setzt zurück.
 *  3. Komplette Kette belegt → konkreter (nicht-0) Port aus dem ephemeralen
 *     Bereich, `activePort` meldet ihn.
 *
 * Die Fallback-Offsets sind relativ zum Preferred-Port — die Tests wählen als
 * Preferred einen freien Port aus dem ephemeralen Bereich und belegen die
 * Kette selbst, damit keine Annahmen über 8080/8081 auf dem CI-Runner nötig
 * sind (dort könnte 8080 durch parallele Läufe belegt sein).
 *
 * Bewusst `runBlocking` statt `runTest`: [RemoteControlServer.start] setzt
 * `_activePort` synchron vor der Rückgabe, daher genügt das direkte
 * `.value`-Lesen — der virtuelle Test-Scheduler brächte hier nichts und
 * mischte sich nachweislich schlecht mit echten Ktor-Engines (Race in
 * `first()` unter dem TestDispatcher).
 */
class RemoteControlServerPortFallbackTest {

    /** Test-Double für StreamControl (nur Status, keine Engine). */
    private class FakeControl : StreamControl {
        override val status = MutableStateFlow(RemoteStreamStatus.IDLE)
        override suspend fun start() {
            status.value = RemoteStreamStatus.STREAMING
        }

        override fun stop() {
            status.value = RemoteStreamStatus.IDLE
        }
    }

    @TempDir
    lateinit var tempDir: File

    private fun newServer(): RemoteControlServer {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            produceFile = { File(tempDir, "token-${System.nanoTime()}.preferences_pb") },
        )
        val tokenStore = RemoteControlTokenStore(dataStore)
        return RemoteControlServer(
            streamControl = FakeControl(),
            tokenStore = tokenStore,
            logStore = LogStore(File(tempDir, "logs-${System.nanoTime()}")),
        )
    }

    /** Belegt [port] für die Dauer des Blocks und gibt nach dem Verlassen frei. */
    private inline fun <T> holdingPort(port: Int, block: () -> T): T =
        ServerSocket().use { holder ->
            holder.bind(InetSocketAddress("0.0.0.0", port))
            block()
        }

    @Test
    fun `bevorzugter Port frei - activePort meldet ihn nach dem Start`() = runBlocking {
        val preferred = ServerSocket(0).use { it.localPort }
        val server = newServer()
        server.preferredPort = preferred
        try {
            server.start()
            assertEquals(preferred, server.activePort.value)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `preferred belegt - Ausweichport aus der Kette mit aktivem Port-Flow`() = runBlocking {
        val preferred = ServerSocket(0).use { it.localPort }
        val server = newServer()
        server.preferredPort = preferred
        try {
            // Preferred belegt → Kette muss auf preferred+1 ausweichen
            // (der Rest der Kette bleibt frei in der Testumgebung).
            holdingPort(preferred) {
                server.start()
            }
            assertEquals(preferred + 1, server.activePort.value)
            assertTrue(server.isRunning)
        } finally {
            server.stop()
        }
        // stop() setzt die Anzeige auf den Default zurück.
        assertEquals(RemoteControlServer.DEFAULT_PORT, server.activePort.value)
        assertNotEquals(preferred + 1, server.activePort.value)
    }

    @Test
    fun `komplette Kette belegt - konkreter Port aus dem ephemeralen Bereich`() = runBlocking {
        val preferred = ServerSocket(0).use { it.localPort }
        val server = newServer()
        server.preferredPort = preferred
        try {
            holdingPort(preferred) {
                ServerSocket(preferred + 1).use { h1 ->
                    ServerSocket(preferred + 2).use { h2 ->
                        ServerSocket(preferred + 3).use {
                            server.start()
                        }
                    }
                }
            }
            val active = server.activePort.value
            assertTrue(active > 0, "activePort muss einen konkreten Port melden (war $active)")
            assertNotEquals(preferred, active)
            assertTrue(server.isRunning)
        } finally {
            server.stop()
        }
    }
}
