package com.vivid.feature.streaming.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasStateDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithTag
import com.vivid.core.ui.LocalWindowWidthClass
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import com.vivid.feature.streaming.ColorSpace
import com.vivid.feature.streaming.FocusMode
import com.vivid.feature.streaming.LutPreset
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.VideoFilter
import com.vivid.feature.streaming.source.VideoSourceKind
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Robolectric-Compose-Tests für [StreamingScreen].
 *
 * Deckt die Render-Pfade der größten ungetesteten Composable ab (Idle/Streaming/
 * Fehler/Target-Status) sowie die Engine-Interaktionen, die ohne echte Kamera
 * sicher klickbar sind (Fackel, Quellenwechsel). Die Overlay-Kinder (ChatOverlay,
 * Widgets) holen ihre ViewModels per hiltViewModel() — der Screen-Slot
 * [StreamingScreen.overlayContent] ersetzt sie im Test durch eigenen Inhalt.
 *
 * Pinnt SDK 34 + en-Qualifier, damit String-Assertionen deterministisch sind.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class StreamingScreenRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var engine: StreamingEngine
    private lateinit var viewModel: StreamingViewModel
    private lateinit var twitchViewModel: TwitchChannelViewModel
    private lateinit var navController: androidx.navigation.NavController

    // Echte StateFlows (relaxed-mock Flows wären null und hängen beim Collect).
    private val streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    private val configIssues = MutableStateFlow<List<com.vivid.feature.streaming.StreamConfigIssue>>(emptyList())
    private val targetStates = MutableStateFlow<List<StreamTargetState>>(emptyList())

    @Before
    fun setUp() {
        engine = mockk(relaxed = true)
        every { engine.streamingState } returns streamingState
        every { engine.targetStates } returns targetStates
        every { engine.activeEncoder } returns MutableStateFlow(null)
        every { engine.measuredEncoderFps } returns MutableStateFlow(null)
        every { engine.focusMode } returns MutableStateFlow(FocusMode.AUTO)
        every { engine.stabilizationEnabled } returns MutableStateFlow(false)
        every { engine.torchEnabled } returns MutableStateFlow(false)
        every { engine.activeSourceKind } returns MutableStateFlow(VideoSourceKind.CAMERA)
        every { engine.activeFilter } returns MutableStateFlow(VideoFilter.NONE)
        every { engine.lowLightBoostEnabled } returns MutableStateFlow(false)
        every { engine.activeLutPreset } returns MutableStateFlow(LutPreset.NONE)
        every { engine.activeColorSpace } returns MutableStateFlow(ColorSpace.SRGB)
        every { engine.replayState } returns MutableStateFlow<ReplayState>(ReplayState.Idle)
        every { engine.exposure } returns MutableStateFlow(0)
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(null)
        every { engine.autoExposureEnabled } returns MutableStateFlow(true)
        every { engine.autoWhiteBalanceEnabled } returns MutableStateFlow(true)
        every { engine.hasWhiteBalanceControl() } returns false

        viewModel = mockk(relaxed = true)
        every { viewModel.streamingEngine } returns engine
        every { viewModel.configIssues } returns configIssues
        every { viewModel.scenes } returns MutableStateFlow(emptyList())
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(null)
        every { viewModel.autoSwitchEnabled } returns MutableStateFlow(false)
        every { viewModel.autoSwitchIntervalSeconds } returns MutableStateFlow(30L)
        every { viewModel.privacyEnabled } returns MutableStateFlow(false)
        every { viewModel.privacyZones } returns MutableStateFlow(emptyList())

        twitchViewModel = mockk(relaxed = true)
        every { twitchViewModel.uiState } returns MutableStateFlow(TwitchChannelUiState())

        navController = mockk(relaxed = true)
    }

    /** Setzt den Screen mit leerem Overlay-Slot (keine hiltViewModel-Kinder im Test). */
    private fun setContent() {
        composeRule.setContent {
            StreamingScreen(
                navController = navController,
                viewModel = viewModel,
                twitchViewModel = twitchViewModel,
                overlayContent = {},
            )
        }
    }

    @Test
    fun `idle shows controls and collapsed scenes without an app bar`() {
        setContent()

        composeRule.onNodeWithText("Live Stream").assertDoesNotExist()
        composeRule.onNodeWithText(START_STREAMING_LABEL).assertIsDisplayed()
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCENES_PANEL_TAG).assertDoesNotExist()
        composeRule.onNodeWithText("Camera").assertDoesNotExist()
        openControls()
        composeRule.onNodeWithText("Camera").assertIsNotEnabled()
        composeRule.onNodeWithText("Screen").assertIsEnabled()
    }

    @Test
    fun `stream statistics show applied fallback profile and measured fps`() {
        every { engine.activeEncoder } returns MutableStateFlow(
            com.vivid.core.data.ResolvedEncoderConfig(
                com.vivid.core.data.VideoCodecPreference.H264,
                com.vivid.core.data.EncoderPreset.FHD30,
                true,
            ),
        )
        every { engine.measuredEncoderFps } returns MutableStateFlow(14)
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(StreamTargetState(url = STREAM_URL, status = StreamTargetStatus.STREAMING))
        setContent()

        composeRule.onNodeWithText("Camera profile: 1920×1080 · 30 fps").assertIsDisplayed()
        composeRule.onNodeWithText("Compatible profile applied").assertIsDisplayed()
        composeRule.onNodeWithText("Measured: 14 fps").assertIsDisplayed()
    }

    private fun openControls() {
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).performClick()
    }

    private fun openScenes() {
        composeRule.onNodeWithTag("open_scenes").performClick()
    }

    @Test
    fun `scenes can be expanded closed and reopened`() {
        setContent()
        composeRule.onNodeWithTag(SCENES_PANEL_TAG).assertDoesNotExist()
        openScenes()
        composeRule.onNodeWithTag(SCENES_PANEL_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("close_scenes").assertDoesNotExist()
        composeRule.onNodeWithTag(SCENES_PANEL_TAG).performTouchInput { swipeDown(durationMillis = 200) }
        composeRule.onNodeWithTag(SCENES_PANEL_TAG).assertDoesNotExist()
        openScenes()
        composeRule.onNodeWithText("Auto switch").assertIsDisplayed()
    }

    @Test
    @Config(sdk = [34], qualifiers = "ru")
    fun `compact Russian screen keeps controls and settings separate`() {
        composeRule.setContent {
            Box(Modifier.requiredWidth(320.dp)) {
                StreamingScreen(
                    navController = navController,
                    viewModel = viewModel,
                    twitchViewModel = twitchViewModel,
                    overlayContent = {},
                )
            }
        }
        val controls = composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val recording = composeRule.onNodeWithTag("open_settings").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Controls overlap settings", controls.right <= recording.left)
    }

    private fun grantRecordingPermissions() {
        val app = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>()
        org.robolectric.Shadows.shadowOf(app).grantPermissions(
            android.Manifest.permission.CAMERA,
            android.Manifest.permission.RECORD_AUDIO,
        )
    }

    @Test
    fun `record without a stream changes the button and can be stopped`() {
        grantRecordingPermissions()
        val recording = MutableStateFlow<ReplayState>(ReplayState.Idle)
        every { engine.replayState } returns recording
        every { engine.startReplay(any(), any()) } answers {
            recording.value = ReplayState.Recording(java.io.File("/tmp/test-record.mp4"))
            true
        }
        every { engine.stopReplay() } answers {
            recording.value = ReplayState.Idle
            null
        }
        setContent()
        openControls()
        composeRule.onNodeWithTag(RECORD_ITEM_TAG).performClick()
        composeRule.onNodeWithText("Stop recording").assertIsDisplayed()
        composeRule.onNodeWithTag(RECORD_ITEM_TAG).performClick()
        verify(exactly = 1) { engine.stopReplay() }
        verify(exactly = 0) { viewModel.startStream() }
    }

    @Test
    fun `failed recording start displays an error`() {
        grantRecordingPermissions()
        every { engine.startReplay(any(), any()) } returns false
        setContent()
        openControls()
        composeRule.onNodeWithTag(RECORD_ITEM_TAG).performClick()
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).performClick()
        composeRule.onNodeWithText("Could not start recording. Check camera and microphone permissions and try again.")
            .assertIsDisplayed()
    }

    @Test
    fun `streaming state shows stop button`() {
        streamingState.value = StreamingState.Streaming
        setContent()

        composeRule.onNodeWithText("Stop Streaming").assertIsDisplayed()
    }

    @Test
    fun `missing url is hidden until start and can be dismissed`() {
        configIssues.value = listOf(
            com.vivid.feature.streaming.StreamConfigIssue(
                com.vivid.feature.streaming.ConfigIssueSeverity.ERROR,
                com.vivid.feature.streaming.R.string.stream_error_no_url,
            ),
        )
        setContent()
        val warning = MISSING_URL_MESSAGE
        composeRule.onNodeWithText(warning).assertDoesNotExist()
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).assertIsDisplayed()
        composeRule.onNodeWithText(START_STREAMING_LABEL).performClick()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
        verify(exactly = 0) { viewModel.startStream() }
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText(warning).assertDoesNotExist()
        composeRule.onNodeWithText(START_STREAMING_LABEL).performClick()
        composeRule.onNodeWithText(warning).assertIsDisplayed()
    }

    @Test
    fun `saving a url clears the pending missing url notification`() {
        configIssues.value = listOf(
            com.vivid.feature.streaming.StreamConfigIssue(
                com.vivid.feature.streaming.ConfigIssueSeverity.ERROR,
                com.vivid.feature.streaming.R.string.stream_error_no_url,
            ),
        )
        val missingUrlIssues = configIssues.value
        setContent()
        composeRule.onNodeWithText(START_STREAMING_LABEL).performClick()
        composeRule.onNodeWithText(MISSING_URL_MESSAGE).assertIsDisplayed()
        composeRule.runOnIdle { configIssues.value = emptyList() }
        composeRule.onNodeWithText(MISSING_URL_MESSAGE).assertDoesNotExist()
        composeRule.runOnIdle { configIssues.value = missingUrlIssues }
        composeRule.onNodeWithText(MISSING_URL_MESSAGE).assertDoesNotExist()
    }

    @Test
    fun `failed state shows error banner with reason`() {
        streamingState.value = StreamingState.Failed("connection refused")
        setContent()

        composeRule.onNodeWithText("Error: connection refused").assertIsDisplayed()
    }

    @Test
    fun `target status rows show url and live label while streaming`() {
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(url = STREAM_URL, status = StreamTargetStatus.STREAMING),
        )
        setContent()

        composeRule.onNodeWithText("rtmp://a.example/live · live", substring = true).assertIsDisplayed()
    }

    @Test
    fun `target status row shows upload bitrate in mbps when above threshold`() {
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(
                url = STREAM_URL,
                status = StreamTargetStatus.STREAMING,
                bitrateKbps = 3_100,
            ),
        )
        setContent()

        composeRule.onNodeWithText("rtmp://a.example/live · live · 3.1 Mbit/s", substring = true).assertIsDisplayed()
    }

    @Test
    fun `target status row shows upload bitrate in kbps when below threshold`() {
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(
                url = STREAM_URL,
                status = StreamTargetStatus.STREAMING,
                bitrateKbps = 850,
            ),
        )
        setContent()

        composeRule.onNodeWithText("rtmp://a.example/live · live · 850 kbps", substring = true).assertIsDisplayed()
    }

    @Test
    fun `video source button is enabled when inactive`() {
        setContent()
        openControls()

        // onClick startet den SAF-Picker (Activity-Result) — verifizierbar ist hier
        // der aktive Zustand; der Picker-Flow selbst braucht einen instrumentierten Test.
        composeRule.onNodeWithText("Video").assertIsEnabled()
    }

    @Test
    fun `screen source button invokes engine switchSource`() {
        setContent()
        openControls()

        composeRule.onNodeWithText("Screen").performClick()
        verify(exactly = 1) { engine.switchSource(VideoSourceKind.SCREEN_CAPTURE) }
    }

    @Test
    fun `settings opens from standalone button`() {
        setContent()
        composeRule.onNodeWithContentDescription("Open Settings").performClick()
        verify(exactly = 1) { navController.navigate("settings_route") }
    }

    @Test
    fun `obs opens from controls and closes the menu`() {
        setContent()
        openControls()
        composeRule.onNodeWithText("Open OBS Control").performScrollTo().performClick()
        verify(exactly = 1) { navController.navigate("obs_control") }
        composeRule.onNodeWithTag("controls_menu").assertDoesNotExist()
    }

    @Test
    fun `torch action still invokes engine from controls`() {
        setContent()
        openControls()
        composeRule.onNodeWithText("Torch: Off").performScrollTo().performClick()
        verify(exactly = 1) { engine.toggleTorch() }
    }

    // --- Accessibility: Semantics der Streaming-Steuerungen ---------------------

    @Test
    fun `torch button exposes switch role and on state when enabled`() {
        every { engine.torchEnabled } returns MutableStateFlow(true)
        setContent()
        openControls()

        composeRule.onNode(
            hasText("Torch: On").and(hasStateDescription("On")),
        ).assertExists()
    }

    @Test
    fun `torch button exposes off state when disabled`() {
        setContent()
        openControls()

        composeRule.onNode(
            hasText("Torch: Off").and(hasStateDescription("Off")),
        ).assertExists()
    }

    @Test
    fun `auto exposure button exposes switch role and auto state`() {
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(IntRange(-4, 4))
        setContent()
        openControls()

        composeRule.onNode(
            hasText("Exposure: Auto").and(hasStateDescription("On")),
        ).assertExists()
    }

    @Test
    fun `exposure slider exposes the current ev value as state description`() {
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(IntRange(-4, 4))
        every { engine.exposure } returns MutableStateFlow(-2)
        setContent()
        openControls()

        composeRule.onNode(hasStateDescription("Exposure -2")).assertExists()
    }

    /** Setzt den Screen in einen Container fester Breite + injizierter Breitenklasse. */
    private fun setContentAdaptive(
        widthClass: WindowWidthSizeClass,
        parentWidthDp: Int,
    ) {
        // Panel nur sichtbar, wenn die Kamera einen EV-Bereich anbietet.
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(IntRange(-4, 4))
        composeRule.setContent {
            CompositionLocalProvider(LocalWindowWidthClass provides widthClass) {
                Box(modifier = Modifier.requiredWidth(parentWidthDp.dp)) {
                    StreamingScreen(
                        navController = navController,
                        viewModel = viewModel,
                        twitchViewModel = twitchViewModel,
                        overlayContent = {},
                    )
                }
            }
        }
        openControls()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag(CAMERA_PANEL_TAG)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `camera controls panel caps at 320dp on expanded windows`() {
        setContentAdaptive(WindowWidthSizeClass.Expanded, parentWidthDp = 900)

        composeRule.onNodeWithTag(CAMERA_PANEL_TAG)
            .assertWidthIsEqualTo(320.dp)
    }

    @Test
    fun `camera controls panel caps at 220dp on compact phones`() {
        setContentAdaptive(WindowWidthSizeClass.Compact, parentWidthDp = 900)

        composeRule.onNodeWithTag(CAMERA_PANEL_TAG)
            .assertWidthIsEqualTo(220.dp)
    }

    @Test
    fun `camera controls panel stays compact on small parent widths`() {
        setContentAdaptive(WindowWidthSizeClass.Compact, parentWidthDp = 300)

        composeRule.onNodeWithTag(CAMERA_PANEL_TAG)
            .assertWidthIsEqualTo(220.dp)
    }

    // --- P1: Datenschutz-Anonymisierung (Master-Toggle + Zonen-Editor) --------

    @Test
    fun `privacy master toggle is visible and forwards the new state`() {
        setContent()
        openScenes()

        composeRule.onNodeWithText("Privacy").assertIsDisplayed()
        composeRule.onNodeWithTag("privacy_toggle").performClick()
        verify(exactly = 1) { viewModel.setPrivacyEnabled(true) }
    }

    @Test
    fun `zones button appears when privacy is enabled and opens the editor`() {
        every { viewModel.privacyEnabled } returns MutableStateFlow(true)
        every { viewModel.privacyZones } returns MutableStateFlow(
            listOf(com.vivid.core.data.PrivacyZone(0.5f, 0.5f, 0.15f, 0.15f)),
        )
        setContent()
        openScenes()

        composeRule.onNodeWithText(ZONES_LABEL).performClick()

        composeRule.onNodeWithTag(ZONE_EDITOR_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("Zone editor (privacy)").assertIsDisplayed()
    }

    @Test
    fun `no zones button while privacy is disabled`() {
        setContent()
        openScenes()

        composeRule.onNodeWithText(ZONES_LABEL).assertDoesNotExist()
    }

    @Test
    fun `turning privacy off closes the open zone editor`() {
        every { viewModel.privacyEnabled } returns MutableStateFlow(true)
        every { viewModel.privacyZones } returns MutableStateFlow(emptyList())
        setContent()
        openScenes()

        composeRule.onNodeWithText(ZONES_LABEL).performClick()
        composeRule.onNodeWithTag(ZONE_EDITOR_TAG).assertIsDisplayed()
        openScenes()
        composeRule.onNodeWithTag("privacy_toggle").performClick()

        composeRule.onNodeWithTag(ZONE_EDITOR_TAG).assertDoesNotExist()
        verify(exactly = 1) { viewModel.setPrivacyEnabled(false) }
    }
    private companion object {
        private const val START_STREAMING_LABEL = "Start Streaming"
        private const val CONTROLS_BUTTON_TAG = "open_controls"
        private const val SCENES_PANEL_TAG = "scenes_panel"
        private const val RECORD_ITEM_TAG = "replay_record"
        private const val MISSING_URL_MESSAGE = "No stream URL configured. Please add it in the settings."
        private const val STREAM_URL = "rtmp://a.example/live"
        private const val CAMERA_PANEL_TAG = "camera_controls_panel"
        private const val ZONES_LABEL = "Zones"
        private const val ZONE_EDITOR_TAG = "zone_editor"
    }

}
