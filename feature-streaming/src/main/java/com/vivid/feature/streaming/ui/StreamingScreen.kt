package com.vivid.feature.streaming.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
// 1. Importiere ein passendes Icon für die OBS-Steuerung
import androidx.compose.material.icons.filled.Podcasts // (Ein gutes Icon für "Broadcasting")
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vivid.core.data.StreamScene
import com.vivid.core.ui.theme.LocalExtendedColors
import com.vivid.feature.chat.ui.ChatOverlay
import com.vivid.feature.widget.TextInfoWidget
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.FocusMode
import com.vivid.feature.streaming.StreamConfigIssue
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(),
) {
    val streamingEngine = viewModel.streamingEngine
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()
    val targetStates by streamingEngine.targetStates.collectAsStateWithLifecycle()
    val focusMode by streamingEngine.focusMode.collectAsStateWithLifecycle()
    val stabilizationEnabled by streamingEngine.stabilizationEnabled.collectAsStateWithLifecycle()
    val torchEnabled by streamingEngine.torchEnabled.collectAsStateWithLifecycle()
    val activeSourceKind by streamingEngine.activeSourceKind.collectAsStateWithLifecycle()
    val activeFilter by streamingEngine.activeFilter.collectAsStateWithLifecycle()
    val lowLightBoostEnabled by streamingEngine.lowLightBoostEnabled.collectAsStateWithLifecycle()
    val configIssues by viewModel.configIssues.collectAsStateWithLifecycle()

    // Szenen (Basic Scenes) + Auto-Scene-Switcher: Liste, aktive Szene,
    // Auto-Wechsel-Zustand — aus SceneRepository/AutoSceneSwitcher.
    val scenes by viewModel.scenes.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeSceneId by viewModel.activeSceneId.collectAsStateWithLifecycle(initialValue = null)
    val autoSwitchEnabled by viewModel.autoSwitchEnabled.collectAsStateWithLifecycle()
    val autoSwitchIntervalSeconds by viewModel.autoSwitchIntervalSeconds.collectAsStateWithLifecycle()

    // Runtime-Permissions (Kamera/Mikro + Notifications) werden beim Go-Live
    // angefordert — der Foreground-Service braucht sie auf Android 13+.
    val context = LocalContext.current
    var permissionDenied by remember { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) {
            permissionDenied = false
            viewModel.startStream()
        } else {
            permissionDenied = true
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
            permissionDenied = false
            viewModel.startStream()
        } else {
            permissionDenied = false
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

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

    // S3: Video-Player (Datei) — der SAF-Picker liefert die Content-Uri, die an
    // die Video-Player-Quelle der Engine geht. Der Wechsel auf die Quelle passiert
    // erst nach erfolgreicher Auswahl, damit keine leere Quelle aktiv wird.
    val videoPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            if (streamingEngine.setVideoPlayerUri(uri)) {
                streamingEngine.switchSource(VideoSourceKind.VIDEO_PLAYER)
            }
        }
    }

    // Re-validiert die Konfiguration, sobald der Screen wieder sichtbar wird
    // (z. B. nach der Rückkehr aus den Einstellungen).
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.runConfigCheck()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.streaming_ui_title)) },
                actions = {
                    // Button für OBS-Steuerung
                    IconButton(onClick = {
                        // 2. HIER IST DIE NAVIGATIONSAKTION ZUM OBS-SCREEN
                        navController.navigate("obs_control")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = stringResource(R.string.streaming_obs_content_desc),
                        )
                    }

                    // Hilfe-Button
                    IconButton(onClick = {
                        navController.navigate("help_route")
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Help,
                            contentDescription = stringResource(R.string.streaming_help_content_desc),
                        )
                    }

                    // Bestehender Button für die Einstellungen
                    IconButton(onClick = {
                        navController.navigate("settings_route")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = stringResource(R.string.streaming_settings_content_desc),
                        )
                    }
                },
            )
        },
        // Szenen-Leiste (Basic Scenes): immer sichtbar — Chips zum Umschalten,
        // „+“ zum Speichern der aktuellen Konfiguration, Löschen der aktiven
        // Szene und der Auto-Scene-Switcher (An/Aus + Intervall).
        bottomBar = {
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
            )
        },
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
        ) {
            // Kamera-Vorschau als SurfaceView: Die Preview-Surface wird an die
            // interne GL-Pipeline der Engine angehängt (attachPreview). Der
            // Encoder selbst hängt NICHT an dieser Surface — der Stream läuft
            // deshalb weiter, wenn die Activity (und damit die Vorschau) zerstört
            // wird (Recents-Wischen, Rotation).
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
                                        )
                                    }

                                    override fun surfaceChanged(
                                        holder: SurfaceHolder,
                                        format: Int,
                                        width: Int,
                                        height: Int,
                                    ) {
                                        streamingEngine.attachPreview(holder.surface, width, height)
                                    }

                                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                                        streamingEngine.detachPreview()
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
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                // S2/S3: Screen-Capture bzw. Video-Player aktiv — kein Kamera-Bild,
                // stattdessen ein dunkler Platzhalter, damit klar ist, dass der
                // Gerätebildschirm bzw. die Datei (und nicht die Kamera) die
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
                                if (activeSourceKind == VideoSourceKind.VIDEO_PLAYER) {
                                    R.string.streaming_source_video_active_hint
                                } else {
                                    R.string.streaming_source_screen_active_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                        if (activeSourceKind == VideoSourceKind.VIDEO_PLAYER) {
                            Spacer(Modifier.size(12.dp))
                            Button(onClick = { videoPickerLauncher.launch("video/*") }) {
                                Text(stringResource(R.string.streaming_source_video_pick))
                            }
                        }
                    }
                }
            }

            Button(
                onClick = {
                    if (streamingState is StreamingState.Streaming) {
                        viewModel.stopStream()
                    } else {
                        requestPermissionsAndStart()
                    }
                },
                enabled = streamingState !is StreamingState.Preparing,
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                val buttonText = when (streamingState) {
                    is StreamingState.Idle -> stringResource(R.string.streaming_start)
                    is StreamingState.Preparing -> stringResource(R.string.streaming_preparing)
                    is StreamingState.Streaming -> stringResource(R.string.streaming_stop)
                    is StreamingState.Failed -> stringResource(R.string.streaming_retry)
                }
                Text(buttonText)
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

            // Fokus-Lock (Moblin #377) + Video-Stabilisierung: Die Buttons steuern
            // die RootEncoder-Kamera (nicht nur die Vorschau) und sind auch vor
            // dem Go-Live schaltbar.
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { streamingEngine.toggleTorch() },
                ) {
                    Text(
                        stringResource(
                            if (torchEnabled) R.string.streaming_torch_on else R.string.streaming_torch_off,
                        ),
                    )
                }

                FilledTonalButton(
                    onClick = { streamingEngine.toggleStabilization() },
                ) {
                    Text(
                        stringResource(
                            if (stabilizationEnabled) R.string.streaming_stabilization_on else R.string.streaming_stabilization_off,
                        ),
                    )
                }

                FilledTonalButton(
                    onClick = { streamingEngine.toggleFocusLock() },
                ) {
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

                // Video-Effekte: Filter-Button zeigt den aktuellen Filter an und
                // wechselt zum nächsten bei Klick (zirkulär durch die Liste).
                FilledTonalButton(
                    onClick = { streamingEngine.nextVideoFilter() },
                ) {
                    Text(
                        stringResource(
                            R.string.streaming_filter_label,
                            stringResource(activeFilter.labelRes),
                        ),
                    )
                }

                // Low-Light-Boost: software-basierte Helligkeitsanhebung (1.5x Gain).
                FilledTonalButton(
                    onClick = { streamingEngine.toggleLowLightBoost() },
                ) {
                    Text(
                        stringResource(
                            if (lowLightBoostEnabled) R.string.streaming_boost_on else R.string.streaming_boost_off,
                        ),
                    )
                }
            }

            // S2: Quellen-Umschalter (Kamera / Bildschirm). Screen-Capture fragt
            // den MediaProjection-Consent an, sobald die Quelle gewechselt wird.
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 12.dp, start = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = { streamingEngine.switchSource(VideoSourceKind.CAMERA) },
                    enabled = activeSourceKind != VideoSourceKind.CAMERA,
                ) {
                    Text(stringResource(R.string.streaming_source_camera))
                }
                FilledTonalButton(
                    onClick = { requestScreenCapture() },
                    enabled = activeSourceKind != VideoSourceKind.SCREEN_CAPTURE,
                ) {
                    Text(stringResource(R.string.streaming_source_screen))
                }
                // S3: Video-Datei-Quelle — der SAF-Picker startet den Datei-Dialog;
                // die Quelle wird erst nach erfolgreicher Auswahl aktiv.
                FilledTonalButton(
                    onClick = { videoPickerLauncher.launch("video/*") },
                    enabled = activeSourceKind != VideoSourceKind.VIDEO_PLAYER,
                ) {
                    Text(stringResource(R.string.streaming_source_video))
                }
            }

            val errorIssues = configIssues.filter { it.severity == ConfigIssueSeverity.ERROR }
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
            if (configIssues.isNotEmpty() && streamingState !is StreamingState.Streaming && errorIssues.isEmpty()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ) {
                    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        configIssues.forEach { issue ->
                            ConfigIssueRow(issue)
                        }
                    }
                }
            }

            // Chat-Overlay (Twitch): unterhalb der Status-Box links unten,
            // blendet sich bei deaktiviertem Overlay automatisch aus.
            ChatOverlay(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = 84.dp),
            )

            // Text-/Info-Widget (Uhrzeit/GPS/Geschwindigkeit): rechts unten,
            // gegenüber dem Chat-Overlay, damit sich beide nicht überlappen.
            TextInfoWidget(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 12.dp, bottom = 84.dp),
            )
        }
    }
}

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

/** Zeigt ein einzelnes Stream-Ziel (Multi-Streaming) mit URL und Status an. */
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${state.url} · $label",
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
) {
    var adding by remember { mutableStateOf(false) }
    var sceneName by remember { mutableStateOf("") }
    // Lokalisierter Standardname (z. B. „Szene 1“) — der Nutzer kann ihn editieren.
    val defaultSceneName = stringResource(R.string.scene_default_name, scenes.size + 1)

    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
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
        }
    }
}
