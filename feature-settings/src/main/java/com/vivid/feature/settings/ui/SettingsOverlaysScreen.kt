package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.dp
import com.vivid.core.data.ChatOverlayPosition
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

        // Third-Party-Emotes (BTTV/FFZ/7TV)
        Text(stringResource(R.string.overlays_emotes_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_emotes_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_emotes_bttv), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.emotesBttvEnabled,
                onCheckedChange = viewModel::onEmotesBttvChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_emotes_ffz), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.emotesFfzEnabled,
                onCheckedChange = viewModel::onEmotesFfzChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_emotes_7tv), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.emotes7tvEnabled,
                onCheckedChange = viewModel::onEmotes7tvChange,
            )
        }

        // Gelöschte Nachrichten
        Text(stringResource(R.string.overlays_deleted_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_deleted_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_deleted_hide), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatOverlayHideDeleted,
                onCheckedChange = viewModel::onChatOverlayHideDeletedChange,
            )
        }

        // Animation für neue Nachrichten
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_animate_new_messages), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatOverlayAnimateNewMessages,
                onCheckedChange = viewModel::onChatOverlayAnimateNewMessagesChange,
            )
        }

        // Chat-Layout-Einstellungen
        Text(stringResource(R.string.overlays_layout_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_layout_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Breite
        Text(stringResource(R.string.overlays_layout_width, uiState.chatOverlayWidthDp))
        Slider(
            value = uiState.chatOverlayWidthDp.toFloat(),
            onValueChange = { viewModel.onChatOverlayWidthChange(it.toInt()) },
            valueRange = 100f..400f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        // Höhe
        Text(stringResource(R.string.overlays_layout_height, uiState.chatOverlayHeightDp))
        Slider(
            value = uiState.chatOverlayHeightDp.toFloat(),
            onValueChange = { viewModel.onChatOverlayHeightChange(it.toInt()) },
            valueRange = 100f..600f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        // Hintergrund-Transparenz
        Text(stringResource(R.string.overlays_layout_background_alpha, (uiState.chatOverlayBackgroundAlpha * 100).toInt()))
        Slider(
            value = uiState.chatOverlayBackgroundAlpha,
            onValueChange = { viewModel.onChatOverlayBackgroundAlphaChange(it) },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        // Schriftgröße
        Text(stringResource(R.string.overlays_layout_font_size, uiState.chatOverlayFontSizeSp))
        Slider(
            value = uiState.chatOverlayFontSizeSp.toFloat(),
            onValueChange = { viewModel.onChatOverlayFontSizeChange(it.toInt()) },
            valueRange = 8f..20f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        // Zeitstempel
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_layout_show_timestamp), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatOverlayShowTimestamp,
                onCheckedChange = viewModel::onChatOverlayShowTimestampChange,
            )
        }

        // Position
        Text(stringResource(R.string.overlays_position_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_position_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.TOP_START,
                onClick = { viewModel.onChatOverlayPositionChange(ChatOverlayPosition.TOP_START) },
                label = { Text(stringResource(R.string.overlays_position_top_start)) },
            )
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.TOP_END,
                onClick = { viewModel.onChatOverlayPositionChange(ChatOverlayPosition.TOP_END) },
                label = { Text(stringResource(R.string.overlays_position_top_end)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.BOTTOM_START,
                onClick = { viewModel.onChatOverlayPositionChange(ChatOverlayPosition.BOTTOM_START) },
                label = { Text(stringResource(R.string.overlays_position_bottom_start)) },
            )
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.BOTTOM_END,
                onClick = { viewModel.onChatOverlayPositionChange(ChatOverlayPosition.BOTTOM_END) },
                label = { Text(stringResource(R.string.overlays_position_bottom_end)) },
            )
        }

        // Farben
        Text(stringResource(R.string.overlays_colors_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_colors_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Username-Farbe
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_colors_username), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = uiState.chatOverlayUsernameColorHex,
                onValueChange = viewModel::onChatOverlayUsernameColorChange,
                label = { Text(stringResource(R.string.overlays_color_hint)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }
        // Text-Farbe
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_colors_text), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = uiState.chatOverlayTextColorHex,
                onValueChange = viewModel::onChatOverlayTextColorChange,
                label = { Text(stringResource(R.string.overlays_color_hint)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }
        // Hintergrund-Farbe
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_colors_background), modifier = Modifier.weight(1f))
            OutlinedTextField(
                value = uiState.chatOverlayBackgroundColorHex,
                onValueChange = viewModel::onChatOverlayBackgroundColorChange,
                label = { Text(stringResource(R.string.overlays_color_hint)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }

        // Grid-Overlay (Positionierung)
        Text(stringResource(R.string.overlays_grid_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_grid_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_grid_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.gridOverlayEnabled,
                onCheckedChange = viewModel::onGridOverlayEnabledChange,
            )
        }
        // Rasterabstand
        Text(stringResource(R.string.overlays_grid_spacing, uiState.gridOverlaySpacingDp))
        Slider(
            value = uiState.gridOverlaySpacingDp.toFloat(),
            onValueChange = { viewModel.onGridOverlaySpacingChange(it.toInt()) },
            valueRange = 10f..100f,
            steps = 17,
            modifier = Modifier.fillMaxWidth(),
        )

        // Akku-Anzeige-Widget
        Text(stringResource(R.string.overlays_battery_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_battery_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_battery_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.batteryEnabled,
                onCheckedChange = viewModel::onBatteryEnabledChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_battery_show_icon), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.batteryShowIcon,
                onCheckedChange = viewModel::onBatteryShowIconChange,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_battery_show_percent), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.batteryShowPercent,
                onCheckedChange = viewModel::onBatteryShowPercentChange,
            )
        }
        // Low-Battery-Schwelle
        Text(stringResource(R.string.overlays_battery_threshold, uiState.batteryLowThresholdPercent))
        Slider(
            value = uiState.batteryLowThresholdPercent.toFloat(),
            onValueChange = { viewModel.onBatteryLowThresholdChange(it.toInt()) },
            valueRange = 5f..50f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
