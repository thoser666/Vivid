package com.vivid.features.obs.control


import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.foundation.text.KeyboardOptions // Make sure this is imported if you use it


import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // Dieser Code ist und war immer korrekt.
        // Der Fehler lag ausschließlich in den Imports oben.
        OutlinedTextField(
            value = uiState.host,
            onValueChange = viewModel::onHostChanged,
            label = { Text("OBS Host") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving
        )

        // For the Port TextField
        OutlinedTextField(
            value = uiState.port, // Convert to String
            onValueChange = { stringValue ->
                viewModel.onPortChanged(stringValue) // ViewModel handles conversion
            },
            label = { Text("OBS Port") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number) // Good addition for port
        )

// For the Password TextField (assuming uiState.password might not be a String)
        OutlinedTextField(
            value = uiState.password, // Ensure it's a String
            onValueChange = viewModel::onPasswordChanged,
            label = { Text("OBS Password") },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving
        )
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = viewModel::saveObsSettings,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text("Save")
            }
        }
    }
}