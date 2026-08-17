package com.vivid.feature.chat.twitch

import app.cash.turbine.test
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchEventSubClientTest {

    private val config = TwitchEventSubConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
        channel = "thoser666",
    )

    private val welcome =
        """{"metadata":{"message_type":"session_welcome","message_id":"w1"},"payload":{"session":{"id":"sess-1","status":"connected"}}}"""

    private fun notification(fromLogin: String, fromName: String, text: String) =
        """{"metadata":{"message_type":"notification","message_id":"n1"},"payload":{"subscription":{},"event":""" +
            """{"from_user_id":"222","from_user_login":"$fromLogin","from_user_name":"$fromName","to_user_id":"111",""" +
            """"whisper_id":"w-1","whisper":{"text":"$text"}}}}"""

    private val reconnect =
        """{"metadata":{"message_type":"session_reconnect","message_id":"r1"},"payload":{"session":{""" +
            """"id":"sess-2","reconnect_url":"wss://reconnect.example/ws"}}}"""

    /** Fake-Socket: Test pusht JSON-Zeilen, schließt den Kanal für einen Drop. */
    private class FakeEventSubSocket : EventSubSocket {
        val incomingLines = Channel<String>(Channel.UNLIMITED)
        var connectedUrl: String? = null

        override suspend fun connect(url: String) {
            connectedUrl = url
        }

        override val incoming: Flow<String> = incomingLines.receiveAsFlow()

        fun push(line: String) {
            incomingLines.trySend(line)
        }

        fun drop() {
            incomingLines.close()
        }

        override fun close() = Unit
    }

    private fun client(
        scope: CoroutineScope,
        sockets: MutableList<FakeEventSubSocket>,
        subscribeRequests: MutableList<String>,
        scheduler: TestCoroutineScheduler,
    ): TwitchEventSubClient {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), any()) } returns "111"
        }
        // Der MockEngine-Dispatcher muss auf dem Test-Scheduler laufen, damit
        // advanceUntilIdle() den Subscribe-POST abwartet (sonst würde er auf
        // einem echten IO-Thread hängen und die Assertions liefen zu früh).
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(scheduler)
            addHandler { request ->
                subscribeRequests.add(request.body.toByteArray().toString(Charsets.UTF_8))
                respond("", HttpStatusCode.NoContent)
            }
        }
        return TwitchEventSubClient(
            scope = scope,
            socketFactory = EventSubSocketFactory { sockets.removeAt(0) },
            whisperClient = whisperClient,
            http = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        )
    }

    @Test
    fun `subscribes to user whisper message after the session welcome`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val subscribeRequests = mutableListOf<String>()
        val client = client(this, sockets, subscribeRequests, testScheduler)

        client.start(config)
        sockets.first().push(welcome)
        advanceUntilIdle()

        assertEquals(1, subscribeRequests.size)
        val body = subscribeRequests.first()
        assertTrue(body.contains("\"type\":\"user.whisper.message\""), body)
        assertTrue(body.contains("\"version\":\"1\""), body)
        assertTrue(body.contains("\"condition\":{\"user_id\":\"111\"}"), body)
        assertTrue(body.contains("\"transport\":{\"method\":\"websocket\",\"session_id\":\"sess-1\"}"), body)
        client.stop()
    }

    @Test
    fun `emits a whisper notification as a private chat message`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.whispers.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(notification("streamer2", "StreamerZwei", "Hallo Bot"))
            advanceUntilIdle()

            val msg = awaitItem()
            assertTrue(msg.isWhisper)
            assertEquals("streamer2", msg.userLogin)
            assertEquals("222", msg.userId)
            assertEquals("StreamerZwei", msg.displayName)
            assertEquals("Hallo Bot", msg.text)
            assertFalse(msg.isBroadcaster)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `whispers from the channel owner count as broadcaster`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.whispers.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(notification("thoser666", "Thoser666", "!diag"))
            advanceUntilIdle()

            val msg = awaitItem()
            assertTrue(msg.isWhisper)
            assertTrue(msg.isBroadcaster, "Whisper vom Kanal-Login muss als Broadcaster gelten")
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores keepalive and non-notification messages`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.whispers.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push("""{"metadata":{"message_type":"session_keepalive","message_id":"k1"},"payload":{}}""")
            sockets.first().push("""{"metadata":{"message_type":"revocation","message_id":"x1"},"payload":{}}""")
            advanceUntilIdle()

            expectNoEvents()
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `graceful reconnect reuses the reconnect url without resubscribing`() = runTest {
        val first = FakeEventSubSocket()
        val second = FakeEventSubSocket()
        val sockets = mutableListOf(first, second)
        val subscribeRequests = mutableListOf<String>()
        val client = client(this, sockets, subscribeRequests, testScheduler)

        client.start(config)
        first.push(welcome)
        advanceUntilIdle()
        assertEquals(1, subscribeRequests.size)

        // session_reconnect → neue URL; Twitch übernimmt die Abos automatisch.
        first.push(reconnect)
        first.drop() // Twitch schließt die alte Verbindung
        advanceUntilIdle()

        assertEquals("wss://reconnect.example/ws", second.connectedUrl)
        // Neue Session: kein erneuter Subscribe (Abos wandern mit).
        second.push(welcome)
        advanceUntilIdle()
        assertEquals(1, subscribeRequests.size)
        client.stop()
    }

    @Test
    fun `hard reconnect resubscribes on the fresh session`() = runTest {
        val first = FakeEventSubSocket()
        val second = FakeEventSubSocket()
        val sockets = mutableListOf(first, second)
        val subscribeRequests = mutableListOf<String>()
        val client = client(this, sockets, subscribeRequests, testScheduler)

        client.start(config)
        first.push(welcome)
        advanceUntilIdle()
        assertEquals(1, subscribeRequests.size)

        // Harte Trennung ohne session_reconnect → neue Session braucht ein Abo.
        first.drop()
        advanceUntilIdle()

        assertEquals(TwitchEventSubClient.DEFAULT_EVENTSUB_URL, second.connectedUrl)
        second.push(welcome)
        advanceUntilIdle()
        assertEquals(2, subscribeRequests.size)
        client.stop()
    }
}
