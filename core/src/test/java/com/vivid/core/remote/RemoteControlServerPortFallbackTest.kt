package com.vivid.core.remote

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.vivid.core.log.LogStore
import com.vivid.core.network.canConnectNow
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
 * Preferred einen freien Port aus der **kalten Range** (24000–32000,
 * IANA-unassigned), damit keine Annahmen über 8080/8081 auf dem CI-Runner
 * nötig sind und die Kettenglieder nicht im heissen ephemeralen Bereich
 * liegen (dort belegen Kernel-Zuweisungen und fremde Prozesse laufend Ports —
 * das war die Flake-Ursache: bevorzugt+1 lag in der heissen Range).
 *
 * **Start vs. Test-Wartezeit (#268).** Diese Klasse wartet *nicht* selbst auf
 * den Bind — [RemoteControlServer.start] tut das im Produktionscode
 * (`awaitEngineBind`, NIO-Probe bis 2 s) und setzt `activePort` erst danach.
 * Ein `awaitPortListening` im Test würde diese Produktionsgarantie nur
 * verdecken. Der Test hält sie stattdessen fest: `start kehrt erst zurück,
 * wenn der gemeldete Port wirklich lauscht`. Der Warte-Helper aus #264
 * gehört dagegen in die Tests, deren Startpfad die Bind-Verzögerung *nicht*
 * selbst abwartet — siehe `WHIPClientTest.startServer`.
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

    /**
     * Freier Port aus der kalten Range (24000–32000, IANA-unassigned): Ziel ist,
     * NICHT im ephemeralen Bereich (Linux 32768–60999, Windows 49152–65535) zu
     * landen — dort belegen Kernel-Zuweisungen und fremde Prozesse laufend
     * Ports (CI-Flake-Ursache: bevorzugt+1 lag im heissen Bereich).
     */
    private fun coldRangePort(): Int {
        repeat(50) {
            val candidate = 24000 + (0..7999).random()
            try {
                ServerSocket().use { it.bind(InetSocketAddress("0.0.0.0", candidate)) }
                return candidate
            } catch (_: Exception) {
                // belegt → nächsten Kandidaten
            }
        }
        // Fallback: Kernel-Wahl (heisse Range) — besser als kein Test.
        return ServerSocket(0).use { it.localPort }
    }

    /** Belegt [port] für die Dauer des Blocks und gibt nach dem Verlassen frei. */
    private inline fun <T> holdingPort(port: Int, block: () -> T): T =
        ServerSocket().use { holder ->
            holder.bind(InetSocketAddress("0.0.0.0", port))
            block()
        }

    @Test
    fun `bevorzugter Port frei - activePort meldet ihn nach dem Start`() = runBlocking {
        val preferred = coldRangePort()
        val server = newServer()
        server.preferredPort = preferred
        try {
            // #268: `coldRangePort()` hat den Port bereits wieder freigegeben.
            // Nimmt ein Fremdprozess ihn im Fenster bis zur Probe in
            // `start()`, ist das ein Umgebungszustand und kein Testfehler. Die
            // Erwartung wird deshalb unmittelbar vor dem Start über dieselbe
            // Policy + dieselbe Probe berechnet (Muster des zweiten Tests), statt
            // `preferred` festzunageln. Die Aussage „freier Preferred-Port gewinnt“
            // deckt PortFallbackPolicyTest deterministisch ab; hier zählt der Weg
            // durch die echte Engine.
            val expected = PortFallbackPolicy.selectPort(preferred) { candidate ->
                RemoteControlServer.probePort(candidate)
            }
            server.start()
            assertEquals(
                expected,
                server.activePort.value,
                "start() muss genau den Port melden, den die Kette unmittelbar davor gewählt hat",
            )
            assertTrue(server.isRunning)
        } finally {
            server.stop()
        }
    }

    @Test
    fun `preferred belegt - Ausweichport aus der Kette mit aktivem Port-Flow`() = runBlocking {
        val preferred = coldRangePort()
        val server = newServer()
        server.preferredPort = preferred
        try {
            // Preferred belegt → Kette muss auf das erste freie Glied
            // ausweichen. Die Erwartung wird über dieselbe Policy + dieselbe
            // Probe berechnet (bei den Haltern offen), denn auf CI-Runnern
            // können fremde Prozesse einzelne Kettenglieder belegen — hart
            // `preferred + 1` zu fordern, wäre dort nicht deterministisch.
            val expected = holdingPort(preferred) {
                val choice = PortFallbackPolicy.selectPort(preferred) { candidate ->
                    RemoteControlServer.probePort(candidate)
                }
                server.start()
                choice
            }
            assertNotEquals(preferred, server.activePort.value)
            assertEquals(expected, server.activePort.value)
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
        val preferred = coldRangePort()
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

    @Test
    fun `start kehrt erst zurück, wenn der gemeldete Port wirklich lauscht`() = runBlocking {
        // #268: `canConnectNow` pollt bewusst nicht — ein wartender Aufruf
        // würde genau das wegprüfen, was diese Zusicherung behauptet. Fällt der
        // Test, gibt `start()` den aktiven Port heraus, bevor die Engine
        // gebunden hat; die Settings-UI würde dann einen toten Port zeigen.
        //
        // Ehrliche Grenze: das ist ein Zeitfenster, kein-deterministischer
        // Nachweis. Die Mutation, die `awaitEngineBind` auf ein sofortiges
        // `return true` verkürzt, blieb lokal grün — auch mit 20 Runden statt
        // einer (gemessen, Protokoll im Issue #268). Der Test benennt die
        // Annahme und fällt dort, wo sie tatsächlich bricht: auf belasteten
        // CI-Runnern, für die das Pollen in `awaitEngineBind` überhaupt
        // existiert. Wiederholung wurde bewusst nicht beibehalten, solange ihr
        // Nutzen nicht messbar ist — sie kostete 40 % Laufzeit der Klasse.
        val preferred = coldRangePort()
        val server = newServer()
        server.preferredPort = preferred
        try {
            server.start()
            val active = server.activePort.value

            assertTrue(active > 0, "activePort muss einen konkreten Port melden (war $active)")
            assertTrue(
                canConnectNow(active),
                "start() darf erst zurückkehren, wenn der gemeldete Port lauscht (Port $active)",
            )
        } finally {
            server.stop()
        }
    }
}
