package com.vivid.core.remote

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

/**
 * Startet die Web-Remote-Control über LAN.
 *
 * Endpunkte (alle mit `Authorization: Bearer <token>` für Aktionen):
 *  - `GET  /status`   → aktueller Stream-Status als JSON
 *  - `POST /start`    → Stream mit gespeicherten Einstellungen starten
 *  - `POST /stop`     → Stream stoppen
 */
@Singleton
class RemoteControlServer @Inject constructor(
    private val streamControl: StreamControl,
    private val tokenStore: RemoteControlTokenStore,
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
            remoteControlModule(streamControl, token)
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
    }
}

/**
 * Routing-Modul der Web-Remote-Control — als eigene Funktion gekapselt,
 * damit es in Unit-Tests mit `testApplication` ohne echten Port geprüft werden kann.
 */
fun Application.remoteControlModule(
    streamControl: StreamControl,
    token: String,
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
