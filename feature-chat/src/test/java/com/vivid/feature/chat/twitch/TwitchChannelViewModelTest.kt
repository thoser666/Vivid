package com.vivid.feature.chat.twitch

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
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

        assertTrue(viewModel.uiState.value.error.orEmpty().contains("nicht vollständig konfiguriert"))
        coVerify(exactly = 0) { client.getStreamInfo(any()) }
    }

    @Test
    fun `refresh forwards client failures without crashing the ui`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val client = mockk<TwitchChannelClient>()
        coEvery { client.getStreamInfo(any()) } throws TwitchChannelException("Rate-Limit")
        val viewModel = TwitchChannelViewModel(repository(), client, tokenStore())

        viewModel.refresh()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.loading)
        assertEquals("Rate-Limit", viewModel.uiState.value.error)
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
        coEvery { client.updateChannelInfo(any(), any(), any()) } throws TwitchChannelException("Scope fehlt")
        val viewModel = TwitchChannelViewModel(repository, client, tokenStore())

        viewModel.updateChannelInfo("Titel", "Kategorie")
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.saving)
        assertEquals("Scope fehlt", viewModel.uiState.value.error)
        coVerify(exactly = 0) { repository.updateTwitchChannelSettings(any(), any(), any(), any()) }
    }
}
