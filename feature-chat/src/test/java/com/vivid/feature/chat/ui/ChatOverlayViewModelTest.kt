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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
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

    private val testDispatcher = StandardTestDispatcher()

    private fun reader(
        messageFlow: MutableSharedFlow<ChatMessage> =
            MutableSharedFlow<ChatMessage>(extraBufferCapacity = 64),
        stateFlow: MutableStateFlow<ChatConnectionState> =
            MutableStateFlow<ChatConnectionState>(ChatConnectionState.Disconnected),
        alertsFlow: MutableSharedFlow<ChatAlert> =
            MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64),
        deletedFlow: MutableSharedFlow<String> =
            MutableSharedFlow<String>(extraBufferCapacity = 64),
    ): TwitchChatEventSubReader = mockk {
        every { messages } returns messageFlow
        every { state } returns stateFlow
        every { alerts } returns alertsFlow
        every { deletedMessageIds } returns deletedFlow
        every { start(any()) } just Runs
        every { stop() } just Runs
        every { triggerTestAlert(any()) } just Runs
    }

    private fun repository(flow: MutableStateFlow<AppSettings>): SettingsRepository = mockk {
        every { appSettingsFlow } returns flow
    }

    private fun badgeClient(badges: Map<String, ChatBadge> = emptyMap()): TwitchBadgeClient = mockk {
        coEvery { load(any()) } returns badges
    }

    private fun emoteService(): ThirdPartyEmoteService = ThirdPartyEmoteService()

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

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        settings: MutableStateFlow<AppSettings>,
        reader: TwitchChatEventSubReader = reader(),
    ) = ChatOverlayViewModel(
        chatReader = reader,
        settingsRepository = repository(settings),
        badgeClient = badgeClient(),
        emoteService = emoteService(),
    )

    @Test
    fun `hideDeleted default is true`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = MutableStateFlow(settings())
        val vm = createViewModel(settings)
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hideDeleted)
    }

    @Test
    fun `hideDeleted reads from settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = MutableStateFlow(settings().copy(chatOverlayHideDeleted = false))
        val vm = createViewModel(settings)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hideDeleted)
    }

    @Test
    fun `deleted message ids are accumulated`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deletedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val r = reader(deletedFlow = deletedFlow)
        val settings = MutableStateFlow(settings())
        val vm = createViewModel(settings, r)
        advanceUntilIdle()

        deletedFlow.tryEmit("msg-1")
        deletedFlow.tryEmit("msg-2")
        deletedFlow.tryEmit("msg-3")
        advanceUntilIdle()

        val state = vm.uiState.value
        assertEquals(3, state.deletedMessageIds.size)
        assertTrue(state.deletedMessageIds.containsAll(listOf("msg-1", "msg-2", "msg-3")))
    }

    @Test
    fun `deleted message ids are cleared on channel change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val deletedFlow = MutableSharedFlow<String>(extraBufferCapacity = 64)
        val r = reader(deletedFlow = deletedFlow)
        val settings = MutableStateFlow(settings(channel = "channel1"))
        val vm = createViewModel(settings, r)
        advanceUntilIdle()

        deletedFlow.tryEmit("msg-1")
        deletedFlow.tryEmit("msg-2")
        advanceUntilIdle()
        assertEquals(2, vm.uiState.value.deletedMessageIds.size)

        // Change channel → deleted IDs should be cleared
        settings.value = settings.value.copy(chatChannel = "channel2")
        advanceUntilIdle()
        assertTrue(vm.uiState.value.deletedMessageIds.isEmpty())
    }
}
