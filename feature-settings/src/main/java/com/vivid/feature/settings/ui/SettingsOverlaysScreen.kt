package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        title = "Overlays & Widgets",
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
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

        // Text-/Info-Widget über der Streaming-Vorschau (Uhrzeit/GPS/Geschwindigkeit)
        Text("Text-/Info-Widget", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Zeigt Uhrzeit, GPS-Koordinaten und Geschwindigkeit als Overlay rechts unten über der Streaming-Vorschau an. Für GPS/Geschwindigkeit wird die Standort-Berechtigung benötigt.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Widget anzeigen", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetEnabled,
                onCheckedChange = viewModel::onWidgetEnabledChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Uhrzeit", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowTime,
                onCheckedChange = viewModel::onWidgetShowTimeChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("GPS-Koordinaten", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowLocation,
                onCheckedChange = viewModel::onWidgetShowLocationChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Geschwindigkeit", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowSpeed,
                onCheckedChange = viewModel::onWidgetShowSpeedChange,
            )
        }
    }
}
