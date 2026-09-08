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

    /** Hype-Train-Alert wie vom Reader: dieselbe ID für begin/progress/end. */
    private fun hypeAlert(
        id: String,
        level: Int = 1,
        progress: Int = 0,
        goal: Int = 0,
        ended: Boolean = false,
    ) = ChatAlert(
        id = "hypetrain-$id",
        type = ChatAlertType.HYPE_TRAIN,
        displayName = "Kanal",
        timestamp = System.currentTimeMillis(),
        detail = AlertDetail(
            hypeTrainLevel = level,
            hypeTrainProgress = progress,
            hypeTrainGoal = goal,
            hypeTrainEnded = ended,
        ),
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

    // --- Hype-Train-Banner ---

    @Test
    fun `hypeTrainEnabled default is true`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val vm = createViewModel(MutableStateFlow(settings()))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.hypeTrainEnabled)
    }

    @Test
    fun `hypeTrainEnabled reads from settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val settings = MutableStateFlow(settings().copy(chatOverlayHypeTrainEnabled = false))
        val vm = createViewModel(settings)
        advanceUntilIdle()
        assertFalse(vm.uiState.value.hypeTrainEnabled)
    }

    @Test
    fun `hype train progress replaces the banner in place`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertsFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val vm = createViewModel(MutableStateFlow(settings()), reader(alertsFlow = alertsFlow))
        advanceUntilIdle()

        alertsFlow.tryEmit(hypeAlert("t1", level = 1, progress = 100, goal = 350))
        alertsFlow.tryEmit(hypeAlert("t1", level = 1, progress = 340, goal = 350))
        advanceUntilIdle()

        val alerts = vm.uiState.value.alerts
        assertEquals(1, alerts.size)
        assertEquals("hypetrain-t1", alerts[0].id)
        assertEquals(340, alerts[0].detail.hypeTrainProgress)
        assertEquals(350, alerts[0].detail.hypeTrainGoal)
    }

    @Test
    fun `hype train end removes the banner from the overlay`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertsFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val vm = createViewModel(MutableStateFlow(settings()), reader(alertsFlow = alertsFlow))
        advanceUntilIdle()

        alertsFlow.tryEmit(hypeAlert("t1", level = 1, progress = 340, goal = 350))
        advanceUntilIdle()
        assertEquals(1, vm.uiState.value.alerts.size)

        alertsFlow.tryEmit(hypeAlert("t1", level = 1, ended = true))
        advanceUntilIdle()
        assertTrue(vm.uiState.value.alerts.isEmpty())
    }

    @Test
    fun `hype train banner survives the alert ttl`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertsFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val vm = createViewModel(MutableStateFlow(settings()), reader(alertsFlow = alertsFlow))
        advanceUntilIdle()

        alertsFlow.tryEmit(
            ChatAlert(id = "follow-1", type = ChatAlertType.FOLLOW, displayName = "X", timestamp = 0L),
        )
        alertsFlow.tryEmit(hypeAlert("t1", level = 1, progress = 100, goal = 350))
        runCurrent()
        assertEquals(2, vm.uiState.value.alerts.size)

        // Nach der TTL verschwindet der Follow-Alert, der Hype-Train bleibt.
        testScheduler.advanceTimeBy(ChatOverlayViewModel.ALERT_TTL_MS + 1_000)
        advanceUntilIdle()

        assertEquals(1, vm.uiState.value.alerts.size)
        assertEquals("hypetrain-t1", vm.uiState.value.alerts[0].id)
    }

    @Test
    fun `hype train alerts are skipped while the toggle is off`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val alertsFlow = MutableSharedFlow<ChatAlert>(extraBufferCapacity = 64)
        val settings = MutableStateFlow(settings().copy(chatOverlayHypeTrainEnabled = false))
        val vm = createViewModel(settings, reader(alertsFlow = alertsFlow))
        advanceUntilIdle()

        alertsFlow.tryEmit(
            ChatAlert(id = "follow-1", type = ChatAlertType.FOLLOW, displayName = "X", timestamp = 0L),
        )
        alertsFlow.tryEmit(hypeAlert("t1", level = 1, progress = 100, goal = 350))
        runCurrent()

        assertEquals(1, vm.uiState.value.alerts.size)
        assertEquals("follow-1", vm.uiState.value.alerts[0].id)

        // Restliche TTL-Timer ablaufen lassen (keine schwebenden Koroutinen).
        advanceUntilIdle()
    }
}
