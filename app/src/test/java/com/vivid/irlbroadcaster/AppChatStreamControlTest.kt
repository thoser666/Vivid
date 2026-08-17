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

    private suspend fun ownerLlmSourceCheck(settings: AppSettings): DiagnosticCheck =
        control(settings).diagnostics().checks.first { it.label == "Owner-KI-Quelle" }

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

    @Test
    fun `owner ki source ok with the exclusive owner llm`() = runTest {
        val check = ownerLlmSourceCheck(
            baseSettings.copy(
                chatBotOwnerLlmBaseUrl = "https://owner.example",
                chatBotOwnerLlmApiKey = "sk-owner",
                chatBotOwnerLlmModel = "claude-4",
            ),
        )
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("eigene Owner-KI (exklusiv)"))
    }

    @Test
    fun `owner ki source ok with the viewer llm as fallback`() = runTest {
        val check = ownerLlmSourceCheck(
            baseSettings.copy(
                chatBotApiBaseUrl = "https://llm.example",
                chatBotApiKey = "sk-secret",
                chatBotModel = "my-model",
            ),
        )
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("Viewer-KI (Fallback)"))
    }

    @Test
    fun `owner ki source is missing when no llm is configured at all`() = runTest {
        val check = ownerLlmSourceCheck(baseSettings)
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("deterministisch"))
    }

    @Test
    fun `diagnostics exposes the owner ki source check to the owner ki`() = runTest {
        val diagnostics = control(baseSettings).diagnostics()
        assertTrue(diagnostics.checks.any { it.label == "Owner-KI-Quelle" })
        // Ohne jede KI ist der Check im Fact-Sheet offen (MISSING).
        assertTrue(diagnostics.factSheet().contains("check:Owner-KI-Quelle=MISSING"))
        // Mit Owner-KI ist er ok.
        val okDiagnostics = control(
            baseSettings.copy(
                chatBotOwnerLlmBaseUrl = "https://owner.example",
                chatBotOwnerLlmApiKey = "sk-owner",
                chatBotOwnerLlmModel = "claude-4",
            ),
        ).diagnostics()
        assertTrue(okDiagnostics.factSheet().contains("check:Owner-KI-Quelle=ok"))
    }
}
