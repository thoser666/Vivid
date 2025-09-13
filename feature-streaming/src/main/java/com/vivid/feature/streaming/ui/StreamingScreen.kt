package com.vivid.feature.streaming.ui

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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

    // Schritt 1: ViewModel und StreamingEngine wieder aktivieren
    val streamingEngine = viewModel.streamingEngine
    val isStreaming by streamingEngine.isStreaming.collectAsState()
    val error by streamingEngine.streamingError.collectAsState()

    // Die URL aus den Einstellungen holen (Beispiel, wird später aus ViewModel kommen)
    val rtmpUrl by viewModel.rtmpUrl.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
    )

    val openGlView = remember { mutableStateOf<OpenGlView?>(null) }

    // LaunchedEffect zum Initialisieren, wenn Berechtigungen erteilt werden
    LaunchedEffect(permissionsState.allPermissionsGranted, openGlView.value) {
        if (permissionsState.allPermissionsGranted && openGlView.value != null) {
            // Schritt 2: Kamera-Initialisierung wieder aktivieren
            streamingEngine.initializeCamera(openGlView.value!!)
            streamingEngine.setVideoSettings(1280, 720, 2500 * 1024, 30, 90)
            streamingEngine.setAudioSettings(44100, true, 128 * 1024)
            streamingEngine.startPreview()
        }
    }

    // Lebenszyklus-Management zur korrekten Steuerung der Vorschau
    DisposableEffect(lifecycleOwner, permissionsState.allPermissionsGranted) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    // Schritt 3: Lebenszyklus-Management wieder aktivieren
                    if (streamingEngine.isStreaming.value) {
                        streamingEngine.stopStreaming()
                    }
                    streamingEngine.stopPreview()
                }
                Lifecycle.Event.ON_RESUME -> {
                    // Vorschau nur neu starten, wenn Berechtigungen noch erteilt sind
                    if (permissionsState.allPermissionsGranted) {
                        streamingEngine.startPreview()
                    }
                }
                // Bei ON_DESTROY sollte die Engine die Ressourcen freigeben.
                // Dies wird normalerweise im onCleared des ViewModels gehandhabt.
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (permissionsState.allPermissionsGranted) {
            // Die AndroidView, die unsere Kamera-Vorschau anzeigt
            AndroidView(
                factory = { ctx ->
                    OpenGlView(ctx).also { view ->
                        openGlView.value = view
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Steuerelemente
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Schritt 4: Buttons wieder aktivieren
            Button(
                onClick = {
                    if (isStreaming) {
                        streamingEngine.stopStreaming()
                    } else {
                        // Stellen Sie sicher, dass die URL nicht leer ist
                        if (rtmpUrl.isNotBlank()) {
                            streamingEngine.startStreaming(rtmpUrl)
                        }
                    }
                },
                // Deaktivieren Sie den Button, wenn keine URL vorhanden ist und nicht gestreamt wird
                enabled = rtmpUrl.isNotBlank() || isStreaming
            ) {
                if (isStreaming) {
                    Text("Stop Stream")
                } else {
                    Text("Start Stream")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { streamingEngine.switchCamera() }) {
                Text("Switch Camera")
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = { navController.navigate("settings_route") }) {
                Text("Settings")
            }

            // Fehlermeldung anzeigen, falls vorhanden
            error?.let {
                Text(
                    text = "Error: $it",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }

        // Berechtigungs-UI
        if (!permissionsState.allPermissionsGranted) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Camera and Microphone permissions are required.")
                Spacer(modifier = Modifier.height(8.dp))
                Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                    Text("Request Permissions")
                }
            }
        }
    }
}