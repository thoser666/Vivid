package com.vivid.core.remote

import com.vivid.core.log.LogStore
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.InetSocketAddress
import java.net.StandardSocketOptions
import java.nio.channels.ServerSocketChannel
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

/** Antwort für `GET /status`. */
@Serializable
data class RemoteStatusResponse(
    val status: RemoteStreamStatus,
)

/** Antwort für erfolgreiche `POST /start` / `POST /stop`. */
@Serializable
data class RemoteActionResponse(
    val ok: Boolean,
)

/** Ein einzelner Log-Eintrag in der `GET /logs`-Antwort (bereits geschwärzt). */
@Serializable
data class RemoteLogEntry(
    val timestampMillis: Long,
    val level: String,
    val tag: String,
    val message: String,
    val isCrash: Boolean,
)

/** Antwort für `GET /logs?days=N`. */
@Serializable
data class RemoteLogsResponse(
    /** Angefragter (geclampter) Zeitraum in Tagen. */
    val days: Int,
    /** Anzahl der gelieferten Einträge. */
    val count: Int,
    val entries: List<RemoteLogEntry>,
)

/**
 * Startet die Web-Remote-Control über LAN.
 *
 * Endpunkte (`Authorization: Bearer <token>` für Aktionen und Logs):
 *  - `GET  /status`          → aktueller Stream-Status als JSON
 *  - `POST /start`           → Stream mit gespeicherten Einstellungen starten
 *  - `POST /stop`            → Stream stoppen
 *  - `GET  /logs?days=N`     → Log-Tage aus dem [LogStore] als JSON
 *
 * `/logs` liefert ausschließlich die bereits durch den [com.vivid.core.log.LogRedactor]
 * geschwärzten Einträge des tagesbasierten [LogStore] — Stream-Keys, Tokens und
 * Passwörter verlassen das Gerät also auch über diesen Endpunkt nie im Klartext.
 */
@Singleton
class RemoteControlServer @Inject constructor(
    private val streamControl: StreamControl,
    private val tokenStore: RemoteControlTokenStore,
    private val logStore: LogStore,
) {
    private var server: EmbeddedServer<*, *>? = null

    /** Bevorzugter Port (8080); die Ausweichkette hängt dahinter (testseitig umstellbar). */
    internal var preferredPort: Int = DEFAULT_PORT

    /** Bevorzugter Port (Lesezugriff wie bisher). */
    val port: Int get() = preferredPort

    private val _activePort = MutableStateFlow(DEFAULT_PORT)

    /**
     * Port, auf dem der Server tatsächlich lauscht — nach der Fallback-Kette
     * (8080 → 8081 → 8082 → 8083 → ephemeral) auch ein Ausweichport. Die
     * Settings-Anzeige folgt diesem Flow reaktiv (QR/Token/Port).
     */
    val activePort: StateFlow<Int> = _activePort.asStateFlow()

    val isRunning: Boolean get() = server != null

    /**
     * Startet den Server (idempotent). Läuft asynchron weiter.
     *
     * Scheitert der Port-Bind (z. B. EADDRINUSE, wenn eine andere App oder
     * eine zweite Vivid-Instanz den Port belegt), wird der Start **bewusst
     * abgefangen und nur geloggt** — die Methode wirft für Port-Konflikte
     * nichts mehr. Hintergrund: Vor der Härtung in v0.5.16-beta crashte ein
     * belegter Port beim App-Start den Prozess (Crash-Kandidat
     * REMOTE-EADDRINUSE-STARTUP in der CrashAdvisoryRegistry). Andere Fehler
     * (z. B. Token-Store) propagieren weiterhin zum fehlertoleranten Aufrufer.
     */
    suspend fun start() {
        if (server != null) return
        // Port-Probe VOR dem Ktor-Bind: Ktor wirft Bind-Fehler asynchron im
        // acceptJob — runCatching beim Aufrufer kann sie nicht fangen, und ein
        // unbehandelter Fehler im SupervisorJob crasht den Prozess (z. B. zwei
        // App-Instanzen oder ein belegter Port). Die Probe scheitert stattdessen
        // synchron mit klarer Ursache; ein Port-Konflikt wird hier direkt
        // behandelt (Log statt Exception), damit kein Aufrufer-Pfad den
        // Prozess gefährden kann.
        // Port-Auswahl über die Fallback-Kette: Der erste freie Kandidat
        // (8080 → 8081 → 8082 → 8083 → ephemeral) gewinnt. Der ephemerale
        // Kandidat wird nicht probiert — der Kernel wählt beim Bind frei.
        var chosen = PortFallbackPolicy.selectPort(preferredPort) { candidate ->
            probePort(candidate)
        }
        if (chosen == preferredPort) {
            Timber.i("Web-Remote-Control: Port %d frei - Server startet wie bevorzugt.", preferredPort)
        } else {
            Timber.w(
                "Web-Remote-Control: Port %d belegt (EADDRINUSE) - Ausweichkette startet bei %d.",
                preferredPort,
                chosen,
            )
        }
        val token = tokenStore.getOrCreateToken()

        // Bind-Verifikation gegen das Probe->Bind-Rennen: Die Vorphase-Probe
        // kann zwischen Pruefung und Engine-Bind ueberholt werden (Diagnostik-
        // Fund: Probe gruen, NIO-Bind der Engine scheiterte trotzdem — die
        // BindException ging im asynchronen Engine-Job verloren). Nach jedem
        // Engine-Start wird der Bind deshalb verifiziert: Der NIO-Probe-Bind
        // auf den gewaehlten Port muss jetzt FEHLSCHLAGEN (Port belegt =
        // Engine hat gebunden). Scheitert der Kandidat, rueckt der naechste
        // nach; der ephemeralen Kernel-Wahl sind 3 Versuche vergoennt.
        // Scheitert alles, startet der Server nicht — bewusst fehlertolerant
        // ohne Throw (EADDRINUSE-Vertrag, siehe KDoc oben).
        var ephemeralAttempts = 0
        while (true) {
            if (PortFallbackPolicy.isEphemeral(chosen)) {
                // Ephemerale Auswahl vorab zu einem konkreten Port aufloesen
                // (NIO-Channel an Port 0 → Kernel-Wahl → schliessen), damit
                // activePort ansagbar bleibt.
                chosen = resolveEphemeralPort()
            }
            val newServer = embeddedServer(
                factory = CIO,
                port = chosen,
                host = "0.0.0.0",
            ) {
                remoteControlModule(streamControl, token, logStore)
            }
            newServer.start(wait = false)
            if (awaitEngineBind(chosen)) {
                server = newServer
                _activePort.value = chosen
                return
            }
            runCatching { newServer.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
            Timber.w(
                "Web-Remote-Control: Port %d wurde zwischen Probe und Bind belegt - naechster Versuch.",
                chosen,
            )
            val next = PortFallbackPolicy.nextCandidate(preferredPort, chosen)
            if (!PortFallbackPolicy.isEphemeral(next)) {
                chosen = next
            } else if (++ephemeralAttempts < 3) {
                chosen = PortFallbackPolicy.EPHEMERAL_PORT
            } else {
                Timber.w(
                    "Web-Remote-Control: Alle Port-Kandidaten scheiterten zwischen Probe und Bind - " +
                        "Server startet nicht (fehlertolerant, kein Crash).",
                )
                _activePort.value = DEFAULT_PORT
                return
            }
        }
    }

    /**
     * Loest die ephemerale Auswahl (Port 0) zu einem konkreten Port auf:
     * NIO-Channel an Port 0 binden → Kernel-Wahl → schliessen. Das kleine
     * Residual-Rennen (anderer Prozess schnappt den Port zwischendurch)
     * behandelt die Bind-Verifikation in [start].
     */
    private fun resolveEphemeralPort(): Int =
        ServerSocketChannel.open().use { channel ->
            channel.bind(InetSocketAddress("0.0.0.0", 0))
            channel.socket().localPort
        }

    /**
     * Verifiziert, dass die Engine [port] tatsaechlich gebunden hat: Der
     * NIO-Probe-Bind muss dann fehlschlagen (Port belegt). Pollt bis 2 s,
     * weil der Engine-Bind asynchron nach start(wait = false) erfolgt.
     */
    private suspend fun awaitEngineBind(port: Int): Boolean {
        val deadline = System.nanoTime() + 2_000_000_000L
        while (System.nanoTime() < deadline) {
            val probeOk = runCatching { probePort(port) }.isSuccess
            if (!probeOk) return true // Port ist belegt → die Engine hat gebunden.
            delay(50)
        }
        return false
    }

    /** Stoppt den Server, falls er läuft. */
    suspend fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        server = null
        _activePort.value = DEFAULT_PORT
    }

    companion object {
        const val DEFAULT_PORT = 8080

        /**
         * Prüft synchron, ob [port] an 0.0.0.0 gebunden werden kann.
         * Absichtlich strikt OHNE `SO_REUSEADDR`: Windows behandelt das Flag
         * wie REUSEPORT (Port-Klau möglich) — ohne das Flag schlägt die Probe
         * auf allen Plattformen deterministisch mit der originalen
         * `java.net.BindException` fehl, wenn der Port belegt ist. Falsche
         * Alarme sind sicher: Der Server startet dann einfach nicht
         * ([start] behandelt den Konflikt seit der EADDRINUSE-Härtung selbst).
         * Port als Parameter, damit Unit-Tests freie/belegte Ports durchspielen.
         */
        internal fun probePort(port: Int) {
            // Bewusst ueber NIO (ServerSocketChannel) statt java.net.ServerSocket:
            // Ktor CIO 3.5.2 bindet ebenfalls via ServerSocketChannel — nur wenn
            // Probe und Engine denselben Bind-Mechanismus nutzen, ist die Probe
            // ein verlaesslicher Freiset-Test. (java.net.ServerSocket auf Windows
            // gewinnt kontra NIO-Channels, was zu falsch-gruenen Probes fuehrt:
            // Probe gruene, NIO-Bind der Engine scheitert trotzdem.)
            ServerSocketChannel.open().use { channel ->
                channel.bind(InetSocketAddress("0.0.0.0", port))
            }
        }

        /** Default-Zeitraum für `/logs`, wenn kein/ungültiger `days`-Parameter kommt. */
        const val DEFAULT_LOG_DAYS = 1

        /** Untere Grenze für `days` (heute). */
        const val MIN_LOG_DAYS = 1

        /** Obere Grenze für `days` — deckt die maximale Vorhaltezeit (30 Tage) ab. */
        const val MAX_LOG_DAYS = 30
    }
}

/**
 * Routing-Modul der Web-Remote-Control — als eigene Funktion gekapselt,
 * damit es in Unit-Tests mit `testApplication` ohne echten Port geprüft werden kann.
 */
fun Application.remoteControlModule(
    streamControl: StreamControl,
    token: String,
    logStore: LogStore,
) {
    install(ContentNegotiation) {
        json(Json { encodeDefaults = true })
    }
    routing {
        get("/status") {
            call.respond(RemoteStatusResponse(streamControl.status.value))
        }
        post("/start") {
            if (!call.isAuthorized(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            streamControl.start()
            call.respond(RemoteActionResponse(ok = true))
        }
        post("/stop") {
            if (!call.isAuthorized(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@post
            }
            streamControl.stop()
            call.respond(RemoteActionResponse(ok = true))
        }
        get("/logs") {
            if (!call.isAuthorized(token)) {
                call.respond(HttpStatusCode.Unauthorized)
                return@get
            }
            val days = call.request.queryParameters["days"]?.toIntOrNull()
                ?: RemoteControlServer.DEFAULT_LOG_DAYS
            val clamped = days.coerceIn(
                RemoteControlServer.MIN_LOG_DAYS,
                RemoteControlServer.MAX_LOG_DAYS,
            )
            val entries = logStore.load(clamped).map { entry ->
                RemoteLogEntry(
                    timestampMillis = entry.timestampMillis,
                    level = entry.level.name,
                    tag = entry.tag,
                    message = entry.message,
                    isCrash = entry.isCrash,
                )
            }
            call.respond(RemoteLogsResponse(days = clamped, count = entries.size, entries = entries))
        }
    }
}

private fun ApplicationCall.isAuthorized(expectedToken: String): Boolean {
    val header = request.headers[HttpHeaders.Authorization] ?: return false
    val provided = header.removePrefix("Bearer ").trim()
    if (provided.isEmpty() || header == provided) return false
    val providedBytes = provided.toByteArray(Charsets.UTF_8)
    val expectedBytes = expectedToken.toByteArray(Charsets.UTF_8)
    return MessageDigest.isEqual(providedBytes, expectedBytes)
}
