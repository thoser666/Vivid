package com.vivid.feature.streaming.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
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
import com.pedro.library.view.OpenGlView
import com.vivid.feature.streaming.StreamingViewModel

@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(),
) {
    // Beobachtet den isStreaming-StateFlow und zeichnet die UI bei Änderungen neu.
    val isStreaming by viewModel.isStreaming.collectAsStateWithLifecycle()

    Box(modifier = Modifier.fillMaxSize()) {
        // Die Kamera-Vorschau wird als Hintergrund angezeigt
        CameraScreen()

        // Zeigt einen "LIVE"-Indikator an, wenn der Stream aktiv ist
        if (isStreaming) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp)
                    .background(Color.Red, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White
                )
            }
        }

        // Der Start/Stopp-Button am unteren Rand
        Button(
            onClick = { viewModel.toggleStreaming() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(32.dp)
        ) {
            Text(text = if (isStreaming) "Stream stoppen" else "Stream starten")
        }
    }
}