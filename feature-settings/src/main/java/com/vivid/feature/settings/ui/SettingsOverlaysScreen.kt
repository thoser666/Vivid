package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.vivid.core.data.AppSettings

/**
 * Kategorie „Overlays & Widgets“: Twitch-Chat-Overlay über der Vorschau und
 * das Text-/Info-Widget (Uhrzeit/GPS/Geschwindigkeit).
 */
@Composable
fun SettingsOverlaysScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
) {
    SettingsSectionScaffold(
        title = stringResource(R.string.cat_overlays_title),
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Chat-Overlay über der Streaming-Vorschau
        Text(stringResource(R.string.overlays_chat_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_chat_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatChannel,
            onValueChange = viewModel::onChatChannelChange,
            label = { Text(stringResource(R.string.overlays_channel_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_chat_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatOverlayEnabled,
                onCheckedChange = viewModel::onChatOverlayEnabledChange,
            )
        }

        // Text-/Info-Widget über der Streaming-Vorschau (Uhrzeit/GPS/Geschwindigkeit)
        Text(stringResource(R.string.overlays_widget_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_widget_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetEnabled,
                onCheckedChange = viewModel::onWidgetEnabledChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_time), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowTime,
                onCheckedChange = viewModel::onWidgetShowTimeChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_location), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowLocation,
                onCheckedChange = viewModel::onWidgetShowLocationChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_speed), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowSpeed,
                onCheckedChange = viewModel::onWidgetShowSpeedChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_altitude), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowAltitude,
                onCheckedChange = viewModel::onWidgetShowAltitudeChange,
            )
        }
    }
}
