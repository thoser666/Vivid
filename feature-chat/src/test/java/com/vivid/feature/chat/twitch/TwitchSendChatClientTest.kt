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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchSendChatClientTest {

    private val config = TwitchEventSubConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
        channel = "thoser666",
    )

    private fun client(engine: MockEngine): TwitchSendChatClient {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "vividbot") } returns "111"
            coEvery { resolveUserId(any(), "thoser666") } returns "222"
        }
        return TwitchSendChatClient(
            http = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            whisperClient = whisperClient,
        )
    }

    private fun engine(
        requests: MutableList<String>,
        responseBody: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): MockEngine = MockEngine { request ->
        requests.add(request.body.toByteArray().toString(Charsets.UTF_8))
        // ContentNegotiation braucht application/json — MockEngine antwortet
        // sonst mit text/plain und die body<T>-Transformation schlägt fehl.
        respond(
            content = responseBody,
            status = status,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun `sends a message with broadcaster and sender id`() = runTest {
        val requests = mutableListOf<String>()
        val client = client(engine(requests, """{"data":[{"message_id":"m-1","is_sent":true}]}"""))

        val result = client.send(config, "Hallo Chat!")

        assertTrue(result.isSent)
        assertEquals("m-1", result.messageId)
        assertNull(result.dropReason)
        assertEquals(1, requests.size)
        val body = requests.first()
        assertTrue(body.contains("\"broadcaster_id\":\"222\""), body)
        assertTrue(body.contains("\"sender_id\":\"111\""), body)
        assertTrue(body.contains("\"message\":\"Hallo Chat!\""), body)
    }

    @Test
    fun `surfaces the drop reason when twitch rejects the message`() = runTest {
        val client = client(
            engine(
                mutableListOf(),
                """{"data":[{"message_id":"","is_sent":false,"drop_reason":{"code":"slow","message":"Nachricht verworfen"}}]}""",
            ),
        )

        val result = client.send(config, "noch eine Nachricht")

        assertFalse(result.isSent)
        assertEquals("Nachricht verworfen", result.dropReason)
    }

    @Test
    fun `throws when the api returns an error status`() = runTest {
        val client = client(engine(mutableListOf(), "", HttpStatusCode.Unauthorized))

        val exception = assertThrows(TwitchSendChatException::class.java) {
            runBlocking { client.send(config, "test") }
        }
        assertTrue(exception.message.orEmpty().contains("401"))
    }

    @Test
    fun `throws when the bot user id cannot be resolved`() = runTest {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "vividbot") } returns null
        }
        val client = TwitchSendChatClient(
            http = HttpClient(MockEngine { error("no request expected") }),
            whisperClient = whisperClient,
        )

        val exception = assertThrows(TwitchSendChatException::class.java) {
            runBlocking { client.send(config, "test") }
        }
        assertTrue(exception.message.orEmpty().contains("Bot-User-ID"))
    }

    @Test
    fun `sanitizes and caps the message length`() = runTest {
        val requests = mutableListOf<String>()
        val client = client(engine(requests, """{"data":[{"message_id":"m-1","is_sent":true}]}"""))
        val longText = "Zeile 1\nZeile 2 " + "x".repeat(600)

        client.send(config, longText)

        val body = requests.first()
        val sent = Regex("\"message\":\"([^\"]*)\"").find(body)!!.groupValues[1]
        assertFalse(sent.contains("\n"))
        assertFalse(sent.contains("\r"))
        assertTrue(sent.length <= TwitchSendChatClient.MAX_MESSAGE_LENGTH)
    }
}
