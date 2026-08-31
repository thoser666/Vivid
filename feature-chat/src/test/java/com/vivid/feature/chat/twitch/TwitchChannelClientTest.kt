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
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchChannelClientTest {
    private val config = TwitchChannelConfig("streamer", "oauth:token", "client-id")

    private fun client(
        responses: MutableList<String>,
        broadcasterId: String? = "123",
    ): TwitchChannelClient {
        val userClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "streamer") } returns broadcasterId
        }
        val engine = MockEngine { request ->
            responses += request.url.toString() + "\n" + request.body.toByteArray().toString(Charsets.UTF_8)
            when {
                request.url.encodedPath.endsWith("/streams") -> respond(
                    """{"data":[{"viewer_count":42,"title":"Live in Berlin","game_name":"Just Chatting"}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                request.url.encodedPath.endsWith("/search/categories") -> respond(
                    """{"data":[{"id":"509658","name":"Just Chatting"}]}""",
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
                else -> respond(
                    "",
                    status = HttpStatusCode.NoContent,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            }
        }
        return TwitchChannelClient(
            http = HttpClient(engine) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } },
            whisperClient = userClient,
        )
    }

    @Test
    fun `loads live viewer count and metadata`() = runTest {
        val requests = mutableListOf<String>()

        val info = client(requests).getStreamInfo(config)

        assertEquals(TwitchStreamInfo(42, "Live in Berlin", "Just Chatting"), info)
        assertTrue(requests.first().contains("user_id=123"))
    }

    @Test
    fun `updates title and resolves category to a game id`() = runTest {
        val requests = mutableListOf<String>()

        client(requests).updateChannelInfo(config, " Neuer Titel ", "Just Chatting")

        val patch = requests.last()
        assertTrue(patch.contains("/channels?broadcaster_id=123"), patch)
        assertTrue(patch.contains("\"game_id\":\"509658\""), patch)
        assertTrue(patch.contains("\"title\":\"Neuer Titel\""), patch)
    }

    @Test
    fun `rejects an unconfigured channel before making a request`() = runTest {
        val exception = assertThrows(TwitchChannelException::class.java) {
            kotlinx.coroutines.runBlocking {
                client(mutableListOf()).updateChannelInfo(
                    config.copy(clientId = ""),
                    title = "Titel",
                    category = "",
                )
            }
        }

        assertTrue(exception.message.orEmpty().contains("nicht konfiguriert"))
    }

    @Test
    fun `rejects incomplete configuration when loading stream info`() = runTest {
        val requests = mutableListOf<String>()

        val exception = assertThrows(TwitchChannelException::class.java) {
            kotlinx.coroutines.runBlocking {
                client(requests).getStreamInfo(config.copy(oauthToken = ""))
            }
        }

        assertTrue(exception.message.orEmpty().contains("nicht konfiguriert"))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `returns null when the broadcaster is offline`() = runTest {
        val userClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "streamer") } returns "123"
        }
        val http = HttpClient(MockEngine {
            respond(
                """{"data":[]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }) { install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) } }
        val channelClient = TwitchChannelClient(http, userClient)

        assertEquals(null, channelClient.getStreamInfo(config))
    }

    @Test
    fun `maps unauthorized api responses to a useful error`() = runTest {
        val userClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "streamer") } returns "123"
        }
        val http = HttpClient(MockEngine {
            respond("", status = HttpStatusCode.Unauthorized)
        })
        val channelClient = TwitchChannelClient(http, userClient)

        val exception = assertThrows(TwitchChannelException::class.java) {
            kotlinx.coroutines.runBlocking { channelClient.getStreamInfo(config) }
        }
        assertTrue(exception.message.orEmpty().contains("401"))
    }
}
