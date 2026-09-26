package com.vivid.feature.chat.kick

import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.session.ChatSendResult
import com.vivid.feature.chat.session.ChatSessionConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestCoroutineScheduler
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract-Tests des Kick-Lese-Adapters (P2 der Multi-Plattform-Skizze,
 * docs/architecture/multi-platform-chat.md) gegen **reale Pusher-Payload-
 * Formen**: Pusher-Frames (`pusher:connection_established`,
 * `pusher_internal:subscription_succeeded`, `App\Events\ChatMessageEvent`),
 * Kanal-Auflösung (api/v2/channels) und Drift-Fälle (data als Objekt,
 * unbekannte Events, fremder Chatroom). Flows/Lifecycle friert die
 * feature-chat-Suite über die Bestands-Suiten ein; hier die reinen
 * Parser-/Vertrags-Pfade plus der Pusher-Handshake am Fake-Socket.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KickChatReaderTest {

    // --- Pusher-Frames (reale Formen) ---------------------------------------

    private val connectionEstablished =
        """{"event":"pusher:connection_established","data":"{\"socket_id\":\"123.456\",\"activity_timeout\":120,\"max_inactivity_timeout\":300}"}"""

    private val subscriptionSucceeded =
        """{"event":"pusher_internal:subscription_succeeded","data":"{}","channel":"chatrooms.12345.v2"}"""

    private val chatEvent =
        """{"event":"App\\Events\\ChatMessageEvent","data":"{\"id\":\"msg-1\",\"chatroom_id\":12345,\"content\":\"Hallo vom Kick\",\"created_at\":\"2026-09-25T18:30:02Z\",\"user\":{\"id\":987,\"username\":\"Bergsteiger\",\"identity\":{\"color\":\"#53FC18\",\"badges\":[\"moderator\",\"sub_gifter\"]}}}"}"""

    private val ping =
        """{"event":"pusher:ping","data":"{}"}"""

    // --- Frame-Parser ---------------------------------------------------------

    @Test
    fun `pusher frame with string data is parsed`() {
        val frame = parsePusherFrame(chatEvent)
        assertNotNull(frame)
        assertEquals("App\\Events\\ChatMessageEvent", frame!!.event)
        assertTrue(frame.data!!.contains(""""chatroom_id":12345"""))
        assertTrue(frame.data!!.contains("Bergsteiger"))
    }

    @Test
    fun `pusher frame tolerates object data (drift fallback)`() {
        val frame = parsePusherFrame(
            """{"event":"App\\Events\\ChatMessageEvent","data":{"id":"d1","content":"obj"}}""",
        )
        assertNotNull(frame)
        assertEquals("App\\Events\\ChatMessageEvent", frame!!.event)
        assertTrue(frame.data!!.contains("obj"))
    }

    @Test
    fun `pusher frame returns null for unknown or malformed lines`() {
        assertNull(parsePusherFrame("not json"))
        assertNull(parsePusherFrame("""{"data":{}}"""))
        assertNull(parsePusherFrame("""{"event":""}"""))
    }

    // --- Kanal-Auflösung --------------------------------------------------------

    @Test
    fun `channel v2 resolves chatroom id`() {
        val body =
            """{"id":42,"slug":"thoser666","chatroom":{"id":12345,"name":"ChatroomName"}}"""
        assertEquals(12345L, parseChatroomId(body))
    }

    @Test
    fun `channel v2 is drift tolerant to id as string`() {
        val body =
            """{"slug":"thoser666","chatroom":{"id":"12345"}}"""
        assertEquals(12345L, parseChatroomId(body))
    }

    @Test
    fun `channel v2 returns null when offline or malformed`() {
        assertNull(parseChatroomId("{}"))
        assertNull(parseChatroomId("""{"chatroom":{}}"""))
        assertNull(parseChatroomId("not json"))
    }

    // --- ChatMessageEvent-Mapping ------------------------------------------------

    @Test
    fun `chat event maps to message with platform kick`() {
        val frame = parsePusherFrame(chatEvent)!!
        val message = mapKickMessage(frame.data, channel = "thoser666", chatroomId = 12345L)
        assertNotNull(message)
        message!!
        assertEquals("msg-1", message.id)
        assertEquals("thoser666", message.channel)
        assertEquals("987", message.userId)
        assertEquals("Bergsteiger", message.userLogin)
        assertEquals("Bergsteiger", message.displayName)
        assertEquals("Hallo vom Kick", message.text)
        assertEquals("#53FC18", message.color)
        assertEquals(listOf("moderator", "sub_gifter"), message.badges)
        assertEquals(ChatPlatform.KICK, message.platform)
        // Bewusst false (P2): Bot-Aktionen laufen nur auf Twitch.
        assertFalse(message.isModerator)
        assertFalse(message.isBroadcaster)
    }

    @Test
    fun `chat event timestamp parses iso utc to epoch ms`() {
        val frame = parsePusherFrame(chatEvent)!!
        val message = mapKickMessage(frame.data, channel = "thoser666", chatroomId = 12345L)!!
        // 2026-09-25T18:30:02Z — Referenz über unabhängige Kalender-Akkumulation.
        assertEquals(isoExpectedEpochMs(2026, 9, 25, 18, 30, 2), message.timestamp)
    }

    @Test
    fun `chat event without created at keeps timestamp zero`() {
        val data =
            """{"id":"m2","chatroom_id":12345,"content":"ohne Zeit","user":{"id":1,"username":"x"}}"""
        val message = mapKickMessage(data, channel = "c", chatroomId = 12345L)!!
        assertEquals(0L, message.timestamp)
    }

    @Test
    fun `chat event from foreign chatroom is skipped (drift guard)`() {
        val frame = parsePusherFrame(chatEvent)!!
        assertNull(mapKickMessage(frame.data, channel = "thoser666", chatroomId = 99999L))
    }

    @Test
    fun `chat event missing mandatory fields yields no message`() {
        assertNull(mapKickMessage(null, channel = "c", chatroomId = 1L))
        assertNull(mapKickMessage("", channel = "c", chatroomId = 1L))
        assertNull(mapKickMessage("not json", channel = "c", chatroomId = 1L))
        assertNull(
            mapKickMessage(
                """{"chatroom_id":1,"content":"kein user","user":{}}""",
                channel = "c",
                chatroomId = 1L,
            ),
        )
        assertNull(
            mapKickMessage(
                """{"id":"m3","chatroom_id":1,"content":"  ","user":{"id":1,"username":"x"}}""",
                channel = "c",
                chatroomId = 1L,
            ),
        )
    }

    // --- ISO-Parser (Festkomma-Verträge) -------------------------------------------

    @Test
    fun `iso parser handles z suffix fraction and offset`() {
        assertEquals(
            isoExpectedEpochMs(2024, 8, 22, 20, 6, 2),
            parseIsoToEpochMs("2024-08-22T20:06:02Z"),
        )
        assertEquals(
            isoExpectedEpochMs(2024, 8, 22, 20, 6, 2, 250),
            parseIsoToEpochMs("2024-08-22T20:06:02.25Z"),
        )
        assertEquals(
            isoExpectedEpochMs(2024, 8, 22, 20, 6, 2),
            parseIsoToEpochMs("2024-08-22T20:06:02+02:00"),
        )
        assertEquals(
            isoExpectedEpochMs(2026, 9, 25, 18, 30, 2, 123),
            parseIsoToEpochMs("2026-09-25T18:30:02.123+00:00"),
        )
    }

    @Test
    fun `iso parser rejects malformed input`() {
        assertNull(parseIsoToEpochMs(null))
        assertNull(parseIsoToEpochMs(""))
        assertNull(parseIsoToEpochMs("kein datum"))
        assertNull(parseIsoToEpochMs("2024-08-22"))
        assertNull(parseIsoToEpochMs("2024-13-01T00:00:00Z"))
        assertNull(parseIsoToEpochMs("2024-08-22T25:00:00Z"))
    }

    // --- Subscribe-Frame + URL-Verträge ---------------------------------------------

    @Test
    fun `subscribe frame targets chatrooms channel v2`() {
        val frame = KickChatReader.buildSubscribeFrame(12345L)
        assertEquals("pusher:subscribe", frame["event"]?.toString()?.replace("\"", ""))
        val data = frame["data"]
        assertNotNull(data)
        assertTrue(data.toString().contains(""""channel":"chatrooms.12345.v2""""))
        assertTrue(data.toString().contains(""""auth":""""))
    }

    @Test
    fun `channel url encodes the slug`() {
        assertEquals(
            "https://kick.com/api/v2/channels/thoser666",
            KickChatReader.channelUrl("thoser666"),
        )
        assertTrue(KickChatReader.channelUrl("mein kanal").contains("mein+kanal"))
    }

    // --- Sealed-Vertrag (P0-Fortsetzung) ----------------------------------------------

    @Test
    fun `kick session config derives platform and channel`() {
        val config = ChatSessionConfig.Kick(channel = "Thoser666")
        assertEquals(ChatPlatform.KICK, config.platform)
        assertEquals("Thoser666", config.channel)
    }

    // --- Reader-Lifecycle am Fake-Socket (Handshake-Vertrag) -----------------------------

    /** Fake-Socket: Test pusht Pusher-Frames, gesendete Frames landen im Log. */
    private class FakeKickSocket : KickSocket {
        val incomingLines = Channel<String>(Channel.UNLIMITED)
        val sent = mutableListOf<String>()
        var connectedUrl: String? = null

        override suspend fun connect(url: String) {
            connectedUrl = url
        }

        override val incoming: Flow<String> = incomingLines.receiveAsFlow()

        override fun send(text: String): Boolean {
            sent.add(text)
            return true
        }

        fun push(line: String) {
            incomingLines.trySend(line)
        }

        override fun close() = Unit
    }

    /**
     * Mock-HTTP für die Kanal-Auflösung: URL → Body. Der MockEngine-Dispatcher
     * läuft auf dem Test-Scheduler, damit `advanceUntilIdle()` die Antwort
     * abwartet (Muster aus `TwitchChatEventSubReaderTest`).
     */
    private fun mockKickHttp(
        scheduler: TestCoroutineScheduler,
        bodies: Map<String, String>,
    ): HttpClient {
        val engine = MockEngine.create {
            dispatcher = StandardTestDispatcher(scheduler)
            addHandler { request ->
                bodies[request.url.toString()]
                    ?.let { respond(it, HttpStatusCode.OK) }
                    ?: respond("", HttpStatusCode.NotFound)
            }
        }
        return HttpClient(engine)
    }

    @Test
    fun `reader connects subscribes and emits messages`() = runTest {
        val socket = FakeKickSocket()
        val reader = KickChatReader(
            scope = kotlinx.coroutines.test.TestScope(StandardTestDispatcher(testScheduler)),
            socketFactory = KickSocketFactory { socket },
            http = mockKickHttp(
                testScheduler,
                mapOf(
                    "https://kick.com/api/v2/channels/thoser666" to
                        """{"slug":"thoser666","chatroom":{"id":12345}}""",
                ),
            ),
        )
        // Nachrichten-Sammler vor dem Handshake abonnieren (SharedFlow ohne
        // Replay — ein später Abonnent würde die Message verpassen).
        val received = mutableListOf<ChatMessage>()
        val collector = launch { reader.messages.collect { received.add(it) } }
        runCurrent()
        reader.start(ChatSessionConfig.Kick(channel = "thoser666"))
        advanceUntilIdle()
        // Kanal aufgelöst, Socket verbindet mit dem öffentlichen Pusher-Endpunkt.
        assertEquals(KickChatReader.PUSHER_APP_URL, socket.connectedUrl)
        // Handshake: connection_established → Subscribe, succeeded → Connected.
        socket.push(connectionEstablished)
        socket.push(subscriptionSucceeded)
        socket.push(chatEvent)
        advanceUntilIdle()
        assertEquals(
            listOf(KickChatReader.buildSubscribeFrame(12345L).toString()),
            socket.sent,
        )
        assertEquals(ChatConnectionState.Connected("thoser666"), reader.state.value)
        assertEquals(1, received.size)
        assertEquals(ChatPlatform.KICK, received[0].platform)
        assertEquals("Hallo vom Kick", received[0].text)
        assertEquals("Bergsteiger", received[0].displayName)
        collector.cancel()
        reader.stop()
    }

    @Test
    fun `reader answers pusher ping with pong`() = runTest {
        val socket = FakeKickSocket()
        val reader = KickChatReader(
            scope = kotlinx.coroutines.test.TestScope(StandardTestDispatcher(testScheduler)),
            socketFactory = KickSocketFactory { socket },
            http = mockKickHttp(
                testScheduler,
                mapOf(
                    "https://kick.com/api/v2/channels/thoser666" to
                        """{"slug":"thoser666","chatroom":{"id":12345}}""",
                ),
            ),
        )
        reader.start(ChatSessionConfig.Kick(channel = "thoser666"))
        advanceUntilIdle()
        socket.push(connectionEstablished)
        socket.push(subscriptionSucceeded)
        socket.push(ping)
        advanceUntilIdle()
        assertTrue(socket.sent.contains(KickChatReader.PUSHER_PONG_FRAME))
        reader.stop()
    }

    @Test
    fun `reader without subscription ack stays disconnected`() = runTest {
        val socket = FakeKickSocket()
        val reader = KickChatReader(
            scope = kotlinx.coroutines.test.TestScope(StandardTestDispatcher(testScheduler)),
            socketFactory = KickSocketFactory { socket },
            http = mockKickHttp(
                testScheduler,
                mapOf(
                    "https://kick.com/api/v2/channels/ghost" to
                        """{"slug":"ghost","chatroom":{"id":1}}""",
                ),
            ),
        )
        reader.start(ChatSessionConfig.Kick(channel = "ghost"))
        advanceUntilIdle()
        // connection_established kommt, aber kein Subscription-Ack → kein
        // Connected; der Subscribe-Frame war trotzdem verschickt worden.
        socket.push(connectionEstablished)
        advanceUntilIdle()
        // Kein Subscription-Ack → niemals Connected (der Run wartet auf den
        // Ack bzw. endet später mit Backoff — beides kein Connected).
        assertTrue(reader.state.value !is ChatConnectionState.Connected)
        assertEquals(1, socket.sent.size)
        reader.stop()
    }

    @Test
    fun `reader skips unknown pusher events silently`() = runTest {
        val socket = FakeKickSocket()
        val reader = KickChatReader(
            scope = kotlinx.coroutines.test.TestScope(StandardTestDispatcher(testScheduler)),
            socketFactory = KickSocketFactory { socket },
            http = mockKickHttp(
                testScheduler,
                mapOf(
                    "https://kick.com/api/v2/channels/thoser666" to
                        """{"slug":"thoser666","chatroom":{"id":12345}}""",
                ),
            ),
        )
        val received = mutableListOf<ChatMessage>()
        val collector = launch { reader.messages.collect { received.add(it) } }
        runCurrent()
        reader.start(ChatSessionConfig.Kick(channel = "thoser666"))
        advanceUntilIdle()
        socket.push(connectionEstablished)
        socket.push("""{"event":"App\Events\SomeFutureEvent","data":"{}"}""")
        socket.push(subscriptionSucceeded)
        socket.push("definitiv kein json")
        socket.push(chatEvent)
        advanceUntilIdle()
        // Nur die echte Chat-Nachricht kam durch — unbekannte/defekte Frames
        // werden übersprungen (Drift-Strategie, Skizze §8).
        assertEquals(1, received.size)
        assertEquals("msg-1", received[0].id)
        collector.cancel()
        reader.stop()
    }

    @Test
    fun `kick sender fails controlled until p4`() = runTest {
        val result = KickChatSender().send(ChatSessionConfig.Kick(channel = "c"), "hi")
        assertTrue(result is ChatSendResult.Failed)
    }

    // --- Hilfen ---------------------------------------------------------------------

    /** Erwartete Epoch-ms (unabhängige Referenz über Kalender-Akkumulation). */
    private fun isoExpectedEpochMs(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int,
        millis: Int = 0,
    ): Long {
        var total = 0L
        for (y in 1970 until year) {
            total += if ((y % 4 == 0 && y % 100 != 0) || y % 400 == 0) 366L else 365L
        }
        val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        for (m in 1 until month) total += monthDays[m - 1]
        if (month > 2 && ((year % 4 == 0 && year % 100 != 0) || year % 400 == 0)) total += 1
        total += day - 1
        return total * 86_400_000L + hour * 3_600_000L + minute * 60_000L +
            second * 1_000L + millis
    }
}
