package com.vivid.feature.chat.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
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
    ): TwitchChatEventSubReader = mockk {
        every { messages } returns messageFlow
        every { state } returns stateFlow
        every { start(any()) } just Runs
        every { stop() } just Runs
    }

    private fun repository(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
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
        val viewModel = ChatOverlayViewModel(reader, repository(settings))
        advanceUntilIdle()

        stateFlow.value = ChatConnectionState.Connecting
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connecting, viewModel.uiState.value.connection)

        stateFlow.value = ChatConnectionState.Connected("kanal")
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connected("kanal"), viewModel.uiState.value.connection)
    }
}
