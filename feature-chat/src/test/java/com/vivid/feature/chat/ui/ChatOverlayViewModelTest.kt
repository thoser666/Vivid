package com.vivid.feature.chat.ui

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.twitch.TwitchChatClient
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

    private fun client(
        messageFlow: MutableSharedFlow<ChatMessage> =
            MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64),
        stateFlow: MutableStateFlow<ChatConnectionState> =
            MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected),
    ): TwitchChatClient = mockk {
        every { messages } returns messageFlow
        every { state } returns stateFlow
        every { start(any()) } just Runs
        every { stop() } just Runs
    }

    private fun repository(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    private fun chatMessage(text: String, index: Int = 0): ChatMessage = ChatMessage(
        id = "id-$index",
        channel = "channel",
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
    fun `enabled with a channel starts the client with the normalized channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = client()
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = " MeinKanal "))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        verify(exactly = 1) { client.start("meinkanal") }
        verify(exactly = 0) { client.stop() }
        assertTrue(viewModel.uiState.value.enabled)
        assertEquals("meinkanal", viewModel.uiState.value.channel)
    }

    @Test
    fun `disabled stops the client and does not start`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = client()
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = false, chatChannel = "kanal"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        verify(exactly = 0) { client.start(any()) }
        verify(exactly = 1) { client.stop() }
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `enabled without a channel stays disconnected`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = client()
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "  "))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        verify(exactly = 0) { client.start(any()) }
        verify(exactly = 1) { client.stop() }
        assertEquals("", viewModel.uiState.value.channel)
    }

    @Test
    fun `channel change reconnects to the new channel`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = client()
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "kanalA"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        settings.value = AppSettings(chatOverlayEnabled = true, chatChannel = "kanalB")
        advanceUntilIdle()

        verify(exactly = 1) { client.start("kanala") }
        verify(exactly = 1) { client.start("kanalb") }
        verify(exactly = 0) { client.stop() }
    }

    @Test
    fun `messages are accumulated in the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val messageFlow = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64)
        val client = client(messageFlow = messageFlow)
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "kanal"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
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
        val client = client(messageFlow = messageFlow)
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "kanal"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
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
        val client = client(messageFlow = messageFlow)
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "kanal"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        messageFlow.tryEmit(chatMessage("hallo"))
        advanceUntilIdle()
        assertEquals(1, viewModel.uiState.value.messages.size)

        settings.value = AppSettings(chatOverlayEnabled = false, chatChannel = "kanal")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.messages.isEmpty())
        assertFalse(viewModel.uiState.value.enabled)
    }

    @Test
    fun `connection state is forwarded to the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val stateFlow = MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected)
        val client = client(stateFlow = stateFlow)
        val settings = MutableStateFlow(AppSettings(chatOverlayEnabled = true, chatChannel = "kanal"))
        val viewModel = ChatOverlayViewModel(client, repository(settings))
        advanceUntilIdle()

        stateFlow.value = ChatConnectionState.Connecting
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connecting, viewModel.uiState.value.connection)

        stateFlow.value = ChatConnectionState.Connected("kanal")
        advanceUntilIdle()
        assertEquals(ChatConnectionState.Connected("kanal"), viewModel.uiState.value.connection)
    }
}
