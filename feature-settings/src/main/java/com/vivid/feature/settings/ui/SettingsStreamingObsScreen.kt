package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
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
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.cat_streaming_title),
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Stream-Einstellungen
        Text(stringResource(R.string.streaming_section_title), style = MaterialTheme.typography.titleLarge)

        // Plattform-Vorlagen: füllen die Ingest-URL und aktivieren RTMPS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StreamPlatform.entries.forEach { platform ->
                FilterChip(
                    selected = uiState.streamUrl == platform.ingestUrl,
                    onClick = { viewModel.applyPlatformPreset(platform) },
                    label = { Text(stringResource(platform.labelRes)) },
                )
            }
        }

        OutlinedTextField(
            value = uiState.streamUrl,
            onValueChange = viewModel::onStreamUrlChange,
            label = { Text(stringResource(R.string.stream_url_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(STREAM_URL_HINT_RES),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.streamKey,
            onValueChange = viewModel::onStreamKeyChange,
            label = { Text(stringResource(R.string.stream_key_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.stream_tls_label), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.streamUseTls,
                onCheckedChange = viewModel::onStreamUseTlsChange,
            )
        }

        // Multi-Streaming: optionales zweites Ziel (parallel zum primären)
        Text(stringResource(R.string.multi_streaming_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.multi_streaming_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.secondaryStreamUrl,
            onValueChange = viewModel::onSecondaryStreamUrlChange,
            label = { Text(stringResource(R.string.secondary_url_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.secondaryStreamKey,
            onValueChange = viewModel::onSecondaryStreamKeyChange,
            label = { Text(stringResource(R.string.secondary_key_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.stream_tls_label), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.secondaryStreamUseTls,
                onCheckedChange = viewModel::onSecondaryStreamUseTlsChange,
            )
        }

        // OBS-Einstellungen
        Text(stringResource(R.string.obs_section_title), style = MaterialTheme.typography.titleLarge)
        OutlinedTextField(
            value = uiState.obsHost,
            onValueChange = viewModel::onObsHostChange,
            label = { Text(stringResource(R.string.obs_host_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.obsPort,
            onValueChange = viewModel::onObsPortChange,
            label = { Text(stringResource(R.string.obs_port_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.obsPassword,
            onValueChange = viewModel::onObsPasswordChange,
            label = { Text(stringResource(R.string.obs_password_label)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.obs_tls_label), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.obsUseTls,
                onCheckedChange = viewModel::onObsUseTlsChange,
            )
        }
    }
}
