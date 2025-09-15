package com.vivid.feature.streaming.ui

import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.vivid.feature.streaming.StreamingEngine


@Composable
fun StreamingScreen(streamingEngine: StreamingEngine) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var isStreaming by remember { mutableStateOf(false) }

    // Manages the lifecycle of the camera and stream
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
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
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Embeds the Android View (SurfaceView) in Compose
        AndroidView(
            factory = { context ->
                SurfaceView(context).apply {
                    holder.addCallback(object : SurfaceHolder.Callback {
                        override fun surfaceCreated(holder: SurfaceHolder) {
                            // Initialize camera only when the surface is ready
                            streamingEngine.initializeCamera(holder.surface)
                        }

                        override fun surfaceChanged(
                            holder: SurfaceHolder,
                            format: Int,
                            width: Int,
                            height: Int
                        ) {
                            // Handle surface changes if necessary (e.g., rotation)
                        }

                        override fun surfaceDestroyed(holder: SurfaceHolder) {
                            // Release resources when the surface is destroyed
                            streamingEngine.release()
                        }
                    })
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Button(
            onClick = {
                if (isStreaming) {
                    streamingEngine.stopStream()
                } else {
                    // Make sure to replace this with your actual stream key and endpoint
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