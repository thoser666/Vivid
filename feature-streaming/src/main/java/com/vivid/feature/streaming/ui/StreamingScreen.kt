package com.vivid.feature.streaming.ui

import android.content.Context
import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewTreeObserver
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.vivid.feature.streaming.ui.scene.SceneEditorScreen
import com.vivid.feature.streaming.CameraModeStatus
import com.vivid.feature.streaming.CameraModeNotice
import com.vivid.core.data.CameraMode
import com.vivid.feature.streaming.StreamingEngine
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vivid.core.ui.LocalWindowWidthClass
import com.vivid.core.ui.adaptiveControlsMaxWidth
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.FocusMode
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamConfigIssue
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.R
import kotlin.math.abs

/** Keep the camera's native output proportions, then crop equally to cover the viewport. */
internal fun cameraPreviewZoom(
    viewportWidth: Float,
    viewportHeight: Float,
    previewAspect: Float,
): Float {
    if (viewportWidth <= 0f || viewportHeight <= 0f || previewAspect <= 0f) return 1f
    val viewportAspect = viewportWidth / viewportHeight
    return maxOf(viewportAspect / previewAspect, previewAspect / viewportAspect)
}

/** Prefer a near-square supported output so a portrait viewport loses less image to cropping. */
internal fun selectPortraitIdlePreviewSize(available: List<IntSize>): IntSize {
    val usable = available.filter { size ->
        size.width > 0 && size.height > 0 &&
            maxOf(size.width, size.height) <= 1920 && minOf(size.width, size.height) >= 720
    }.ifEmpty { available.filter { it.width > 0 && it.height > 0 } }
    return usable.minWithOrNull(
        compareBy<IntSize> { abs(it.width - it.height).toFloat() / maxOf(it.width, it.height) }
            .thenBy { abs(it.width.toLong() * it.height - 1080L * 1080L) },
    ) ?: IntSize(1920, 1080)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(),
    twitchViewModel: TwitchChannelViewModel = hiltViewModel(),
    onPreviewFullscreenChange: (Boolean) -> Unit = {},
    // Optional local UI overlay. Scene widgets are composed in the encoder GL path.
    // The default slot owns Activity permission prompts; tests may replace it.
    overlayContent: @Composable BoxScope.() -> Unit = { SceneLocationPermission() },
) {
    val streamingEngine = viewModel.streamingEngine
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()
    val targetStates by streamingEngine.targetStates.collectAsStateWithLifecycle()
    val measuredFps by streamingEngine.measuredEncoderFps.collectAsStateWithLifecycle()
    val streamStartedAtMs by streamingEngine.streamStartedAtMs.collectAsStateWithLifecycle()
    val cameraModeStatus by streamingEngine.cameraModeStatus.collectAsStateWithLifecycle()
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val appSettings by viewModel.appSettings.collectAsStateWithLifecycle(com.vivid.core.data.AppSettings())
    val scenes by viewModel.scenes.collectAsStateWithLifecycle(emptyList())
    val activeSceneId by viewModel.activeSceneId.collectAsStateWithLifecycle(null)
    val configIssues by viewModel.configIssues.collectAsStateWithLifecycle()
    var startAttempt by remember { mutableIntStateOf(0) }
    var startAttempted by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<PreviewNotice?>(null) }
    var noticeSequence by remember { mutableIntStateOf(0) }
    fun showNotice(text: String, error: Boolean, source: String) {
        notice = PreviewNotice(text, error, source, ++noticeSequence)
    }
    StreamConfigurationFeedback(startAttempted, startAttempt, configIssues, streamingState,
        onMessage = { text, error -> showNotice(text, error, "configuration") },
        onClear = { if (notice?.source == "configuration") notice = null },
        onAttemptConsumed = { startAttempted = false })
    StreamSessionFeedback(cameraModeStatus, targetStates, streamingState, startAttempt) { text, error ->
        showNotice(text, error, "session")
    }
    val twitchState by twitchViewModel.uiState.collectAsStateWithLifecycle()

    val sceneEditor by viewModel.sceneEditor.collectAsStateWithLifecycle()
    val sceneSourceStates by streamingEngine.sceneProducerStates.collectAsStateWithLifecycle()
    val privacyEnabled by viewModel.privacyEnabled.collectAsStateWithLifecycle(false)
    sceneEditor?.let { editor ->
        SceneEditorScreen(editor, streamingEngine, activeSourceKind, sceneSourceStates,
            scenes.flatMap { it.composition?.sources.orEmpty() }, privacyEnabled,
            viewModel::editScene, viewModel::saveSceneEditor, viewModel::cancelSceneEditor,
            viewModel::grantSceneProjection)
        return
    }

    RefreshTwitchStatus(streamingState, twitchViewModel)

    // MainActivity requests capture permissions once on the first app launch.
    // Navigation to this screen never repeats a denied request; Start can retry.
    // The idle preview still requires an actual camera grant.
    val context = LocalContext.current
    val displayRotationDegrees = rememberDisplayRotation()
    var permissionDenied by remember { mutableStateOf(false) }
    val requestPermissionsAndStart = rememberStreamStartAction(viewModel, streamingEngine) { granted ->
        permissionDenied = !granted
        startAttempted = startAttempted && granted
    }

    val permissionMessage = stringResource(R.string.streaming_permission_required)
    LaunchedEffect(permissionDenied, startAttempt) {
        if (permissionDenied) showNotice(permissionMessage, true, "permission")
        else if (notice?.source == "permission") notice = null
    }
    val requestPermissionsAndRecord = rememberRecordingAction(viewModel) { message ->
        showNotice(message, true, "recording")
    }

    StartGrantedIdlePreview(streamingEngine, context)

    val requestScreenCapture = rememberScreenCaptureAction(streamingEngine)

    val requestVideoPicker = rememberVideoPickerAction(streamingEngine)
    val selectScene = rememberSceneSelectionAction(viewModel, scenes)

    RefreshStreamingScreenOnResume(viewModel, streamingEngine)

    var controlsExpanded by remember { mutableStateOf(false) }
    val previewFullscreen = !controlsExpanded
    SideEffect { onPreviewFullscreenChange(previewFullscreen) }

    Scaffold(
        // The preview uses the cutout area; only controls avoid the camera hole.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val density = LocalDensity.current
            val previewSize = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
            StreamingSourcePreview(streamingEngine, activeSourceKind, previewSize, displayRotationDegrees)

            BoxWithConstraints(
                modifier = Modifier.fillMaxSize().zIndex(1f).windowInsetsPadding(WindowInsets.displayCutout),
            ) {
                val topButtonMaxWidth = maxWidth / 2 - 20.dp
                var bottomControlsHeight by remember { mutableIntStateOf(0) }
                Box(
                    Modifier.fillMaxSize().padding(top = 72.dp,
                        bottom = with(density) { bottomControlsHeight.toDp() }),
                    contentAlignment = Alignment.Center,
                ) {
                    notice?.let { message ->
                        TransientPreviewMessage(message, onDismiss = { if (notice == message) notice = null })
                    }
                }
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .onSizeChanged { bottomControlsHeight = it.height }
                        .padding(start = 16.dp, end = 16.dp, bottom = if (maxWidth > maxHeight) 16.dp else 48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    PreviewSelectionControls(scenes, activeSceneId, appSettings.cameraControls,
                        streamingEngine, activeSourceKind, selectScene,
                        showZoom = appSettings.localOverlays.showZoom,
                        showScenes = appSettings.localOverlays.showScenes,
                        modifier = Modifier.align(Alignment.End).widthIn(max = 720.dp))
                    StreamStartButton(streamingState, onStop = viewModel::stopStream) {
                        startAttempted = true
                        startAttempt++
                        if (configIssues.none { it.messageRes == R.string.stream_error_no_url }) requestPermissionsAndStart()
                    }
                }

                // Twitch-Status: Viewerzahl und Stream-Metadaten als kompakte Anzeige.
                twitchState.streamInfo?.let { info ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 12.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(pluralStringResource(R.plurals.twitch_viewers_count, info.viewerCount, info.viewerCount))
                            Text(
                                text = info.category.ifBlank { info.title },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                StreamingControlsMenu(
                    streamingEngine, navController, topButtonMaxWidth,
                    controlsExpanded = controlsExpanded,
                    onControlsExpandedChange = { controlsExpanded = it },
                    onRecord = requestPermissionsAndRecord,
                    onScreenCapture = requestScreenCapture,
                    onVideoPicker = { requestVideoPicker() },
                    onEditScene = { scenes.firstOrNull { it.id == activeSceneId }?.let(viewModel::openSceneEditor) },
                )
                FilledIconButton(
                    onClick = { navController.navigate("settings_route") },
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp).testTag("open_settings"),
                ) {
                    Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.streaming_settings_content_desc))
                }

            }

            // Chat-Overlay + Widgets: als Slot ausgelagert (siehe Parameter-Doku).
            overlayContent()

            if (streamingState is StreamingState.Streaming) {
                StreamStatusOverlay(
                    fps = measuredFps,
                    bitrateKbps = currentStreamBitrateKbps(targetStates),
                    startedAtMs = streamStartedAtMs,
                    showFps = appSettings.localOverlays.showFps,
                    showDuration = appSettings.localOverlays.showDuration,
                    showBitrate = appSettings.localOverlays.showBitrate,
                    modifier = Modifier.align(Alignment.TopStart)
                        .windowInsetsPadding(WindowInsets.displayCutout)
                        .padding(start = 16.dp, top = 64.dp),
                )
            }


        }
    }
}

@Composable
private fun StartGrantedIdlePreview(streamingEngine: StreamingEngine, context: Context) {
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            streamingEngine.startIdlePreviewIfReady()
        }
    }
}

/** Popups and sheets own a window; their navigation buttons must stay available too. */
@Composable
private fun ShowMenuSystemBars() {
    val view = LocalView.current
    val window = (view.parent as? androidx.compose.ui.window.DialogWindowProvider)?.window
        ?: LocalActivity.current?.window
    DisposableEffect(view, window) {
        fun showBars() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) showMenuSystemBars(view)
            else window?.let { showLegacyMenuSystemBars(view, it) }
        }
        val listener = ViewTreeObserver.OnWindowFocusChangeListener { focused ->
            if (focused) showBars()
        }
        view.viewTreeObserver.addOnWindowFocusChangeListener(listener)
        showBars()
        onDispose {
            if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(listener)
        }
    }
}

/** A popup's platform controller belongs to its own window, rather than the Activity. */
@androidx.annotation.RequiresApi(Build.VERSION_CODES.R)
internal fun showMenuSystemBars(view: android.view.View) {
    view.windowInsetsController?.apply {
        systemBarsBehavior = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            android.view.WindowInsetsController.BEHAVIOR_DEFAULT
        } else {
            // API 30 uses value 1 for swipe behavior, renamed DEFAULT in API 31.
            1
        }
        show(android.view.WindowInsets.Type.systemBars())
    }
}

internal fun showLegacyMenuSystemBars(view: android.view.View, window: android.view.Window) {
    WindowCompat.getInsetsController(window, view).apply {
        systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
        show(WindowInsetsCompat.Type.systemBars())
    }
}

@Composable
private fun BoxScope.StreamingControlsMenu(
    streamingEngine: StreamingEngine,
    navController: NavController,
    topButtonMaxWidth: Dp,
    controlsExpanded: Boolean,
    onControlsExpandedChange: (Boolean) -> Unit,
    onRecord: () -> Unit,
    onScreenCapture: () -> Unit,
    onVideoPicker: () -> Unit,
    onEditScene: () -> Unit,
) {
    fun dismissControls() { onControlsExpandedChange(false) }
    Box(
        modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 16.dp),
    ) {
        Button(
            onClick = { onControlsExpandedChange(true) },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
            modifier = Modifier.widthIn(max = topButtonMaxWidth).testTag("open_controls"),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.streaming_controls), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = controlsExpanded,
            onDismissRequest = { onControlsExpandedChange(false) },
            modifier = Modifier.width(adaptiveControlsMaxWidth(LocalWindowWidthClass.current)).testTag("controls_menu"),
        ) {
            ShowMenuSystemBars()
            SourceSelectionItems(streamingEngine, ::dismissControls, onScreenCapture, onVideoPicker)
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.scene_editor_open)) },
                onClick = { dismissControls(); onEditScene() },
            )
            RecordingMenuItem(streamingEngine, onRecord)
            CameraToggleItems(streamingEngine, navController, ::dismissControls)
            VideoEffectItems(streamingEngine)
            ExposureMenuItems(streamingEngine)
            ControlsNavigationItems(navController, ::dismissControls)
        }
    }
}

@Composable
private fun SourceSelectionItems(streamingEngine: StreamingEngine, onDismiss: () -> Unit, onScreenCapture: () -> Unit, onVideoPicker: () -> Unit) {
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    DropdownMenuItem(
        onClick = {
            onDismiss()
            streamingEngine.switchSource(VideoSourceKind.CAMERA)
        },
        enabled = activeSourceKind != VideoSourceKind.CAMERA,
        text = {
            Text(stringResource(R.string.streaming_source_camera))
        },
    )
    DropdownMenuItem(
        onClick = {
            onDismiss()
            onScreenCapture()
        },
        enabled = activeSourceKind != VideoSourceKind.SCREEN_CAPTURE,
        text = {
            Text(stringResource(R.string.streaming_source_screen))
        },
    )
    DropdownMenuItem(
        onClick = {
            onDismiss()
            onVideoPicker()
        },
        enabled = activeSourceKind != VideoSourceKind.VIDEO_PLAYER,
        text = {
            Text(stringResource(R.string.streaming_source_video))
        },
    )
}

@Composable
private fun RecordingMenuItem(streamingEngine: StreamingEngine, onRecord: () -> Unit) {
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val replayState by streamingEngine.replayState.collectAsStateWithLifecycle()
    val composition by streamingEngine.sceneComposition.collectAsStateWithLifecycle()
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()
    val canRecord = remember(activeSourceKind, composition, streamingState, replayState) {
        streamingEngine.canRecordReplay()
    }
    DropdownMenuItem(
        modifier = Modifier.testTag("replay_record"),
        enabled = replayState is ReplayState.Recording || canRecord,
        onClick = {
            if (replayState is ReplayState.Recording) streamingEngine.stopReplay()
            else onRecord()
        },
        leadingIcon = {
            Icon(Icons.Filled.FiberManualRecord, contentDescription = null,
                tint = if (replayState is ReplayState.Recording) MaterialTheme.colorScheme.error
                else LocalContentColor.current)
        },
        text = { Text(stringResource(if (replayState is ReplayState.Recording)
            R.string.streaming_replay_stop else R.string.streaming_replay_record)) },
    )
}

@Composable
private fun CameraToggleItems(streamingEngine: StreamingEngine, navController: NavController, onDismiss: () -> Unit) {
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val torchEnabled by streamingEngine.torchEnabled.collectAsStateWithLifecycle()
    val stabilizationEnabled by streamingEngine.stabilizationEnabled.collectAsStateWithLifecycle()
    val focusMode by streamingEngine.focusMode.collectAsStateWithLifecycle()
    val camOn = stringResource(R.string.streaming_a11y_state_on)
    val camOff = stringResource(R.string.streaming_a11y_state_off)
    DropdownMenuItem(
        onClick = { streamingEngine.toggleTorch() },
        enabled = activeSourceKind == VideoSourceKind.CAMERA,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (torchEnabled) camOn else camOff
        },
        text = {
            Text(
                stringResource(
                    if (torchEnabled) R.string.streaming_torch_on else R.string.streaming_torch_off,
                ),
            )
        },
    )
    DropdownMenuItem(
        onClick = { streamingEngine.toggleStabilization() },
        enabled = activeSourceKind == VideoSourceKind.CAMERA,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (stabilizationEnabled) camOn else camOff
        },
        text = {
            Text(
                stringResource(
                    if (stabilizationEnabled) R.string.streaming_stabilization_on else R.string.streaming_stabilization_off,
                ),
            )
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.streaming_obs_content_desc)) },
        leadingIcon = { Icon(Icons.Default.Podcasts, contentDescription = null) },
        onClick = {
            onDismiss()
            navController.navigate("obs_control")
        },
    )
    DropdownMenuItem(
        onClick = { streamingEngine.toggleFocusLock() },
        enabled = activeSourceKind == VideoSourceKind.CAMERA,
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (focusMode == FocusMode.LOCKED_INFINITY) camOn else camOff
        },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val isLocked = focusMode == FocusMode.LOCKED_INFINITY
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(
                        if (isLocked) R.string.streaming_focus_inf else R.string.streaming_focus_auto,
                    ),
                )
            }
        },
    )
}

@Composable
private fun VideoEffectItems(streamingEngine: StreamingEngine) {
    val camOn = stringResource(R.string.streaming_a11y_state_on)
    val camOff = stringResource(R.string.streaming_a11y_state_off)
    val activeFilter by streamingEngine.activeFilter.collectAsStateWithLifecycle()
    val lowLightBoostEnabled by streamingEngine.lowLightBoostEnabled.collectAsStateWithLifecycle()
    val activeLutPreset by streamingEngine.activeLutPreset.collectAsStateWithLifecycle()
    val activeColorSpace by streamingEngine.activeColorSpace.collectAsStateWithLifecycle()
    DropdownMenuItem(
        onClick = { streamingEngine.nextVideoFilter() },
        text = {
            Text(
                stringResource(
                    R.string.streaming_filter_label,
                    stringResource(activeFilter.labelRes),
                ),
            )
        },
    )
    DropdownMenuItem(
        onClick = { streamingEngine.toggleLowLightBoost() },
        modifier = Modifier.semantics {
            role = Role.Switch
            stateDescription = if (lowLightBoostEnabled) camOn else camOff
        },
        text = {
            Text(
                stringResource(
                    if (lowLightBoostEnabled) R.string.streaming_boost_on else R.string.streaming_boost_off,
                ),
            )
        },
    )
    DropdownMenuItem(
        onClick = {
            val nextPreset = when (activeLutPreset) {
                com.vivid.feature.streaming.LutPreset.NONE -> com.vivid.feature.streaming.LutPreset.WARM
                com.vivid.feature.streaming.LutPreset.WARM -> com.vivid.feature.streaming.LutPreset.COOL
                com.vivid.feature.streaming.LutPreset.COOL -> com.vivid.feature.streaming.LutPreset.NONE
            }
            streamingEngine.setLutPreset(nextPreset)
        },
        text = {
            Text(
                stringResource(
                    when (activeLutPreset) {
                        com.vivid.feature.streaming.LutPreset.NONE -> R.string.streaming_lut_none
                        com.vivid.feature.streaming.LutPreset.WARM -> R.string.streaming_lut_warm
                        com.vivid.feature.streaming.LutPreset.COOL -> R.string.streaming_lut_cool
                    },
                ),
            )
        },
    )
    DropdownMenuItem(
        onClick = {
            val nextSpace = when (activeColorSpace) {
                com.vivid.feature.streaming.ColorSpace.SRGB -> com.vivid.feature.streaming.ColorSpace.DISPLAY_P3
                com.vivid.feature.streaming.ColorSpace.DISPLAY_P3 -> com.vivid.feature.streaming.ColorSpace.APPLE_LOG
                com.vivid.feature.streaming.ColorSpace.APPLE_LOG -> com.vivid.feature.streaming.ColorSpace.SRGB
            }
            streamingEngine.setColorSpace(nextSpace)
        },
        text = {
            Text(
                stringResource(
                    R.string.streaming_color_space_label,
                    stringResource(activeColorSpace.labelRes),
                ),
            )
        },
    )
}

@Composable
private fun ExposureMenuItems(streamingEngine: StreamingEngine) {
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val exposure by streamingEngine.exposure.collectAsStateWithLifecycle()
    val exposureRange by streamingEngine.exposureRange.collectAsStateWithLifecycle()
    val autoExposureEnabled by streamingEngine.autoExposureEnabled.collectAsStateWithLifecycle()
    val autoWhiteBalanceEnabled by streamingEngine.autoWhiteBalanceEnabled.collectAsStateWithLifecycle()
        val hasWhiteBalance = streamingEngine.hasWhiteBalanceControl()
    if (exposureRange != null || hasWhiteBalance) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("camera_controls_panel"),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // A11y: Zustandstexte für die Auto-Toggles (TalkBack).
            val autoOn = stringResource(R.string.streaming_a11y_state_on)
            val autoOff = stringResource(R.string.streaming_a11y_state_off)
            exposureRange?.let { range ->
                // A11y: aktueller EV-Wert als Zustand des Sliders.
                val exposureState = stringResource(R.string.streaming_exposure_label, exposure)
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.streaming_exposure_label, exposure),
                        style = MaterialTheme.typography.labelSmall,
                    )
                    Spacer(Modifier.width(8.dp))
                    Slider(
                        value = exposure.toFloat(),
                        onValueChange = { streamingEngine.setExposure(it.toInt()) },
                        valueRange = range.first.toFloat()..range.last.toFloat(),
                        steps = (range.last - range.first - 1).coerceAtLeast(0),
                        enabled = activeSourceKind == VideoSourceKind.CAMERA && autoExposureEnabled,
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                stateDescription = exposureState
                            },
                    )
                }
                DropdownMenuItem(
                    onClick = { streamingEngine.setAutoExposure(!autoExposureEnabled) },
                    enabled = activeSourceKind == VideoSourceKind.CAMERA,
                    modifier = Modifier.semantics {
                        role = Role.Switch
                        stateDescription = if (autoExposureEnabled) autoOn else autoOff
                    },
                    text = {
                        Text(
                            stringResource(
                                if (autoExposureEnabled) R.string.streaming_auto_exposure_on else R.string.streaming_auto_exposure_off,
                            ),
                        )
                    },
                )
            }
            if (hasWhiteBalance) {
                DropdownMenuItem(
                    onClick = { streamingEngine.setAutoWhiteBalance(!autoWhiteBalanceEnabled) },
                    enabled = activeSourceKind == VideoSourceKind.CAMERA,
                    modifier = Modifier.semantics {
                        role = Role.Switch
                        stateDescription = if (autoWhiteBalanceEnabled) autoOn else autoOff
                    },
                    text = {
                        Text(
                            stringResource(
                                if (autoWhiteBalanceEnabled) R.string.streaming_auto_wb_on else R.string.streaming_auto_wb_off,
                            ),
                        )
                    },
                )
            }
        }
    }
}

@Composable
private fun ControlsNavigationItems(navController: NavController, onDismiss: () -> Unit) {
    HorizontalDivider()
    DropdownMenuItem(
        text = { Text(stringResource(R.string.replay_library_title)) },
        onClick = {
            onDismiss()
            navController.navigate("replay_library")
        },
    )
    DropdownMenuItem(
        text = { Text(stringResource(R.string.streaming_help_content_desc)) },
        onClick = {
            onDismiss()
            navController.navigate("help_route")
        },
    )
}

@Composable
internal fun StreamingSourcePreview(streamingEngine: StreamingEngine, activeSourceKind: VideoSourceKind, previewSize: IntSize, displayRotationDegrees: Int) {
    // Every source previews the same composed GL output that reaches the encoder.
    AndroidView(
        factory = { context ->
            @SuppressLint("ClickableViewAccessibility")
            SurfaceView(context).also { view ->
                if (activeSourceKind == VideoSourceKind.CAMERA) streamingEngine.initializeCamera()
                view.holder.addCallback(
                    object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            streamingEngine.attachPreview(
                                holder.surface,
                                view.width,
                                view.height,
                                (view.display?.rotation ?: Surface.ROTATION_0) * 90,
                            )
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int,
                        ) {
                            streamingEngine.attachPreview(
                                holder.surface, width, height,
                                (view.display?.rotation ?: Surface.ROTATION_0) * 90,
                            )
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            streamingEngine.detachPreview(holder.surface)
                        }
                    },
                )
                // Tap-to-Focus (Tipp), Pinch-Zoom und Zoom-Reset (Doppeltipp)
                // auf der Kamera-Vorschau (RootEncoder-Kamera, nicht CameraX).
                val gestures = StreamingPreviewGestures(
                    context = context,
                    onTapToFocus = { v, e -> streamingEngine.tapToFocus(v, e) },
                    onZoomScale = { scale -> streamingEngine.zoomBy(scale) },
                    onDoubleTap = { streamingEngine.resetZoom() },
                )
                view.setOnTouchListener { target, event ->
                    streamingEngine.dispatchSceneTouch(target.width, target.height, event) ||
                        gestures.onTouch.onTouch(target, event)
                }
            }
        },
        update = { view ->
            // GL crops the native camera buffer to this viewport without stretching.
            view.holder.setFixedSize(previewSize.width, previewSize.height)
            view.scaleX = 1f
            view.scaleY = 1f
            val surface = view.holder.surface
            if (surface?.isValid == true) {
                streamingEngine.attachPreview(
                    surface, previewSize.width, previewSize.height,
                    displayRotationDegrees,
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )

}

@Composable
private fun StreamStartButton(streamingState: StreamingState, onStop: () -> Unit, onStart: () -> Unit) {
    var confirmStop by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(streamingState) {
        if (streamingState !is StreamingState.Streaming) confirmStop = false
    }
    if (confirmStop && streamingState is StreamingState.Streaming) {
        AlertDialog(
            onDismissRequest = { confirmStop = false },
            title = { Text(stringResource(R.string.streaming_stop_confirm_title)) },
            text = { Text(stringResource(R.string.streaming_stop_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmStop = false; onStop() },
                    modifier = Modifier.testTag("confirm_stop_stream"),
                ) { Text(stringResource(R.string.streaming_stop_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmStop = false }) {
                    Text(stringResource(R.string.streaming_keep_live))
                }
            },
        )
    }
    Button(
        onClick = {
            if (streamingState is StreamingState.Streaming) {
                confirmStop = true
            } else {
                onStart()
            }
        },
        enabled = streamingState !is StreamingState.Preparing,
        modifier = Modifier.testTag("stream_start_stop"),
    ) {
        val buttonText = when (streamingState) {
            is StreamingState.Idle -> stringResource(R.string.streaming_start)
            is StreamingState.Preparing -> stringResource(R.string.streaming_preparing)
            is StreamingState.Streaming -> stringResource(R.string.streaming_stop)
            is StreamingState.Failed -> stringResource(R.string.streaming_retry)
        }
        Text(buttonText)
    }

}

internal data class PreviewNotice(val text: String, val isError: Boolean, val source: String, val sequence: Int)

/** Local, bounded feedback: it cannot cover the top buttons or the bottom selectors. */
@Composable
internal fun TransientPreviewMessage(notice: PreviewNotice, onDismiss: () -> Unit) {
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(notice) {
        kotlinx.coroutines.delay(3_000)
        dismiss()
    }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Surface(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.Center).widthIn(max = 560.dp).heightIn(max = maxHeight)
                .testTag("stream_preview_message"),
            shape = MaterialTheme.shapes.medium,
            color = if (notice.isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (notice.isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
        ) {
            Text(notice.text, style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp))
        }
    }
}

@Composable
private fun rememberDisplayRotation(): Int {
    val context = LocalContext.current
    val localView = LocalView.current
    var displayRotationDegrees by remember { mutableIntStateOf((localView.display?.rotation ?: Surface.ROTATION_0) * 90) }
    DisposableEffect(context, localView) {
        val manager = context.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit
            override fun onDisplayRemoved(displayId: Int) = Unit
            override fun onDisplayChanged(displayId: Int) {
                if (displayId == localView.display?.displayId) {
                    displayRotationDegrees = (localView.display?.rotation ?: Surface.ROTATION_0) * 90
                }
            }
        }
        manager?.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        onDispose { manager?.unregisterDisplayListener(listener) }
    }
    return displayRotationDegrees
}

@Composable
private fun rememberRecordingAction(viewModel: StreamingViewModel, onError: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val recordingErrorMessage = stringResource(R.string.streaming_replay_start_error)
    val latestOnError by rememberUpdatedState(onError)
    fun startRecording() {
        viewModel.toggleReplay { if (!it) latestOnError(recordingErrorMessage) }
    }
    val recordingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) startRecording() else latestOnError(recordingErrorMessage)
    }
    fun requestPermissionsAndRecord() {
        val missing = buildList {
            if (viewModel.streamingEngine.sceneNeedsCameraPermission()) add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
        }.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startRecording() else recordingPermissionLauncher.launch(missing.toTypedArray())
    }

    return ::requestPermissionsAndRecord
}

@Composable
private fun rememberStreamStartAction(
    viewModel: StreamingViewModel,
    streamingEngine: StreamingEngine,
    onPermissionsChanged: (Boolean) -> Unit,
): () -> Unit {
    val context = LocalContext.current

    // #326: Der MediaProjection-Consent ist ab Android 14 ein einmaliges Token.
    // Für die aktive Screen-Capture-Quelle wird der System-Dialog deshalb vor
    // jedem Go-Live neu gezeigt — sonst startet der Stream mit toter Projektion
    // und der Service stirbt als "Unable to start service" (#327).
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (streamingEngine.onScreenCaptureConsentResult(result.resultCode, result.data)) {
            viewModel.startStream()
        }
        // Abgelehnt/abgebrochen: kein Start ohne gültigen Consent. Die Quelle
        // bleibt aktiv, damit der nächste Versuch den Dialog erneut zeigt.
    }

    fun startStreamWithFreshConsent() {
        if (!streamingEngine.needsScreenCaptureConsent()) {
            viewModel.startStream()
            return
        }
        val consentIntent = streamingEngine.createScreenCaptureConsentIntent()
        if (consentIntent == null) {
            // Quelle (noch) nicht erzeugt — der Start scheitert kontrolliert
            // über den Engine-Zustand statt über eine tote Projektion.
            viewModel.startStream()
        } else {
            consentLauncher.launch(consentIntent)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            onPermissionsChanged(true)
            startStreamWithFreshConsent()
        } else {
            onPermissionsChanged(false)
        }
    }

    fun requestPermissionsAndStart() {
        val needed = buildList {
            if (streamingEngine.sceneNeedsCameraPermission()) add(Manifest.permission.CAMERA)
            add(Manifest.permission.RECORD_AUDIO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            onPermissionsChanged(true)
            startStreamWithFreshConsent()
        } else {
            onPermissionsChanged(false)
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    return ::requestPermissionsAndStart
}

@Composable
private fun StreamConfigurationFeedback(
    startAttempted: Boolean,
    startAttempt: Int,
    configIssues: List<StreamConfigIssue>,
    streamingState: StreamingState,
    onMessage: (String, Boolean) -> Unit,
    onClear: () -> Unit,
    onAttemptConsumed: () -> Unit,
) {
    val issues = configIssues.filter { it.messageRes != R.string.stream_error_no_url || startAttempted }
    val message = issues.map { resolveIssueText(it) }.joinToString("\n")
    // Consuming an attempt must not re-open the same message on the next recomposition.
    LaunchedEffect(configIssues, streamingState, startAttempt) {
        if (issues.isNotEmpty() && streamingState !is StreamingState.Streaming && streamingState !is StreamingState.Failed) {
            onMessage(message, issues.any { it.severity == ConfigIssueSeverity.ERROR })
            onAttemptConsumed()
        } else {
            onClear()
            if (streamingState !is StreamingState.Idle) onAttemptConsumed()
        }
    }
}

@Composable
private fun StreamSessionFeedback(
    cameraStatus: CameraModeStatus,
    targets: List<StreamTargetState>,
    state: StreamingState,
    startAttempt: Int,
    onMessage: (String, Boolean) -> Unit,
) {
    val cameraMessage = cameraStatus.notice?.let { notice ->
        stringResource(when (notice) {
            CameraModeNotice.UNSUPPORTED -> R.string.camera_mode_fallback_unsupported
            CameraModeNotice.PREPARATION_FAILED -> R.string.camera_mode_fallback_preparation
            CameraModeNotice.CAPTURE_FAILED -> R.string.camera_mode_capture_failed
            CameraModeNotice.RECORDING_FAILED -> R.string.camera_mode_recording_failed
        })
    }
    LaunchedEffect(cameraMessage) { cameraMessage?.let { onMessage(it, false) } }
    val failedTarget = targets.firstOrNull { it.status == StreamTargetStatus.FAILED }
    val failure = when {
        state is StreamingState.Failed -> state.reasonRes
            ?.let { stringResource(it) }
            ?: state.reason.orEmpty()
        state is StreamingState.Streaming && failedTarget != null -> failedTarget.failureReason.orEmpty()
        else -> null
    }
    val failureMessage = failure?.let { stringResource(R.string.streaming_error_prefix, it) }
    LaunchedEffect(failureMessage, startAttempt) { failureMessage?.let { onMessage(it, true) } }
}

@Composable
private fun RefreshTwitchStatus(streamingState: StreamingState, twitchViewModel: TwitchChannelViewModel) {
    // Viewerzahl während eines laufenden Twitch-Streams periodisch aktualisieren.
    LaunchedEffect(streamingState is StreamingState.Streaming) {
        if (streamingState is StreamingState.Streaming) {
            while (true) {
                twitchViewModel.refresh()
                kotlinx.coroutines.delay(TWITCH_REFRESH_INTERVAL_MS)
            }
        }
    }

}

@Composable
private fun rememberSceneSelectionAction(
    viewModel: StreamingViewModel,
    scenes: List<com.vivid.core.data.StreamScene>,
): (com.vivid.core.data.StreamScene) -> Unit {
    val engine = viewModel.streamingEngine
    val latestScenes by rememberUpdatedState(scenes)
    var pendingId by rememberSaveable { mutableStateOf<String?>(null) }
    var previousSource by rememberSaveable { mutableStateOf(VideoSourceKind.CAMERA.name) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val scene = latestScenes.find { it.id == pendingId && it.enabled &&
            it.videoSource == com.vivid.core.data.SceneVideoSource.SCREEN_CAPTURE }
        pendingId = null
        val granted = engine.onScreenCaptureConsentResult(result.resultCode, result.data)
        if (granted && scene != null) viewModel.applyScene(scene)
        else engine.switchSource(VideoSourceKind.valueOf(previousSource))
    }
    return { scene ->
        if (scene.videoSource == com.vivid.core.data.SceneVideoSource.SCREEN_CAPTURE) {
            previousSource = engine.activeSourceKind.value.name
            if (engine.switchSource(VideoSourceKind.SCREEN_CAPTURE)) {
                val intent = engine.createScreenCaptureConsentIntent()
                if (intent != null) {
                    pendingId = scene.id
                    launcher.launch(intent)
                } else engine.switchSource(VideoSourceKind.valueOf(previousSource))
            }
        } else viewModel.applyScene(scene)
    }
}

@Composable
private fun rememberScreenCaptureAction(streamingEngine: StreamingEngine): () -> Unit {
    // S2: Screen-Capture (MediaProjection) — Consent-Dialog und Quellen-Wechsel.
    // Der System-Dialog „Bildschirm übertragen" wird per Activity-Result gestartet;
    // das Ergebnis (RESULT_OK + Daten) geht an die Screen-Capture-Quelle der Engine.
    val screenCaptureLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val granted = streamingEngine.onScreenCaptureConsentResult(result.resultCode, result.data)
        if (!granted) {
            // Consent verweigert → zurück zur Kamera; die Quelle bleibt für einen
            // erneuten Versuch erzeugt (Consent wird beim nächsten Toggle neu angefragt).
            streamingEngine.switchSource(VideoSourceKind.CAMERA)
        }
    }

    fun requestScreenCapture() {
        if (streamingEngine.switchSource(VideoSourceKind.SCREEN_CAPTURE)) {
            streamingEngine.createScreenCaptureConsentIntent()?.let {
                screenCaptureLauncher.launch(it)
            }
        }
    }

    return ::requestScreenCapture
}

@Composable
private fun rememberVideoPickerAction(streamingEngine: StreamingEngine): () -> Unit {
    // S3: Video-Player (Datei) — der SAF-Picker liefert die Content-Uri, die an
    // die Video-Player-Quelle der Engine geht. Der Wechsel auf die Quelle passiert
    // erst nach erfolgreicher Auswahl, damit keine leere Quelle aktiv wird.
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null && streamingEngine.setVideoPlayerUri(uri)) {
            streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)
        }
    }

    return { videoPickerLauncher.launch("video/*") }
}

@Composable
private fun RefreshStreamingScreenOnResume(viewModel: StreamingViewModel, streamingEngine: StreamingEngine) {
    // Re-validiert die Konfiguration, sobald der Screen wieder sichtbar wird
    // (z. B. nach der Rückkehr aus den Einstellungen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.runConfigCheck()
                streamingEngine.startIdlePreviewIfReady()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

}

/** Permission dialogs are owned by the visible Activity, never by the encoder display. */
@Composable
private fun SceneLocationPermission(viewModel: SceneWidgetsOverlayViewModel = hiltViewModel()) {
    val scene by viewModel.state.collectAsStateWithLifecycle(SceneOverlayState())
    val needsLocation = scene.widgets.any { (_, widget) ->
        widget.type == com.vivid.core.data.WidgetType.TEXT && with(widget.configuration) {
            widgetShowLocation || widgetShowSpeed || widgetShowAltitude ||
                Regex("\\{(lat|lon|location|speed|altitude|road|city|country|distance|gforce)\\}").containsMatchIn(widgetTemplate)
        }
    }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(needsLocation) {
        if (needsLocation && ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }
}

private const val TWITCH_REFRESH_INTERVAL_MS = 30_000L

/** Löst einen Selbst-Check-Befund in die lokalisierte Meldung auf. */
@Composable
private fun resolveIssueText(issue: StreamConfigIssue): String {
    val prefix = if (issue.prefixRes != 0) stringResource(issue.prefixRes) else ""
    return prefix + stringResource(issue.messageRes, *issue.formatArgs.toTypedArray())
}
