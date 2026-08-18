package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AppSettings

/**
 * Kategorie „Streaming & OBS“: Stream-URL/-Key (inkl. Plattform-Vorlagen),
 * Multi-Streaming (zweites Ziel) und OBS-WebSocket-Verbindung.
 */
@Composable
fun SettingsStreamingObsScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    SettingsSectionScaffold(
        title = "Streaming & OBS",
        onBack = onBack,
        onSave = viewModel::saveSettings,
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
    }
}
