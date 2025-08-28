package com.vivid.feature.streaming.ui

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingViewModel

@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel()
) {
    val context = LocalContext.current

    // 1. Liste der benötigten Berechtigungen definieren.
    val permissionsToRequest = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO
    )

    // 2. Ein State, der speichert, ob die Berechtigungen erteilt wurden.
    var hasPermissions by remember {
        mutableStateOf(hasPermissions(context, permissionsToRequest))
    }

    // 3. Der ActivityResultLauncher, der den Berechtigungsdialog startet.
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions ->
            // Prüfen, ob ALLE angeforderten Berechtigungen erteilt wurden.
            hasPermissions = permissions.values.all { it }
        }
    )

    // 4. Die UI, die basierend auf dem `hasPermissions`-Status wechselt.
    if (hasPermissions) {
        StreamingContent(viewModel = viewModel)
    } else {
        // Starten Sie die Berechtigungsanforderung, sobald der Erklärungsbildschirm angezeigt wird.
        // `LaunchedEffect` stellt sicher, dass dies nur einmal passiert.
        LaunchedEffect(Unit) {
            permissionLauncher.launch(permissionsToRequest)
        }
        // Optional können Sie auch einen Button anzeigen, falls der Benutzer ablehnt.
        PermissionsRequiredScreen(
            onPermissionsRequested = {
                permissionLauncher.launch(permissionsToRequest)
            }
        )
    }
}

// Hilfsfunktion, um zu prüfen, ob die Berechtigungen bereits erteilt sind.
private fun hasPermissions(context: Context, permissions: Array<String>): Boolean =
    permissions.all {
        ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
    }


@Composable
private fun StreamingContent(viewModel: StreamingViewModel) {
    val isStreaming by viewModel.isStreaming.collectAsState()
    // NEU: Fehlerstatus vom ViewModel abrufen
    val streamingError by viewModel.streamingError.collectAsState()
    var rtmpUrl by remember { mutableStateOf("rtmp://a.rtmp.youtube.com/live2/") }
    // NEU: Wir erstellen eine Referenz für unsere OpenGlView
    val openGlView = remember { mutableStateOf<OpenGlView?>(null) }
    // NEU: LaunchedEffect startet die Initialisierung, wenn die View bereit ist
    LaunchedEffect(openGlView.value) {
        openGlView.value?.let { view ->
            viewModel.initialize(view)
        }
    }
    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // Erstellen Sie die View und weisen Sie sie unserer Referenz zu
                OpenGlView(context).also {
                    openGlView.value = it
                }
            }
        )
        // UI-Steuerelemente (jetzt in einer separaten Funktion für die Übersicht)
        StreamingControls(
            isStreaming = isStreaming,
            rtmpUrl = rtmpUrl,
            onRtmpUrlChange = { rtmpUrl = it },
            onStartStopClick = {
                if (isStreaming) viewModel.stopStream() else viewModel.startStream(rtmpUrl)
            },
            onSwitchCameraClick = { viewModel.switchCamera() }
        )
        // NEU: Zeigt eine Fehlerüberlagerung an, wenn ein Fehler auftritt
        streamingError?.let { error ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Ein Fehler ist aufgetreten:\n$error",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// NEU: Ausgelagerte UI-Steuerelemente
@Composable
fun BoxScope.StreamingControls(
    isStreaming: Boolean,
    rtmpUrl: String,
    onRtmpUrlChange: (String) -> Unit,
    onStartStopClick: () -> Unit,
    onSwitchCameraClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .align(Alignment.BottomCenter)
            .padding(16.dp)
    ) {
        OutlinedTextField(
            value = rtmpUrl,
            onValueChange = onRtmpUrlChange,
            label = { Text("RTMP Stream URL") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            enabled = !isStreaming
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Button(onClick = onStartStopClick, modifier = Modifier.weight(1f)) {
                Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
            }
            Spacer(modifier = Modifier.weight(0.1f))
            Button(onClick = onSwitchCameraClick, modifier = Modifier.weight(1f), enabled = !isStreaming) {
                Text("Switch Camera")
            }
        }
    }
}


@Composable
private fun PermissionsRequiredScreen(onPermissionsRequested: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "Kamera- und Mikrofonberechtigung erforderlich",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Um live streamen zu können, benötigt diese App Zugriff auf Ihre Kamera und Ihr Mikrofon.",
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onPermissionsRequested) {
                Text("Berechtigungen erneut anfordern")
            }
        }
    }
}