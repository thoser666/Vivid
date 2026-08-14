package com.vivid.feature.settings.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vivid.core.update.UpdateCheckResult
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController, // Nicht mehr optional, da wir ihn brauchen
    installedVersionName: String = "", // aus der Nav-Route (App-BuildConfig), für den Update-Indikator
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val remoteControl by viewModel.remoteControl.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ab Android 17 (API 37, targetSdk 37) braucht die Web-Remote-Control die
    // ACCESS_LOCAL_NETWORK-Runtime-Berechtigung. Wird sie hier erteilt, wird
    // der LAN-Server neu gestartet, damit er sie übernimmt.
    val context = LocalContext.current
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.restartRemoteControlServer()
        }
    }
    val needsLocalNetworkPermission =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) != PackageManager.PERMISSION_GRANTED

    // Einmaliger Update-Check beim Öffnen der Einstellungen (für den Obtainium-Test).
    LaunchedEffect(key1 = installedVersionName) {
        viewModel.checkForUpdates(installedVersionName)
    }

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

            // Plattform-Vorlagen: füllen die Ingest-URL und aktivieren RTMPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreamPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = uiState.streamUrl == platform.ingestUrl,
                        onClick = { viewModel.applyPlatformPreset(platform) },
                        label = { Text(platform.label) },
                    )
                }
            }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (RTMPS)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.streamUseTls,
                    onCheckedChange = viewModel::onStreamUseTlsChange,
                )
            }

            // Multi-Streaming: optionales zweites Ziel (parallel zum primären)
            Text("Multi-Streaming (optional)", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Zweites Stream-Ziel: Das Signal wird gleichzeitig zu beiden Zielen gesendet. Leer lassen, um nur das primäre Ziel zu nutzen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.secondaryStreamUrl,
                onValueChange = viewModel::onSecondaryStreamUrlChange,
                label = { Text("Zweite Stream-URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.secondaryStreamKey,
                onValueChange = viewModel::onSecondaryStreamKeyChange,
                label = { Text("Zweiter Stream-Schlüssel") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (RTMPS)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.secondaryStreamUseTls,
                    onCheckedChange = viewModel::onSecondaryStreamUseTlsChange,
                )
            }

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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (wss://)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.obsUseTls,
                    onCheckedChange = viewModel::onObsUseTlsChange,
                )
            }

            // Chat-Overlay über der Streaming-Vorschau
            Text("Chat-Overlay", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Zeigt den Twitch-Chat des angegebenen Kanals über der Streaming-Vorschau an (anonym gelesen).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.chatChannel,
                onValueChange = viewModel::onChatChannelChange,
                label = { Text("Twitch-Kanal (ohne #)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chat-Overlay anzeigen", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.chatOverlayEnabled,
                    onCheckedChange = viewModel::onChatOverlayEnabledChange,
                )
            }

            // Web-Remote-Control: Zugangsdaten für den LAN-Server
            Text("Web-Remote-Control", style = MaterialTheme.typography.titleLarge)
            if (remoteControl.token.isNotBlank()) {
                Text(
                    text = "Im gleichen WLAN erreichbar unter http://<GERÄTE-IP>:${remoteControl.port}/status — Aktionen via Authorization: Bearer <token>.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = remoteControl.token,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Remote-Token") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "Token wird geladen…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (needsLocalNetworkPermission) {
                OutlinedButton(
                    onClick = { localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("LAN-Zugriff für Remote-Control erlauben")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version + Update-Status — direkt sichtbar, ohne in den About-Screen zu gehen
            if (installedVersionName.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Version $installedVersionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val result = updateState.result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Text(
                                    text = "⬆ Update verfügbar: ${result.latestVersion}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Text(
                                text = "· aktuell",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        else -> Unit // Checking / Fehler: still bleiben
                    }
                }
            }

            OutlinedButton(
                onClick = { navController.navigate("about_route") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Über Vivid & Updates")
            }

            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }
}
