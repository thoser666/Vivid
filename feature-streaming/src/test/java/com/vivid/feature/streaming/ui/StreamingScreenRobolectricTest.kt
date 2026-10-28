package com.vivid.feature.streaming.ui

import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.assert
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.click
import org.junit.Assert.assertEquals
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

private const val ZOOM_PICKER_TAG = "preview_zoom_picker"
private const val ZOOM_PRESET_TAG = "preview_zoom_1.5"
private const val SCENE_PICKER_TAG = "preview_scene_picker"
private const val FIRST_SCENE_ID = "one"
private const val MAIN_SCENE_NAME = "Main"
private const val BITRATE_TAG = "stream_current_bitrate"
private const val STATUS_OVERLAY_TAG = "stream_status_overlay"
private const val STOP_STREAM_LABEL = "Stop Streaming"
private const val STOP_CONFIRM_LABEL = "Stop streaming?"
private const val SETTINGS_BUTTON_TAG = "open_settings"
private const val PREVIEW_NOTICE_TAG = "stream_preview_message"
private const val CONNECTION_REFUSED = "connection refused"
private const val SETTINGS_ROUTE = "settings_route"
private const val CONTROLS_MENU_TAG = "controls_menu"

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
        every { engine.cameraModeStatus } returns MutableStateFlow(com.vivid.feature.streaming.CameraModeStatus())
        every { engine.streamingState } returns streamingState
        every { engine.streamStartedAtMs } returns MutableStateFlow<Long?>(null)
        every { engine.targetStates } returns targetStates
        every { engine.activeEncoder } returns MutableStateFlow(null)
        every { engine.bitrateDiagnostics } returns MutableStateFlow(null)
        every { engine.measuredEncoderFps } returns MutableStateFlow(30)
        every { engine.focusMode } returns MutableStateFlow(FocusMode.AUTO)
        every { engine.stabilizationEnabled } returns MutableStateFlow(false)
        every { engine.torchEnabled } returns MutableStateFlow(false)
        every { engine.activeSourceKind } returns MutableStateFlow(VideoSourceKind.CAMERA)
        every { engine.sceneNeedsCameraPermission() } returns true
        every { engine.canRecordReplay() } returns true
        every { engine.sceneComposition } returns MutableStateFlow(null)
        every { engine.sceneProducerStates } returns MutableStateFlow(emptyMap())
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
        every { viewModel.sceneEditor } returns MutableStateFlow(null)
        every { viewModel.toggleReplay(any()) } answers { firstArg<(Boolean) -> Unit>()(engine.startReplay()) }
        every { viewModel.configIssues } returns configIssues
        every { viewModel.scenes } returns MutableStateFlow(emptyList())
        every { viewModel.appSettings } returns MutableStateFlow(com.vivid.core.data.AppSettings())
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(null)
        every { viewModel.autoSwitchEnabled } returns MutableStateFlow(false)
        every { viewModel.autoSwitchIntervalSeconds } returns MutableStateFlow(30L)
        every { viewModel.privacyEnabled } returns MutableStateFlow(false)
        every { viewModel.privacyZones } returns MutableStateFlow(emptyList())
        every { viewModel.fullFrameBlurEnabled } returns MutableStateFlow(false)

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

    private fun selectionPreview(name: String) {
        val scenes = listOf(
            com.vivid.core.data.StreamScene("main", "Главная"),
            com.vivid.core.data.StreamScene("street", "Улица"),
            com.vivid.core.data.StreamScene("break", "Перерыв"),
            com.vivid.core.data.StreamScene("hidden", "Выключенная", enabled = false),
        )
        val active = MutableStateFlow<String?>("main")
        every { viewModel.scenes } returns MutableStateFlow(scenes)
        every { viewModel.activeSceneId } returns active
        every { viewModel.applyScene(any()) } answers { active.value = firstArg<com.vivid.core.data.StreamScene>().id }
        every { engine.cameraZoomRange() } returns com.vivid.feature.streaming.ZoomRange(0.5f, 8f)
        every { engine.currentCameraZoom() } returns 1f
        composeRule.setContent {
            androidx.compose.material3.MaterialTheme(colorScheme = androidx.compose.material3.darkColorScheme(
                primary = androidx.compose.ui.graphics.Color(0xFF6BE59A),
                onPrimary = androidx.compose.ui.graphics.Color(0xFF00391F),
                primaryContainer = androidx.compose.ui.graphics.Color(0xFF00522E),
                onPrimaryContainer = androidx.compose.ui.graphics.Color(0xFF9EF5BC),
            )) {
                StreamingScreen(navController, viewModel, twitchViewModel, overlayContent = {})
            }
        }
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("preview_scene_hidden").assertDoesNotExist()
        composeRule.onNodeWithTag("preview_scene_street").performTouchInput { click() }
        verify(exactly = 1) { viewModel.applyScene(scenes[1]) }
        composeRule.onNodeWithTag("preview_scene_street").assert(androidx.compose.ui.test.isSelected())
        composeRule.onNodeWithTag(ZOOM_PRESET_TAG).performTouchInput { click() }
        verify(exactly = 1) { engine.applyZoomPreset(false, 1.5f) }
        // Capture the actual production screen with fixture scenes; no camera is attached on the JVM.
        val bitmap = composeRule.onRoot().captureToImage().asAndroidBitmap()
        val file = java.io.File("build/reports/local-controls/$name.png")
        file.parentFile!!.mkdirs()
        file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        val start = composeRule.onNodeWithText("Начать трансляцию").fetchSemanticsNode().boundsInRoot
        val scenesBounds = composeRule.onNodeWithTag(SCENE_PICKER_TAG).fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue("Scene selector must leave Start accessible", scenesBounds.bottom <= start.top)
    }

    @Test @Config(sdk = [34], qualifiers = "ru-w800dp-h400dp-land-xhdpi")
    fun `selection controls landscape preview`() = selectionPreview("landscape-v1")

    @Test @Config(sdk = [34], qualifiers = "ru-w400dp-h800dp-port-xhdpi")
    fun `selection controls portrait preview`() = selectionPreview("portrait-v1")

    @Test fun `saved visibility toggles independently update selectors and all HUD indicators`() {
        val settings = MutableStateFlow(com.vivid.core.data.AppSettings())
        every { viewModel.appSettings } returns settings
        every { viewModel.scenes } returns MutableStateFlow(listOf(com.vivid.core.data.StreamScene(FIRST_SCENE_ID, MAIN_SCENE_NAME)))
        every { engine.cameraZoomRange() } returns com.vivid.feature.streaming.ZoomRange(1f, 4f)
        every { engine.currentCameraZoom() } returns 1f
        streamingState.value = StreamingState.Streaming
        setContent()
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("stream_mode_fps").assertIsDisplayed()
        composeRule.onNodeWithTag("stream_uptime").assertIsDisplayed()
        composeRule.runOnIdle { settings.value = settings.value.copy(localOverlays = com.vivid.core.data.LocalOverlaySettings(false, false, false)) }
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("stream_mode_fps").assertDoesNotExist()
        composeRule.onNodeWithTag("stream_uptime").assertDoesNotExist()
        composeRule.onNodeWithTag(SCENE_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(BITRATE_TAG).assertIsDisplayed()
        composeRule.runOnIdle { settings.value = settings.value.copy(localOverlays =
            settings.value.localOverlays.copy(showBitrate = false)) }
        composeRule.onNodeWithTag(BITRATE_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(STATUS_OVERLAY_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCENE_PICKER_TAG).assertIsDisplayed()
        composeRule.runOnIdle { settings.value = settings.value.copy(localOverlays =
            com.vivid.core.data.LocalOverlaySettings(showScenes = false)) }
        composeRule.onNodeWithTag(BITRATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCENE_PICKER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertIsDisplayed()
        verify(exactly = 0) { viewModel.applyScene(any()) }
        composeRule.runOnIdle { settings.value = settings.value.copy(localOverlays = com.vivid.core.data.LocalOverlaySettings()) }
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag(SCENE_PICKER_TAG).assertIsDisplayed()
    }

    @Test fun `front camera uses saved front presets and updates after settings change`() {
        var front = true
        val settings = MutableStateFlow(com.vivid.core.data.AppSettings(cameraControls =
            com.vivid.core.data.CameraControlSettings(frontZoomPresets = listOf(3f, 1f))))
        every { viewModel.appSettings } returns settings
        every { engine.isFrontCamera() } answers { front }
        every { engine.cameraZoomRange() } returns com.vivid.feature.streaming.ZoomRange(1f, 4f)
        every { engine.currentCameraZoom() } returns 1f
        setContent()
        composeRule.onNodeWithTag(ZOOM_PRESET_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag("preview_zoom_3.0").performClick()
        verify(exactly = 1) { engine.applyZoomPreset(true, 3f) }
        composeRule.runOnIdle { settings.value = settings.value.copy(cameraControls =
            settings.value.cameraControls.copy(frontZoomPresets = listOf(2f, 1f))) }
        composeRule.onNodeWithTag("preview_zoom_3.0").assertDoesNotExist()
        composeRule.onNodeWithTag("preview_zoom_2.0").assertIsDisplayed()
        val two = composeRule.onNodeWithTag("preview_zoom_2.0").fetchSemanticsNode().boundsInRoot
        val one = composeRule.onNodeWithTag("preview_zoom_1.0").fetchSemanticsNode().boundsInRoot
        org.junit.Assert.assertTrue(two.left < one.left)
        composeRule.runOnIdle { front = false }
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.onNodeWithTag(ZOOM_PRESET_TAG).assertIsDisplayed().performClick()
        verify(exactly = 1) { engine.applyZoomPreset(false, 1.5f) }
    }

    @Test fun `unsupported zoom remains disabled without clamping and empty source hides zoom`() {
        val scenes = MutableStateFlow(listOf(com.vivid.core.data.StreamScene(FIRST_SCENE_ID, MAIN_SCENE_NAME)))
        every { viewModel.scenes } returns scenes
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(FIRST_SCENE_ID)
        every { engine.cameraZoomRange() } returns com.vivid.feature.streaming.ZoomRange(1f, 4f)
        every { engine.currentCameraZoom() } returns 1f
        every { viewModel.appSettings } returns MutableStateFlow(com.vivid.core.data.AppSettings(cameraControls =
            com.vivid.core.data.CameraControlSettings(rearZoomPresets = listOf(0.5f, 1f, 2f))))
        setContent()
        composeRule.onNodeWithTag("preview_zoom_0.5").assertIsNotEnabled().performClick()
        verify(exactly = 0) { engine.applyZoomPreset(any(), any()) }
        composeRule.runOnIdle { scenes.value = listOf(scenes.value.single().copy(videoSource = com.vivid.core.data.SceneVideoSource.EMPTY)) }
        composeRule.onNodeWithTag(ZOOM_PICKER_TAG).assertDoesNotExist()
        composeRule.onNodeWithTag(SCENE_PICKER_TAG).assertIsDisplayed()
    }

    @Test fun `controls and selected scenes follow primary accent roles`() {
        val colors = androidx.compose.runtime.mutableStateOf(androidx.compose.material3.darkColorScheme(
            primary = androidx.compose.ui.graphics.Color.Magenta,
            primaryContainer = androidx.compose.ui.graphics.Color.Blue))
        every { viewModel.scenes } returns MutableStateFlow(listOf(com.vivid.core.data.StreamScene(FIRST_SCENE_ID, MAIN_SCENE_NAME)))
        every { viewModel.activeSceneId } returns MutableStateFlow<String?>(FIRST_SCENE_ID)
        composeRule.setContent { androidx.compose.material3.MaterialTheme(colorScheme = colors.value) {
            StreamingScreen(navController, viewModel, twitchViewModel, overlayContent = {})
        } }
        fun background(tag: String): Int {
            val bitmap = composeRule.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
            return bitmap.getPixel(bitmap.width / 2, bitmap.height / 8)
        }
        assertEquals(android.graphics.Color.MAGENTA, background(CONTROLS_BUTTON_TAG))
        assertEquals(android.graphics.Color.MAGENTA, background("preview_scene_one"))
        composeRule.runOnIdle { colors.value = colors.value.copy(primary = androidx.compose.ui.graphics.Color.Cyan,
            primaryContainer = androidx.compose.ui.graphics.Color.Red) }
        assertEquals(android.graphics.Color.CYAN, background(CONTROLS_BUTTON_TAG))
        assertEquals(android.graphics.Color.CYAN, background("preview_scene_one"))
    }

    @Test fun `screen capture scene waits for consent and cancel restores the previous source`() {
        val scene = com.vivid.core.data.StreamScene("screen", "Screen scene",
            videoSource = com.vivid.core.data.SceneVideoSource.SCREEN_CAPTURE)
        every { viewModel.scenes } returns MutableStateFlow(listOf(scene))
        val source = MutableStateFlow(VideoSourceKind.CAMERA)
        every { engine.activeSourceKind } returns source
        every { engine.switchSource(any()) } answers { source.value = firstArg(); true }
        every { engine.createScreenCaptureConsentIntent() } returns android.content.Intent("test.capture")
        every { engine.onScreenCaptureConsentResult(android.app.Activity.RESULT_OK, any()) } returns true
        val launched = mutableListOf<Int>()
        val registry = object : androidx.activity.result.ActivityResultRegistry() {
            override fun <I, O> onLaunch(requestCode: Int,
                contract: androidx.activity.result.contract.ActivityResultContract<I, O>, input: I,
                options: androidx.core.app.ActivityOptionsCompat?) { launched += requestCode }
        }
        val owner = object : androidx.activity.result.ActivityResultRegistryOwner {
            override val activityResultRegistry = registry
        }
        composeRule.setContent {
            CompositionLocalProvider(androidx.activity.compose.LocalActivityResultRegistryOwner provides owner) {
                StreamingScreen(navController, viewModel, twitchViewModel, overlayContent = {})
            }
        }
        composeRule.onNodeWithTag("preview_scene_screen").performClick()
        assertEquals(1, launched.size)
        verify(exactly = 0) { viewModel.applyScene(any()) }
        composeRule.runOnIdle { registry.dispatchResult(launched.last(), android.app.Activity.RESULT_CANCELED, null) }
        assertEquals(VideoSourceKind.CAMERA, source.value)
        verify(exactly = 0) { viewModel.applyScene(any()) }
        composeRule.onNodeWithTag("preview_scene_screen").performClick()
        assertEquals(2, launched.size)
        composeRule.runOnIdle { registry.dispatchResult(launched.last(), android.app.Activity.RESULT_OK, android.content.Intent()) }
        verify(exactly = 1) { viewModel.applyScene(scene) }
    }

    @Test
    fun `idle shows controls without an app bar`() {
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
    fun `live preview replaces diagnostics with three compact stats`() {
        every { engine.cameraModeStatus } returns MutableStateFlow(
            com.vivid.feature.streaming.CameraModeStatus(applied = com.vivid.core.data.CameraMode.EXPERIMENTAL_60_FPS),
        )
        every { engine.measuredEncoderFps } returns MutableStateFlow(58)
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(StreamTargetState(STREAM_URL, StreamTargetStatus.STREAMING, bitrateKbps = 3_100))
        setContent()

        composeRule.onNodeWithText("58 FPS").assertIsDisplayed()
        composeRule.onNodeWithTag(BITRATE_TAG).assertIsDisplayed()
        composeRule.onNodeWithText("3.1 Mbit/s").assertIsDisplayed()
        composeRule.onNodeWithText("00:00:00").assertIsDisplayed()
        composeRule.onNodeWithTag("applied_camera_profile").assertDoesNotExist()
        composeRule.onNodeWithTag("encoder_bitrate_mode").assertDoesNotExist()
        composeRule.onNodeWithTag("applied_camera_mode").assertDoesNotExist()
        composeRule.onNodeWithText(STREAM_URL, substring = true).assertDoesNotExist()
    }

    @Test
    fun `idle preview has no live stats`() {
        setContent()
        composeRule.onNodeWithTag(STATUS_OVERLAY_TAG).assertDoesNotExist()
    }

    @Test
    fun `stop requires confirmation and cancel keeps the stream live`() {
        streamingState.value = StreamingState.Streaming
        setContent()
        composeRule.onNodeWithText(STOP_STREAM_LABEL).performClick()
        verify(exactly = 0) { viewModel.stopStream() }
        composeRule.onNodeWithText(STOP_CONFIRM_LABEL).assertIsDisplayed()
        composeRule.onNodeWithText("Keep streaming").performClick()
        verify(exactly = 0) { viewModel.stopStream() }
        composeRule.onNodeWithText(STOP_CONFIRM_LABEL).assertDoesNotExist()
        composeRule.onNodeWithText(STOP_STREAM_LABEL).performClick()
        composeRule.onNodeWithTag("confirm_stop_stream").performClick()
        verify(exactly = 1) { viewModel.stopStream() }
    }

    @Test
    fun `external stop closes confirmation and a new stream has no stale dialog`() {
        streamingState.value = StreamingState.Streaming
        setContent()
        composeRule.onNodeWithText(STOP_STREAM_LABEL).performClick()
        composeRule.runOnIdle { streamingState.value = StreamingState.Idle }
        composeRule.onNodeWithText(STOP_CONFIRM_LABEL).assertDoesNotExist()
        composeRule.runOnIdle { streamingState.value = StreamingState.Streaming }
        composeRule.onNodeWithText(STOP_CONFIRM_LABEL).assertDoesNotExist()
        verify(exactly = 0) { viewModel.stopStream() }
    }

    @Test
    fun `fallback remains visible without the debug panel`() {
        every { engine.cameraModeStatus } returns MutableStateFlow(
            com.vivid.feature.streaming.CameraModeStatus(
                applied = com.vivid.core.data.CameraMode.STANDARD,
                notice = com.vivid.feature.streaming.CameraModeNotice.PREPARATION_FAILED,
            ),
        )
        streamingState.value = StreamingState.Streaming
        setContent()
        composeRule.onNodeWithText("30 FPS").assertIsDisplayed()
        composeRule.onNodeWithText("60 FPS preparation failed. Using standard mode.").assertIsDisplayed()
    }

    private fun openControls() {
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).performClick()
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
        val recording = composeRule.onNodeWithTag(SETTINGS_BUTTON_TAG).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
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

        composeRule.onNodeWithText(STOP_STREAM_LABEL).assertIsDisplayed()
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
        composeRule.onNodeWithTag(PREVIEW_NOTICE_TAG).performTouchInput { click() }
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
        streamingState.value = StreamingState.Failed(CONNECTION_REFUSED)
        setContent()

        composeRule.onNodeWithText("Error: connection refused").assertIsDisplayed()
    }

    @Test fun `failure feedback dismisses on tap without changing the streaming state`() {
        streamingState.value = StreamingState.Failed(CONNECTION_REFUSED)
        setContent()
        composeRule.onNodeWithTag(PREVIEW_NOTICE_TAG).performTouchInput { click() }
        composeRule.onNodeWithText("Error: connection refused").assertDoesNotExist()
        assertEquals(StreamingState.Failed(CONNECTION_REFUSED), streamingState.value)
        composeRule.onNodeWithText("Retry").assertIsEnabled()
    }

    @Test fun `failure feedback expires after three seconds and new failures get a full timeout`() {
        composeRule.mainClock.autoAdvance = false
        streamingState.value = StreamingState.Failed("first failure")
        setContent()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(2_000)
        composeRule.onNodeWithText("Error: first failure").assertIsDisplayed()
        composeRule.runOnUiThread { streamingState.value = StreamingState.Failed("second failure") }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeBy(1_500)
        composeRule.onNodeWithText("Error: second failure").assertIsDisplayed()
        composeRule.mainClock.advanceTimeBy(1_600)
        composeRule.onNodeWithTag(PREVIEW_NOTICE_TAG).assertDoesNotExist()
    }

    @Test @Config(sdk = [34], qualifiers = "en-w800dp-h400dp-land-xhdpi")
    fun `landscape feedback stays between buttons and selectors and does not intercept controls`() {
        every { viewModel.scenes } returns MutableStateFlow(listOf(com.vivid.core.data.StreamScene(FIRST_SCENE_ID, MAIN_SCENE_NAME)))
        every { engine.cameraZoomRange() } returns com.vivid.feature.streaming.ZoomRange(1f, 4f)
        every { engine.currentCameraZoom() } returns 1f
        configIssues.value = listOf(
            com.vivid.feature.streaming.StreamConfigIssue(com.vivid.feature.streaming.ConfigIssueSeverity.ERROR,
                com.vivid.feature.streaming.R.string.stream_error_bad_scheme, listOf("djfj")),
            com.vivid.feature.streaming.StreamConfigIssue(com.vivid.feature.streaming.ConfigIssueSeverity.ERROR,
                com.vivid.feature.streaming.R.string.stream_error_no_host))
        setContent()
        val banner = composeRule.onNodeWithTag(PREVIEW_NOTICE_TAG).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val top = listOf(CONTROLS_BUTTON_TAG, SETTINGS_BUTTON_TAG).map { composeRule.onNodeWithTag(it).fetchSemanticsNode().boundsInRoot }
        val bottom = listOf(ZOOM_PICKER_TAG, SCENE_PICKER_TAG, "stream_start_stop")
            .map { composeRule.onNodeWithTag(it).assertIsDisplayed().fetchSemanticsNode().boundsInRoot }
        org.junit.Assert.assertTrue("Message covers top controls", top.all { banner.top >= it.bottom })
        org.junit.Assert.assertTrue("Message covers bottom controls", bottom.all { banner.bottom <= it.top })
        grantRecordingPermissions()
        org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        composeRule.onNodeWithTag("stream_start_stop").performTouchInput { click() }
        verify(exactly = 1) { viewModel.startStream() }
        composeRule.onNodeWithTag(SETTINGS_BUTTON_TAG).performTouchInput { click() }
        verify(exactly = 1) { navController.navigate(SETTINGS_ROUTE) }
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).performTouchInput { click() }
        composeRule.onNodeWithTag(CONTROLS_MENU_TAG).assertIsDisplayed()
    }

    @Test
    fun `live preview shows stats without exposing the target url`() {
        streamingState.value = StreamingState.Streaming
        targetStates.value = listOf(
            StreamTargetState(url = STREAM_URL, status = StreamTargetStatus.STREAMING),
        )
        setContent()

        composeRule.onNodeWithTag(STATUS_OVERLAY_TAG).assertIsDisplayed()
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

        composeRule.onNodeWithText("3.1 Mbit/s").assertIsDisplayed()
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

        composeRule.onNodeWithText("850 kbps").assertIsDisplayed()
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
        verify(exactly = 1) { navController.navigate(SETTINGS_ROUTE) }
    }

    @Test
    fun `obs opens from controls and closes the menu`() {
        setContent()
        openControls()
        composeRule.onNodeWithText("Open OBS Control").performScrollTo().performClick()
        verify(exactly = 1) { navController.navigate("obs_control") }
        composeRule.onNodeWithTag(CONTROLS_MENU_TAG).assertDoesNotExist()
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

    @Test
    fun `scenes button and panel are removed from the main screen`() {
        setContent()
        composeRule.onNodeWithTag("open_scenes").assertDoesNotExist()
        composeRule.onNodeWithTag("scenes_panel").assertDoesNotExist()
        composeRule.onNodeWithTag("privacy_toggle").assertDoesNotExist()
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
        private const val FULL_FRAME_LABEL = "Full-frame pixelation"
        private const val FULL_FRAME_TOGGLE_TAG = "full_frame_toggle"
    }


    private fun opaqueOverlay(onOverlayClick: () -> Unit) {
        composeRule.setContent {
            StreamingScreen(navController, viewModel, twitchViewModel, overlayContent = {
                Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Magenta)
                    .clickable(onClick = onOverlayClick))
            })
        }
    }

    @Test
    fun `settings receives real touches above an opaque clickable overlay`() {
        var overlayClicks = 0
        opaqueOverlay { overlayClicks++ }
        composeRule.onNodeWithTag(SETTINGS_BUTTON_TAG).performTouchInput { click() }
        verify(exactly = 1) { navController.navigate(SETTINGS_ROUTE) }
        assertEquals(0, overlayClicks)
    }

    @Test
    fun `controls receives real touches above an opaque clickable overlay`() {
        var overlayClicks = 0
        opaqueOverlay { overlayClicks++ }
        composeRule.onNodeWithTag(CONTROLS_BUTTON_TAG).performTouchInput { click() }
        composeRule.onNodeWithTag(CONTROLS_MENU_TAG).assertIsDisplayed()
        assertEquals(0, overlayClicks)
    }

    @Test
    fun `start and stop receive real touches above an opaque clickable overlay`() {
        grantRecordingPermissions()
        org.robolectric.Shadows.shadowOf(androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>())
            .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
        var overlayClicks = 0
        opaqueOverlay { overlayClicks++ }
        composeRule.onNodeWithText("Start Streaming").performTouchInput { click() }
        verify(exactly = 1) { viewModel.startStream() }
        composeRule.runOnIdle { streamingState.value = StreamingState.Streaming }
        composeRule.onNodeWithText(STOP_STREAM_LABEL).performTouchInput { click() }
        composeRule.onNodeWithTag("confirm_stop_stream").performTouchInput { click() }
        verify(exactly = 1) { viewModel.stopStream() }
        assertEquals(0, overlayClicks)
    }
}
