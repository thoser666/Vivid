package com.vivid.feature.obscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // <-- WICHTIGER IMPORT für den 'by'-Delegaten
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.feature.obscontrol.ObsControlViewModel
import com.vivid.feature.obscontrol.ConnectionState // <-- WICHTIGER IMPORT für deinen UI-Zustand

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel()
) {
    // State für die Eingabefelder
    var ip by remember { mutableStateOf("192.168.1.100") } // Beispiel-IP
    var port by remember { mutableStateOf("4455") }
    var password by remember { mutableStateOf("") }

    // UI-Zustand aus dem ViewModel abonnieren
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // UI basierend auf dem ConnectionState rendern
        when (val state = uiState) {
            is ConnectionState.Connected -> {
                Text("Connected to OBS!")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) {
                    Text("Disconnect")
                }
            }
            is ConnectionState.Connecting -> {
                CircularProgressIndicator()
                Text("Connecting...")
            }
            is ConnectionState.Disconnected -> {
                Text("Enter OBS Connection Details")
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = ip, onValueChange = { ip = it }, label = { Text("IP Address") })
                TextField(value = port, onValueChange = { port = it }, label = { Text("Port") })
                TextField(value = password, onValueChange = { password = it }, label = { Text("Password") })
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.connect(password, ip, port) }) {
                    Text("Connect")
                }
            }
            is ConnectionState.Error -> {
                Text("Error: ${state.message}")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) { // Reset-Möglichkeit
                    Text("Retry")
                }
            }
        }
    }
}