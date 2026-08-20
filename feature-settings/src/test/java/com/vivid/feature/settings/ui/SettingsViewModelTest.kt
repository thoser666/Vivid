package com.vivid.feature.settings.ui

import com.vivid.core.data.AccentColor
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.ThemeMode
import com.vivid.core.remote.RemoteControlServer
import com.vivid.core.remote.RemoteControlTokenStore
import com.vivid.core.R
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.UpdateChecker
import com.vivid.feature.chat.bot.ChatBotEngine
import com.vivid.feature.chat.bot.ChatBotUsage
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private fun repository() = mockk<SettingsRepository> {
        every { appSettingsFlow } returns MutableStateFlow(AppSettings())
    }

    private fun tokenStore(token: String = "test-token"): RemoteControlTokenStore = mockk {
        coEvery { getOrCreateToken() } returns token
    }

    private fun createViewModel(
        repository: SettingsRepository = repository(),
        checker: UpdateChecker = mockk(relaxed = true),
        tokenStore: RemoteControlTokenStore = tokenStore(),
        remoteControlServer: RemoteControlServer = mockk(relaxed = true),
        chatBotEngine: ChatBotEngine = mockk {
            every { usage } returns MutableStateFlow(ChatBotUsage())
        },
    ) = SettingsViewModel(repository, checker, tokenStore, remoteControlServer, chatBotEngine)

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads settings from repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(
                    streamUrl = "rtmp://live.example/app",
                    streamKey = "key-1",
                    streamUseTls = true,
                    obsHost = "192.168.1.5",
                    obsPort = "4456",
                    obsPassword = "obs-secret",
                    obsUseTls = true,
                ),
            )
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("rtmp://live.example/app", viewModel.uiState.value.streamUrl)
        assertEquals("key-1", viewModel.uiState.value.streamKey)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
        assertEquals("192.168.1.5", viewModel.uiState.value.obsHost)
        assertEquals("4456", viewModel.uiState.value.obsPort)
        assertEquals("obs-secret", viewModel.uiState.value.obsPassword)
        assertEquals(true, viewModel.uiState.value.obsUseTls)
    }

    @Test
    fun `loads secondary stream settings from repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(
                    streamUrl = "rtmp://live.example/app",
                    streamKey = "key-1",
                    secondaryStreamUrl = "rtmp://second.example/app",
                    secondaryStreamKey = "key-2",
                    secondaryStreamUseTls = true,
                ),
            )
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("rtmp://second.example/app", viewModel.uiState.value.secondaryStreamUrl)
        assertEquals("key-2", viewModel.uiState.value.secondaryStreamKey)
        assertEquals(true, viewModel.uiState.value.secondaryStreamUseTls)
    }

    @Test
    fun `input changes update the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onStreamUrlChange("rtmp://new/app")
        viewModel.onStreamKeyChange("new-key")
        viewModel.onStreamUseTlsChange(true)
        viewModel.onSecondaryStreamUrlChange("rtmp://new-secondary/app")
        viewModel.onSecondaryStreamKeyChange("new-key-2")
        viewModel.onSecondaryStreamUseTlsChange(true)
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")
        viewModel.onObsUseTlsChange(true)
        viewModel.onChatChannelChange("meinKanal")
        viewModel.onChatOverlayEnabledChange(true)

        assertEquals("rtmp://new/app", viewModel.uiState.value.streamUrl)
        assertEquals("new-key", viewModel.uiState.value.streamKey)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
        assertEquals("rtmp://new-secondary/app", viewModel.uiState.value.secondaryStreamUrl)
        assertEquals("new-key-2", viewModel.uiState.value.secondaryStreamKey)
        assertEquals(true, viewModel.uiState.value.secondaryStreamUseTls)
        assertEquals("obs.example.com", viewModel.uiState.value.obsHost)
        assertEquals("4455", viewModel.uiState.value.obsPort)
        assertEquals("pw", viewModel.uiState.value.obsPassword)
        assertEquals(true, viewModel.uiState.value.obsUseTls)
        assertEquals("meinKanal", viewModel.uiState.value.chatChannel)
        assertEquals(true, viewModel.uiState.value.chatOverlayEnabled)
    }

    @Test
    fun `loads chat settings from repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(
                    chatChannel = "meinKanal",
                    chatOverlayEnabled = true,
                ),
            )
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("meinKanal", viewModel.uiState.value.chatChannel)
        assertEquals(true, viewModel.uiState.value.chatOverlayEnabled)
    }

    @Test
    fun `applying a platform preset fills the ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.applyPlatformPreset(StreamPlatform.Kick)

        assertEquals("rtmp://live.kick.com/app", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying the youtube preset fills the youtube ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.applyPlatformPreset(StreamPlatform.YouTube)

        assertEquals("rtmp://a.rtmp.youtube.com/live2", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying the twitch preset fills the twitch ingest url and enables tls`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.applyPlatformPreset(StreamPlatform.Twitch)

        assertEquals("rtmp://live.twitch.tv/app", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying the custom preset clears the url and leaves tls untouched`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        // Vorher: Vorlage aktiv (URL + TLS an)
        viewModel.applyPlatformPreset(StreamPlatform.Twitch)
        assertEquals("rtmp://live.twitch.tv/app", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)

        // Custom: URL wird geleert, TLS bleibt an (wird NICHT zurückgesetzt)
        viewModel.applyPlatformPreset(StreamPlatform.Custom)
        assertEquals("", viewModel.uiState.value.streamUrl)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `custom preset does not force tls on`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        // TLS bewusst aus → Custom darf es nicht einschalten
        viewModel.onStreamUseTlsChange(false)
        viewModel.applyPlatformPreset(StreamPlatform.Custom)

        assertEquals("", viewModel.uiState.value.streamUrl)
        assertEquals(false, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `applying a preset keeps an already entered stream key`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onStreamKeyChange("live_12345_secret")
        viewModel.applyPlatformPreset(StreamPlatform.Twitch)

        assertEquals("rtmp://live.twitch.tv/app", viewModel.uiState.value.streamUrl)
        assertEquals("live_12345_secret", viewModel.uiState.value.streamKey)
        assertEquals(true, viewModel.uiState.value.streamUseTls)
    }

    @Test
    fun `saveSettings persists stream and obs settings and emits the save event`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onStreamUrlChange("rtmp://live/app")
        viewModel.onStreamKeyChange("key-9")
        viewModel.onStreamUseTlsChange(true)
        viewModel.onSecondaryStreamUrlChange("rtmp://live-second/app")
        viewModel.onSecondaryStreamKeyChange("key-8")
        viewModel.onSecondaryStreamUseTlsChange(true)
        viewModel.onObsHostChange("obs.example.com")
        viewModel.onObsPortChange("4455")
        viewModel.onObsPasswordChange("pw")
        viewModel.onChatChannelChange("meinKanal")
        viewModel.onChatOverlayEnabledChange(true)

        val events = mutableListOf<Unit>()
        val collector = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.saveEvent.collect { events.add(it) }
        }

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { repository.updateStreamSettings("rtmp://live/app", "key-9", true) }
        coVerify { repository.updateSecondaryStreamSettings("rtmp://live-second/app", "key-8", true) }
        coVerify { repository.updateObsSettings("obs.example.com", "4455", "pw", false) }
        coVerify { repository.updateChatSettings("meinKanal", true) }
        assertEquals(1, events.size)
        collector.cancel()
    }

    @Test
    fun `saveSettings persists the chat bot enable state and mode switch`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onChatBotEnabledChange(true)
        viewModel.onChatBotModeChange(ChatBotMode.COMMAND)

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify {
            repository.updateChatBotSettings(
                enabled = true,
                apiBaseUrl = "https://api.openai.com",
                apiKey = "",
                model = "gpt-4o-mini",
                systemPrompt = "",
                replyCooldownSeconds = 8L,
                mentionsOnly = true,
                maxRepliesPerMinute = 10,
                mode = ChatBotMode.COMMAND,
                login = "",
                oauthToken = "",
            )
        }
    }

    @Test
    fun `widget toggles update the ui state and persist on save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        // Defaults: Widget aus, alle Felder sichtbar.
        assertEquals(false, viewModel.uiState.value.widgetEnabled)
        assertEquals(true, viewModel.uiState.value.widgetShowTime)

        viewModel.onWidgetEnabledChange(true)
        viewModel.onWidgetShowTimeChange(false)
        viewModel.onWidgetShowLocationChange(true)
        viewModel.onWidgetShowSpeedChange(false)

        assertEquals(true, viewModel.uiState.value.widgetEnabled)
        assertEquals(false, viewModel.uiState.value.widgetShowTime)
        assertEquals(true, viewModel.uiState.value.widgetShowLocation)
        assertEquals(false, viewModel.uiState.value.widgetShowSpeed)

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify {
            repository.updateWidgetSettings(
                enabled = true,
                showTime = false,
                showLocation = true,
                showSpeed = false, showAltitude = any())
        }
    }

    @Test
    fun `sentry toggle updates the ui state and persists on save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        // Default: Fehlerberichte an.
        assertEquals(true, viewModel.uiState.value.sentryEnabled)

        viewModel.onSentryEnabledChange(false)
        assertEquals(false, viewModel.uiState.value.sentryEnabled)

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify { repository.updateSentryEnabled(false) }
    }

    @Test
    fun `sentry default stays enabled after loading settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(true, viewModel.uiState.value.sentryEnabled)
    }

    @Test
    fun `input changes update the chat bot ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onChatBotEnabledChange(true)
        viewModel.onChatBotModeChange(ChatBotMode.COMMAND)
        viewModel.onChatBotLoginChange("vividbot")
        viewModel.onChatBotOauthTokenChange("oauth:tok123")
        viewModel.onChatBotApiBaseUrlChange("https://llm.example")
        viewModel.onChatBotApiKeyChange("sk-secret")
        viewModel.onChatBotModelChange("my-model")
        viewModel.onChatBotSystemPromptChange("Du bist mein Bot.")
        viewModel.onChatBotReplyCooldownSecondsChange("12")
        viewModel.onChatBotMentionsOnlyChange(false)
        viewModel.onChatBotMaxRepliesPerMinuteChange("25")
        viewModel.onChatBotIgnoreBotsChange("rivuletbot")
        viewModel.onChatBotCommandScopeChange(ChatBotCommandScope.PREFIX)
        viewModel.onChatBotCommandPrefixChange("v")
        viewModel.onChatBotPerViewerCooldownSecondsChange("90")
        viewModel.onChatBotPerViewerMaxRepliesChange("4")
        viewModel.onChatBotMaxRepliesPerHourChange("50")

        assertEquals(true, viewModel.uiState.value.chatBotEnabled)
        assertEquals(ChatBotMode.COMMAND, viewModel.uiState.value.chatBotMode)
        assertEquals("vividbot", viewModel.uiState.value.chatBotLogin)
        assertEquals("oauth:tok123", viewModel.uiState.value.chatBotOauthToken)
        assertEquals("https://llm.example", viewModel.uiState.value.chatBotApiBaseUrl)
        assertEquals("sk-secret", viewModel.uiState.value.chatBotApiKey)
        assertEquals("my-model", viewModel.uiState.value.chatBotModel)
        assertEquals("Du bist mein Bot.", viewModel.uiState.value.chatBotSystemPrompt)
        assertEquals(12L, viewModel.uiState.value.chatBotReplyCooldownSeconds)
        assertEquals(false, viewModel.uiState.value.chatBotMentionsOnly)
        assertEquals(25, viewModel.uiState.value.chatBotMaxRepliesPerMinute)
        assertEquals("rivuletbot", viewModel.uiState.value.chatBotIgnoreBots)
        assertEquals(ChatBotCommandScope.PREFIX, viewModel.uiState.value.chatBotCommandScope)
        assertEquals("v", viewModel.uiState.value.chatBotCommandPrefix)
        assertEquals(90L, viewModel.uiState.value.chatBotPerViewerCooldownSeconds)
        assertEquals(4, viewModel.uiState.value.chatBotPerViewerMaxReplies)
        assertEquals(50, viewModel.uiState.value.chatBotMaxRepliesPerHour)

        viewModel.onChatBotModeChange(ChatBotMode.AUTONOMOUS)
        assertEquals(ChatBotMode.AUTONOMOUS, viewModel.uiState.value.chatBotMode)
    }

    @Test
    fun `owner settings input changes update the ui state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle() // initial load drained so edits are not overwritten

        viewModel.onChatBotOwnerLoginsChange("streamer2, zweitkonto")
        viewModel.onChatBotOwnerLlmBaseUrlChange("https://owner.example")
        viewModel.onChatBotOwnerLlmApiKeyChange("sk-owner")
        viewModel.onChatBotOwnerLlmModelChange("claude-4")
        viewModel.onChatBotOwnerWhisperRepliesChange(false)
        viewModel.onChatBotTwitchClientIdChange("client-abc")

        assertEquals("streamer2, zweitkonto", viewModel.uiState.value.chatBotOwnerLogins)
        assertEquals("https://owner.example", viewModel.uiState.value.chatBotOwnerLlmBaseUrl)
        assertEquals("sk-owner", viewModel.uiState.value.chatBotOwnerLlmApiKey)
        assertEquals("claude-4", viewModel.uiState.value.chatBotOwnerLlmModel)
        assertEquals(false, viewModel.uiState.value.chatBotOwnerWhisperReplies)
        assertEquals("client-abc", viewModel.uiState.value.chatBotTwitchClientId)
    }

    @Test
    fun `numeric chat bot fields fall back to zero on invalid input`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onChatBotReplyCooldownSecondsChange("abc")
        viewModel.onChatBotMaxRepliesPerMinuteChange("xyz")

        assertEquals(0L, viewModel.uiState.value.chatBotReplyCooldownSeconds)
        assertEquals(0, viewModel.uiState.value.chatBotMaxRepliesPerMinute)
    }

    @Test
    fun `limit presets fill the three limit fields`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.LOCKER)
        assertEquals(30L, viewModel.uiState.value.chatBotPerViewerCooldownSeconds)
        assertEquals(0, viewModel.uiState.value.chatBotPerViewerMaxReplies)
        assertEquals(0, viewModel.uiState.value.chatBotMaxRepliesPerHour)
        assertEquals(ChatBotLimitPreset.LOCKER.name, viewModel.uiState.value.chatBotLimitPreset)

        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.BALANCED)
        assertEquals(60L, viewModel.uiState.value.chatBotPerViewerCooldownSeconds)
        assertEquals(10, viewModel.uiState.value.chatBotPerViewerMaxReplies)
        assertEquals(120, viewModel.uiState.value.chatBotMaxRepliesPerHour)
        assertEquals(ChatBotLimitPreset.BALANCED.name, viewModel.uiState.value.chatBotLimitPreset)

        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.STRICT)
        assertEquals(180L, viewModel.uiState.value.chatBotPerViewerCooldownSeconds)
        assertEquals(5, viewModel.uiState.value.chatBotPerViewerMaxReplies)
        assertEquals(60, viewModel.uiState.value.chatBotMaxRepliesPerHour)
        assertEquals(ChatBotLimitPreset.STRICT.name, viewModel.uiState.value.chatBotLimitPreset)

        // Nach der Voreinstellung bleiben die Werte frei anpassbar —
        // eine manuelle Änderung markiert die Auswahl als „Eigene“ (CUSTOM).
        viewModel.onChatBotPerViewerCooldownSecondsChange("90")
        assertEquals(90L, viewModel.uiState.value.chatBotPerViewerCooldownSeconds)
        assertEquals(ChatBotLimitPreset.CUSTOM, viewModel.uiState.value.chatBotLimitPreset)
    }

    @Test
    fun `manual limit edits mark the selection as custom`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.BALANCED)
        assertEquals(ChatBotLimitPreset.BALANCED.name, viewModel.uiState.value.chatBotLimitPreset)

        viewModel.onChatBotPerViewerMaxRepliesChange("3")
        assertEquals(ChatBotLimitPreset.CUSTOM, viewModel.uiState.value.chatBotLimitPreset)

        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.STRICT)
        viewModel.onChatBotMaxRepliesPerHourChange("20")
        assertEquals(ChatBotLimitPreset.CUSTOM, viewModel.uiState.value.chatBotLimitPreset)
    }

    @Test
    fun `saveSettings persists the complete chat bot configuration`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onChatBotEnabledChange(true)
        viewModel.onChatBotModeChange(ChatBotMode.AUTONOMOUS)
        viewModel.onChatBotLoginChange("vividbot")
        viewModel.onChatBotOauthTokenChange("oauth:tok123")
        viewModel.onChatBotApiBaseUrlChange("https://llm.example")
        viewModel.onChatBotApiKeyChange("sk-secret")
        viewModel.onChatBotModelChange("my-model")
        viewModel.onChatBotSystemPromptChange("Du bist mein Bot.")
        viewModel.onChatBotReplyCooldownSecondsChange("5")
        viewModel.onChatBotMentionsOnlyChange(false)
        viewModel.onChatBotMaxRepliesPerMinuteChange("20")
        viewModel.onChatBotIgnoreBotsChange("rivuletbot, otherbot")
        viewModel.onChatBotCommandScopeChange(ChatBotCommandScope.PREFIX)
        viewModel.onChatBotCommandPrefixChange("v")
        viewModel.onChatBotLimitPresetChange(ChatBotLimitPreset.STRICT)
        viewModel.onChatBotOwnerLoginsChange("streamer2, zweitkonto")
        viewModel.onChatBotOwnerLlmBaseUrlChange("https://owner.example")
        viewModel.onChatBotOwnerLlmApiKeyChange("sk-owner")
        viewModel.onChatBotOwnerLlmModelChange("claude-4")
        viewModel.onChatBotOwnerWhisperRepliesChange(false)
        viewModel.onChatBotTwitchClientIdChange("client-abc")

        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify {
            repository.updateChatBotSettings(
                enabled = true,
                apiBaseUrl = "https://llm.example",
                apiKey = "sk-secret",
                model = "my-model",
                systemPrompt = "Du bist mein Bot.",
                replyCooldownSeconds = 5L,
                mentionsOnly = false,
                maxRepliesPerMinute = 20,
                mode = ChatBotMode.AUTONOMOUS,
                login = "vividbot",
                oauthToken = "oauth:tok123",
                ignoreBots = "rivuletbot, otherbot",
                commandScope = ChatBotCommandScope.PREFIX,
                commandPrefix = "v",
                perViewerCooldownSeconds = 180L,
                perViewerMaxReplies = 5,
                maxRepliesPerHour = 60,
                limitPreset = ChatBotLimitPreset.STRICT.name,
                ownerLogins = "streamer2, zweitkonto",
                ownerLlmBaseUrl = "https://owner.example",
                ownerLlmApiKey = "sk-owner",
                ownerLlmModel = "claude-4",
                ownerWhisperReplies = false,
                twitchClientId = "client-abc",
            )
        }
    }

    @Test
    fun `ownerLlmSource is OWNER when all three owner llm fields are set`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onChatBotOwnerLlmBaseUrlChange("https://owner.example")
        viewModel.onChatBotOwnerLlmApiKeyChange("sk-owner")
        viewModel.onChatBotOwnerLlmModelChange("claude-4")

        assertEquals(OwnerLlmSource.OWNER, viewModel.ownerLlmSource)
    }

    @Test
    fun `ownerLlmSource falls back to the viewer llm when no owner llm is set`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onChatBotApiBaseUrlChange("https://llm.example")
        viewModel.onChatBotApiKeyChange("sk-secret")
        viewModel.onChatBotModelChange("my-model")

        assertEquals(OwnerLlmSource.VIEWER_FALLBACK, viewModel.ownerLlmSource)
    }

    @Test
    fun `ownerLlmSource is DETERMINISTIC when no llm is configured at all`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(OwnerLlmSource.DETERMINISTIC, viewModel.ownerLlmSource)
    }

    @Test
    fun `botUsage forwards the engine usage state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val engine = mockk<ChatBotEngine> {
            every { usage } returns MutableStateFlow(
                ChatBotUsage(
                    repliesThisHour = 7,
                    hourlyBudget = 120,
                    totalRepliesThisStream = 12,
                    topViewers = listOf(ChatBotUsage.ViewerUsage("ViewerEins", 5)),
                ),
            )
        }
        val viewModel = createViewModel(chatBotEngine = engine)
        advanceUntilIdle()

        assertEquals(7, viewModel.botUsage.value.repliesThisHour)
        assertEquals(120, viewModel.botUsage.value.hourlyBudget)
        assertEquals(12, viewModel.botUsage.value.totalRepliesThisStream)
        assertEquals("ViewerEins", viewModel.botUsage.value.topViewers.first().displayName)
    }

    // --- Web-Remote-Control ---

    @Test
    fun `loads the remote control token and default port`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel(tokenStore = tokenStore("abc-123"))
        advanceUntilIdle()

        assertEquals(RemoteControlServer.DEFAULT_PORT, viewModel.remoteControl.value.port)
        assertEquals("abc-123", viewModel.remoteControl.value.token)
    }

    @Test
    fun `restartRemoteControlServer stops and starts the server`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val server = mockk<RemoteControlServer> {
            coEvery { stop() } just runs
            coEvery { start() } just runs
        }
        val viewModel = createViewModel(remoteControlServer = server)
        advanceUntilIdle()

        viewModel.restartRemoteControlServer()
        advanceUntilIdle()

        coVerify(exactly = 1) { server.stop() }
        coVerify(exactly = 1) { server.start() }
    }

    // --- Update-Indikator (Obtainium-Test) ---

    @Test
    fun `checkForUpdates reports an available update`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check("0.2.0-nightly.93") } returns UpdateCheckResult.UpdateAvailable(
            latestVersion = "0.2.0-nightly.97",
            releaseUrl = "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118",
        )
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        assertFalse(viewModel.updateState.value.checking)
        assertEquals(
            UpdateCheckResult.UpdateAvailable("0.2.0-nightly.97", "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-1118"),
            viewModel.updateState.value.result,
        )
    }

    @Test
    fun `checkForUpdates reports up to date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.UpToDate(latestVersion = "0.2.0-nightly.97")
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.97")
        advanceUntilIdle()

        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), viewModel.updateState.value.result)
    }

    @Test
    fun `checkForUpdates maps errors without touching the settings state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.Error(
            R.string.update_error_check_failed,
            listOf("network down"),
        )
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        assertEquals(
            UpdateCheckResult.Error(R.string.update_error_check_failed, listOf("network down")),
            viewModel.updateState.value.result,
        )
    }

    @Test
    fun `checkForUpdates ignores a blank version`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("")
        advanceUntilIdle()

        coVerify(exactly = 0) { checker.check(any()) }
        assertEquals(null, viewModel.updateState.value.result)
    }

    @Test
    fun `checkForUpdates only runs once per version`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } returns UpdateCheckResult.UpToDate("0.2.0-nightly.97")
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle()

        coVerify(exactly = 1) { checker.check("0.2.0-nightly.93") }
    }

    @Test
    fun `checkForUpdates is ignored while a check is running`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val gate = CompletableDeferred<UpdateCheckResult>()
        val checker = mockk<UpdateChecker>()
        coEvery { checker.check(any()) } coAnswers { gate.await() }
        val viewModel = createViewModel(checker = checker)

        viewModel.checkForUpdates("0.2.0-nightly.93")
        advanceUntilIdle() // läuft bis zum gate
        assertTrue(viewModel.updateState.value.checking)

        viewModel.checkForUpdates("0.2.0-nightly.93") // muss ignoriert werden

        gate.complete(UpdateCheckResult.UpToDate("0.2.0-nightly.97"))
        advanceUntilIdle()
        assertFalse(viewModel.updateState.value.checking)
        assertEquals(UpdateCheckResult.UpToDate("0.2.0-nightly.97"), viewModel.updateState.value.result)
    }

    @Test
    fun `loads theme settings from repository`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(
                AppSettings(
                    themeMode = ThemeMode.AMOLED,
                    themeAccent = AccentColor.OCEAN_BLUE,
                ),
            )
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(ThemeMode.AMOLED, viewModel.uiState.value.themeMode)
        assertEquals(AccentColor.OCEAN_BLUE, viewModel.uiState.value.themeAccent)
    }

    @Test
    fun `theme mode and accent change handlers update the state`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(ThemeMode.SYSTEM, viewModel.uiState.value.themeMode)
        assertEquals(AccentColor.VIVID_GREEN, viewModel.uiState.value.themeAccent)

        viewModel.onThemeModeChange(ThemeMode.DARK)
        viewModel.onAccentColorChange(AccentColor.ROYAL_PURPLE)

        assertEquals(ThemeMode.DARK, viewModel.uiState.value.themeMode)
        assertEquals(AccentColor.ROYAL_PURPLE, viewModel.uiState.value.themeAccent)
    }

    @Test
    fun `saveSettings persists the theme settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateStreamSettings(any(), any(), any()) } just runs
            coEvery { updateSecondaryStreamSettings(any(), any(), any()) } just runs
            coEvery { updateObsSettings(any(), any(), any(), any()) } just runs
            coEvery { updateChatSettings(any(), any()) } just runs
            coEvery { updateChatBotSettings(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } just runs
            coEvery { updateWidgetSettings(any(), any(), any(), any(), any()) } just runs
            coEvery { updateSentryEnabled(any()) } just runs
            coEvery { updateThemeSettings(any(), any()) } just runs
        }

        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.onThemeModeChange(ThemeMode.AMOLED)
        viewModel.onAccentColorChange(AccentColor.TEAL)
        viewModel.saveSettings()
        advanceUntilIdle()

        coVerify {
            repository.updateThemeSettings(
                themeMode = ThemeMode.AMOLED,
                accentColor = AccentColor.TEAL,
            )
        }
    }
}
