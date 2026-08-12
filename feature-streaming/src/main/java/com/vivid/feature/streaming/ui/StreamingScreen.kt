package com.vivid.feature.streaming.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
// 1. Importiere ein passendes Icon für die OBS-Steuerung
import androidx.compose.material.icons.filled.Podcasts // (Ein gutes Icon für "Broadcasting")
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.ConfigIssueSeverity
import com.vivid.feature.streaming.StreamConfigIssue
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
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val configIssues by viewModel.configIssues.collectAsStateWithLifecycle()

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
            // ... der restliche Inhalt des Screens bleibt unverändert
            AndroidView(
                factory = { context ->
                    OpenGlView(context).also { view ->
                        streamingEngine.initializeCamera(view)
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )

            Button(
                onClick = {
                    if (streamingState is StreamingState.Streaming) {
                        viewModel.stopStream()
                    } else {
                        viewModel.startStream()
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

            if (streamingState is StreamingState.Failed) {
                val reason = (streamingState as StreamingState.Failed).reason
                Text(
                    text = "Error: $reason",
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
