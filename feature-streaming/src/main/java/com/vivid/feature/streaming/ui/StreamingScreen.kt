package com.vivid.feature.streaming.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
// 1. Importiere ein passendes Icon für die OBS-Steuerung
import androidx.compose.material.icons.filled.Podcasts // (Ein gutes Icon für "Broadcasting")
import androidx.compose.material3.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
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
                        streamingEngine.stopStream()
                    } else {
                        streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
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
            }
            // In StreamingScreen.kt:
            Button(
                onClick = { navController.navigate("obs_control") },
            ) {
                Text("OBS Steuerung öffnen")
            }
        }
    }
}
