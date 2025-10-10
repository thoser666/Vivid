package com.vivid.features.obs.control

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    // Collect the UI state from the ViewModel in a lifecycle-aware manner.
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // The OutlinedTextFields now get their labels from strings.xml.
        OutlinedTextField(
            value = uiState.host,
            onValueChange = viewModel::onHostChanged,
            label = { Text(stringResource(id = R.string.obs_settings_host_label)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving,
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.port,
            onValueChange = viewModel::onPortChanged,
            label = { Text(stringResource(id = R.string.obs_settings_port_label)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = viewModel::onPasswordChanged,
            label = { Text(stringResource(id = R.string.obs_settings_password_label)) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = uiState.isSaving,
            singleLine = true,
            // Hide password characters from the user.
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
        )

        // Spacer to push the save button to the bottom.
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = viewModel::saveObsSettings,
            modifier = Modifier.fillMaxWidth(),
            // The button is disabled while the save operation is in progress.
            enabled = !uiState.isSaving
        ) {
            if (uiState.isSaving) {
                // Show a progress indicator during the save operation.
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                Text(stringResource(id = R.string.obs_settings_save_button))
            }
        }
    }
}