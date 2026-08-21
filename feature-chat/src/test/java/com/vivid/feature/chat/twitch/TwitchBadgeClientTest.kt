package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchBadgeClientTest {

    private val config = TwitchEventSubConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
        channel = "thoser666",
    )

    private val globalBadges = """
        {"data":[{"set_id":"moderator","versions":[
          {"id":"1","image_url_1x":"https://cdn/mod/1","image_url_2x":"https://cdn/mod/2","image_url_4x":"https://cdn/mod/4","title":"Moderator","description":"Moderator"}
        ]}]}
    """.trimIndent()

    private val channelBadges = """
        {"data":[
          {"set_id":"broadcaster","versions":[{"id":"1","image_url_1x":"https://cdn/bc/1","image_url_2x":"https://cdn/bc/2","title":"Broadcaster"}]},
          {"set_id":"subscriber","versions":[{"id":"6","image_url_1x":"https://cdn/sub6/1","image_url_2x":"https://cdn/sub6/2","title":"Subscriber (6 months)"}]}
        ]}
    """.trimIndent()

    private fun client(
        engine: MockEngine,
        broadcasterId: String? = "222",
    ): TwitchBadgeClient {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "thoser666") } returns broadcasterId
        }
        return TwitchBadgeClient(
            http = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
            whisperClient = whisperClient,
        )
    }

    private fun engine(
        requests: MutableList<String>,
        global: String = globalBadges,
        channel: String = channelBadges,
    ): MockEngine = MockEngine { request ->
        val url = request.url.toString()
        requests.add(url)
        val body = when {
            url.contains("/chat/badges/global") -> global
            url.contains("/chat/badges?") -> channel
            else -> ""
        }
        respond(
            content = body,
            status = HttpStatusCode.OK,
            headers = headersOf(HttpHeaders.ContentType, "application/json"),
        )
    }

    @Test
    fun `loads global and channel badges into the set-id keyed map`() = runTest {
        val requests = mutableListOf<String>()
        val client = client(engine(requests))

        val badges = client.load(config)

        assertEquals(3, badges.size)
        val bc = badges["broadcaster/1"]!!
        assertEquals("Broadcaster", bc.title)
        assertTrue(bc.imageUrl.contains("/2"), bc.imageUrl)
        assertEquals("Moderator", badges["moderator/1"]!!.title)
        assertEquals("Subscriber (6 months)", badges["subscriber/6"]!!.title)
        // Zwei Requests: global + Kanal (mit aufgelöster Broadcaster-ID).
        assertTrue(requests.any { it.contains("/chat/badges/global") }, requests.toString())
        assertTrue(requests.any { it.contains("/chat/badges?broadcaster_id=222") }, requests.toString())
    }

    @Test
    fun `channel badges win over global badges on the same key`() = runTest {
        val global = """{"data":[{"set_id":"subscriber","versions":[{"id":"6","image_url_1x":"https://cdn/global","image_url_2x":"https://cdn/global2","title":"Global"}]}]}"""
        val channel = """{"data":[{"set_id":"subscriber","versions":[{"id":"6","image_url_1x":"https://cdn/ch","image_url_2x":"https://cdn/ch2","title":"Channel"}]}]}"""
        val client = client(engine(mutableListOf(), global = global, channel = channel))

        val badges = client.load(config)

        assertEquals("Channel", badges["subscriber/6"]!!.title)
        assertTrue(badges["subscriber/6"]!!.imageUrl.contains("/ch2"), badges["subscriber/6"]!!.imageUrl)
    }

    @Test
    fun `returns only global badges when the broadcaster id cannot be resolved`() = runTest {
        val client = client(
            engine(mutableListOf()),
            broadcasterId = null,
        )

        val badges = client.load(config)

        assertEquals(setOf("moderator/1"), badges.keys)
    }

    @Test
    fun `returns an empty map when the api fails`() = runTest {
        val client = client(
            MockEngine {
                respond(
                    content = "",
                    status = HttpStatusCode.Unauthorized,
                    headers = headersOf(HttpHeaders.ContentType, "application/json"),
                )
            },
        )

        val badges = client.load(config)

        assertTrue(badges.isEmpty())
    }
}
