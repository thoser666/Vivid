package com.vivid.features.obs.control.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

import com.vivid.features.obs.control.ObsControlViewModel

import com.vivid.features.obs.control.ObsControlUiState

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel()
) {
    // Diese Zeile verbindet die UI mit dem ViewModel-Zustand.
    val uiState by viewModel.uiState.collectAsState()

    Box(/*...*/) {
        Column(/*...*/) {
            when (val state = uiState) {
                is ObsControlUiState.Idle -> {
                    // Ruft die ViewModel-Funktion auf
                    Button(onClick = viewModel::connectToObs) {
                        Text("Mit OBS verbinden")
                    }
                }
                is ObsControlUiState.Connecting -> { /*...*/ }
                is ObsControlUiState.Connected -> { /*...*/ }
                is ObsControlUiState.Error -> {
                    // Ruft die ViewModel-Funktion auf
                    Button(onClick = viewModel::dismissError) {
                        Text("Erneut versuchen")
                    }
                }
            }
        }
    }
}