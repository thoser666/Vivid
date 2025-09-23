package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.feature.streaming.data.repository.ObsControlViewModel

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel(),
) {
    // Abonniere die StateFlows aus dem OBSWebSocketClient
    val connectionState by viewModel.obsClient.connectionState.collectAsStateWithLifecycle()
    val streamState by viewModel.obsClient.streamState.collectAsStateWithLifecycle()
    val errorState by viewModel.obsClient.errorState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Verbindungsstatus anzeigen
        Text("OBS Connection: ${connectionState.name}")
        Spacer(Modifier.height(8.dp))
        Text("OBS Stream: ${streamState.name}")

        Spacer(Modifier.height(24.dp))

        // Verbindungs-Button
        Button(
            onClick = {
                if (connectionState == OBSWebSocketClient.ConnectionState.DISCONNECTED) {
                    viewModel.connectToObs()
                } else {
                    viewModel.disconnectFromObs()
                }
            },
            enabled = connectionState != OBSWebSocketClient.ConnectionState.CONNECTING,
        ) {
            when (connectionState) {
                OBSWebSocketClient.ConnectionState.CONNECTING -> CircularProgressIndicator()
                OBSWebSocketClient.ConnectionState.DISCONNECTED, OBSWebSocketClient.ConnectionState.ERROR -> Text("Connect to OBS")
                OBSWebSocketClient.ConnectionState.CONNECTED -> Text("Disconnect from OBS")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Stream-Button
        Button(
            onClick = { viewModel.toggleObsStream() },
            enabled = connectionState == OBSWebSocketClient.ConnectionState.CONNECTED &&
                streamState != OBSWebSocketClient.StreamState.STARTING &&
                streamState != OBSWebSocketClient.StreamState.STOPPING,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (streamState == OBSWebSocketClient.StreamState.STREAMING) Color.Red else Color.Green,
            ),
        ) {
            val streamButtonText = when (streamState) {
                OBSWebSocketClient.StreamState.STOPPED -> "Start OBS Stream"
                OBSWebSocketClient.StreamState.STARTING -> "Starting..."
                OBSWebSocketClient.StreamState.STREAMING -> "Stop OBS Stream"
                OBSWebSocketClient.StreamState.STOPPING -> "Stopping..."
            }
            Text(streamButtonText)
        }

        // Fehleranzeige
        errorState?.let {
            Spacer(Modifier.height(16.dp))
            Text("Error: $it", color = Color.Red)
        }
    }
}
