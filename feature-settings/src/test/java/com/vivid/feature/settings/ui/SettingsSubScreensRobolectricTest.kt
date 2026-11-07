package com.vivid.feature.settings.ui

import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.hasAnyChild
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.isToggleable
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.vivid.core.data.AppSettings
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.core.log.LogStore
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

private const val TELE_LENS_LABEL = "Tele"
private const val FAILURE_SENTINEL = "boom sentinel"

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

    @Test
    fun `camera mode disables unsupported experimental capture and locks both choices while active`() {
        composeRule.setContent { CameraModeSection(CameraModeUiState(locked = true, experimentalSupported = true)) {} }
        composeRule.onNodeWithTag("camera_mode_STANDARD").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_mode_EXPERIMENTAL_60_FPS").assertIsNotEnabled()
    }

    @Test
    fun `camera mode shows fallback and measured fps rather than requested fps`() {
        composeRule.setContent {
            CameraModeSection(CameraModeUiState(status = com.vivid.feature.streaming.CameraModeStatus(
                applied = com.vivid.core.data.CameraMode.STANDARD,
                notice = com.vivid.feature.streaming.CameraModeNotice.PREPARATION_FAILED), measuredFps = 30)) {}
        }
        composeRule.onNodeWithTag("camera_mode_EXPERIMENTAL_60_FPS").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_mode_STANDARD").assertIsEnabled()
        composeRule.onNodeWithText("Measured FPS: 30").assertIsDisplayed()
        composeRule.onNodeWithText("60 FPS preparation failed. Using standard mode.").assertIsDisplayed()
    }

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
                    SettingsCameraViewModel.LensUiState(id = "1", displayName = TELE_LENS_LABEL, isActive = false),
                ),
            )
            every { currentLensId } returns MutableStateFlow("0")
        }
        composeRule.setContent { SettingsCameraScreen(viewModel = viewModel) }
        composeRule.onNodeWithText("Focus").assertIsDisplayed()
        composeRule.onNodeWithText("Infinity").assertIsDisplayed()
        composeRule.onNodeWithText("Macro").assertIsDisplayed()
        composeRule.onNodeWithText(TELE_LENS_LABEL).performClick()
        verify { viewModel.selectLens("1") }
    }

    @Test
    fun `camera screen contains exactly one scrollable without nested scroll`() {
        // Regression Issue #215 / Sentry VIVID-36 (CAM-FOCUS-INFINITE-SCROLL):
        // Ein eigener, ungebundener verticalScroll des Camera-Screens innerhalb
        // des bereits scrollbaren SettingsSectionScaffold wurde mit unendlicher
        // Maximalhoehe gemessen (fataler Compose-Layout-Crash,
        // v0.5.10-beta..v0.5.12-beta / versionCode 5102..5122). Der Screen
        // muss sich auf den Scroll des Scaffolds verlassen — genau EIN
        // Scrollable im Baum, und kein Scrollable misst seine eigenen Kinder
        // erneut (kein WrapContentHeight-Opt-Out, der Workaround-Hinweis des
        // historischen Crashes).
        composeRule.setContent { SettingsCameraScreen(viewModel = cameraViewModel()) }

        composeRule.onNode(
            hasScrollAction().and(hasAnyDescendant(hasAnyChild(hasScrollAction()))),
        ).assertDoesNotExist()
    }

    @Test
    fun `camera screen content scrolls in section scaffold without crash`() {
        // Regression Issue #215: Der Inhalt (Fokus-Karte, Linsen-Karte) wird
        // vom Section-Scaffold-Scroll getragen; Scrollen zum unteren Inhalt
        // darf den Screen nicht crashen (historisch starb die erste
        // Messung des doppelten Scrolls mit der Infinite-Height-Exception).
        composeRule.setContent { SettingsCameraScreen(viewModel = cameraViewModel()) }

        composeRule.onNode(hasScrollAction()).performScrollToNode(hasText(TELE_LENS_LABEL))
        composeRule.onNodeWithText(TELE_LENS_LABEL).assertIsDisplayed()
    }

    private fun cameraViewModel(): SettingsCameraViewModel =
        mockk(relaxed = true) {
            every { focusDistance } returns MutableStateFlow(2.5f)
            every { hasManualFocus } returns MutableStateFlow(true)
            every { availableLenses } returns MutableStateFlow(
                listOf(
                    SettingsCameraViewModel.LensUiState(id = "0", displayName = "Wide", isActive = true),
                    SettingsCameraViewModel.LensUiState(id = "1", displayName = TELE_LENS_LABEL, isActive = false),
                ),
            )
            every { currentLensId } returns MutableStateFlow("0")
        }

    @Test
    fun `zoom creates and deletes independent front presets without camera actions`() {
        val controls = androidx.compose.runtime.mutableStateOf(CameraControlsUiState(loaded = true))
        composeRule.setContent {
            CameraZoomScreen(controls.value, {}, { update -> controls.value = controls.value.copy(settings = update(controls.value.settings)) })
        }
        composeRule.onNodeWithContentDescription("Apply zoom").assertDoesNotExist()
        composeRule.onNode(hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasTestTag("front_zoom_create"))
        composeRule.onNodeWithTag("front_zoom_create").performClick()
        composeRule.onNodeWithTag("camera_zoom_value").performTextInput("2,5")
        composeRule.onNodeWithText("Save").performClick()
        composeRule.runOnIdle {
            org.junit.Assert.assertEquals(listOf(0.5f, 1f, 2.5f), controls.value.settings.frontZoomPresets)
            org.junit.Assert.assertEquals(listOf(1f, 1.5f, 2f), controls.value.settings.rearZoomPresets)
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(androidx.compose.ui.test.hasTestTag("front_zoom_delete_2"))
        composeRule.onNodeWithTag("front_zoom_delete_2").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(listOf(0.5f, 1f), controls.value.settings.frontZoomPresets) }
    }

    @Test
    fun `camera mirror switch and stabilization dropdown forward changes and disable unsupported modes`() {
        val state = androidx.compose.runtime.mutableStateOf(CameraControlsUiState(loaded = true,
            stabilizationModes = setOf(com.vivid.core.data.StabilizationMode.OFF, com.vivid.core.data.StabilizationMode.STANDARD)))
        composeRule.setContent {
            CameraAdditionalControls(state.value, {}, { update -> state.value = state.value.copy(settings = update(state.value.settings)) })
        }
        composeRule.onNodeWithTag("camera_mirror_front").performClick().assertIsOn()
        composeRule.onNodeWithTag("camera_stabilization").performClick()
        composeRule.onNodeWithTag("camera_stabilization_CINEMATIC").assertIsNotEnabled()
        composeRule.onNodeWithTag("camera_stabilization_STANDARD").performClick()
        composeRule.runOnIdle { org.junit.Assert.assertEquals(com.vivid.core.data.StabilizationMode.STANDARD, state.value.settings.stabilization) }
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
                            message = FAILURE_SENTINEL,
                        ),
                    ),
                ),
            )
        }

        composeRule.setContent { SettingsLogsScreen(onBack = {}, viewModel = viewModel) }
        composeRule.onNodeWithText("Logs & Diagnostics").assertIsDisplayed()
        // Log-Liste liegt teils below the fold — gezielt zum Eintrag scrollen.
        composeRule.onNodeWithTag("logs_list").performScrollToNode(
            androidx.compose.ui.test.hasText(FAILURE_SENTINEL, substring = true),
        )
        composeRule.onAllNodesWithText(FAILURE_SENTINEL, substring = true)[0].assertExists()
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

    // Streaming profile navigation is covered by StreamProfilesScreenTest.

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
        composeRule.onNodeWithText("Start web remote control automatically").assertIsDisplayed()
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("Send error reports (Sentry)"),
        )
        composeRule.onNodeWithText("Privacy & error reports").assertIsDisplayed()
        composeRule.onNodeWithText("Send error reports (Sentry)").assertIsDisplayed()
    }

    @Test
    fun `remote-privacy autostart toggle reflects state`() {
        composeRule.setContent {
            SettingsRemotePrivacyScreen(
                uiState = AppSettings(remoteControlEnabled = false),
                viewModel = settingsViewModel(),
                remoteControl = RemoteControlInfo(port = 8080, token = "secret-token"),
                onBack = {},
            )
        }
        composeRule.onNode(hasScrollAction()).performScrollToNode(
            hasText("Start web remote control automatically"),
        )
        // Erster Toggle im Screen = Autostart-Switch (vor Sentry).
        composeRule.onAllNodes(isToggleable())[0].assertIsOff()
    }

}
