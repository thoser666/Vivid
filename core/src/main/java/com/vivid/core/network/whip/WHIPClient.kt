package com.vivid.core.network.whip

import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/** Session-Zustand nach RFC 9725 §4.4–4.7 (Fehler-Taxonomie für die UI-Anzeige). */
sealed interface WHIPState {
    data object Idle : WHIPState
    data class Connecting(val endpoint: String) : WHIPState
    data class Live(val resourceUrl: String) : WHIPState
    data class Failed(val reason: WHIPFailure, val detail: String? = null) : WHIPState
    data object Closed : WHIPState
}

enum class WHIPFailure {
    NETWORK, PROTOCOL, UNAUTHORIZED, FORBIDDEN, RATE_LIMITED, PRECONDITION_FAILED, REDIRECT_LOOP
}

/** Ergebnis des WHIP-POST: Session-URI (Location) + ETag + SDP-Answer. */
data class WHIPResource(val sessionUrl: String, val etag: String?, val answerSdp: String)

/**
 * WHIP-Client (RFC 9725) — P0-Spike (Analysedokument docs/whip-spike.md §5).
 *
 * Signalisierung über Ktor CIO nach dem OBSWebSocketClient-Muster (Issue #226):
 * injizierter [HttpClient], Generation-Counter gegen veraltete Rücksetzungen,
 * StateFlow-Zustand, fehlertoleranter Vertrag (kein Throw an den Aufrufer,
 * Fehler landen als [WHIPState.Failed] im Flow), [shutdown] für Test-Hosts.
 *
 * Redirects (RFC 9725 §4.8: 307/308 methode-erhaltend folgen) übernimmt das
 * Ktor-HttpRedirect-Plugin (im Standard-Client aktiv, RFC-konformes Verhalten).
 *
 * Bewusst OHNE org.webrtc-Abhängigkeit: Der komplette HTTP-Contract
 * (POST/PATCH/DELETE, Bearer, ETag) ist JVM-testbar; die Media-Pipeline
 * (PeerConnection) kommt erst in P1 als WHIPStreamRoute in feature-streaming.
 */
@Singleton
class WHIPClient @Inject constructor(
    private val httpClient: HttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val generation = AtomicInteger(0)
    private val _state = MutableStateFlow<WHIPState>(WHIPState.Idle)
    val state: StateFlow<WHIPState> = _state.asStateFlow()

    private var resource: WHIPResource? = null

    /**
     * RFC 9725 §4.4: POST mit SDP-Offer (`application/sdp`) an den Endpoint;
     * `201 Created` + `Location` (Session-URI) + Body = SDP-Answer
     * (`application/sdp` oder `text/plain` — beides wird akzeptiert).
     * Liefert `null` bei Fehler (Zustand dann [WHIPState.Failed]).
     */
    suspend fun publish(endpoint: String, sdpOffer: String, bearer: String? = null): WHIPResource? {
        val gen = generation.get()
        _state.value = WHIPState.Connecting(endpoint)
        try {
            val response: HttpResponse = httpClient.post(endpoint) {
                bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                setBody(TextContent(sdpOffer, APPLICATION_SDP))
            }
            if (response.status != HttpStatusCode.Created) {
                val detail = runCatching { response.bodyAsText() }.getOrNull()?.take(MAX_DETAIL)
                if (generation.get() == gen) {
                    _state.value = WHIPState.Failed(mapFailure(response.status), detail)
                }
                Timber.w("WHIP publish abgelehnt: HTTP ${response.status}")
                return null
            }
            val location = response.headers[HttpHeaders.Location]
            if (location.isNullOrEmpty()) {
                if (generation.get() == gen) {
                    _state.value = WHIPState.Failed(WHIPFailure.PROTOCOL, "201 ohne Location-Header")
                }
                Timber.w("WHIP publish: 201 ohne Location-Header (Endpoint nicht RFC-konform)")
                return null
            }
            val created = WHIPResource(
                sessionUrl = resolveSessionUrl(endpoint, location),
                etag = response.headers[HttpHeaders.ETag],
                answerSdp = response.bodyAsText(),
            )
            resource = created
            if (generation.get() == gen) {
                _state.value = WHIPState.Live(created.sessionUrl)
            }
            Timber.i("WHIP session created: ${created.sessionUrl}")
            return created
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (generation.get() == gen) {
                _state.value = WHIPState.Failed(WHIPFailure.NETWORK, t.message?.take(MAX_DETAIL))
                Timber.e(t, "WHIP publish fehlgeschlagen")
            }
            return null
        }
    }

    /**
     * RFC 9725 §4.6: Trickle-ICE-Kandidaten via PATCH (`application/trickle-ice-sdpfrag`,
     * `If-Match` aus dem ETag der POST-Antwort). `true` nur bei HTTP 200;
     * 412 (Precondition Failed) setzt den Failed-Zustand und liefert `false`.
     */
    suspend fun patchCandidates(resource: WHIPResource, sdpFragment: String, bearer: String? = null): Boolean {
        val gen = generation.get()
        return try {
            val response = httpClient.patch(resource.sessionUrl) {
                bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
                header(HttpHeaders.IfMatch, resource.etag ?: "*")
                setBody(TextContent(sdpFragment, TRICKLE_ICE))
            }
            when (response.status) {
                HttpStatusCode.OK -> true
                HttpStatusCode.PreconditionFailed -> {
                    if (generation.get() == gen) {
                        _state.value = WHIPState.Failed(WHIPFailure.PRECONDITION_FAILED, "ETag-Konflikt")
                    }
                    false
                }
                else -> {
                    if (generation.get() == gen) {
                        _state.value = WHIPState.Failed(mapFailure(response.status), null)
                    }
                    false
                }
            }
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (generation.get() == gen) {
                _state.value = WHIPState.Failed(WHIPFailure.NETWORK, t.message?.take(MAX_DETAIL))
                Timber.e(t, "WHIP PATCH fehlgeschlagen")
            }
            false
        }
    }

    /**
     * RFC 9725 §4.5: DELETE auf die Session-URI. 200/404 gelten als beendet
     * (404 tolerant — die Session kann serverseitig bereits weg sein,
     * z. B. ICE-Deadline; empirisch am MediaMTX-Testserver verifiziert).
     */
    suspend fun terminate(bearer: String? = null): Boolean {
        val gen = generation.get()
        val res = resource
        resource = null
        if (res == null) {
            if (generation.get() == gen) _state.value = WHIPState.Closed
            return true
        }
        val result = try {
            val response = httpClient.delete(res.sessionUrl) {
                bearer?.let { header(HttpHeaders.Authorization, "Bearer $it") }
            }
            response.status == HttpStatusCode.OK || response.status == HttpStatusCode.NotFound
        } catch (t: Throwable) {
            if (t is CancellationException) throw t
            if (generation.get() == gen) Timber.e(t, "WHIP DELETE fehlgeschlagen")
            false
        }
        if (generation.get() == gen) _state.value = WHIPState.Closed
        Timber.i("WHIP session terminated: ${res.sessionUrl}")
        return result
    }

    /** Test-/Host-Teardown — Vertrag wie OBSWebSocketClient.shutdown(). */
    internal fun shutdown() {
        generation.incrementAndGet()
        scope.cancel()
    }

    private fun mapFailure(status: HttpStatusCode): WHIPFailure = when (status) {
        HttpStatusCode.Unauthorized -> WHIPFailure.UNAUTHORIZED
        HttpStatusCode.Forbidden -> WHIPFailure.FORBIDDEN
        HttpStatusCode.TooManyRequests -> WHIPFailure.RATE_LIMITED
        else -> WHIPFailure.PROTOCOL
    }

    companion object {
        private const val MAX_DETAIL = 200
        private val APPLICATION_SDP = ContentType("application", "sdp")
        private val TRICKLE_ICE = ContentType("application", "trickle-ice-sdpfrag")

        /** Location absolut auflösen (RFC 9725 §4.4 erlaubt relative URIs). */
        internal fun resolveSessionUrl(endpoint: String, location: String): String {
            if (location.startsWith("http://") || location.startsWith("https://")) return location
            val schemeEnd = endpoint.indexOf("://")
            if (schemeEnd < 0) return location
            val pathStart = endpoint.indexOf('/', schemeEnd + 3)
            val origin = if (pathStart < 0) endpoint else endpoint.substring(0, pathStart)
            return if (location.startsWith("/")) origin + location else "$origin/$location"
        }
    }
}
