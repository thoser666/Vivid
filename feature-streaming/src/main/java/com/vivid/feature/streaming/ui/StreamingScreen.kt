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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.data.repository.StreamingViewModel

@Composable
fun StreamingScreen( navController: NavController) {
    val streamingEngine: StreamingEngine = hiltViewModel<StreamingViewModel>().streamingEngine
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStreaming by remember { mutableStateOf(false) }
    // Wir benötigen eine Referenz zur OpenGlView, um sie zu steuern
    var openGlView: OpenGlView? by remember { mutableStateOf(null) }

    // Managt den Lebenszyklus der Kamera, des Streams und der OpenGlView
    DisposableEffect(lifecycleOwner, openGlView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
//                Lifecycle.Event.ON_RESUME -> openGlView?.onResume() // Wichtig für GLSurfaceView
                Lifecycle.Event.ON_RESUME -> {
                    // If you need to re-initialize or prepare something in your streamingEngine
                    // when the screen resumes (that isn't already handled by camera initialization),
                    // you can do it here.
                    // For example, if you stopped camera preview in ON_PAUSE and need to restart it.
                    // streamingEngine.startPreview() // Assuming StreamingEngine has such a method
                }
                Lifecycle.Event.ON_PAUSE -> {
     //               openGlView?.onPause() // Wichtig für GLSurfaceView
                    if (isStreaming) {
                        streamingEngine.stopStream()
                        isStreaming = false
                    }
                    streamingEngine.release()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            // stelle sicher, dass beim Verlassen des Screens alles freigegeben wird
            streamingEngine.release()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // AndroidView, um die OpenGlView in Compose einzubetten
        AndroidView(
            factory = { context ->
                OpenGlView(context).also { view ->
                    // Initialisiere die Kamera mit der View-Instanz
                    streamingEngine.initializeCamera(view)
                    openGlView = view
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                if (isStreaming) {
                    streamingEngine.stopStream()
                } else {
                    // Ersetze dies mit deinem Stream-Schlüssel und Endpunkt
                    streamingEngine.startStream("rtmp://a.rtmp.youtube.com/live2")
                }
                isStreaming = !isStreaming
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
        }
    }
}