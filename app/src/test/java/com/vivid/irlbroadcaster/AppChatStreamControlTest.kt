package com.vivid.irlbroadcaster

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.StreamControl
import com.vivid.core.repository.StreamingRepository
import com.vivid.feature.chat.bot.DiagnosticCheck
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppChatStreamControlTest {

    private fun control(settings: AppSettings): AppChatStreamControl {
        val streamControl = mockk<StreamControl>()
        val engine = mockk<StreamingEngine> {
            every { streamingState } returns MutableStateFlow(StreamingState.Streaming)
        }
        val repository = mockk<StreamingRepository> {
            every { isConnectedToObs } returns MutableStateFlow(true)
        }
        val settingsRepository = mockk<SettingsRepository> {
            every { appSettingsFlow } returns flowOf(settings)
        }
        return AppChatStreamControl(streamControl, engine, repository, settingsRepository)
    }

    private suspend fun whisperCheck(settings: AppSettings): DiagnosticCheck =
        control(settings).diagnostics().checks.first { it.label == "Whisper (privater Antwortweg)" }

    private val baseSettings = AppSettings(
        chatBotOwnerWhisperReplies = true,
        chatBotTwitchClientId = "client-abc",
        chatBotOauthToken = "oauth:tok123",
    )

    @Test
    fun `whisper check ok when client id and token are set`() = runTest {
        val check = whisperCheck(baseSettings)
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("Client-ID + Token gesetzt"))
    }

    @Test
    fun `whisper check fails when client id is missing`() = runTest {
        val check = whisperCheck(baseSettings.copy(chatBotTwitchClientId = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Client-ID"))
    }

    @Test
    fun `whisper check fails when bot token is missing`() = runTest {
        val check = whisperCheck(baseSettings.copy(chatBotOauthToken = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Bot-Token"))
    }

    @Test
    fun `whisper check fails when both are missing`() = runTest {
        val check = whisperCheck(baseSettings.copy(chatBotTwitchClientId = "", chatBotOauthToken = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Client-ID + Bot-Token fehlen"))
    }

    @Test
    fun `whisper check ok when private replies are disabled by choice`() = runTest {
        // Toggle aus = bewusst öffentliche Antworten → kein offener Punkt,
        // auch wenn Client-ID/Token fehlen.
        val check = whisperCheck(
            baseSettings.copy(
                chatBotOwnerWhisperReplies = false,
                chatBotTwitchClientId = "",
                chatBotOauthToken = "",
            ),
        )
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("deaktiviert"))
    }

    @Test
    fun `diagnostics exposes the whisper check to the owner ki`() = runTest {
        val diagnostics = control(baseSettings).diagnostics()
        assertTrue(diagnostics.checks.any { it.label == "Whisper (privater Antwortweg)" })
        // Das Fact-Sheet (für !ask/!diag mit Owner-KI) listet alle Checks.
        assertTrue(diagnostics.factSheet().contains("check:Whisper (privater Antwortweg)=ok"))
    }
}
