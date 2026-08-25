package com.vivid.irlbroadcaster

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
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
import java.io.File
import java.nio.file.Files

class AppChatStreamControlTest {

    private fun control(settings: AppSettings, logStore: LogStore = LogStore(File("unused"))): AppChatStreamControl {
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
        return AppChatStreamControl(streamControl, engine, repository, settingsRepository, logStore)
    }

    private suspend fun whisperCheck(settings: AppSettings): DiagnosticCheck =
        control(settings).diagnostics().checks.first { it.label == "Whisper (privater Antwortweg)" }

    private suspend fun ownerLlmSourceCheck(settings: AppSettings): DiagnosticCheck =
        control(settings).diagnostics().checks.first { it.label == "Owner-KI-Quelle" }

    private suspend fun alertsCheck(settings: AppSettings): DiagnosticCheck =
        control(settings).diagnostics().checks.first { it.label == "Event-Alerts konfiguriert" }

    private suspend fun crashSummaryCheck(
        settings: AppSettings,
        logStore: LogStore,
    ): DiagnosticCheck =
        control(settings, logStore).diagnostics().checks.first { it.label == "Crash-Zusammenfassung" }

    private fun newLogStore(dirName: String): LogStore =
        // Frisches, leeres Verzeichnis pro Testaufruf, damit Tage-Dateien
        // früherer Läufe die Crash-Zählung nicht verfälschen.
        LogStore(File(Files.createTempDirectory("diag_store").toFile(), dirName))

    private fun crashEntry(daysAgo: Int) = LogEntry(
        timestampMillis = System.currentTimeMillis() - daysAgo * 24L * 60 * 60 * 1000,
        level = LogLevel.ASSERT,
        tag = "Crash",
        message = "boom",
        isCrash = true,
    )

    private val baseSettings = AppSettings(
        chatBotOwnerWhisperReplies = true,
        chatBotTwitchClientId = "client-abc",
        chatBotOauthToken = "oauth:tok123",
    )

    private val alertsBaseSettings = AppSettings(
        chatChannel = "mychannel",
        chatBotLogin = "vividbot",
        chatBotOauthToken = "oauth:tok123",
        chatBotTwitchClientId = "client-abc",
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

    @Test
    fun `alerts check ok when channel bot and client id are set`() = runTest {
        val check = alertsCheck(alertsBaseSettings)
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("Moderator"))
    }

    @Test
    fun `alerts check fails when the chat channel is missing`() = runTest {
        val check = alertsCheck(alertsBaseSettings.copy(chatChannel = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Chat-Kanal fehlt"))
    }

    @Test
    fun `alerts check fails when the bot login is missing`() = runTest {
        val check = alertsCheck(alertsBaseSettings.copy(chatBotLogin = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Bot-Login fehlt"))
    }

    @Test
    fun `alerts check fails when the bot token is missing`() = runTest {
        val check = alertsCheck(alertsBaseSettings.copy(chatBotOauthToken = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Bot-Token fehlt"))
    }

    @Test
    fun `alerts check fails when the client id is missing`() = runTest {
        val check = alertsCheck(alertsBaseSettings.copy(chatBotTwitchClientId = ""))
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Client-ID fehlt"))
    }

    @Test
    fun `alerts check lists all missing parts at once`() = runTest {
        val check = alertsCheck(AppSettings())
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.contains("Kanal, Bot-Login, Bot-Token und Client-ID fehlen"))
    }

    @Test
    fun `diagnostics exposes the alerts check to the owner ki`() = runTest {
        val diagnostics = control(alertsBaseSettings).diagnostics()
        assertTrue(diagnostics.checks.any { it.label == "Event-Alerts konfiguriert" })
        // Das Fact-Sheet (für !ask/!diag mit Owner-KI) listet alle Checks.
        assertTrue(diagnostics.factSheet().contains("check:Event-Alerts konfiguriert=ok"))
        val missing = control(alertsBaseSettings.copy(chatBotTwitchClientId = "")).diagnostics()
        assertTrue(missing.factSheet().contains("check:Event-Alerts konfiguriert=MISSING"))
    }

    // ── Crash-Zusammenfassung (LogStore) ─────────────────────────────────────

    @Test
    fun `crash summary ok when no crashes are recorded`() = runTest {
        val store = newLogStore("diag_no_crash")
        store.add(crashEntry(0).copy(isCrash = false))
        val check = crashSummaryCheck(baseSettings, store)
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("keine Crashes"))
    }

    @Test
    fun `crash summary counts crashes within the retention window`() = runTest {
        val store = newLogStore("diag_crashes")
        store.add(crashEntry(daysAgo = 0))
        store.add(crashEntry(daysAgo = 1))
        val check = crashSummaryCheck(baseSettings, store)
        assertFalse(check.ok)
        assertTrue(check.detail, check.detail.startsWith("2 Crash/Crashes"))
        assertTrue(check.detail, check.detail.contains("Auswertung im Log-Screen"))
    }

    @Test
    fun `crash summary respects the retention window`() = runTest {
        val store = newLogStore("diag_window")
        // Vorhaltezeit 3 Tage: der Crash von vor 10 Tagen zählt nicht mehr.
        store.add(crashEntry(daysAgo = 10))
        val check = crashSummaryCheck(baseSettings.copy(logsRetentionDays = 3), store)
        assertTrue(check.detail, check.ok)
        assertTrue(check.detail, check.detail.contains("letzten 3 Tagen"))
    }

    @Test
    fun `crash summary clamps an invalid retention setting`() = runTest {
        val store = newLogStore("diag_clamp")
        store.add(crashEntry(daysAgo = 20))
        // Retention 0 → geklemmt auf 1 Tag (heute) → alter Crash zählt nicht mehr.
        val low = crashSummaryCheck(baseSettings.copy(logsRetentionDays = 0), store)
        assertTrue(low.detail, low.ok)
        assertTrue(low.detail, low.detail.contains("letzten 1 Tagen"))
        // Retention 500 → geklemmt auf 30 Tage → Crash von vor 20 Tagen zählt.
        val high = crashSummaryCheck(baseSettings.copy(logsRetentionDays = 500), store)
        assertFalse(high.ok)
        assertTrue(high.detail, high.detail.contains("letzten 30 Tagen"))
    }

    @Test
    fun `crash summary is exposed in the fact sheet`() = runTest {
        val diagnostics = control(baseSettings).diagnostics()
        assertTrue(diagnostics.factSheet().contains("check:Crash-Zusammenfassung=ok"))
    }
}
