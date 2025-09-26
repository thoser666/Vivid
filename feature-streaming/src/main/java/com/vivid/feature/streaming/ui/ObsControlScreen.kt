package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.features.obs.control.ObsControlUiState
import com.vivid.features.obs.control.ObsControlViewModel

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel()
) {
    // Wenn die Imports korrekt sind, funktioniert diese Zeile garantiert.
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            when (val state = uiState) {
                is ObsControlUiState.Idle -> {
                    Button(onClick = viewModel::connectToObs) {
                        Text("Mit OBS verbinden")
                    }
                }
                is ObsControlUiState.Connecting -> {
                    CircularProgressIndicator()
                    Text("Verbinde...")
                }
                is ObsControlUiState.Connected -> {
                    Text("Erfolgreich verbunden!", color = MaterialTheme.colorScheme.primary)
                }
                is ObsControlUiState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = viewModel::dismissError) {
                        Text("Erneut versuchen")
                    }
                }
            }
        }
    }
}