package com.vivid.feature.streaming

package com.vivid.feature.streaming

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.vivid.core.R // Assuming core module will have common strings like permissions

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun StreamingScreen(navController: NavController) {
    val context = LocalContext.current

    // Request CAMERA and RECORD_AUDIO permissions
    val permissionsState = rememberMultiplePermissionsState(
        permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
    )

    Scaffold(
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.streaming_screen_title)) })
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            when {
                permissionsState.allPermissionsGranted -> {
                    Text(text = "Permissions granted! Camera preview will go here.")
                    Spacer(modifier = Modifier.height(16.dp))
                    // TODO: Implement CameraX preview and stream control here
                    Button(onClick = { /* Start stream logic */ }) {
                        Text(stringResource(R.string.start_stream_button))
                    }
                }
                permissionsState.shouldShowRationale -> {
                    Text(text = stringResource(R.string.permissions_rationale))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { permissionsState.launchMultiplePermissionRequest() }) {
                        Text(stringResource(R.string.grant_permissions_button))
                    }
                }
                !permissionsState.allPermissionsGranted && !permissionsState.shouldShowRationale -> {
                    Text(text = stringResource(R.string.permissions_denied_permanently))
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { /* Navigate to app settings or show dialog */ }) {
                        Text(stringResource(R.string.open_settings_button))
                    }
                }
            }
        }
    }
}