package com.vivid.feature.streaming.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.Help
// 1. Importiere ein passendes Icon für die OBS-Steuerung
import androidx.compose.material.icons.filled.Podcasts // (Ein gutes Icon für "Broadcasting")
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
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
    val configIssues by viewModel.configIssues.collectAsStateWithLifecycle()

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
