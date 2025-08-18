package com.vivid.feature.streaming

import android.Manifest
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.vivid.core.R

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StreamingScreen(
    navController: NavController,
    viewModel: StreamingViewModel = hiltViewModel(), // ViewModel per Hilt injizieren
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    // Kameravorschau und -auswahl aus dem ViewModel holen
    val preview by viewModel.preview.collectAsState()
    val cameraSelector by viewModel.cameraSelector.collectAsState()

    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
        onPermissionsResult = {
            if (it.values.all { isGranted -> isGranted }) {
                // Berechtigungen wurden erteilt, starte die Kamera
                viewModel.startCamera(context, lifecycleOwner)
            }
        },
    )

    // Wenn sich die Kameraauswahl ändert, starte die Kamera neu
    LaunchedEffect(cameraSelector) {
        viewModel.startCamera(context, lifecycleOwner)
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.streaming_screen_title)) })
        },
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            when {
                permissionsState.allPermissionsGranted -> {
                    // Wenn die Vorschau bereit ist, zeige sie an
                    preview?.let {
                        CameraPreview(
                            modifier = Modifier.fillMaxSize(),
                            preview = it,
                        )
                    }

                    // Steuerungselemente über der Vorschau
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Button(onClick = { /* TODO: viewModel.startStopStream() */ }) {
                            Text(stringResource(R.string.start_stream_button))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { viewModel.switchCamera() }) {
                            Text("Switch Camera") // TODO: Ressource verwenden
                        }
                    }
                }
                permissionsState.shouldShowRationale -> {
                    // UI, um Berechtigungen anzufordern
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.permissions_rationale))
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                            Text(stringResource(R.string.grant_permissions_button))
                        }
                    }
                }
                else -> {
                    // UI für permanent verweigerte Berechtigungen
                    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = stringResource(R.string.permissions_denied_permanently))
                    }
                }
            }

            // Starte die Berechtigungsanfrage und die Kamera beim ersten Laden
            LaunchedEffect(Unit) {
                if (!permissionsState.allPermissionsGranted) {
                    permissionsState.launchMultiplePermissionRequest()
                } else {
                    viewModel.startCamera(context, lifecycleOwner)
                }
            }
        }
    }
}
