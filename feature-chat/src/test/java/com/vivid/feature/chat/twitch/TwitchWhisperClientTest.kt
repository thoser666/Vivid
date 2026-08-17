package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchWhisperClientTest {

    private val config = TwitchWhisperConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
    )

    private fun client(engine: MockEngine): TwitchWhisperClient =
        TwitchWhisperClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    /** MockEngine, die GET /users beantwortet und POST /whispers aufzeichnet. */
    private fun engine(
        usersStatus: HttpStatusCode = HttpStatusCode.OK,
        whisperStatus: HttpStatusCode = HttpStatusCode.NoContent,
    ): Pair<MockEngine, MutableList<Pair<String, String>>> {
        val whisperRequests = mutableListOf<Pair<String, String>>()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users") -> {
                    if (!usersStatus.isSuccess()) {
                        respond("""{"error":"Unauthorized","status":401}""", usersStatus)
                    } else {
                        val login = request.url.parameters["login"]
                        val id = if (login == "vividbot") "111" else "222"
                        respond(
                            content = """{"data":[{"id":"$id","login":"$login"}]}""",
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json"),
                        )
                    }
                }
                request.method == HttpMethod.Post && request.url.encodedPath.endsWith("/whispers") -> {
                    val body = request.body.toByteArray().toString(Charsets.UTF_8)
                    val url = request.url.toString()
                    whisperRequests.add(url to body)
                    if (!whisperStatus.isSuccess()) {
                        respond("""{"error":"Bad Request","status":400}""", whisperStatus)
                    } else {
                        respond("", whisperStatus)
                    }
                }
                else -> respond("", HttpStatusCode.NotFound)
            }
        }
        return engine to whisperRequests
    }

    @Test
    fun `sends a whisper via the helix api with resolved user ids`() = runTest {
        val (mockEngine, whisperRequests) = engine()
        val client = client(mockEngine)

        client.whisper(config, "streamer2", "Hallo privat!")

        assertEquals(1, whisperRequests.size)
        val (url, body) = whisperRequests.first()
        assertTrue(url.contains("from_user_id=111"), "from_user_id muss die Bot-User-ID sein: $url")
        assertTrue(url.contains("to_user_id=222"), "to_user_id muss die Ziel-User-ID sein: $url")
        assertTrue(body.contains("\"message\":\"Hallo privat!\""))
    }

    @Test
    fun `sends bearer auth and the client id header`() = runTest {
        var authorization: String? = null
        var clientId: String? = null
        val engine = MockEngine { request ->
            if (request.url.encodedPath.endsWith("/users")) {
                authorization = request.headers[HttpHeaders.Authorization]
                clientId = request.headers["Client-Id"]
                respond(
                    content = """{"data":[{"id":"111","login":"vividbot"}]}""",
                    status = HttpStatusCode.OK,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            } else {
                authorization = request.headers[HttpHeaders.Authorization]
                clientId = request.headers["Client-Id"]
                respond("", HttpStatusCode.NoContent)
            }
        }
        val client = client(engine)

        client.whisper(config, "streamer2", "Hallo")

        assertEquals("Bearer tok123", authorization, "oauth:-Präfix muss entfernt werden")
        assertEquals("client-abc", clientId)
    }

    @Test
    fun `sanitizes and truncates the whisper message`() = runTest {
        val (mockEngine, whisperRequests) = engine()
        val client = client(mockEngine)

        client.whisper(config, "streamer2", "  zeile eins\nzeile zwei  ${"x".repeat(600)}")

        val body = whisperRequests.first().second
        assertFalse(body.contains("\\n"), "Zeilenumbrüche müssen entfernt werden")
        assertTrue(body.length <= 550, "Nachricht darf das 500-Zeichen-Limit nicht überschreiten")
    }

    @Test
    fun `throws when the client id is missing`() = runTest {
        val client = client(engine().first)

        val exception = runCatching { client.whisper(config.copy(clientId = ""), "streamer2", "Hallo") }.exceptionOrNull()

        assertTrue(exception is TwitchWhisperException)
        assertTrue(exception!!.message!!.contains("Client-ID"))
    }

    @Test
    fun `throws with a helpful message when the token lacks the whisper scope`() = runTest {
        val client = client(engine(whisperStatus = HttpStatusCode.Unauthorized).first)

        val exception = runCatching { client.whisper(config, "streamer2", "Hallo") }.exceptionOrNull()

        assertTrue(exception is TwitchWhisperException)
        assertTrue(exception!!.message!!.contains("user:manage:whispers"))
    }

    @Test
    fun `throws when the recipient blocks whispers from strangers`() = runTest {
        val client = client(engine(whisperStatus = HttpStatusCode.BadRequest).first)

        val exception = runCatching { client.whisper(config, "streamer2", "Hallo") }.exceptionOrNull()

        assertTrue(exception is TwitchWhisperException)
        assertTrue(exception!!.message!!.contains("blockt Whispers"))
    }

    @Test
    fun `resolves the target user id only once per login`() = runTest {
        var usersCalls = 0
        val whisperRequests = mutableListOf<String>()
        val engine = MockEngine { request ->
            when {
                request.url.encodedPath.endsWith("/users") -> {
                    usersCalls += 1
                    val login = request.url.parameters["login"]
                    respond(
                        content = """{"data":[{"id":"222","login":"$login"}]}""",
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "application/json"),
                    )
                }
                else -> {
                    whisperRequests.add(request.url.toString())
                    respond("", HttpStatusCode.NoContent)
                }
            }
        }
        val client = client(engine)

        client.whisper(config, "streamer2", "eins")
        client.whisper(config, "Streamer2", "zwei")

        assertEquals(2, whisperRequests.size)
        // Erster Whisper: Bot-ID + Ziel-ID auflösen (2 Calls); der zweite
        // Whisper mit normalisiertem Login trifft nur noch den Cache.
        assertEquals(2, usersCalls, "Der Cache muss die zweite Auflösung sparen (normalisierter Login)")
    }
}
