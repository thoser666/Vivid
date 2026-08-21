package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchModerationClientTest {

    private val config = TwitchEventSubConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
        channel = "thoser666",
    )

    private data class Captured(val method: String, val url: String, val body: String)

    private fun client(engine: MockEngine): TwitchModerationClient {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "vividbot") } returns "111"
            coEvery { resolveUserId(any(), "thoser666") } returns "222"
            coEvery { resolveUserId(any(), "troll1") } returns "333"
            coEvery { resolveUserId(any(), "mod1") } returns "444"
        }
        return TwitchModerationClient(
            http = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            whisperClient = whisperClient,
        )
    }

    private fun engine(
        captured: MutableList<Captured>,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): MockEngine = MockEngine { request ->
        captured.add(
            Captured(
                request.method.value,
                request.url.toString(),
                request.body.toByteArray().toString(Charsets.UTF_8),
            ),
        )
        respond(
            content = "{}",
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun `ban posts the target user to the moderation bans endpoint without duration`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.ban(config, "troll1")

        assertEquals("✅ @troll1 wurde verbannt.", reply)
        assertEquals(1, captured.size)
        val request = captured.first()
        assertEquals("POST", request.method)
        assertTrue(request.url.contains("/helix/moderation/bans"), request.url)
        assertTrue(request.url.contains("broadcaster_id=222"), request.url)
        assertTrue(request.url.contains("moderator_id=111"), request.url)
        assertTrue(request.body.contains("\"user_id\":\"333\""), request.body)
        assertFalse(request.body.contains("duration"), request.body)
    }

    @Test
    fun `timeout sends the duration in seconds`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.timeout(config, "mod1", 10)

        assertEquals("⏱ @mod1 wurde für 10 Min. getimeoutet.", reply)
        assertTrue(captured.first().body.contains("\"duration\":600"), captured.first().body)
    }

    @Test
    fun `timeout without a duration defaults to five minutes`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.timeout(config, "mod1", null)

        assertEquals("⏱ @mod1 wurde für 5 Min. getimeoutet.", reply)
        assertTrue(captured.first().body.contains("\"duration\":300"), captured.first().body)
    }

    @Test
    fun `delete removes each tracked message id`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.deleteRecent(config, null, listOf("m-1", "m-2"))

        assertEquals("🗑 2 Nachricht(en) gelöscht.", reply)
        assertEquals(2, captured.size)
        assertTrue(captured.all { it.method == "DELETE" })
        assertTrue(captured.all { it.url.contains("/helix/moderation/chat") })
        assertTrue(captured.any { it.url.contains("message_id=m-1") }, captured.toString())
        assertTrue(captured.any { it.url.contains("message_id=m-2") }, captured.toString())
    }

    @Test
    fun `delete with a count picks only the last n messages`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        client.deleteRecent(config, 2, listOf("m-1", "m-2", "m-3"))

        assertEquals(2, captured.size)
        assertTrue(captured.any { it.url.contains("message_id=m-2") }, captured.toString())
        assertTrue(captured.any { it.url.contains("message_id=m-3") }, captured.toString())
        assertTrue(captured.none { it.url.contains("message_id=m-1") }, captured.toString())
    }

    @Test
    fun `delete skips messages twitch rejects and reports the count`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(
            MockEngine { request ->
                captured.add(
                    Captured(
                        request.method.value,
                        request.url.toString(),
                        request.body.toByteArray().toString(Charsets.UTF_8),
                    ),
                )
                val status = if (request.url.toString().contains("m-2")) {
                    HttpStatusCode.BadRequest // zu alt / schon gelöscht
                } else {
                    HttpStatusCode.NoContent
                }
                respond(
                    content = "",
                    status = status,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val reply = client.deleteRecent(config, null, listOf("m-1", "m-2"))

        assertEquals("🗑 1 Nachricht(en) gelöscht.", reply)
        assertEquals(2, captured.size)
    }

    @Test
    fun `delete reports when nothing could be deleted`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(
            MockEngine { request ->
                captured.add(
                    Captured(
                        request.method.value,
                        request.url.toString(),
                        request.body.toByteArray().toString(Charsets.UTF_8),
                    ),
                )
                respond(
                    content = "",
                    status = HttpStatusCode.BadRequest,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val reply = client.deleteRecent(config, null, listOf("m-1"))

        assertEquals("⚠️ Keine Nachricht gelöscht — alle waren zu alt oder bereits weg.", reply)
    }

    @Test
    fun `delete without tracked messages answers without a request`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.deleteRecent(config, null, emptyList())

        assertEquals("Keine Nachrichten im Puffer zum Löschen — der Bot muss die Nachrichten zuerst gesehen haben.", reply)
        assertEquals(0, captured.size)
    }

    @Test
    fun `ban with a missing auth scope throws a descriptive exception`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured, HttpStatusCode.Unauthorized))

        val exception = assertThrows(TwitchModerationException::class.java) {
            runBlocking { client.ban(config, "troll1") }
        }
        assertTrue(exception.message.orEmpty().contains("moderator:manage:banned_users"), exception.message.orEmpty())
    }

    @Test
    fun `ban of an unknown user is reported without a request`() = runTest {
        val captured = mutableListOf<Captured>()
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "vividbot") } returns "111"
            coEvery { resolveUserId(any(), "thoser666") } returns "222"
            coEvery { resolveUserId(any(), "ghost") } returns null
        }
        val client = TwitchModerationClient(
            http = HttpClient(engine(captured)) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            whisperClient = whisperClient,
        )

        val reply = client.ban(config, "ghost")

        assertEquals("❌ User ghost konnte nicht gefunden werden.", reply)
        assertEquals(0, captured.size)
    }

    @Test
    fun `ban without a user shows the usage hint`() = runTest {
        val captured = mutableListOf<Captured>()
        val client = client(engine(captured))

        val reply = client.ban(config, "  @  ")

        assertEquals("Bitte gib einen Benutzernamen an: !ban <user>", reply)
        assertEquals(0, captured.size)
    }

}
