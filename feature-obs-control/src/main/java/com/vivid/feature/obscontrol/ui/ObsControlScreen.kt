package com.vivid.feature.obscontrol.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue // <-- WICHTIGER IMPORT für den 'by'-Delegaten
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.feature.obscontrol.ConnectionState // <-- WICHTIGER IMPORT für deinen UI-Zustand
import com.vivid.feature.obscontrol.ObsControlViewModel
import com.vivid.feature.obscontrol.R
import java.util.Locale

@Composable
fun ObsControlScreen(
    viewModel: ObsControlViewModel = hiltViewModel(),
) {
    // State für die Eingabefelder
    var ip by remember { mutableStateOf("192.168.1.100") } // Beispiel-IP
    var port by remember { mutableStateOf("4455") }
    var password by remember { mutableStateOf("") }
    // UI-Zustand aus dem ViewModel abonnieren
    val uiState by viewModel.uiState.collectAsState()
    val savedUseTls by viewModel.savedUseTls.collectAsState()
    val currentProgramScene by viewModel.currentProgramScene.collectAsState()
    val inputs by viewModel.inputs.collectAsState()
    val muteStates by viewModel.muteStates.collectAsState()
    val audioLevels by viewModel.audioLevels.collectAsState()
    val syncOffsets by viewModel.syncOffsets.collectAsState()
    val snapshot by viewModel.snapshot.collectAsState()
    val blackoutActive by viewModel.blackoutActive.collectAsState()
    val blackoutPreparing by viewModel.blackoutPreparing.collectAsState()

    // false = ws:// (Standard-OBS-LAN), true = wss:// (Remote mit TLS)
    // Startet mit dem gespeicherten Wert aus den Einstellungen.
    var useTls by remember { mutableStateOf(false) }
    LaunchedEffect(savedUseTls) { useTls = savedUseTls }

    // Kein Scaffold: Root paddet selbst auf die System-Bars (Edge-to-Edge).
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // UI basierend auf dem ConnectionState rendern
        when (val state = uiState) {
            is ConnectionState.Connected -> {
                ObsConnectedControls(
                    viewModel = viewModel,
                    currentProgramScene = currentProgramScene,
                    inputs = inputs,
                    muteStates = muteStates,
                    audioLevels = audioLevels,
                    syncOffsets = syncOffsets,
                    snapshot = snapshot,
                    blackoutActive = blackoutActive,
                    blackoutPreparing = blackoutPreparing,
                )
            }
            is ConnectionState.Connecting -> {
                CircularProgressIndicator()
                Text(stringResource(R.string.obs_connecting))
            }
            is ConnectionState.Disconnected -> {
                Text(stringResource(R.string.obs_enter_details))
                Spacer(modifier = Modifier.height(8.dp))
                TextField(value = ip, onValueChange = { ip = it }, label = { Text(stringResource(R.string.obs_ip_label)) })
                TextField(value = port, onValueChange = { port = it }, label = { Text(stringResource(R.string.obs_port_label)) })
                TextField(value = password, onValueChange = { password = it }, label = { Text(stringResource(R.string.obs_password_label)) })
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(
                            if (useTls) R.string.obs_secure_connection else R.string.obs_plain_connection,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = useTls, onCheckedChange = { useTls = it })
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.connect(password, ip, port, useTls) }) {
                    Text(stringResource(R.string.obs_connect))
                }
            }
            is ConnectionState.Error -> {
                val errorText = if (state.messageRes != 0) {
                    stringResource(state.messageRes)
                } else {
                    state.message
                }
                Text(stringResource(R.string.obs_error_prefix, errorText))
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.disconnect() }) { // Reset-Möglichkeit
                    Text(stringResource(R.string.obs_retry))
                }
            }
        }
    }
}

@Composable
private fun ObsConnectedControls(
    viewModel: ObsControlViewModel,
    currentProgramScene: String?,
    inputs: List<String>,
    muteStates: Map<String, Boolean>,
    audioLevels: Map<String, Float>,
    syncOffsets: Map<String, Long>,
    snapshot: ByteArray?,
    blackoutActive: Boolean,
    blackoutPreparing: Boolean,
) {
    Text(stringResource(R.string.obs_connected))
    Spacer(modifier = Modifier.height(8.dp))

    currentProgramScene?.let {
        Text(stringResource(R.string.obs_current_scene, it))
        Spacer(modifier = Modifier.height(8.dp))
    }

    // Screen-Black (PARITY Row 71)
    val blackoutLabel = when {
        blackoutActive -> R.string.obs_blackout_restore
        blackoutPreparing -> R.string.obs_blackout_preparing
        else -> R.string.obs_blackout_button
    }
    Button(
        onClick = { viewModel.toggleBlackout() },
        enabled = !blackoutPreparing,
    ) {
        Text(stringResource(blackoutLabel))
    }
    Spacer(modifier = Modifier.height(8.dp))

    // Snapshot (PARITY Row 71)
    Text(stringResource(R.string.obs_snapshot_section))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { viewModel.takeScreenshot() }) {
            Text(stringResource(R.string.obs_snapshot_button))
        }
        Spacer(modifier = Modifier.width(12.dp))
        snapshot?.let { bytes ->
            val bitmap = remember(bytes) { BitmapFactory.decodeByteArray(bytes, 0, bytes.size) }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.obs_snapshot_section),
                    modifier = Modifier
                        .weight(1f)
                        .height(80.dp),
                    contentScale = ContentScale.Fit,
                )
            }
        }
    }

    // Audio-Inputs: Levels / Sync / Mute (PARITY Row 71)
    if (inputs.isNotEmpty()) {
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
        Text(stringResource(R.string.obs_inputs_section))
        inputs.forEach { inputName ->
            ObsInputRow(
                inputName = inputName,
                levelDb = audioLevels[inputName],
                syncOffsetNs = syncOffsets[inputName],
                muted = muteStates[inputName] ?: false,
                onToggleMute = { viewModel.toggleMute(inputName) },
                onShiftSync = { direction -> viewModel.adjustSyncOffset(inputName, direction) },
            )
        }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Button(onClick = { viewModel.disconnect() }) {
        Text(stringResource(R.string.obs_disconnect))
    }
}

@Composable
private fun ObsInputRow(
    inputName: String,
    levelDb: Float?,
    syncOffsetNs: Long?,
    muted: Boolean,
    onToggleMute: () -> Unit,
    onShiftSync: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(inputName)
            val levelText = levelDb?.let {
                String.format(Locale.ROOT, "%.1f dB", it)
            } ?: "— dB"
            Text(levelText)
        }

        // Audio-Sync verschieben (± 50 ms)
        IconButton(onClick = { onShiftSync(-1) }, enabled = syncOffsetNs != null) {
            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.obs_sync_delta_help))
        }
        Text(
            syncOffsetNs?.let { String.format(Locale.ROOT, "%.0f ms", it / 1_000_000.0) } ?: "—",
        )
        IconButton(onClick = { onShiftSync(1) }, enabled = syncOffsetNs != null) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.obs_sync_delta_help))
        }

        // Mute/Unmute
        Switch(checked = muted, onCheckedChange = { onToggleMute() })
    }
}