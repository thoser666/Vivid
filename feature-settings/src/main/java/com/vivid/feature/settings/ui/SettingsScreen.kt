package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController, // Nicht mehr optional, da wir ihn brauchen
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Lauscht auf das saveEvent vom ViewModel
    LaunchedEffect(key1 = Unit) {
        viewModel.saveEvent.collectLatest {
            // Zeige eine Bestätigung an
            scope.launch {
                snackbarHostState.showSnackbar("Einstellungen gespeichert")
            }
            // Navigiere zurück zum vorherigen Bildschirm
            navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues) // Verwende paddingValues vom Scaffold
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stream-Einstellungen
            Text("Stream-Einstellungen", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = uiState.streamUrl,
                onValueChange = viewModel::onStreamUrlChange,
                label = { Text("Stream-URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.streamKey,
                onValueChange = viewModel::onStreamKeyChange,
                label = { Text("Stream-Schlüssel") },
                modifier = Modifier.fillMaxWidth(),
            )

            // OBS-Einstellungen
            Text("OBS-Einstellungen", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = uiState.obsHost,
                onValueChange = viewModel::onObsHostChange,
                label = { Text("OBS Host") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.obsPort,
                onValueChange = viewModel::onObsPortChange,
                label = { Text("OBS Port") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.obsPassword,
                onValueChange = viewModel::onObsPasswordChange,
                label = { Text("OBS Passwort") },
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }
}
