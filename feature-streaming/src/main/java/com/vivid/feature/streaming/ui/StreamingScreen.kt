package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState

@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val streamingEngine = viewModel.streamingEngine

    // KORREKTUR 3: Den neuen, reichhaltigeren StateFlow abonnieren
    val streamingState by streamingEngine.streamingState.collectAsStateWithLifecycle()

    // ... (DisposableEffect bleibt wie er ist)

    Box(modifier = Modifier.fillMaxSize()) {
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
                    streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
                }
            },
            // Deaktiviere den Button, während die Verbindung aufgebaut wird.
            enabled = streamingState !is StreamingState.Preparing,
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Passe den Text an den aktuellen Zustand an.
            val buttonText = when (streamingState) {
                is StreamingState.Idle -> "Start Streaming"
                is StreamingState.Preparing -> "Preparing..."
                is StreamingState.Streaming -> "Stop Streaming"
                is StreamingState.Failed -> "Retry"
            }
            Text(buttonText)
        }

        // Optional: Zeige eine Fehlermeldung an
        if (streamingState is StreamingState.Failed) {
            val reason = (streamingState as StreamingState.Failed).reason
            Text(
                text = "Error: $reason",
                modifier = Modifier.align(Alignment.TopCenter)
            )
        }
    }
}