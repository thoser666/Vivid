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

    /** Port, auf dem der Server lauscht (standardmäßig 8080). */
    val port: Int = DEFAULT_PORT

    val isRunning: Boolean get() = server != null

    /** Startet den Server (idempotent). Läuft asynchron weiter. */
    suspend fun start() {
        if (server != null) return
        val token = tokenStore.getOrCreateToken()
        val newServer = embeddedServer(
            factory = CIO,
            port = port,
            host = "0.0.0.0",
        ) {
            remoteControlModule(streamControl, token, logStore)
        }
        newServer.start(wait = false)
        server = newServer
    }

    /** Stoppt den Server, falls er läuft. */
    suspend fun stop() {
        server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000)
        server = null
    }

    companion object {
        const val DEFAULT_PORT = 8080

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
