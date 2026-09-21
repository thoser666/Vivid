package com.vivid.feature.chat.session

import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Verträge des [ChatSessionManager] (P3-Vorgriff, Skizze 4.3): deklaratives
 * [ChatSessionManager.setSessions] mit **Always-Restart** (verhaltensidentisch
 * zum heutigen Bot-Start), Stop entfernter Plattformen, Merge der Nachrichten,
 * States-Aggregat und [ChatSessionManager.sendToOrigin]-Routing.
 * Läuft agent-frei mit handgeschriebenen Fakes (Hausmuster nach dem
 * ObsControlViewModelTest-Umbau).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ChatSessionManagerTest {

    private val dispatcher = StandardTestDispatcher()

    /** Fake-Adapter mit Aufrufliste (start/stop) und steuerbaren Flows. */
    private class FakeAdapter : ChatReader, ChatSender {
        val startCalls = mutableListOf<ChatSessionConfig>()
        var stopCalls = 0
        val messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 16)
        val stateFlow = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
        var nextSend: ChatSendResult = ChatSendResult.Sent

        override val messages = messageFlow
        override val state = stateFlow

        override fun start(config: ChatSessionConfig) {
            // Vertrag des Twitch-Adapters: start() stoppt implizit (Always-Restart).
            stopCalls++
            startCalls.add(config)
        }

        override fun stop() {
            stopCalls++
        }

        override suspend fun send(config: ChatSessionConfig, text: String): ChatSendResult =
            nextSend
    }

    private lateinit var twitchReader: FakeAdapter
    private lateinit var manager: ChatSessionManager

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        twitchReader = FakeAdapter()
        manager = ChatSessionManager(
            readers = mapOf(ChatPlatform.TWITCH to twitchReader),
            senders = mapOf(ChatPlatform.TWITCH to twitchReader),
            scope = TestScope(dispatcher),
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun twitchConfig(channel: String = "kanal"): ChatSessionConfig.Twitch =
        ChatSessionConfig.Twitch(
            com.vivid.feature.chat.twitch.TwitchEventSubConfig(
                botLogin = "bot",
                oauthToken = "tok",
                clientId = "cid",
                channel = channel,
            ),
        )

    @Test
    fun `setSessions starts the twitch session`() = runTest(dispatcher) {
        manager.setSessions(listOf(twitchConfig()))
        runCurrent()
        assertEquals(1, twitchReader.startCalls.size)
        assertEquals("kanal", twitchReader.startCalls.single().channel)
        assertEquals(setOf(ChatPlatform.TWITCH), manager.activePlatforms.value)
    }

    @Test
    fun `setSessions always restarts — behavior parity with the legacy bot start`() = runTest(dispatcher) {
        val config = twitchConfig()
        manager.setSessions(listOf(config))
        manager.setSessions(listOf(config))
        runCurrent()
        // Zweiter setSessions-Aufruf = Neustart (start stoppt implizit im Adapter):
        assertEquals(2, twitchReader.startCalls.size)
        assertEquals(setOf(ChatPlatform.TWITCH), manager.activePlatforms.value)
    }

    @Test
    fun `setSessions with empty list stops all sessions`() = runTest(dispatcher) {
        manager.setSessions(listOf(twitchConfig()))
        manager.setSessions(emptyList())
        runCurrent()
        assertEquals(0, manager.sessions.value.size)
        assertTrue(twitchReader.stopCalls >= 2) // impliziter Restart-Stop + expliziter Stop
        assertEquals(emptySet<ChatPlatform>(), manager.activePlatforms.value)
    }

    @Test
    fun `stopAll empties the registry and stops readers`() = runTest(dispatcher) {
        manager.setSessions(listOf(twitchConfig()))
        manager.stopAll()
        runCurrent()
        assertTrue(manager.sessions.value.isEmpty())
        assertTrue(twitchReader.stopCalls >= 2)
    }

    @Test
    fun `messages merges the active sessions flows`() = runTest(dispatcher) {
        val received = mutableListOf<ChatMessage>()
        val job = launch { manager.messages.collect { received.add(it) } }
        manager.setSessions(listOf(twitchConfig()))
        runCurrent()
        val message = ChatMessage(
            id = "m1", channel = "kanal", userId = "u1", userLogin = "user1",
            displayName = "User1", color = null, text = "hi", badges = emptyList(),
            emotesTag = "", timestamp = 0L, isModerator = false, isSubscriber = false,
        )
        twitchReader.messageFlow.tryEmit(message)
        runCurrent()
        assertEquals(listOf(message), received)
        job.cancel()
    }

    @Test
    fun `states reports the connection state per platform`() = runTest(dispatcher) {
        manager.setSessions(listOf(twitchConfig()))
        twitchReader.stateFlow.value = ChatConnectionState.Connected("kanal")
        runCurrent()
        assertEquals(
            mapOf(ChatPlatform.TWITCH to ChatConnectionState.Connected("kanal")),
            manager.states.value,
        )
    }

    @Test
    fun `sendToOrigin returns null without an active session and routes otherwise`() = runTest(dispatcher) {
        val origin = ChatMessage(
            id = "m1", channel = "kanal", userId = "u1", userLogin = "user1",
            displayName = "User1", color = null, text = "hi", badges = emptyList(),
            emotesTag = "", timestamp = 0L, isModerator = false, isSubscriber = false,
            platform = ChatPlatform.TWITCH,
        )
        // Keine Session: null (kein Crash).
        assertNull(manager.sendToOrigin("hi", origin))
        // Mit Session: geroutet zum Twitch-Sender.
        manager.setSessions(listOf(twitchConfig()))
        assertEquals(ChatSendResult.Sent, manager.sendToOrigin("hi", origin))
        twitchReader.nextSend = ChatSendResult.Dropped("Slow-Mode")
        assertEquals(ChatSendResult.Dropped("Slow-Mode"), manager.sendToOrigin("hi", origin))
    }
}
