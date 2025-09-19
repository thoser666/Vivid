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
// WICHTIGER IMPORT: Füge diesen hinzu
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingEngine

@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val streamingEngine = viewModel.streamingEngine

    // KORREKTUR: So sammelst du den StateFlow ein.
    // Die UI wird sich automatisch neu zeichnen, wenn sich der Wert in der Engine ändert.
    val isStreaming by streamingEngine.isStreamingFlow.collectAsStateWithLifecycle()

    // Diese Referenz hält die View-Instanz
    var openGlView: OpenGlView? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner, streamingEngine) {
        val observer = LifecycleEventObserver { _, event ->
            // In der Regel nicht mehr nötig, da die Bibliothek und die Engine den Lebenszyklus verwalten.
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // Wenn der Screen verlassen wird, MUSS die Engine die Kamera freigeben
            streamingEngine.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                OpenGlView(context).also { view ->
                    streamingEngine.initializeCamera(view)
                    openGlView = view
                }
            },
            modifier = Modifier.fillMaxSize()
        )
        Button(
            onClick = {
                if (isStreaming) { // Jetzt wird hier der korrekte, beobachtete Zustand verwendet
                    streamingEngine.stopStream()
                } else {
                    streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            // Der Text wird sich auch automatisch aktualisieren
            Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
        }
    }
}