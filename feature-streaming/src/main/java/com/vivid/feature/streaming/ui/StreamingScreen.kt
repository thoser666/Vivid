package com.vivid.feature.streaming.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import androidx.navigation.NavController
import com.vivid.data.model.StreamingState
import com.vivid.feature.streaming.data.repository.StreamingViewModel

@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(),
) {
    val streamingState by viewModel.streamingState.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Die Kamera-Vorschau als Hintergrund
        CameraScreen()

        // "LIVE"-Indikator, sichtbar nur im Streaming-Zustand
        AnimatedVisibility(
            visible = streamingState is StreamingState.Streaming,
            modifier = Modifier.align(Alignment.TopStart),
        ) {
            Box(
                modifier = Modifier
                    .padding(16.dp)
                    .background(Color.Red, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Text(text = "LIVE", color = Color.White)
            }
        }

        // Start/Stopp-Button und Ladeanzeige
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp),
        ) {
            Button(
                onClick = { viewModel.toggleStreaming() },
                // Deaktiviere den Button, während eine Zustandsänderung läuft
                enabled = streamingState is StreamingState.Idle || streamingState is StreamingState.Streaming,
            ) {
                Text(
                    text = when (streamingState) {
                        is StreamingState.Streaming -> "Stream stoppen"
                        else -> "Stream starten"
                    },
                )
            }
            // Zeige einen Ladeindikator, wenn verbunden oder getrennt wird
            if (streamingState is StreamingState.Connecting || streamingState is StreamingState.Disconnecting) {
                CircularProgressIndicator(modifier = Modifier.size(48.dp))
            }
        }
    }
}
