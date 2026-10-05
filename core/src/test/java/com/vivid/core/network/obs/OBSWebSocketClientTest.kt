package com.vivid.core.network.obs

import com.google.gson.Gson
import com.vivid.core.network.KtorClientFactory
import com.vivid.core.network.awaitPortListening
import com.vivid.core.network.canConnectNow
import com.vivid.core.network.obs.security.generateAuthenticationString
import io.ktor.client.HttpClient
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.delay
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Echte WebSocket-End-to-End-Tests für den OBS-Client: Der Client verbindet
 * sich gegen einen lokalen Ktor-Testserver (CIO, zufälliger Port) — derselbe
 * Transport wie im Produktivbetrieb (Ktor CIO WebSockets, eigene Sockets
 * außerhalb der Network-Security-Config, Issue #226 / Sentry VIVID-M).
 *
 * Der Server agiert als OBS-Ersatz: Hello beim Upgrade, Identify-Bestätigung
 * (op:2), GetVersion-Antwort — die Protokoll-Details deckt
 * [OBSWebSocketClientControlTest] ab; hier stehen Transport, Handshake und
 * Verbindungslebenszyklus im Fokus.
 */
// JUnit-Timeout (interrupt-basiert): Ein E2E-Hang darf den Gradle-Test-Worker
// nie ewig blockieren — der Test bricht mit Stacktrace ab (Issue #226).
@Timeout(30)
class OBSWebSocketClientTest {

    private var server: EmbeddedServer<*, *>? = null
    private var port = 0
    private val serverReceived = CopyOnWriteArrayList<String>()
    private val gson = Gson()

    private var lastClient: OBSWebSocketClient? = null
    private var lastHttpClient: HttpClient? = null

    private fun newClient(): OBSWebSocketClient {
        val http = KtorClientFactory.create().also { lastHttpClient = it }
        return OBSWebSocketClient(http, gson).also { lastClient = it }
    }

    @AfterEach
    fun tearDown() {
        // Alle nicht-daemonisierten Transport-Threads freigeben, sonst haengt
        // der Gradle-Test-Worker: Der CIO-Client-Threadpool ist kein Daemon und
        // ueberlebt ohne close() den JVM-Exit (Issue #226).
        runCatching { lastClient?.shutdown() }
        runCatching { lastHttpClient?.close() }
        runCatching { server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
        server = null
    }

    private fun startServer(handler: suspend (DefaultWebSocketServerSession) -> Unit) {
        // Freien Port vorab reservieren (resolvedConnectors steht in Ktor 3.6
        // nicht zur Verfuegung); die Reservierung ist sofort wieder frei.
        val freePort = ServerSocket(0).use { it.localPort }
        val s = embeddedServer(CIO, port = freePort, host = "127.0.0.1") {
            install(WebSockets)
            routing { webSocket("/") { handler(this) } }
        }
        s.start(wait = false)
        port = freePort
        server = s
        // #264: Der Port ist mit `ServerSocket(0)` nur *reserviert*; CIO bindet
        // asynchron, `start(wait = false)` kehrt vorher zurück. Ohne diese
        // Schleife läuft der connect() bei Last ins Leere und der Test
        // scheitert an `awaitConnected` statt am Handshake.
        check(awaitPortListening(freePort)) {
            "CIO-Server hat den Port $freePort nicht innerhalb von 5s gebunden"
        }
    }

    /** OBS-ähnlicher Handler: Hello zuerst, Identify bestätigen, GetVersion beantworten. */
    private fun obsLikeHandler(closeAfterIdentify: Boolean = false): suspend (DefaultWebSocketServerSession) -> Unit =
        { session ->
            session.send(Frame.Text("""{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"C1","salt":"S1"}}}"""))
            for (frame in session.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                serverReceived += text
                when {
                    text.contains("\"op\":1") -> {
                        session.send(Frame.Text("""{"op":2}"""))
                        if (closeAfterIdentify) {
                            // Beobachtbares connected-Fenster lassen, sonst
                            // springt der Zustand true->false zwischen zwei
                            // Poll-Intervallen und der Test misst daneben.
                            delay(500)
                            session.close(CloseReason(CloseReason.Codes.NORMAL, "server shutting down"))
                        }
                    }
                    text.contains("\"requestType\":\"GetVersion\"") ->
                        session.send(
                            Frame.Text(
                                """{"op":7,"d":{"requestType":"GetVersion","requestStatus":{"result":true,"code":100},"responseData":{"version":"30.0.2"}}}""",
                            ),
                        )
                }
            }
        }

    /** Blockierendes Polling (kein Coroutine-Await): vermeidet runBlocking-Bridges. */
    private fun awaitCondition(timeoutMs: Long = 3_000, cond: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (cond()) return true
            Thread.sleep(25)
        }
        return cond()
    }

    private fun awaitConnected(client: OBSWebSocketClient, timeoutMs: Long = 3_000): Boolean =
        awaitCondition(timeoutMs) { client.isConnected.value }

    /** Die Identify-Nachricht des Servers, sobald sie gebucht ist (sonst `null`). */
    private fun identifyFrame(): String? = serverReceived.firstOrNull { it.contains("\"op\":1") }

    /** Alle GetVersion-Requests, die der Server bisher gebucht hat. */
    private fun getVersionFrames(): List<String> = serverReceived.filter { it.contains("\"requestType\":\"GetVersion\"") }

    @Test
    fun `isConnected is initially false`() {
        assertFalse(newClient().isConnected.value)
    }

    @Test
    fun `URL building uses ws for plain OBS and wss for TLS`() {
        assertEquals("ws://127.0.0.1:4455", OBSWebSocketClient.buildObsWebSocketUrl("127.0.0.1", 4455, useTls = false))
        assertEquals("wss://192.168.1.10:443", OBSWebSocketClient.buildObsWebSocketUrl("192.168.1.10", 443, useTls = true))
    }

    @Test
    fun `connect completes the hello-identify handshake over plain ws`() {
        startServer(obsLikeHandler())
        val client = newClient()

        client.connect("mypassword", "127.0.0.1", port)

        assertTrue(awaitConnected(client), "Handshake über ws:// muss gelingen (Transport außerhalb der NSC)")

        // #264: `connected` und die Folge-Nachrichten sind KEINE Simultanereignisse.
        // handleIdentified() setzt `isConnected` zuerst und ruft sendRequest() danach
        // auf; der Server wiederum verbucht das Frame auf eigener Coroutine. Es gibt
        // keine Happens-before-Kante dazwischen — der Test darf deshalb nicht auf
        // Gleichzeitigkeit prüfen, sondern auf das beobachtbare Ergebnis pollen.
        assertTrue(
            awaitCondition(timeoutMs = 5_000) { identifyFrame() != null },
            "Identify (op:1) kam nicht beim Server an: $serverReceived",
        )
        val identify = checkNotNull(identifyFrame()) { "Identify fehlt trotz erfolgreichem Polling" }
        val response = gson.fromJson(identify, com.vivid.core.network.obs.security.AuthenticationResponse::class.java)
        assertEquals(
            generateAuthenticationString("mypassword", "S1", "C1"),
            response.d.authentication,
        )
        assertTrue(
            identify.contains("\"eventSubscriptions\":${OBSWebSocketClient.EVENT_SUBSCRIPTION_MASK}"),
            "Identify muss die Event-Subscriptions tragen: $identify",
        )
        // Gleiches Muster für GetVersion, und ohne die alte Festnagelung auf
        // requestId "1": die Id zaehlt pro Client hoch, ist also an eine
        // Reihenfolge gebunden, an der der Test nichts verloren hat. Geprueft
        // wird, dass der Client nach dem Identifizieren *irgendeinen* korrelierten
        // GetVersion sendet; die Zaehler-Semantik deckt der Reconnect-Test ab.
        assertTrue(
            awaitCondition(timeoutMs = 5_000) {
                serverReceived.any { it.contains("\"requestType\":\"GetVersion\"") }
            },
            "GetVersion muss dem Identifizieren folgen (Server bekam nur: $serverReceived)",
        )
    }

    @Test
    fun `blank password never authenticates and stays disconnected`() {
        startServer(obsLikeHandler())
        val client = newClient()

        client.connect("", "127.0.0.1", port)

        // Etwas Zeit geben, damit ein fehlerhafter Auth-Pfad sichtbar würde.
        awaitCondition(timeoutMs = 800) { serverReceived.isNotEmpty() }
        assertFalse(client.isConnected.value)
        assertTrue(
            serverReceived.none { it.contains("\"op\":1") },
            "Ohne Passwort darf keine Identify-Antwort gesendet werden",
        )
    }

    @Test
    fun `connection failure resets state without crashing the caller`() {
        // Port reservieren und sofort freigeben: nichts lauscht dort.
        val freePort = ServerSocket(0).use { it.localPort }
        val client = newClient()

        client.connect("pw", "127.0.0.1", freePort)

        assertTrue(
            awaitCondition(timeoutMs = 3_000) { !client.isConnected.value },
            "Gescheiterte Verbindung muss sauber zurücksetzen (fehlertoleranter Vertrag)",
        )
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `server-initiated close resets the connected state`() {
        startServer(obsLikeHandler(closeAfterIdentify = true))
        val client = newClient()

        client.connect("pw", "127.0.0.1", port)
        assertTrue(awaitConnected(client))

        assertTrue(
            awaitCondition { !client.isConnected.value },
            "Server-seitiges Close muss die Verbindung zurücksetzen",
        )
    }

    @Test
    fun `disconnect closes the session so the server loop ends`() {
        var serverLoopEnded = false
        startServer({ session ->
            session.send(Frame.Text("""{"op":0,"d":{"rpcVersion":1,"authentication":{"challenge":"C1","salt":"S1"}}}"""))
            for (frame in session.incoming) {
                val text = (frame as? Frame.Text)?.readText() ?: continue
                serverReceived += text
                if (text.contains("\"op\":1")) session.send(Frame.Text("""{"op":2}"""))
            }
            serverLoopEnded = true
        })
        val client = newClient()
        client.connect("pw", "127.0.0.1", port)
        assertTrue(awaitConnected(client))

        client.disconnect()

        assertTrue(awaitCondition { serverLoopEnded }, "Server muss das Client-Close beobachten (Loop-Ende)")
        assertFalse(client.isConnected.value)
    }

    @Test
    fun `server port accepts connections as soon as startServer returns`() {
        // #264 (R1): Der Bind-Race. `ServerSocket(0)` reserviert nur, CIO bindet
        // asynchron. Faellt dieser Test, war startServer() zu frueh zurueck und
        // jeder nachfolgende E2E-Test in dieser Klasse ist unzuverlaessig.
        startServer(obsLikeHandler())

        assertTrue(
            canConnectNow(port),
            "startServer() muss erst zurueckkehren, wenn der Port wirklich lauscht",
        )
    }

    @Test
    fun `connected state never outruns the version request`() {
        // #264 (R2): Der Ordnungs-Race. handleIdentified() setzt `isConnected`
        // und ruft erst DANACH sendRequest(GetVersion) auf; der Server bucht das
        // Frame auf eigener Coroutine. Der Vertrag ist deshalb "connected fuehrt
        // zu GetVersion", nicht "connected UND GetVersion gleichzeitig".
        startServer(obsLikeHandler())
        val client = newClient()

        client.connect("pw", "127.0.0.1", port)

        assertTrue(awaitConnected(client), "Verbindung muss zustande kommen")
        assertTrue(
            awaitCondition(timeoutMs = 5_000) { getVersionFrames().isNotEmpty() },
            "GetVersion muss dem Connected-Zustand folgen (Server bekam: $serverReceived)",
        )
    }

    @Test
    fun `reconnect keeps issuing version requests with a fresh request id`() {
        // #264: Ersetzt die alte, auf requestId "1" festgenagelte Erwartung. Der
        // Zaehler lebt pro Client und laeuft ueber reconnect() weiter — genau das
        // macht "1" als Anker falsch. Hier wird die Zaehlersemantik positiv
        // geprueft: jede Runde traegt die naechste Id.
        startServer(obsLikeHandler())
        val client = newClient()

        repeat(3) { round ->
            client.connect("pw", "127.0.0.1", port)
            assertTrue(awaitConnected(client), "Runde $round: Verbindung muss zustande kommen")

            val expectedId = (round + 1).toString()
            assertTrue(
                awaitCondition(timeoutMs = 5_000) {
                    getVersionFrames().any { it.contains("\"requestId\":\"$expectedId\"") }
                },
                "Runde $round: GetVersion mit requestId=$expectedId erwartet, " +
                    "Server bekam: $serverReceived",
            )

            client.disconnect()
            assertTrue(awaitCondition { !client.isConnected.value }, "Runde $round: Disconnect")
        }

        assertEquals(3, getVersionFrames().size, "Pro Runde genau ein GetVersion: $serverReceived")
    }

    @Test
    fun `connect after disconnect starts a fresh session`() {
        startServer(obsLikeHandler())
        val client = newClient()

        client.connect("pw", "127.0.0.1", port)
        assertTrue(awaitConnected(client))
        client.disconnect()
        assertTrue(awaitCondition { !client.isConnected.value })

        client.connect("pw", "127.0.0.1", port)
        assertTrue(awaitConnected(client), "Wiederverbindung nach Disconnect muss funktionieren")
    }
}
