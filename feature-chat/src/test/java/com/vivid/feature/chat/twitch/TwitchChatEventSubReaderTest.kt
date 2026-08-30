package com.vivid.feature.chat.twitch

import com.vivid.feature.chat.model.AlertDetail
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.model.ChatConnectionState
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
import java.time.Instant
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TwitchChatEventSubReaderTest {

    private val config = TwitchEventSubConfig(
        botLogin = "vividbot",
        oauthToken = "oauth:tok123",
        clientId = "client-abc",
        channel = "thoser666",
    )

    private val welcome =
        """{"metadata":{"message_type":"session_welcome","message_id":"w1"},"payload":{"session":{"id":"sess-1","status":"connected"}}}"""

    private val reconnect =
        """{"metadata":{"message_type":"session_reconnect","message_id":"r1"},"payload":{"session":{"id":"sess-2","reconnect_url":"wss://reconnect.example/ws"}}}"""

    /**
     * Notification-Vorlage für channel.chat.message. Die Platzhalter werden
     * per replace gesetzt — bewusst EIN zusammenhängendes JSON-Literal statt
     * String-Konkatenation (die hat in der Vergangenheit zu kaputten
     * JSON-Quote-Zählungen geführt; jedes Literal wurde vorher validiert).
     */
    private val notificationTemplate =
        """{"metadata":{"message_type":"notification","message_id":"n1"},"payload":{"subscription":{},"event":{"broadcaster_user_id":"222","broadcaster_user_login":"thoser666","broadcaster_user_name":"Thoser666","chatter_user_id":"333","chatter_user_login":"__LOGIN__","chatter_user_name":"__NAME__","message_id":"m-1","message":{"text":"__TEXT__","fragments":[]},"color":"#1E90FF","badges":[{"set_id":"broadcaster","id":"1","info":""}],"message_type":"chat","message_timestamp":"2023-08-22T20:06:02.029203596Z"}}}"""

    private fun notification(login: String = "viewer1", name: String = "ViewerEins", text: String = "Hallo Bot") =
        notificationTemplate
            .replace("__LOGIN__", login)
            .replace("__NAME__", name)
            .replace("__TEXT__", text)

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
    ): TwitchChatEventSubReader {
        val whisperClient = mockk<TwitchWhisperClient> {
            coEvery { resolveUserId(any(), "vividbot") } returns "111"
            coEvery { resolveUserId(any(), "thoser666") } returns "222"
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
        return TwitchChatEventSubReader(
            scope = scope,
            socketFactory = EventSubSocketFactory { sockets.removeAt(0) },
            whisperClient = whisperClient,
            http = HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            },
        )
    }

    @Test
    fun `subscribes to chat and alert topics after the session welcome`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val subscribeRequests = mutableListOf<String>()
        val client = client(this, sockets, subscribeRequests, testScheduler)

        client.start(config)
        sockets.first().push(welcome)
        advanceUntilIdle()

        // Chat + Follow + Subscribe + Gift + Resub + Raid + MessageDelete = 7 Subscriptions auf derselben Session.
        assertEquals(7, subscribeRequests.size)
        val chat = subscribeRequests[0]
        assertTrue(chat.contains("\"type\":\"channel.chat.message\""), chat)
        assertTrue(chat.contains("\"version\":\"1\""), chat)
        assertTrue(chat.contains("\"condition\":{\"broadcaster_user_id\":\"222\",\"user_id\":\"111\"}"), chat)
        assertTrue(chat.contains("\"transport\":{\"method\":\"websocket\",\"session_id\":\"sess-1\"}"), chat)
        // Event-Alerts: Follow v2 (Moderator = Bot), Subscribe v1, Gift v1,
        // Resub v1 (alle mit nur broadcaster_user_id), Raid v1 (alle Raids).
        val bodies = subscribeRequests.joinToString("\n")
        assertTrue(bodies.contains("\"type\":\"channel.follow\"\n  \"version\":\"2\"") || bodies.contains("\"type\":\"channel.follow\""), bodies)
        assertTrue(bodies.contains("\"condition\":{\"broadcaster_user_id\":\"222\",\"moderator_user_id\":\"111\"}"), bodies)
        assertTrue(bodies.contains("\"type\":\"channel.subscribe\""), bodies)
        assertTrue(bodies.contains("\"type\":\"channel.subscription.gift\""), bodies)
        assertTrue(bodies.contains("\"type\":\"channel.subscription.message\""), bodies)
        assertTrue(bodies.contains("\"condition\":{\"broadcaster_user_id\":\"222\"}"), bodies)
        assertTrue(bodies.contains("\"type\":\"channel.raid\""), bodies)
        assertTrue(bodies.contains("\"condition\":{\"to_broadcaster_user_id\":\"222\",\"from_broadcaster_user_id\":\"\"}"), bodies)
        // Nach erfolgreichem Subscribe gilt der Chat als verbunden.
        assertEquals(ChatConnectionState.Connected("thoser666"), client.state.value)
        client.stop()
    }

    @Test
    fun `emits a chat notification as a public chat message`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.messages.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(notification("viewer1", "ViewerEins", "Hallo Bot"))
            advanceUntilIdle()

            val msg = awaitItem()
            assertFalse(msg.isWhisper, "Kanal-Nachrichten sind nie Whispers")
            assertEquals("viewer1", msg.userLogin)
            assertEquals("333", msg.userId)
            assertEquals("ViewerEins", msg.displayName)
            assertEquals("Hallo Bot", msg.text)
            assertEquals("#1E90FF", msg.color)
            assertEquals("thoser666", msg.channel)
            assertTrue(msg.badges.contains("broadcaster/1"))
            assertTrue(msg.isBroadcaster)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `maps badges and emote fragments to the irc formats`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)
        val notificationWithEmote =
            """{"metadata":{"message_type":"notification","message_id":"n1"},"payload":{"subscription":{},"event":{"broadcaster_user_id":"222","broadcaster_user_login":"thoser666","broadcaster_user_name":"Thoser666","chatter_user_id":"333","chatter_user_login":"modi","chatter_user_name":"Modi","message_id":"m-2","message":{"text":"Hallo! HeyGuys","fragments":[{"type":"text","text":"Hallo! ","emote":null},{"type":"emote","text":"HeyGuys","emote":{"id":"30259","emote_set_id":"0","owner_id":"0"}}]},"color":null,"badges":[{"set_id":"broadcaster","id":"1","info":""},{"set_id":"subscriber","id":"6","info":""},{"set_id":"moderator","id":"1","info":""}],"message_type":"chat","message_timestamp":"2023-08-22T20:06:02.029203596Z"}}}"""

        client.messages.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(notificationWithEmote)
            advanceUntilIdle()

            val msg = awaitItem()
            // Badges im IRC-Format ("set_id/id") — Owner-Erkennung bleibt kompatibel.
            assertTrue(msg.badges.contains("broadcaster/1"))
            assertTrue(msg.badges.contains("subscriber/6"))
            assertTrue(msg.badges.contains("moderator/1"))
            assertTrue(msg.isBroadcaster)
            assertTrue(msg.isSubscriber)
            assertTrue(msg.isModerator)
            // Emotes aus den Fragments auf das IRC-emotesTag-Format gemappt
            // ("Hallo! " = 7 Zeichen, "HeyGuys" = 7 Zeichen → 7-13).
            assertEquals("30259:7-13", msg.emotesTag)
            assertNull(msg.color)
            assertEquals(Instant.parse("2023-08-22T20:06:02Z").toEpochMilli(), msg.timestamp)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `ignores keepalive revocation and empty messages`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.messages.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push("""{"metadata":{"message_type":"session_keepalive","message_id":"k1"},"payload":{}}""")
            sockets.first().push("""{"metadata":{"message_type":"revocation","message_id":"x1"},"payload":{}}""")
            sockets.first().push(notification(text = "   "))
            advanceUntilIdle()

            expectNoEvents()
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits follow subscribe and raid notifications as alerts`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)
        val follow =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.follow","message_id":"a1"},"payload":{"subscription":{},"event":{"user_id":"333","user_login":"follower1","user_name":"FollowerEins","broadcaster_user_id":"222","broadcaster_user_login":"thoser666","broadcaster_user_name":"Thoser666","followed_at":"2026-08-21T10:00:00Z"}}}"""
        val subscribe =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.subscribe","message_id":"a2"},"payload":{"subscription":{},"event":{"user_id":"444","user_login":"sub1","user_name":"SubEins","broadcaster_user_id":"222","tier":"1000","is_gift":true,"gifter_user_name":"GifterX"}}}"""
        val raid =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.raid","message_id":"a3"},"payload":{"subscription":{},"event":{"from_broadcaster_user_id":"555","from_broadcaster_user_login":"raider1","from_broadcaster_user_name":"RaiderEins","to_broadcaster_user_id":"222","to_broadcaster_user_login":"thoser666","to_broadcaster_user_name":"Thoser666","viewers":12}}}"""

        client.alerts.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(follow)
            sockets.first().push(subscribe)
            sockets.first().push(raid)
            advanceUntilIdle()

            val f = awaitItem()
            assertEquals(ChatAlertType.FOLLOW, f.type)
            assertEquals("FollowerEins", f.displayName)
            val s = awaitItem()
            assertEquals(ChatAlertType.SUBSCRIBE, s.type)
            assertEquals("SubEins", s.displayName)
            assertEquals("1000", s.detail.tier)
            assertEquals("GifterX", s.detail.gifterName)
            val r = awaitItem()
            assertEquals(ChatAlertType.RAID, r.type)
            assertEquals("RaiderEins", r.displayName)
            assertEquals(12, r.detail.viewerCount)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `emits gift subs and resubs as alerts`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)
        val gift =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.subscription.gift","message_id":"a4"},"payload":{"subscription":{},"event":{"user_id":"666","user_login":"gifter1","user_name":"GifterEins","broadcaster_user_id":"222","total":3,"tier":"1000","cumulative_total":9,"is_anonymous":false}}}"""
        val anonymousGift =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.subscription.gift","message_id":"a5"},"payload":{"subscription":{},"event":{"user_id":"","user_login":"","user_name":"","broadcaster_user_id":"222","total":1,"tier":"2000","cumulative_total":null,"is_anonymous":true}}}"""
        val resub =
            """{"metadata":{"message_type":"notification","subscription_type":"channel.subscription.message","message_id":"a6"},"payload":{"subscription":{},"event":{"user_id":"777","user_login":"resubber1","user_name":"ResubberEins","broadcaster_user_id":"222","tier":"1000","cumulative_months":24,"streak_months":6}}}"""

        client.alerts.test {
            client.start(config)
            sockets.first().push(welcome)
            sockets.first().push(gift)
            sockets.first().push(anonymousGift)
            sockets.first().push(resub)
            advanceUntilIdle()

            val g = awaitItem()
            assertEquals(ChatAlertType.GIFT_SUB, g.type)
            assertEquals("GifterEins", g.displayName)
            assertEquals(3, g.detail.count)
            assertEquals(9, g.detail.cumulativeTotal)
            assertEquals("1000", g.detail.tier)
            assertFalse(g.detail.isAnonymous)
            val anon = awaitItem()
            assertEquals(ChatAlertType.GIFT_SUB, anon.type)
            assertTrue(anon.detail.isAnonymous)
            assertEquals(1, anon.detail.count)
            assertEquals(0, anon.detail.cumulativeTotal)
            val re = awaitItem()
            assertEquals(ChatAlertType.RESUB, re.type)
            assertEquals("ResubberEins", re.displayName)
            assertEquals(24, re.detail.months)
            assertEquals(6, re.detail.streakMonths)
            assertEquals("1000", re.detail.tier)
            client.stop()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `trigger test alert emits a synthetic alert on the alerts flow`() = runTest {
        val sockets = mutableListOf(FakeEventSubSocket())
        val client = client(this, sockets, mutableListOf(), testScheduler)

        client.alerts.test {
            client.start(config)
            client.triggerTestAlert(
                ChatAlertType.RAID,
                displayName = "TestKanal",
                detail = AlertDetail(viewerCount = 7),
            )
            advanceUntilIdle()

            val alert = awaitItem()
            assertEquals(ChatAlertType.RAID, alert.type)
            assertEquals("TestKanal", alert.displayName)
            assertEquals(7, alert.detail.viewerCount)
            assertTrue(alert.id.startsWith("test-raid-"))
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
        assertEquals(7, subscribeRequests.size)

        // session_reconnect → neue URL; Twitch übernimmt die Abos automatisch.
        first.push(reconnect)
        first.drop() // Twitch schließt die alte Verbindung
        advanceUntilIdle()

        assertEquals("wss://reconnect.example/ws", second.connectedUrl)
        // Neue Session: kein erneuter Subscribe (Abos wandern mit).
        second.push(welcome)
        advanceUntilIdle()
        assertEquals(7, subscribeRequests.size)
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
        assertEquals(7, subscribeRequests.size)

        // Harte Trennung ohne session_reconnect → neue Session braucht ein Abo.
        first.drop()
        advanceUntilIdle()

        assertEquals(TwitchChatEventSubReader.DEFAULT_EVENTSUB_URL, second.connectedUrl)
        second.push(welcome)
        advanceUntilIdle()
        assertEquals(14, subscribeRequests.size)
        client.stop()
    }
}
