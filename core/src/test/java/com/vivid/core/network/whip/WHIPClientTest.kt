package com.vivid.core.network.whip

import com.vivid.core.network.KtorClientFactory
import com.vivid.core.network.awaitPortListening
import com.vivid.core.network.canConnectNow
import io.ktor.client.HttpClient
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.contentType
import io.ktor.server.request.path
import io.ktor.server.request.receiveText
import io.ktor.server.response.header
import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.delete
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import java.net.ServerSocket
import java.util.concurrent.atomic.AtomicInteger

/**
 * Echte HTTP-End-to-End-Tests für den WHIP-Client (RFC 9725): Ktor-Testserver
 * (CIO, zufälliger Port) als WHIP-Ersatz — dieselbe Infrastruktur wie die
 * OBS-E2E-Tests (Issue #226). Vertragsbasis sind die P0-MediaMTX-Befunde
 * (docs/whip-spike.md): 201 + Location + ETag, `text/plain`-Answer-Fallback,
 * PATCH mit If-Match/412, DELETE mit 404-Toleranz (ICE-Deadline: MediaMTX
 * reappt Sessions ohne ICE-Verbindung nach ~10 s — empirisch verifiziert).
 *
 * Hausmuster (#226): koroutinenfreie Testkörper über runTest auf echtem
 * Socket-I/O, blockierende Polls vermeiden, Teardown schließt HttpClient und
 * Server explizit (CIO-Threads sind nicht-daemonisch).
 *
 * Hausmuster (#268): `startServer()` wartet über `awaitPortListening` auf den
 * tatsächlichen Bind, bevor der erste HTTP-Request rausgeht. `WHIPClient` hat
 * keinen Request-Retry — ein `connect refused` wird direkt zu
 * `WHIPFailure.NETWORK`, `publish()` liefert `null`, und der Test scheitert am
 * Transport statt am WHIP-Vertrag.
 */
@Timeout(30)
class WHIPClientTest {

    private var server: EmbeddedServer<*, *>? = null
    private var port = 0
    private var lastHttpClient: HttpClient? = null

    private val applicationSdp = ContentType("application", "sdp")

    private val offerSdp = "v=0\r\n" +
        "o=- 4611731400430051336 2 IN IP4 127.0.0.1\r\n" +
        "s=-\r\n" +
        "t=0 0\r\n" +
        "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
        "c=IN IP4 0.0.0.0\r\n" +
        "a=ice-ufrag:EsAw\r\n" +
        "a=ice-pwd:P2uYro0UCOQ4zxjKXaWCBui1\r\n" +
        "a=fingerprint:sha-256 4A:AD:36:50:83:16:4E:85:4A:53:16:73:8F:FF:B6:1A:68:B2:4B:12:1C:59:6C:82:9B:20:EA:B5:90:80:EC:60\r\n" +
        "a=setup:actpass\r\n" +
        "a=mid:0\r\n" +
        "a=sendonly\r\n" +
        "a=rtcp-mux\r\n" +
        "a=rtpmap:96 H264/90000\r\n"

    private val answerSdp = "v=0\r\n" +
        "o=- 993578300321551957 1790777794 IN IP4 0.0.0.0\r\n" +
        "s=-\r\n" +
        "t=0 0\r\n" +
        "m=video 9 UDP/TLS/RTP/SAVPF 96\r\n" +
        "c=IN IP4 0.0.0.0\r\n" +
        "a=setup:active\r\n" +
        "a=mid:0\r\n" +
        "a=recvonly\r\n" +
        "a=rtcp-mux\r\n" +
        "a=rtpmap:96 H264/90000\r\n"

    @AfterEach
    fun tearDown() {
        // Nicht-daemonische CIO-Threads freigeben, sonst hängt der Test-Worker (#226).
        runCatching { lastHttpClient?.close() }
        runCatching { server?.stop(gracePeriodMillis = 100, timeoutMillis = 1_000) }
        server = null
        lastHttpClient = null
    }

    private fun newClient(): WHIPClient {
        val http = KtorClientFactory.create().also { lastHttpClient = it }
        return WHIPClient(http)
    }

    private fun startServer(route: Routing.() -> Unit) {
        // Freien Port vorab reservieren (resolvedConnectors steht in Ktor 3.6
        // nicht zur Verfügung); die Reservierung ist sofort wieder frei.
        val freePort = ServerSocket(0).use { it.localPort }
        val s = embeddedServer(CIO, port = freePort, host = "127.0.0.1") { routing { route() } }
        s.start(wait = false)
        port = freePort
        server = s
        // #268: Der Port ist mit `ServerSocket(0)` nur *reserviert*; CIO bindet
        // asynchron, `start(wait = false)` kehrt vorher zurück. Dieselbe Schranke
        // wie in den OBS-E2E-Tests (#264).
        check(awaitPortListening(freePort)) {
            "CIO-Server hat den Port $freePort nicht innerhalb von 5s gebunden"
        }
    }

    private fun endpoint() = "http://127.0.0.1:$port/mystream/whip"

    @Test
    fun `publish receives 201 with location etag and answer sdp`() = runTest {
        var receivedOffer: String? = null
        var receivedContentType: ContentType? = null
        startServer {
            post("/mystream/whip") {
                receivedOffer = call.receiveText()
                receivedContentType = call.request.contentType()
                call.response.header(HttpHeaders.Location, "/mystream/whip/session-1")
                call.response.header(HttpHeaders.ETag, "etag-1")
                call.response.header("Accept-Patch", "application/trickle-ice-sdpfrag")
                call.respondText(answerSdp, applicationSdp, HttpStatusCode.Created)
            }
        }
        val client = newClient()
        val resource = client.publish(endpoint(), offerSdp)

        assertNotNull(resource)
        assertEquals(offerSdp, receivedOffer)
        assertEquals(applicationSdp, receivedContentType)
        assertEquals("http://127.0.0.1:$port/mystream/whip/session-1", resource!!.sessionUrl)
        assertEquals("etag-1", resource.etag)
        assertEquals(answerSdp, resource.answerSdp)
        assertTrue(client.state.value is WHIPState.Live)
    }

    @Test
    fun `publish accepts text plain answer bodies`() = runTest {
        startServer {
            post("/mystream/whip") {
                call.response.header(HttpHeaders.Location, "/mystream/whip/s")
                call.respondText(answerSdp, ContentType.Text.Plain, HttpStatusCode.Created)
            }
        }
        val client = newClient()
        val resource = client.publish(endpoint(), offerSdp)
        assertNotNull(resource)
        assertEquals(answerSdp, resource!!.answerSdp)
    }

    @Test
    fun `error statuses map to the failure taxonomy`() = runTest {
        val cases = listOf(
            HttpStatusCode.Unauthorized to WHIPFailure.UNAUTHORIZED,
            HttpStatusCode.Forbidden to WHIPFailure.FORBIDDEN,
            HttpStatusCode.TooManyRequests to WHIPFailure.RATE_LIMITED,
        )
        val counter = AtomicInteger(0)
        startServer {
            post("/mystream/whip") {
                val status = cases[counter.getAndIncrement()].first
                call.respondText("err", ContentType.Text.Plain, status)
            }
        }
        for ((status, reason) in cases) {
            val client = newClient()
            val resource = client.publish(endpoint(), offerSdp)
            assertNull(resource, "erwartet null für $status")
            val state = client.state.value
            assertTrue(state is WHIPState.Failed, "erwartet Failed für $status")
            assertEquals(reason, (state as WHIPState.Failed).reason, "falsche Taxonomie für $status")
        }
    }

    @Test
    fun `patch sends if-match and treats 412 as precondition failure`() = runTest {
        var ifMatch: String? = null
        startServer {
            patch("/mystream/whip/s1") {
                ifMatch = call.request.headers[HttpHeaders.IfMatch]
                if (ifMatch == "etag-ok") {
                    call.respondText("ok", ContentType.Text.Plain, HttpStatusCode.OK)
                } else {
                    call.respondText("conflict", ContentType.Text.Plain, HttpStatusCode.PreconditionFailed)
                }
            }
        }
        val client = newClient()

        val live = WHIPResource("${endpoint()}/s1", "etag-ok", "")
        assertTrue(client.patchCandidates(live, "a=ice-ufrag:x\r\n"))
        assertEquals("etag-ok", ifMatch)

        val stale = WHIPResource("${endpoint()}/s1", "etag-stale", "")
        assertFalse(client.patchCandidates(stale, "a=ice-ufrag:x\r\n"))
        val state = client.state.value
        assertTrue(state is WHIPState.Failed && state.reason == WHIPFailure.PRECONDITION_FAILED)
    }

    @Test
    fun `delete follows the session lifecycle and tolerates 404`() = runTest {
        val sessionAlive = java.util.concurrent.atomic.AtomicBoolean(false)
        startServer {
            post("/mystream/whip") {
                sessionAlive.set(true)
                call.response.header(HttpHeaders.Location, "/mystream/whip/s1")
                call.respondText(answerSdp, applicationSdp, HttpStatusCode.Created)
            }
            delete("/mystream/whip/s1") {
                if (sessionAlive.compareAndSet(true, false)) {
                    call.respondText("bye", ContentType.Text.Plain, HttpStatusCode.OK)
                } else {
                    // MediaMTX-Präzedenz: reappte Session (ICE-Deadline) → 404.
                    call.respondText("gone", ContentType.Text.Plain, HttpStatusCode.NotFound)
                }
            }
        }
        val client = newClient()

        assertNotNull(client.publish(endpoint(), offerSdp))
        assertTrue(client.terminate())
        assertEquals(WHIPState.Closed, client.state.value)

        // Zweites terminate ohne Resource: true, ohne Server-Call.
        assertTrue(client.terminate())

        // Neue Session, Server reappt sie (ICE-Deadline-Simulation) → DELETE 404 → trotzdem true.
        assertNotNull(client.publish(endpoint(), offerSdp))
        sessionAlive.set(false)
        assertTrue(client.terminate())
    }

    @Test
    fun `server port accepts connections as soon as startServer returns`() {
        // #268: Der Bind-Race. `ServerSocket(0)` reserviert nur, CIO bindet
        // asynchron. Fällt dieser Test, war startServer() zu früh zurück und
        // jeder weitere E2E-Test dieser Klasse trifft ins Leere.
        startServer {
            post("/mystream/whip") {
                call.response.header(HttpHeaders.Location, "/mystream/whip/s")
                call.respondText(answerSdp, applicationSdp, HttpStatusCode.Created)
            }
        }

        assertTrue(
            canConnectNow(port),
            "startServer() muss erst zurückkehren, wenn der Port wirklich lauscht",
        )
    }

    @Test
    fun `publish on a dead endpoint fails tolerant with network reason`() = runTest {
        val deadPort = ServerSocket(0).use { it.localPort }
        val client = newClient()
        val resource = client.publish("http://127.0.0.1:$deadPort/mystream/whip", offerSdp)
        assertNull(resource)
        val state = client.state.value
        assertTrue(state is WHIPState.Failed && state.reason == WHIPFailure.NETWORK)
    }

    @Test
    fun `resolveSessionUrl handles absolute relative and bare locations`() {
        assertEquals("http://h:1/a/b", WHIPClient.resolveSessionUrl("http://h:1/whip", "/a/b"))
        assertEquals("http://h:1/a/b", WHIPClient.resolveSessionUrl("http://h:1/whip", "a/b"))
        assertEquals("https://x/y", WHIPClient.resolveSessionUrl("http://h:1/whip", "https://x/y"))
        assertEquals("a/b", WHIPClient.resolveSessionUrl("kein-scheme", "a/b"))
        assertEquals("http://h:1/whip", WHIPClient.resolveSessionUrl("http://h:1", "/whip"))
    }
}
