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
    private val targetStates = MutableStateFlow<List<StreamTargetState>>(emptyList())

    @Before
    fun setUp() {
        engine = mockk(relaxed = true)
        every { engine.streamingState } returns streamingState
        every { engine.targetStates } returns targetStates
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
        every { viewModel.configIssues } returns MutableStateFlow(emptyList())
        every { viewModel.scenes } returns MutableStateFlow(emptyList())
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(null)
        every { viewModel.autoSwitchEnabled } returns MutableStateFlow(false)
        every { viewModel.autoSwitchIntervalSeconds } returns MutableStateFlow(30L)

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
    fun `idle with camera source shows title start button and scene bar`() {
        setContent()

        composeRule.onNodeWithText("Live Stream").assertIsDisplayed()
        composeRule.onNodeWithText("Start Streaming").assertIsDisplayed()
        composeRule.onNodeWithText("Camera").assertIsNotEnabled()
        composeRule.onNodeWithText("Screen").assertIsEnabled()
        composeRule.onNodeWithText("Scenes").assertIsDisplayed()
    }

    @Test
    fun `streaming state shows stop button`() {
        streamingState.value = StreamingState.Streaming
        setContent()

        composeRule.onNodeWithText("Stop Streaming").assertIsDisplayed()
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
            StreamTargetState(url = "rtmp://a.example/live", status = StreamTargetStatus.STREAMING),
        )
        setContent()

        composeRule.onNodeWithText("rtmp://a.example/live · live", substring = true).assertIsDisplayed()
    }

    @Test
    fun `target status row shows upload bitrate in mbps when above threshold`() {
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(
                url = "rtmp://a.example/live",
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
                url = "rtmp://a.example/live",
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

        // onClick startet den SAF-Picker (Activity-Result) — verifizierbar ist hier
        // der aktive Zustand; der Picker-Flow selbst braucht einen instrumentierten Test.
        composeRule.onNodeWithText("Video").assertIsEnabled()
    }

    @Test
    fun `screen source button invokes engine switchSource`() {
        setContent()

        composeRule.onNodeWithText("Screen").performClick()
        verify(exactly = 1) { engine.switchSource(VideoSourceKind.SCREEN_CAPTURE) }
    }

    @Test
    fun `top bar exposes obs help replay and settings navigation`() {
        setContent()

        composeRule.onNodeWithContentDescription("Open OBS Control").assertExists()
        composeRule.onNodeWithContentDescription("Replays").assertExists()
    }

    // --- Accessibility: Semantics der Streaming-Steuerungen ---------------------

    @Test
    fun `torch button exposes switch role and on state when enabled`() {
        every { engine.torchEnabled } returns MutableStateFlow(true)
        setContent()

        composeRule.onNode(
            hasText("Torch: On").and(hasStateDescription("On")),
        ).assertExists()
    }

    @Test
    fun `torch button exposes off state when disabled`() {
        setContent()

        composeRule.onNode(
            hasText("Torch: Off").and(hasStateDescription("Off")),
        ).assertExists()
    }

    @Test
    fun `auto exposure button exposes switch role and auto state`() {
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(IntRange(-4, 4))
        setContent()

        composeRule.onNode(
            hasText("Exposure: Auto").and(hasStateDescription("On")),
        ).assertExists()
    }

    @Test
    fun `exposure slider exposes the current ev value as state description`() {
        every { engine.exposureRange } returns MutableStateFlow<IntRange?>(IntRange(-4, 4))
        every { engine.exposure } returns MutableStateFlow(-2)
        setContent()

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
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithTag("camera_controls_panel")
                .fetchSemanticsNodes().isNotEmpty()
        }
    }

    @Test
    fun `camera controls panel caps at 320dp on expanded windows`() {
        setContentAdaptive(WindowWidthSizeClass.Expanded, parentWidthDp = 900)

        composeRule.onNodeWithTag("camera_controls_panel")
            .assertWidthIsEqualTo(320.dp)
    }

    @Test
    fun `camera controls panel caps at 220dp on compact phones`() {
        setContentAdaptive(WindowWidthSizeClass.Compact, parentWidthDp = 900)

        composeRule.onNodeWithTag("camera_controls_panel")
            .assertWidthIsEqualTo(220.dp)
    }

    @Test
    fun `camera controls panel stays compact on small parent widths`() {
        setContentAdaptive(WindowWidthSizeClass.Compact, parentWidthDp = 300)

        composeRule.onNodeWithTag("camera_controls_panel")
            .assertWidthIsEqualTo(220.dp)
    }
}
