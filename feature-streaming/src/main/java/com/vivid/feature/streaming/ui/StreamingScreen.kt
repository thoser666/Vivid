package com.vivid.feature.streaming.ui

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
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.vivid.feature.streaming.StreamingEngine
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vivid.core.data.StreamScene
import com.vivid.core.ui.LocalWindowWidthClass
import com.vivid.core.ui.adaptiveControlsMaxWidth
import com.vivid.core.ui.theme.LocalExtendedColors
import com.vivid.feature.chat.twitch.TwitchChannelUiState
import com.vivid.feature.chat.twitch.TwitchChannelViewModel
import com.vivid.feature.chat.ui.ChatOverlay
import com.vivid.feature.widget.GridOverlay
import com.vivid.feature.widget.ImageWidget
import com.vivid.feature.widget.QrCodeWidget
import com.vivid.feature.widget.SlideshowWidget
import com.vivid.feature.widget.SubtitlesOverlay
import com.vivid.feature.widget.TextInfoWidget
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
    // Overlays (Chat + Widgets) als Slot: Die Kinder holen ihre ViewModels
    // selbst per hiltViewModel() — der Slot erlaubt Tests/Embedding, sie durch
    // eigenen Inhalt zu ersetzen (Default = [DefaultStreamingOverlay]).
    overlayContent: @Composable BoxScope.() -> Unit = { DefaultStreamingOverlay() },
) {
    val streamingEngine = viewModel.streamingEngine
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()
    val targetStates by streamingEngine.targetStates.collectAsStateWithLifecycle()
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val configIssues by viewModel.configIssues.collectAsStateWithLifecycle()
    // Missing configuration should not obscure the preview before a Go-Live attempt.
    val previewConfigIssues = configIssues.filterNot { it.messageRes == R.string.stream_error_no_url }
    var startAttempted by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val dismissLabel = stringResource(android.R.string.ok)
    StreamConfigurationFeedback(startAttempted, configIssues, streamingState, snackbarHostState) {
        startAttempted = false
    }
    val twitchState by twitchViewModel.uiState.collectAsStateWithLifecycle()

    RefreshTwitchStatus(streamingState, twitchViewModel)

    // Szenen (Basic Scenes) + Auto-Scene-Switcher: Liste, aktive Szene,
    // Auto-Wechsel-Zustand — aus SceneRepository/AutoSceneSwitcher.
    val scenes by viewModel.scenes.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSceneId by viewModel.activeSceneId.collectAsStateWithLifecycle(initialValue = null)
    val autoSwitchEnabled by viewModel.autoSwitchEnabled.collectAsStateWithLifecycle()
    val autoSwitchIntervalSeconds by viewModel.autoSwitchIntervalSeconds.collectAsStateWithLifecycle()

    // P1: Datenschutz-Anonymisierung — Master-Toggle + Zonen-Editor-Zustand
    // (Zonen persistiert im ZoneRepository, framesynchron an die Engine).
    val privacyEnabled by viewModel.privacyEnabled.collectAsStateWithLifecycle(initialValue = false)
    val privacyZones by viewModel.privacyZones.collectAsStateWithLifecycle(initialValue = emptyList())
    var privacyEditing by remember { mutableStateOf(false) }

    // #249: Der Screen-Eintritt fordert NIE Permissions an — auch nicht die
    // Kamera-Permission für die Idle-Vorschau. Ein Auto-Request öffnete den
    // Systemdialog beim bloßen Betreten des Screens (GrantPermissionsActivity
    // über der MainActivity, im selben Task) und leerte damit den Semantik-Baum
    // instrumentierter Compose-Tests im Emulator-Gate (Tag-Run 36849526447).
    // CAMERA/Mikrofon/Notifications werden ausschließlich im Go-Live-Flow
    // (User-Geste) angefordert; die Idle-Preview startet nur bei bereits
    // erteilter Permission (Guard hier + in startIdlePreviewIfReady).
    val context = LocalContext.current
    val displayRotationDegrees = rememberDisplayRotation()
    var permissionDenied by remember { mutableStateOf(false) }
    val requestPermissionsAndStart = rememberStreamStartAction(viewModel) { granted ->
        permissionDenied = !granted
        startAttempted = startAttempted && granted
    }

    val requestPermissionsAndRecord = rememberRecordingAction(streamingEngine, snackbarHostState, dismissLabel)

    // Start idle preview only when camera permission is already granted.
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            streamingEngine.startIdlePreviewIfReady()
        }
    }

    val requestScreenCapture = rememberScreenCaptureAction(streamingEngine)

    val requestVideoPicker = rememberVideoPickerAction(streamingEngine)

    RefreshStreamingScreenOnResume(viewModel, streamingEngine)

    var scenesExpanded by rememberSaveable { mutableStateOf(false) }

    if (scenesExpanded) {
        ModalBottomSheet(
            onDismissRequest = { scenesExpanded = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            dragHandle = {
                Box(
                    modifier = Modifier.fillMaxWidth().height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(width = 32.dp, height = 4.dp)
                            .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape),
                    )
                }
            },
        ) {
            Column(Modifier.testTag("scenes_panel")) {
                SceneSwitcherBar(
                    scenes = scenes,
                    activeSceneId = activeSceneId,
                    autoSwitchEnabled = autoSwitchEnabled,
                    autoSwitchIntervalSeconds = autoSwitchIntervalSeconds,
                    isStreaming = streamingState is StreamingState.Streaming,
                    onApplyScene = viewModel::applyScene,
                    onSaveScene = viewModel::saveScene,
                    onDeleteScene = viewModel::deleteScene,
                    onAutoSwitchEnabledChange = viewModel::setAutoSwitchEnabled,
                    onAutoSwitchIntervalChange = viewModel::setAutoSwitchIntervalSeconds,
                    privacyEnabled = privacyEnabled,
                    onPrivacyEnabledChange = { enabled ->
                        // Deaktivieren schließt zugleich den Zonen-Editor (die
                        // Zonen sind ohne Toggle nicht mehr im Bild).
                        if (!enabled) privacyEditing = false
                        viewModel.setPrivacyEnabled(enabled)
                    },
                    onOpenZoneEditor = {
                        scenesExpanded = false
                        privacyEditing = true
                    },
                )
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val topButtonMaxWidth = maxWidth / 2 - 20.dp
            val density = LocalDensity.current
            val previewSize = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
            StreamingSourcePreview(streamingEngine, activeSourceKind, previewSize, displayRotationDegrees) {
                requestVideoPicker()
            }

            StreamStartButton(streamingState, onStop = viewModel::stopStream) {
                startAttempted = true
                if (configIssues.none { it.messageRes == R.string.stream_error_no_url }) requestPermissionsAndStart()
            }

            TextButton(
                onClick = { scenesExpanded = true },
                modifier = Modifier.align(Alignment.BottomCenter).testTag("open_scenes"),
            ) {
                Text(stringResource(R.string.scene_bar_title))
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

            // Per-Ziel-Status (Multi-Streaming): zeigt jedes Ziel mit aktuellem Zustand.
            if (targetStates.isNotEmpty() && streamingState !is StreamingState.Idle) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 72.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        targetStates.forEach { state ->
                            TargetStatusRow(state)
                        }
                    }
                }
            }

            StreamingControlsMenu(
                streamingEngine, navController, topButtonMaxWidth,
                onRecord = requestPermissionsAndRecord,
                onScreenCapture = requestScreenCapture,
                onVideoPicker = { requestVideoPicker() },
            )
            FilledIconButton(
                onClick = { navController.navigate("settings_route") },
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 12.dp, end = 16.dp).testTag("open_settings"),
            ) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.streaming_settings_content_desc))
            }

            StreamingIssueBanners(previewConfigIssues, streamingState, permissionDenied)

            // Chat-Overlay + Widgets: als Slot ausgelagert (siehe Parameter-Doku).
            overlayContent()

            // P1: Zonen-Editor (Anonymisierung, Skizze §5) über der Vorschau —
            // nur im Idle-Modus geöffnet (Szenen-Sperre-Muster: während des
            // Streams wird nicht editiert), Zonen via ViewModel persistiert und
            // framesynchron an die Engine.
            if (privacyEditing) {
                ZoneEditorOverlay(
                    zones = privacyZones,
                    onZonesChange = viewModel::savePrivacyZones,
                    onClose = { privacyEditing = false },
                )
            }

        }
    }
}

@Composable
private fun BoxScope.StreamingControlsMenu(
    streamingEngine: StreamingEngine,
    navController: NavController,
    topButtonMaxWidth: Dp,
    onRecord: () -> Unit,
    onScreenCapture: () -> Unit,
    onVideoPicker: () -> Unit,
) {
    var controlsExpanded by remember { mutableStateOf(false) }
    fun dismissControls() { controlsExpanded = false }
    Box(
        modifier = Modifier.align(Alignment.TopStart).padding(top = 12.dp, start = 16.dp),
    ) {
        FilledTonalButton(
            onClick = { controlsExpanded = true },
            modifier = Modifier.widthIn(max = topButtonMaxWidth).testTag("open_controls"),
            contentPadding = PaddingValues(horizontal = 12.dp),
        ) {
            Text(stringResource(R.string.streaming_controls), maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        DropdownMenu(
            expanded = controlsExpanded,
            onDismissRequest = { controlsExpanded = false },
            modifier = Modifier.width(adaptiveControlsMaxWidth(LocalWindowWidthClass.current)).testTag("controls_menu"),
        ) {
            SourceSelectionItems(streamingEngine, ::dismissControls, onScreenCapture, onVideoPicker)
            HorizontalDivider()
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
    DropdownMenuItem(
        modifier = Modifier.testTag("replay_record"),
        enabled = activeSourceKind == VideoSourceKind.CAMERA,
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
private fun StreamingSourcePreview(streamingEngine: StreamingEngine, activeSourceKind: VideoSourceKind, previewSize: IntSize, displayRotationDegrees: Int, onVideoPicker: () -> Unit) {
    // Camera2 and encoding both render through GL, so idle preview shows effects too.
    // Der Encoder hängt nicht an der Activity-Surface und läuft bei
    // deren Zerstörung (Recents-Wischen, Rotation) weiter.
    // S2: Bei aktiver Screen-Capture-Quelle wird keine Kamera-Vorschau
    // angehängt — stattdessen erscheint ein Platzhalter mit Hinweis.
    if (activeSourceKind == VideoSourceKind.CAMERA) {
        AndroidView(
            factory = { context ->
                @SuppressLint("ClickableViewAccessibility")
                SurfaceView(context).also { view ->
                    streamingEngine.initializeCamera()
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
                    view.setOnTouchListener(gestures.onTouch)
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
    } else {
        // S2/S3/Replay: Screen-Capture, Video-Player oder Replay aktiv — kein
        // Kamera-Bild, stattdessen ein dunkler Platzhalter, damit klar ist, dass
        // der Gerätebildschirm bzw. die Datei (und nicht die Kamera) die
        // Videoquelle ist.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = stringResource(
                        when (activeSourceKind) {
                            VideoSourceKind.VIDEO_PLAYER -> R.string.streaming_source_video_active_hint
                            VideoSourceKind.REPLAY -> R.string.streaming_source_replay_active_hint
                            else -> R.string.streaming_source_screen_active_hint
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp),
                )
                if (activeSourceKind == VideoSourceKind.VIDEO_PLAYER) {
                    Spacer(Modifier.size(12.dp))
                    Button(onClick = { onVideoPicker() }) {
                        Text(stringResource(R.string.streaming_source_video_pick))
                    }
                }
            }
        }
    }

}

@Composable
private fun BoxScope.StreamStartButton(streamingState: StreamingState, onStop: () -> Unit, onStart: () -> Unit) {
    Button(
        onClick = {
            if (streamingState is StreamingState.Streaming) {
                onStop()
            } else {
                onStart()
            }
        },
        enabled = streamingState !is StreamingState.Preparing,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 48.dp),
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

@Composable
private fun BoxScope.StreamingIssueBanners(previewConfigIssues: List<StreamConfigIssue>, streamingState: StreamingState, permissionDenied: Boolean) {
    val errorIssues = previewConfigIssues.filter { it.severity == ConfigIssueSeverity.ERROR }
    if (streamingState is StreamingState.Failed) {
        val reason = (streamingState as StreamingState.Failed).reason
        PreviewMessageBanner(
            text = stringResource(R.string.streaming_error_prefix, reason ?: ""),
            isError = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else if (permissionDenied) {
        PreviewMessageBanner(
            text = stringResource(R.string.streaming_permission_required),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    } else if (errorIssues.isNotEmpty() && streamingState !is StreamingState.Streaming) {
        PreviewMessageBanner(
            text = errorIssues.map { resolveIssueText(it) }.joinToString("\n"),
            isError = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }

    // Selbst-Check: Befunde nur im Idle-Zustand anzeigen (nicht während/nach dem
    // Streamen). Bei gesetzten Fehler-Befunden (errorIssues) wird die Liste ausgeblendet
    // — das Banner zeigt dieselben Fehler bereits, eine Doppelanzeige überlappte sonst.
    if (previewConfigIssues.isNotEmpty() && streamingState !is StreamingState.Streaming && errorIssues.isEmpty()) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                previewConfigIssues.forEach { issue ->
                    ConfigIssueRow(issue)
                }
            }
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
private fun rememberRecordingAction(streamingEngine: StreamingEngine, snackbarHostState: SnackbarHostState, dismissLabel: String): () -> Unit {
    val context = LocalContext.current
    var recordingError by remember { mutableStateOf(false) }
    val recordingErrorMessage = stringResource(R.string.streaming_replay_start_error)
    LaunchedEffect(recordingError) {
        if (recordingError) {
            try {
                snackbarHostState.showSnackbar(recordingErrorMessage, actionLabel = dismissLabel)
            } finally {
                recordingError = false
            }
        }
    }
    fun startRecording() {
        recordingError = !streamingEngine.startReplay()
    }
    val recordingPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) startRecording() else recordingError = true
    }
    fun requestPermissionsAndRecord() {
        val missing = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) startRecording() else recordingPermissionLauncher.launch(missing.toTypedArray())
    }

    return ::requestPermissionsAndRecord
}

@Composable
private fun rememberStreamStartAction(viewModel: StreamingViewModel, onPermissionsChanged: (Boolean) -> Unit): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            onPermissionsChanged(true)
            viewModel.startStream()
        } else {
            onPermissionsChanged(false)
        }
    }

    fun requestPermissionsAndStart() {
        val needed = buildList {
            add(Manifest.permission.CAMERA)
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
            viewModel.startStream()
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
    configIssues: List<StreamConfigIssue>,
    streamingState: StreamingState,
    snackbarHostState: SnackbarHostState,
    onAttemptConsumed: () -> Unit,
) {
    val missingUrlMessage = stringResource(R.string.stream_error_no_url)
    val dismissLabel = stringResource(android.R.string.ok)
    LaunchedEffect(startAttempted, configIssues, streamingState) {
        if (!startAttempted) return@LaunchedEffect
        if (configIssues.any { it.messageRes == R.string.stream_error_no_url }) {
            try {
                snackbarHostState.showSnackbar(missingUrlMessage, actionLabel = dismissLabel)
            } finally {
                onAttemptConsumed()
            }
        } else if (streamingState !is StreamingState.Idle) {
            onAttemptConsumed()
        }
    }
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

/**
 * Standard-Overlay-Inhalt des Streaming-Screens: Chat-Overlay (Twitch) links
 * unten, Text-/Info-Widget rechts unten, Grid/Bild/QR/Slideshow über der
 * Vorschau. Jedes Kind blendet sich bei deaktivierter Einstellung selbst aus.
 */
@Composable
private fun BoxScope.DefaultStreamingOverlay() {
    ChatOverlay(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .padding(start = 12.dp, bottom = 84.dp),
    )

    TextInfoWidget(
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 84.dp),
    )

    GridOverlay()

    ImageWidget(
        modifier = Modifier
            .align(Alignment.TopStart)
            .padding(start = 12.dp, top = 12.dp),
    )

    QrCodeWidget(
        modifier = Modifier
            .align(Alignment.TopEnd)
            .padding(end = 12.dp, top = 12.dp),
    )

    SlideshowWidget(
        modifier = Modifier
            .align(Alignment.Center)
            .padding(12.dp),
    )

    // Untertitel (Speech-to-Text) ueber der Vorschau, unten mittig.
    SubtitlesOverlay(
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 140.dp),
    )
}

private const val TWITCH_REFRESH_INTERVAL_MS = 30_000L

/** Zeigt einen einzelnen Selbst-Check-Befund mit passendem Icon und Farbe an. */
@Composable
private fun ConfigIssueRow(issue: StreamConfigIssue) {
    val isError = issue.severity == ConfigIssueSeverity.ERROR
    val tint = if (isError) {
        MaterialTheme.colorScheme.error
    } else {
        LocalExtendedColors.current.warning
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Error else Icons.Filled.Warning,
            contentDescription = null,
            tint = tint,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = resolveIssueText(issue),
            color = tint,
        )
    }
}

/** Löst einen Selbst-Check-Befund in die lokalisierte Meldung auf. */
@Composable
private fun resolveIssueText(issue: StreamConfigIssue): String {
    val prefix = if (issue.prefixRes != 0) stringResource(issue.prefixRes) else ""
    return prefix + stringResource(issue.messageRes, *issue.formatArgs.toTypedArray())
}

/** Banner über der Kamera-Vorschau: opaker Container garantiert Kontrast auf dem schwarzen Preview. */
@Composable
private fun PreviewMessageBanner(
    text: String,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
) {
    val container = if (isError) {
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh to MaterialTheme.colorScheme.onSurface
    }
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = container.first,
        contentColor = container.second,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

/** Zeigt ein einzelnes Stream-Ziel (Multi-Streaming) mit URL, Status und Upload-Bitrate an. */
@Composable
private fun TargetStatusRow(state: StreamTargetState) {
    val label = stringResource(
        when (state.status) {
            StreamTargetStatus.IDLE -> R.string.streaming_target_idle
            StreamTargetStatus.PREPARING -> R.string.streaming_target_preparing
            StreamTargetStatus.STREAMING -> R.string.streaming_target_streaming
            StreamTargetStatus.FAILED -> R.string.streaming_target_failed
        },
    )
    val color = when (state.status) {
        StreamTargetStatus.STREAMING -> LocalExtendedColors.current.success
        StreamTargetStatus.FAILED -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val bitrateLabel = state.bitrateKbps?.takeIf { it > 0 }?.let { kbps ->
        if (kbps >= 1_000) {
            stringResource(R.string.streaming_target_bitrate_mbps, kbps / 1_000.0)
        } else {
            stringResource(R.string.streaming_target_bitrate_kbps, kbps.toString())
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = if (bitrateLabel != null) "${state.url} · $label · $bitrateLabel" else "${state.url} · $label",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

/**
 * Szenen-Leiste (Moblin: Basic Scenes) als Bottom-Bar des Streaming-Screens.
 *
 * - Chips für jede gespeicherte Szene (Tipp = anwenden; während des Streams
 *   gesperrt, weil die Engine-Quelle nur zwischen Starts gewechselt wird)
 * - „+“ öffnet die Eingabe für eine neue Szene (Name vorbefüllt, lokalisiert)
 * - Mülleimer löscht die aktive Szene
 * - Auto-Scene-Switcher: An/Aus-Toggle + Intervall (Sekunden, Minimum 5 s)
 */
@Composable
private fun SceneSwitcherBar(
    scenes: List<StreamScene>,
    activeSceneId: String?,
    autoSwitchEnabled: Boolean,
    autoSwitchIntervalSeconds: Long,
    isStreaming: Boolean,
    onApplyScene: (StreamScene) -> Unit,
    onSaveScene: (String) -> Unit,
    onDeleteScene: (String) -> Unit,
    onAutoSwitchEnabledChange: (Boolean) -> Unit,
    onAutoSwitchIntervalChange: (Long) -> Unit,
    privacyEnabled: Boolean,
    onPrivacyEnabledChange: (Boolean) -> Unit,
    onOpenZoneEditor: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf("") }
    // Lokalisierter Standardname (z. B. „Szene 1“) — der Nutzer kann ihn editieren.
    val defaultSceneName = stringResource(R.string.scene_default_name, scenes.size + 1)

    // Custom bottomBar: Das M3-Scaffold paddet eigene bottomBars NICHT mit den
    // Navigationsleisten-Insets — ohne dieses Padding läge die Szenen-Leiste
    // unter der Gesture-Navigation (Edge-to-Edge).
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            if (adding) {
                // Eingabe-Modus: Name + Speichern/Abbrechen.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = sceneName,
                        onValueChange = { sceneName = it },
                        label = { Text(stringResource(R.string.scene_name_hint)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            onSaveScene(sceneName)
                            sceneName = ""
                            adding = false
                        },
                        enabled = sceneName.isNotBlank(),
                    ) {
                        Text(stringResource(R.string.scene_add))
                    }
                    IconButton(onClick = {
                        sceneName = ""
                        adding = false
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.scene_add_cancel),
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.scene_bar_title),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    if (scenes.isEmpty()) {
                        Text(
                            text = stringResource(R.string.scene_empty_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            scenes.forEach { scene ->
                                FilterChip(
                                    selected = scene.id == activeSceneId,
                                    onClick = { onApplyScene(scene) },
                                    enabled = !isStreaming,
                                    label = { Text(scene.name) },
                                )
                            }
                        }
                    }
                    FilledTonalIconButton(onClick = {
                        sceneName = defaultSceneName
                        adding = true
                    }) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = stringResource(R.string.scene_add),
                        )
                    }
                    if (activeSceneId != null) {
                        FilledTonalIconButton(onClick = { onDeleteScene(activeSceneId) }) {
                            Icon(
                                imageVector = Icons.Filled.Delete,
                                contentDescription = stringResource(R.string.scene_delete_desc),
                            )
                        }
                    }
                }
            }
            // Auto-Scene-Switcher: An/Aus + Intervall (nur bei An editierbar).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.scene_auto_switch),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = autoSwitchIntervalSeconds.toString(),
                    onValueChange = { raw ->
                        raw.toLongOrNull()?.let(onAutoSwitchIntervalChange)
                    },
                    label = { Text(stringResource(R.string.scene_interval_seconds)) },
                    singleLine = true,
                    enabled = autoSwitchEnabled,
                    modifier = Modifier.width(120.dp),
                )
                Switch(
                    checked = autoSwitchEnabled,
                    onCheckedChange = onAutoSwitchEnabledChange,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            // P1: Datenschutz-Anonymisierung (Skizze §5) — Master-Toggle wirkt
            // auch während des Streams (Composer-Rebuild an Position 0), der
            // Zonen-Editor folgt der Szenen-Sperre (nur im Idle-Modus).
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val privacyOn = stringResource(R.string.streaming_a11y_state_on)
                val privacyOff = stringResource(R.string.streaming_a11y_state_off)
                Text(
                    text = stringResource(R.string.privacy_master_toggle_label),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                if (privacyEnabled) {
                    FilledTonalButton(
                        onClick = onOpenZoneEditor,
                        enabled = !isStreaming,
                    ) {
                        Text(stringResource(R.string.streaming_privacy_zones))
                    }
                }
                Switch(
                    checked = privacyEnabled,
                    onCheckedChange = onPrivacyEnabledChange,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .testTag("privacy_toggle")
                        .semantics {
                            role = Role.Switch
                            stateDescription = if (privacyEnabled) privacyOn else privacyOff
                        },
                )
            }
        }
    }
}
