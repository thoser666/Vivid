package com.vivid.feature.chat.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.AlertDetail
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.model.ChatBadge
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.emotes.ThirdPartyEmoteService
import com.vivid.feature.chat.twitch.TwitchBadgeClient
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ChatOverlayViewModelTest {

    private fun reader(
        messageFlow: MutableSharedFlow<ChatMessage> =
            MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64),
        stateFlow: MutableStateFlow<ChatConnectionState> =
            MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected),
        alertsFlow: MutableSharedFlow<ChatAlert> =
            MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64),
    ): TwitchChatEventSubReader = mockk {
        every { messages } returns messageFlow
        every { state } returns stateFlow
        every { alerts } returns alertsFlow
        every { start(any()) } just Runs
        every { stop() } just Runs
        every { triggerTestAlert(any()) } just Runs
    }

    private fun repository(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    /** Badge-Client-Stub: liefert standardmäßig eine leere Map (kein Crash). */
    private fun badgeClient(badges: Map<String, ChatBadge> = emptyMap()): TwitchBadgeClient = mockk {
        coEvery { load(any()) } returns badges
    }

    /** ThirdPartyEmoteService-Stub. */
    private fun emoteService(): ThirdPartyEmoteService = ThirdPartyEmoteService()

    /** Voll konfiguriert (Kanal + Bot-Zugangsdaten für EventSub). */
    private fun settings(
        enabled: Boolean = true,
        channel: String = "kanal",
        botLogin: String = "vividbot",
    ): AppSettings = AppSettings(
        chatOverlayEnabled = enabled,
        chatChannel = channel,
        chatBotLogin = botLogin,
        chatBotOauthToken = "tok123",
        chatBotTwitchClientId = "cid-abc",
    )

    private fun chatMessage(text: String, index: Int = 0): ChatMessage = ChatMessage(
        id = "id-$index",
        channel = "kanal",
        userId = "user-$index",
        userLogin = "user$index",
        displayName = "User$index",
        color = null,
        text = text,
        badges = emptyList(),
        emotesTag = "",
        timestamp = 0L,
        isModerator = false,
        isSubscriber = false,
    )

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `enabled with channel and bot credentials starts the reader with the event sub config`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings(channel = " MeinKanal "))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        verify(exactly = 1) {
            reader.start(
                match {
                    it.channel == "meinkanal" &&
                        it.botLogin == "vividbot" &&
                        it.oauthToken == "tok123" &&
                        it.clientId == "cid-abc"
                },
            )
        }
        verify(exactly = 0) { reader.stop() }
        assertTrue(viewModel.uiState.value.enabled)
        assertTrue(viewModel.uiState.value.configured)
        assertEquals("meinkanal", viewModel.uiState.value.channel)
    }

    @Test
    fun `disabled stops the reader and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings(enabled = false))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        verify(exactly = 0) { reader.start(any()) }
        verify(exactly = 1) { reader.stop() }
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `enabled without a channel stays disconnected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings(channel = "  "))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        verify(exactly = 0) { reader.start(any()) }
        verify(exactly = 1) { reader.stop() }
        assertEquals("", viewModel.uiState.value.channel)
    }

    @Test
    fun `enabled without bot credentials marks the overlay as unconfigured`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings(botLogin = ""))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        verify(exactly = 0) { reader.start(any()) }
        verify(exactly = 1) { reader.stop() }
        assertFalse(viewModel.uiState.value.configured)
        assertTrue(viewModel.uiState.value.enabled)
    }

    @Test
    fun `channel change reconnects to the new channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings(channel = "kanalA"))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        settings.value = settings(channel = "kanalB")
        advanceUntilIdle()

        verify(exactly = 1) { reader.start(match { it.channel == "kanala" }) }
        verify(exactly = 1) { reader.start(match { it.channel == "kanalb" }) }
        verify(exactly = 0) { reader.stop() }
    }

    @Test
    fun `messages are accumulated in the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
        val reader = reader(messageFlow = messageFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        messageFlow.tryEmit(chatMessage("hallo"))
        messageFlow.tryEmit(chatMessage("welt", 1))
        advanceUntilIdle()

        assertEquals(listOf("hallo", "welt"), viewModel.uiState.value.messages.map { it.text })
    }

    @Test
    fun `message list is capped at MAX_MESSAGES`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
        val reader = reader(messageFlow = messageFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        repeat(ChatOverlayViewModel.MAX_MESSAGES + 5) { i ->
            messageFlow.tryEmit(chatMessage("n$i", i))
        }
        advanceUntilIdle()

        assertEquals(ChatOverlayViewModel.MAX_MESSAGES, viewModel.uiState.value.messages.size)
        assertEquals("n5", viewModel.uiState.value.messages.first().text)
        assertEquals("n${ChatOverlayViewModel.MAX_MESSAGES + 4}", viewModel.uiState.value.messages.last().text)
    }

    @Test
    fun `messages are cleared when the overlay is disabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
        val reader = reader(messageFlow = messageFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        messageFlow.tryEmit(chatMessage("hallo"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)

        settings.value = settings(enabled = false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `connection state is forwarded to the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stateFlow = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
        val reader = reader(stateFlow = stateFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        stateFlow.value = ChatConnectionState.Connecting
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connecting, viewModel.uiState.value.connection)

        stateFlow.value = ChatConnectionState.Connected("kanal")
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connected("kanal"), viewModel.uiState.value.connection)
    }

    // --- Twitch-Badges (Broadcaster/Mod/Sub) im Overlay ---

    @Test
    fun `badges are loaded with the event sub config and exposed in the state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val badgeMap = mapOf(
            "broadcaster/1" to ChatBadge("broadcaster", "1", "Broadcaster", "https://cdn/bc/2"),
            "moderator/1" to ChatBadge("moderator", "1", "Moderator", "https://cdn/mod/2"),
        )
        val badgeClient = badgeClient(badgeMap)
        val reader = reader()
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient, emoteService())
        advanceUntilIdle()

        coVerify(exactly = 1) {
            badgeClient.load(match { it.channel == "kanal" && it.botLogin == "vividbot" })
        }
        assertEquals(badgeMap, viewModel.uiState.value.badges)
    }

    @Test
    fun `badges are cleared when the overlay is disabled`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val badgeClient = badgeClient(
            mapOf("broadcaster/1" to ChatBadge("broadcaster", "1", "Broadcaster", "https://cdn/bc/2")),
        )
        val reader = reader()
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient, emoteService())
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.badges.isNotEmpty())

        settings.value = settings(enabled = false)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.badges.isEmpty())
    }

    @Test
    fun `a failed badge load keeps the overlay running without badges`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val badgeClient = mockk<TwitchBadgeClient> {
            coEvery { load(any()) } throws RuntimeException("kaputt")
        }
        val reader = reader()
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient, emoteService())
        advanceUntilIdle()

        // Kein Crash, keine Badges — das Overlay läuft trotzdem (leere Map).
        assertTrue(viewModel.uiState.value.badges.isEmpty())
        assertTrue(viewModel.uiState.value.enabled)
    }

    @Test
    fun `a stale badge load for a previous channel is discarded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val badgesA = mapOf(
            "broadcaster/1" to ChatBadge("broadcaster", "1", "Broadcaster", "https://cdn/a/2"),
        )
        val badgesB = mapOf(
            "moderator/1" to ChatBadge("moderator", "1", "Moderator", "https://cdn/b/2"),
        )
        val badgeClient = mockk<TwitchBadgeClient> {
            // Kanal A lädt langsam, Kanal B schnell — die alte Antwort von A
            // trifft NACH der von B ein und darf die Badge-Map nicht mit den
            // Badges des alten Kanals überschreiben.
            coEvery { load(match { it.channel == "kanala" }) } coAnswers { delay(1000); badgesA }
            coEvery { load(match { it.channel == "kanalb" }) } coAnswers { delay(100); badgesB }
        }
        val reader = reader()
        val settings = MutableStateFlow(settings(channel = "kanalA"))
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient, emoteService())
        runCurrent() // Load für Kanal A startet (suspended im delay)
        assertEquals(emptyMap<String, ChatBadge>(), viewModel.uiState.value.badges)

        settings.value = settings(channel = "kanalB")
        runCurrent() // Load B startet; A ist noch in-flight, Map wurde geleert
        assertEquals(emptyMap<String, ChatBadge>(), viewModel.uiState.value.badges)

        advanceTimeBy(101) // B (100 ms) ist fertig
        runCurrent()
        assertEquals(badgesB, viewModel.uiState.value.badges)

        advanceTimeBy(1000) // A (1000 ms) trifft jetzt ein — muss verworfen werden
        runCurrent()
        assertEquals(badgesB, viewModel.uiState.value.badges)
    }

    @Test
    fun `a badge load completing after disable is discarded`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val badges = mapOf(
            "broadcaster/1" to ChatBadge("broadcaster", "1", "Broadcaster", "https://cdn/bc/2"),
        )
        val badgeClient = mockk<TwitchBadgeClient> {
            coEvery { load(any()) } coAnswers { delay(1000); badges }
        }
        val reader = reader()
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient, emoteService())
        runCurrent() // Load startet, noch in-flight

        settings.value = settings(enabled = false)
        runCurrent()
        assertTrue(viewModel.uiState.value.badges.isEmpty())

        advanceTimeBy(1001) // Load-Antwort kommt nach dem Deaktivieren an
        runCurrent()
        assertTrue(viewModel.uiState.value.badges.isEmpty())
    }

    // --- Event-Alerts (Follow/Sub/Raid) im Overlay ---

    @Test
    fun `alerts are shown capped and auto dismiss after the ttl`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val reader = reader(alertsFlow = alertFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        repeat(ChatOverlayViewModel.MAX_ALERTS + 2) { i ->
            alertFlow.tryEmit(
                ChatAlert(
                    id = "a$i",
                    type = ChatAlertType.FOLLOW,
                    displayName = "User$i",
                    timestamp = 0L,
                ),
            )
        }
        // runCurrent statt advanceUntilIdle: die TTL-Entfernung (delay) darf
        // hier noch nicht feuern, sonst wären die Alerts schon wieder weg.
        runCurrent()

        // Nur die letzten MAX_ALERTS bleiben sichtbar (älteste fliegen raus).
        assertEquals(ChatOverlayViewModel.MAX_ALERTS, viewModel.uiState.value.alerts.size)
        assertEquals("a2", viewModel.uiState.value.alerts.first().id)
        assertEquals("a4", viewModel.uiState.value.alerts.last().id)

        // Nach Ablauf der TTL verschwinden die Alerts automatisch.
        advanceTimeBy(ChatOverlayViewModel.ALERT_TTL_MS)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.alerts.isEmpty())
    }

    @Test
    fun `alerts are cleared when the overlay channel changes`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val reader = reader(alertsFlow = alertFlow)
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        alertFlow.tryEmit(
            ChatAlert(
                id = "raid-1",
                type = ChatAlertType.RAID,
                displayName = "RaiderEins",
                timestamp = 0L,
                detail = AlertDetail(viewerCount = 12),
            ),
        )
        runCurrent()
        assertEquals(1, viewModel.uiState.value.alerts.size)

        settings.value = settings(channel = "andererKanal")
        runCurrent()

        // Kanalwechsel leert die Alerts sofort (nicht erst nach der TTL).
        assertTrue(viewModel.uiState.value.alerts.isEmpty())
    }

    @Test
    fun `trigger test alert is forwarded to the reader`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val reader = reader()
        val settings = MutableStateFlow(settings())
        val viewModel = ChatOverlayViewModel(reader, repository(settings), badgeClient(), emoteService())
        advanceUntilIdle()

        viewModel.triggerTestAlert(ChatAlertType.FOLLOW)

        verify(exactly = 1) { reader.triggerTestAlert(ChatAlertType.FOLLOW) }
    }
}
