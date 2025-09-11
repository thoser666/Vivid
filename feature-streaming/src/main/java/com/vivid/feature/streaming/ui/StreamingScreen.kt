package com.vivid.feature.streaming.ui

import android.Manifest
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.data.repository.StreamingViewModel

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    //val streamingEngine = viewModel.streamingEngine

   // val isStreaming by streamingEngine.isStreaming.collectAsState()
   // val error by streamingEngine.streamingError.collectAsState()

    val rtmpUrl = "rtmp://your_rtmp_server/your_app/your_stream_key"

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
    )

    // Wir erstellen hier eine Referenz auf die OpenGlView, die wir später füllen.
    val openGlView = remember { mutableStateOf<OpenGlView?>(null) }

    // LaunchedEffect wird ausgeführt, wenn die Berechtigungen erteilt werden.
    // Wenn die Berechtigungen widerrufen werden, wird die Coroutine abgebrochen.
    LaunchedEffect(permissionsState.allPermissionsGranted) {
        if (permissionsState.allPermissionsGranted && openGlView.value != null) {
            // Initialisieren und Vorschau starten.
//            streamingEngine.initializeCamera(openGlView.value!!)
//            streamingEngine.setVideoSettings(1280, 720, 2500 * 1024, 30, 90)
//            streamingEngine.setAudioSettings(44100, true, 128 * 1024)
//            streamingEngine.startPreview()
        }
    }

    // Lebenszyklus-Management
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) {
//                if (streamingEngine.isStreaming.value) {
//                    streamingEngine.stopStreaming()
//                }
//                streamingEngine.stopPreview()
//            } else if (event == Lifecycle.Event.ON_RESUME) {
                // Die Vorschau wird nur neu gestartet, wenn die Berechtigungen noch erteilt sind.
                if (permissionsState.allPermissionsGranted) {
//                    streamingEngine.startPreview()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionsState.allPermissionsGranted) {
            AndroidView(
                factory = { ctx ->
                    // Erstellen der Ansicht und Zuweisen zur Referenz.
                    OpenGlView(ctx).also {
                        openGlView.value = it
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Steuerelemente (unverändert)
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            error?.let {
                Toast.makeText(context, "Error: $it", Toast.LENGTH_SHORT).show()
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(onClick = {
//                if (isStreaming) {
//                    streamingEngine.stopStreaming()
//                } else {
//                    streamingEngine.startStreaming(rtmpUrl)
//                }
            }) {
//                Text(if (isStreaming) "Stop Stream" else "Start Stream")
            }
            Spacer(modifier = Modifier.height(16.dp))
//            Button(onClick = { streamingEngine.switchCamera() }) {
                Text("Switch Camera")
            }
            Button(onClick = { navController.navigate("settings_route") }) {
                Text("Settings")
            }
        }

        // Berechtigungs-UI (unverändert)
        if (!permissionsState.allPermissionsGranted) {
            Column(
  //              modifier = Modifier.align(Alignment.Center),
            ) {
                Text("Camera and Microphone permissions are required to stream.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Request Permissions")
                }
            }
        }
    }
}
