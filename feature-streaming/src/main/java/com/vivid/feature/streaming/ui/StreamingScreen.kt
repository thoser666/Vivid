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
import androidx.hilt.navigation.compose.hiltViewModel // Oder wie auch immer du die Engine bekommst
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingEngine
//import com.vivid.feature.streaming.opengles.OpenGlView // Deine benutzerdefinierte View

@Composable
fun StreamingScreen(
    navController: NavController,
    streamingEngine: StreamingEngine = hiltViewModel() // Beispiel: Engine über ViewModel holen
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    // Zustand direkt von der Engine abfragen
    val isStreaming by remember(streamingEngine.isStreaming) {
        mutableStateOf(streamingEngine.isStreaming)
    }

    // Diese Referenz hält die View-Instanz
    var openGlView: OpenGlView? by remember { mutableStateOf(null) }

    DisposableEffect(lifecycleOwner, streamingEngine) {
        val observer = LifecycleEventObserver { _, event ->
            // The library might handle these internally or through the StreamingEngine
            // No direct calls to openGlView.onResume() or openGlView.onPause() are usually needed.
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            streamingEngine.release() // Ensure resources are released
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { context ->
                OpenGlView(context).also { view ->
                    // Initialisiere die Engine mit der View-Instanz
                    streamingEngine.initializeCamera(view)
                    openGlView = view
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                if (streamingEngine.isStreaming) {
                    streamingEngine.stopStream()
                } else {
                    streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
                }
                // UI neu zeichnen lassen (ggf. ist hier ein besserer State-Flow nötig)
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
        }
    }
}