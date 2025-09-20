package com.vivid.feature.streaming.ui

// WICHTIGE IMPORTE HINZUFÜGEN
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
// -- Ende der neuen Importe --

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingState


// Füge die Annotation hinzu, um Scaffold/TopAppBar zu verwenden
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val streamingEngine = viewModel.streamingEngine
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()

    // 1. Umhülle alles mit einem Scaffold
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Live Stream") },
                // 2. Füge eine "actions"-Sektion mit einem Icon-Button hinzu
                actions = {
                    IconButton(onClick = {
                        // 3. HIER IST DIE NAVIGATIONSAKTION
                        navController.navigate("settings_route")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Open Settings"
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // Der ursprüngliche Box-Inhalt kommt hier rein
        Box(
            modifier = Modifier
                .fillMaxSize()
                // Wichtig: Wende das Padding vom Scaffold an, damit dein Inhalt
                // nicht von der TopAppBar überdeckt wird.
                .padding(paddingValues)
        ) {
            AndroidView(
                factory = { context ->
                    OpenGlView(context).also { view ->
                        streamingEngine.initializeCamera(view)
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Button(
                onClick = {
                    if (streamingState is StreamingState.Streaming) {
                        streamingEngine.stopStream()
                    } else {
                        // TODO: Hier die URL aus den Settings verwenden
                        streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
                    }
                },
                enabled = streamingState !is StreamingState.Preparing,
                modifier = Modifier.align(Alignment.BottomCenter)
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
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        }
    }
}