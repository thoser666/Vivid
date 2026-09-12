package com.vivid.feature.settings.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vivid.core.data.AppSettings
import com.vivid.core.data.EncoderPreset
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für die restlichen Settings-Screens (Camera,
 * Logs & Diagnose, Streaming/OBS, Remote & Privacy). Gleiche Technik wie
 * [SettingsScreensRobolectricTest]: VMs als Parameter, MockK statt Hilt,
 * SDK-34-/en-Pin für deterministische String-Assertionen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class SettingsSubScreensRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun settingsViewModel(): SettingsViewModel =
        mockk(relaxed = true) {
            every { this@mockk.updateState } returns MutableStateFlow(SettingsUpdateState())
            every { saveEvent } returns kotlinx.coroutines.flow.MutableSharedFlow()
        }

    private fun logsViewModel(dir: String): SettingsLogsViewModel = SettingsLogsViewModel(
        LogBuffer(),
        LogStore(File.createTempFile("logs", "").parentFile!!.resolve(dir)),
        // appSettingsFlow wird im VM-Init per first() gelesen — der relaxed
        // Mock liefert sonst einen leeren Flow (NoSuchElementException).
        mockk(relaxed = true) {
            every { appSettingsFlow } returns MutableStateFlow(AppSettings())
            coEvery { updateLogsRetentionDays(any()) } returns Unit
        },
    )

    // --- Camera Controls ----------------------------------------------------

    @Test
    fun `camera shows not-supported hint when manual focus is unavailable`() {
        val viewModel = mockk<SettingsCameraViewModel>(relaxed = true) {
            every { focusDistance } returns MutableStateFlow(0f)
            every { hasManualFocus } returns MutableStateFlow(false)
            every { availableLenses } returns MutableStateFlow(emptyList())
            every { currentLensId } returns MutableStateFlow("0")
        }
        composeRule.setContent { SettingsCameraScreen(viewModel = viewModel) }
        composeRule.onNodeWithText("Camera Controls").assertIsDisplayed()
        composeRule.onNodeWithText("Manual focus is not supported on this device.").assertIsDisplayed()
    }

    @Test
    fun `camera renders focus slider and lens chips and forwards selection`() {
        val viewModel = mockk<SettingsCameraViewModel>(relaxed = true) {
            every { focusDistance } returns MutableStateFlow(2.5f)
            every { hasManualFocus } returns MutableStateFlow(true)
            every { availableLenses } returns MutableStateFlow(
                listOf(
                    SettingsCameraViewModel.LensUiState(id = "0", displayName = "Wide", isActive = true),
                    SettingsCameraViewModel.LensUiState(id = "1", displayName = "Tele", isActive = false),
                ),
            )
            every { currentLensId } returns MutableStateFlow("0")
        }
        composeRule.setContent { SettingsCameraScreen(viewModel = viewModel) }
        composeRule.onNodeWithText("Focus").assertIsDisplayed()
        composeRule.onNodeWithText("Infinity").assertIsDisplayed()
        composeRule.onNodeWithText("Macro").assertIsDisplayed()
        composeRule.onNodeWithText("Tele").performClick()
        verify { viewModel.selectLens("1") }
    }

    // --- Logs & Diagnostics ---------------------------------------------------

    @Test
    fun `logs renders entries with copy and share actions`() {
        // Render-Pfad mit gestubbtem VM (deterministisch); der echte VM inkl.
        // Live-Puffer ist im Toggle-Test darunter abgedeckt.
        val viewModel = mockk<SettingsLogsViewModel>(relaxed = true) {
            every { uiState } returns MutableStateFlow(
                LogsUiState(
                    entries = listOf(
                        LogEntry(
                            timestampMillis = System.currentTimeMillis(),
                            level = LogLevel.INFO,
                            tag = "Test",
                            message = "boom sentinel",
                        ),
                    ),
                ),
            )
        }

        composeRule.setContent { SettingsLogsScreen(onBack = {}, viewModel = viewModel) }
        composeRule.onNodeWithText("Logs & Diagnostics").assertIsDisplayed()
        // Log-Liste liegt teils below the fold — gezielt zum Eintrag scrollen.
        composeRule.onNodeWithTag("logs_list").performScrollToNode(
            androidx.compose.ui.test.hasText("boom sentinel", substring = true),
        )
        composeRule.onAllNodesWithText("boom sentinel", substring = true)[0].assertExists()
        composeRule.onAllNodesWithText("Copy")[0].assertExists()
        composeRule.onAllNodesWithText("Share")[0].assertExists()
        composeRule.onAllNodesWithText("Retention (days)")[0].assertExists()
    }

    @Test
    fun `logs errors-only chip toggles the viewmodel state`() {
        val viewModel = logsViewModel(dir = "logs_ui_test2")
        composeRule.setContent { SettingsLogsScreen(onBack = {}, viewModel = viewModel) }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Errors & crashes").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onAllNodesWithText("Errors & crashes")[0].performClick()
        composeRule.waitUntil(5_000) { viewModel.uiState.value.errorsOnly }
        org.junit.Assert.assertTrue(viewModel.uiState.value.errorsOnly)
    }

    // --- Streaming / OBS ------------------------------------------------------

    @Test
    fun `streaming-obs renders stream fields and twitch section`() {
        composeRule.setContent {
            SettingsStreamingObsScreen(
                uiState = AppSettings(),
                viewModel = settingsViewModel(),
                twitchViewModel = mockk<TwitchChannelViewModel>(relaxed = true) {
                    every { uiState } returns MutableStateFlow(TwitchChannelUiState())
                },
                twitchState = TwitchChannelUiState(),
                onBack = {},
            )
        }
        composeRule.onAllNodesWithText("Stream URL")[0].assertExists()
        composeRule.onAllNodesWithText("Twitch channel control")[0].assertExists()
        composeRule.onAllNodesWithText("Stream title")[0].assertExists()
    }

    @Test
    fun `streaming-obs forwards stream url edits`() {
        val viewModel = settingsViewModel()
        composeRule.setContent {
            SettingsStreamingObsScreen(
                uiState = AppSettings(),
                viewModel = viewModel,
                twitchViewModel = mockk<TwitchChannelViewModel>(relaxed = true) {
                    every { uiState } returns MutableStateFlow(TwitchChannelUiState())
                },
                twitchState = TwitchChannelUiState(),
                onBack = {},
            )
        }
        composeRule.onAllNodesWithText("Stream URL")[0].performTextInput("rtmp://a.live/b")
        verify { viewModel.onStreamUrlChange("rtmp://a.live/b") }
    }

    // --- Encoder-Presets (4K/60fps + HEVC) --------------------------------------

    @Test
    fun `streaming-obs shows encoder preset chips and codec preference`() {
        composeRule.setContent {
            SettingsStreamingObsScreen(
                uiState = AppSettings(encoderPreset = EncoderPreset.S_4K60),
                viewModel = settingsViewModel(),
                twitchViewModel = mockk<TwitchChannelViewModel>(relaxed = true) {
                    every { uiState } returns MutableStateFlow(TwitchChannelUiState())
                },
                twitchState = TwitchChannelUiState(),
                onBack = {},
            )
        }
        composeRule.onAllNodesWithText("Encoder (resolution & codec)")[0].assertExists()
        composeRule.onAllNodesWithText("2160p60")[0].assertExists()
        composeRule.onAllNodesWithText("1080p30")[0].assertExists()
        composeRule.onAllNodesWithText("H.265 (HEVC)")[0].assertExists()
    }

    @Test
    fun `streaming-obs encoder preset click forwards to viewmodel`() {
        val viewModel = settingsViewModel()
        composeRule.setContent {
            SettingsStreamingObsScreen(
                uiState = AppSettings(),
                viewModel = viewModel,
                twitchViewModel = mockk<TwitchChannelViewModel>(relaxed = true) {
                    every { uiState } returns MutableStateFlow(TwitchChannelUiState())
                },
                twitchState = TwitchChannelUiState(),
                onBack = {},
            )
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText("2160p60"))
        composeRule.onAllNodesWithText("2160p60")[0].performClick()
        composeRule.onAllNodesWithText("2160p60")[0].performClick()
        verify { viewModel.onEncoderPresetChange(EncoderPreset.S_4K60) }
    }

    // --- Remote & Privacy -------------------------------------------------------

    @Test
    fun `remote-privacy shows token, permissions note and sentry toggle`() {
        val viewModel = settingsViewModel()
        composeRule.setContent {
            SettingsRemotePrivacyScreen(
                uiState = AppSettings(sentryEnabled = true),
                viewModel = viewModel,
                remoteControl = RemoteControlInfo(port = 8080, token = "secret-token"),
                onBack = {},
            )
        }
        composeRule.onNodeWithText("Web remote control").assertIsDisplayed()
        composeRule.onNodeWithText("Remote token").assertIsDisplayed()
        composeRule.onNodeWithText("Privacy & error reports").assertIsDisplayed()
        composeRule.onNodeWithText("Send error reports (Sentry)").assertIsDisplayed()
    }
}
