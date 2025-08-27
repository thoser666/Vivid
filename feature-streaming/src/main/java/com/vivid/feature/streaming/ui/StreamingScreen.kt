package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.pedro.library.view.OpenGlView // Der Import ist korrekt
import com.vivid.feature.streaming.StreamingViewModel

@Composable
fun StreamingScreen(
    navController: NavController,
    streamUrl: String? = null,
    viewModel: StreamingViewModel = hiltViewModel()
) {

    val isStreaming by viewModel.isStreaming.collectAsState()
    var rtmpUrl by remember { mutableStateOf("rtmp://a.rtmp.youtube.com/live2/") }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { context ->
                // Erstellen Sie die OpenGlView
                val openGlView = OpenGlView(context)
                // Übergeben Sie sie sofort an das ViewModel zur Initialisierung.
                // Das ist der entscheidende Verbindungsschritt.
                viewModel.initialize(openGlView)
                // Geben Sie die View zur Anzeige zurück
                openGlView
            },
            // Der 'update'-Block wird nicht benötigt, da die View von der 
            // 'factory' nur einmal erstellt und verbunden wird.
        )

        // Der Rest des UI-Codes ist korrekt und bleibt unverändert.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = rtmpUrl,
                onValueChange = { rtmpUrl = it },
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
                Button(
                    onClick = {
                        if (isStreaming) {
                            viewModel.stopStream()
                        } else {
                            viewModel.startStream(rtmpUrl)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isStreaming) "Stop Streaming" else "Start Streaming")
                }
                Spacer(modifier = Modifier.weight(0.1f))
                Button(
                    onClick = { viewModel.switchCamera() },
                    modifier = Modifier.weight(1f),
                    enabled = !isStreaming
                ) {
                    Text("Switch Camera")
                }
            }
        }
    }
}