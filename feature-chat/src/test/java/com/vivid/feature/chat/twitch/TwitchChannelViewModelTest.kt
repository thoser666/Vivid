package com.vivid.feature.chat.twitch

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.feature.chat.R
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class TwitchChannelViewModelTest {
    private val settings = AppSettings(
        chatChannel = "streamer",
        chatBotOauthToken = "oauth:bot-token",
        chatBotTwitchClientId = "client-id",
    )

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun repository(
        value: AppSettings = settings,
    ): SettingsRepository = mockk {
        every { appSettingsFlow } returns MutableStateFlow(value)
        coEvery { updateTwitchChannelSettings(any(), any(), any(), any()) } just runs
    }

    private fun tokenStore(session: TwitchTokenSession? = null): TwitchTokenStore = mockk {
        coEvery { loadSession() } returns session
    }

    @Test
    fun `refresh exposes live stream information`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery {
            client.getStreamInfo(
                TwitchChannelConfig("streamer", "oauth:bot-token", "client-id"),
            )
        } returns TwitchStreamInfo(42, "Live in Berlin", "Just Chatting")
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(TwitchStreamInfo(42, "Live in Berlin", "Just Chatting"), viewModel.uiState.value.streamInfo)
        assertFalse(viewModel.uiState.value.loading)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `refresh reports an actionable error when twitch is not configured`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        val viewModel = TwitchChannelViewModel(
            repository(AppSettings(chatChannel = "streamer")),
            client,
            tokenStore(),
        )

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(R.string.twitch_channel_error_not_configured, viewModel.uiState.value.errorRes)
        assertEquals(null, viewModel.uiState.value.error)
        coVerify(exactly = 0) { client.getStreamInfo(any()) }
    }

    @Test
    fun `refresh forwards client failures without crashing the ui`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery { client.getStreamInfo(any()) } throws TwitchChannelException(
            "twitch http 429 (viewer count)",
            statusCode = 429,
            messageRes = R.string.twitch_channel_error_http_429,
        )
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals(R.string.twitch_channel_error_http_429, viewModel.uiState.value.errorRes)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `refresh keeps a raw exception detail untranslated`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery { client.getStreamInfo(any()) } throws TwitchChannelException("Rate-Limit")
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals("Rate-Limit", viewModel.uiState.value.error)
        assertEquals(0, viewModel.uiState.value.errorRes)
    }

    @Test
    fun `refresh falls back to the localized message when the failure carries no detail`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery { client.getStreamInfo(any()) } throws TwitchChannelException("")
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(R.string.twitch_channel_error_load_failed, viewModel.uiState.value.errorRes)
        assertEquals(null, viewModel.uiState.value.error)
    }

    @Test
    fun `refresh forwards the format arguments of the localized message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery { client.getStreamInfo(any()) } throws TwitchChannelException(
            "category not found: Just Chatting",
            messageRes = R.string.twitch_channel_error_category_not_found,
            formatArgs = listOf("Just Chatting"),
        )
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(R.string.twitch_channel_error_category_not_found, viewModel.uiState.value.errorRes)
        assertEquals(listOf("Just Chatting"), viewModel.uiState.value.formatArgs)
    }

    @Test
    fun `refresh prefers the securely stored oauth session over settings`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery {
            client.getStreamInfo(TwitchChannelConfig("streamer", "stored-access", "client-id"))
        } returns TwitchStreamInfo(3, "Gespeichert", "Games")
        val viewModel = TwitchChannelViewModel(
            repository(),
            client,
            tokenStore(
                TwitchTokenSession(
                    accessToken = "stored-access",
                    refreshToken = "stored-refresh",
                    expiresAtMillis = System.currentTimeMillis() + 60_000L,
                ),
            ),
        )

        viewModel.refresh()
        advanceUntilIdle()

        coVerify {
            client.getStreamInfo(TwitchChannelConfig("streamer", "stored-access", "client-id"))
        }
        assertEquals(TwitchStreamInfo(3, "Gespeichert", "Games"), viewModel.uiState.value.streamInfo)
    }

    @Test
    fun `update sets channel metadata and persists the successful values`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repository()
        val client = mockk<TwitchChannelClient>()
        coEvery { client.updateChannelInfo(any(), " Neuer Titel ", " Just Chatting ") } just runs
        val viewModel = TwitchChannelViewModel(repository, client, tokenStore())

        viewModel.updateChannelInfo(" Neuer Titel ", " Just Chatting ")
        advanceUntilIdle()

        coVerify {
            client.updateChannelInfo(
                TwitchChannelConfig("streamer", "oauth:bot-token", "client-id"),
                " Neuer Titel ",
                " Just Chatting ",
            )
        }
        coVerify {
            repository.updateTwitchChannelSettings(
                channel = "streamer",
                oauthToken = "",
                title = "Neuer Titel",
                category = "Just Chatting",
            )
        }
        assertFalse(viewModel.uiState.value.saving)
    }

    @Test
    fun `update leaves persistence untouched when twitch rejects the change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val repository = repository()
        val client = mockk<TwitchChannelClient>()
        coEvery { client.updateChannelInfo(any(), any(), any()) } throws TwitchChannelException(
            "twitch http 403 (channel info)",
            statusCode = 403,
            messageRes = R.string.twitch_channel_error_http_403,
        )
        val viewModel = TwitchChannelViewModel(repository, client, tokenStore())

        viewModel.updateChannelInfo("Titel", "Kategorie")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.saving)
        assertEquals(R.string.twitch_channel_error_http_403, viewModel.uiState.value.errorRes)
        assertEquals(null, viewModel.uiState.value.error)
        coVerify(exactly = 0) { repository.updateTwitchChannelSettings(any(), any(), any(), any()) }
    }
}
