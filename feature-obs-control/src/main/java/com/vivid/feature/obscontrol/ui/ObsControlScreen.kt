package com.vivid.feature.obscontrol.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // <-- WICHTIGER IMPORT für den 'by'-Delegaten
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.feature.obscontrol.ConnectionState // <-- WICHTIGER IMPORT für deinen UI-Zustand
import com.vivid.feature.obscontrol.ObsControlViewModel
import com.vivid.feature.obscontrol.R

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel(),
) {
    // State für die Eingabefelder
    var ip by remember { mutableStateOf("192.168.1.100") } // Beispiel-IP
    var port by remember { mutableStateOf("4455") }
    var password by remember { mutableStateOf("") }
    // UI-Zustand aus dem ViewModel abonnieren
    val uiState by viewModel.uiState.collectAsState()
    val savedUseTls by viewModel.savedUseTls.collectAsState()

    // false = ws:// (Standard-OBS-LAN), true = wss:// (Remote mit TLS)
    // Startet mit dem gespeicherten Wert aus den Einstellungen.
    var useTls by remember { mutableStateOf(false) }
    LaunchedEffect(savedUseTls) { useTls = savedUseTls }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // UI basierend auf dem ConnectionState rendern
        when (val state = uiState) {
            is ConnectionState.Connected -> {
                Text(stringResource(R.string.obs_connected))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) {
                    Text(stringResource(R.string.obs_disconnect))
                }
            }
            is ConnectionState.Connecting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.obs_connecting))
            }
            is ConnectionState.Disconnected -> {
                Text(stringResource(R.string.obs_enter_details))
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = ip, onValueChange = { ip = it }, label = { Text(stringResource(R.string.obs_ip_label)) })
                TextField(value = port, onValueChange = { port = it }, label = { Text(stringResource(R.string.obs_port_label)) })
                TextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.obs_password_label)) })
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (useTls) R.string.obs_secure_connection else R.string.obs_plain_connection,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = useTls, onCheckedChange = { useTls = it })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.connect(password, ip, port, useTls) }) {
                    Text(stringResource(R.string.obs_connect))
                }
            }
            is ConnectionState.Error -> {
                val errorText = if (state.messageRes != 0) {
                    stringResource(state.messageRes)
                } else {
                    state.message
                }
                Text(stringResource(R.string.obs_error_prefix, errorText))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) { // Reset-Möglichkeit
                    Text(stringResource(R.string.obs_retry))
                }
            }
        }
    }
}
