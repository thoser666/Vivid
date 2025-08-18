package com.vivid.feature.playback

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController

@Composable
fun PlaybackScreen(
    navController: NavController,
    streamUrl: String? = null,
    viewModel: PlaybackViewModel = hiltViewModel()
) {
    val currentStreamUrl by viewModel.currentStreamUrl.collectAsState()

    LaunchedEffect(streamUrl) {
        streamUrl?.let { viewModel.setStreamUrl(it) }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Stream Player
        currentStreamUrl?.let { url ->
            StreamPlayer(
                streamUrl = url,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            )
        }

        // Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { /* Previous stream */ }) {
                Text("Previous")
            }
            Button(onClick = { viewModel.togglePlayback() }) {
                Text("Play/Pause")
            }
            Button(onClick = { /* Next stream */ }) {
                Text("Next")
            }
        }
    }
}