package com.vivid.feature.streaming.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.FocusMode
import com.vivid.feature.streaming.StreamConfigIssue
import com.vivid.feature.streaming.StreamTargetState
import com.vivid.feature.streaming.StreamTargetStatus
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel

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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
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
                title = { Text("Live Stream") },
                actions = {
                    // Button für OBS-Steuerung
                    IconButton(onClick = {
                        // 2. HIER IST DIE NAVIGATIONSAKTION ZUM OBS-SCREEN
                        navController.navigate("obs_control")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Podcasts,
                            contentDescription = "Open OBS Control",
                        )
                    }

                    // Bestehender Button für die Einstellungen
                    IconButton(onClick = {
                        navController.navigate("settings_route")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings",
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
                    is StreamingState.Idle -> "Start Streaming"
                    is StreamingState.Preparing -> "Preparing..."
                    is StreamingState.Streaming -> "Stop Streaming"
                    is StreamingState.Failed -> "Retry"
                }
                Text(buttonText)
            }

            // Per-Ziel-Status (Multi-Streaming): zeigt jedes Ziel mit aktuellem Zustand.
            if (targetStates.isNotEmpty() && streamingState !is StreamingState.Idle) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = 16.dp, end = 16.dp, bottom = 72.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    targetStates.forEach { state ->
                        TargetStatusRow(state)
                    }
                }
            }

            // Fokus-Lock (Moblin #377): Fokus auf Unendlich fixieren, damit der
            // Autofokus während des Streamings nicht auf Regentropfen/Schmutz
            // scharf stellt. Gilt für die Streaming-Kamera (RootEncoder), nicht
            // nur für die Vorschau, und ist auch vor dem Go-Live schaltbar.
            FilledTonalButton(
                onClick = { streamingEngine.toggleFocusLock() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 12.dp, end = 16.dp),
            ) {
                val isLocked = focusMode == FocusMode.LOCKED_INFINITY
                Icon(
                    imageVector = if (isLocked) Icons.Filled.Lock else Icons.Filled.LockOpen,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (isLocked) "Fokus: ∞" else "Fokus: Auto")
            }

            if (streamingState is StreamingState.Failed) {
                val reason = (streamingState as StreamingState.Failed).reason
                Text(
                    text = "Error: $reason",
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (permissionDenied) {
                Text(
                    text = "Kamera-, Mikrofon- und Benachrichtigungs-Berechtigung werden für Streaming benötigt. Bitte erneut auf Go Live tippen und erlauben.",
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else if (errorMessage != null) {
                Text(
                    text = errorMessage.orEmpty(),
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }

            // Selbst-Check: Befunde nur im Idle-Zustand anzeigen (nicht während/nach dem Streamen)
            if (configIssues.isNotEmpty() && streamingState !is StreamingState.Streaming) {
                Column(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                ) {
                    configIssues.forEach { issue ->
                        ConfigIssueRow(issue)
                    }
                }
            }
        }
    }
}

/** Zeigt einen einzelnen Selbst-Check-Befund mit passendem Icon und Farbe an. */
@Composable
private fun ConfigIssueRow(issue: StreamConfigIssue) {
    val isError = issue.severity == ConfigIssueSeverity.ERROR
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 2.dp),
    ) {
        Icon(
            imageVector = if (isError) Icons.Filled.Error else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (isError) Color(0xFFB3261E) else Color(0xFF8A6D00),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = issue.message,
            color = if (isError) Color(0xFFB3261E) else Color(0xFF8A6D00),
        )
    }
}

/** Zeigt ein einzelnes Stream-Ziel (Multi-Streaming) mit URL und Status an. */
@Composable
private fun TargetStatusRow(state: StreamTargetState) {
    val label = when (state.status) {
        StreamTargetStatus.IDLE -> "bereit"
        StreamTargetStatus.PREPARING -> "verbinde…"
        StreamTargetStatus.STREAMING -> "sendet live"
        StreamTargetStatus.FAILED -> "fehlgeschlagen"
    }
    val color = when (state.status) {
        StreamTargetStatus.STREAMING -> Color(0xFF2E7D32)
        StreamTargetStatus.FAILED -> Color(0xFFB3261E)
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
