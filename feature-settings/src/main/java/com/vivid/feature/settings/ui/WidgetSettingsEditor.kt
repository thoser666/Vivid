package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.ui.unit.dp
import com.vivid.core.data.ChatOverlayPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import com.vivid.core.data.AppSettings
import com.vivid.feature.widget.WidgetVariableResolver
import androidx.compose.material3.OutlinedTextField

import com.vivid.core.data.WidgetType

/** Template-Variablen, die per Chip in das Text-Widget-Template eingefügt werden können. */
private val TEMPLATE_VARIABLES = listOf(
    "{time}", "{date}", "{speed}", "{altitude}", "{lat}", "{lon}",
    "{road}", "{city}", "{country}", "{timer}", "{distance}", "{gforce}",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WidgetSettingsEditor(type: WidgetType, uiState: AppSettings, onChange: (AppSettings) -> Unit) {
    if (type == WidgetType.CHAT) {
        // Chat-Overlay über der Streaming-Vorschau
        Text(stringResource(R.string.overlays_chat_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_chat_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Scope-Voraussetzungen für Event-Alerts sichtbar machen (Follow
        // braucht moderator:read:followers + Mod-Rechte, Subs/Gifts/Resubs
        // channel:read:subscriptions — Fehlen degradiert nur den Alert-Typ).
        Text(
            text = stringResource(R.string.overlays_chat_scopes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatChannel,
            onValueChange = { value -> onChange(uiState.copy(chatChannel = value)) },
            label = { Text(stringResource(R.string.overlays_channel_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // YouTube-Chat (Multi-Plattform-Chat, P1): Kanal-ID + Toggle — gelesen
        // wird anonym über innertube-Polling, kein OAuth nötig.
        OutlinedTextField(
            value = uiState.youtubeChannelId,
            onValueChange = { value -> onChange(uiState.copy(youtubeChannelId = value)) },
            label = { Text(stringResource(R.string.overlays_youtube_channel_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_youtube_chat_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.youtubeChatEnabled,
                onCheckedChange = { value -> onChange(uiState.copy(youtubeChatEnabled = value)) },
            )
        }
        // Kick-Chat (Multi-Plattform-Chat, P2): Slug + Toggle — gelesen wird
        // anonym über das Pusher-Protokoll, kein OAuth nötig.
        OutlinedTextField(
            value = uiState.kickChannel,
            onValueChange = { value -> onChange(uiState.copy(kickChannel = value)) },
            label = { Text(stringResource(R.string.overlays_kick_channel_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_kick_chat_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.kickChatEnabled,
                onCheckedChange = { value -> onChange(uiState.copy(kickChatEnabled = value)) },
            )
        }


    }
    if (type == WidgetType.TEXT) {
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
            Text(stringResource(R.string.overlays_widget_time), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowTime,
                onCheckedChange = { value -> onChange(uiState.copy(widgetShowTime = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_location), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowLocation,
                onCheckedChange = { value -> onChange(uiState.copy(widgetShowLocation = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_speed), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowSpeed,
                onCheckedChange = { value -> onChange(uiState.copy(widgetShowSpeed = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_widget_altitude), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.widgetShowAltitude,
                onCheckedChange = { value -> onChange(uiState.copy(widgetShowAltitude = value)) },
            )
        }

        // Template-Editor: benutzerdefiniertes Template mit {var}-Platzhaltern.
        // Ein leeres Template schaltet auf den Toggle-Modus zurück (siehe TextInfoWidget).
        Text(stringResource(R.string.overlays_widget_template_label), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.overlays_widget_template_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.widgetTemplate,
            onValueChange = { value -> onChange(uiState.copy(widgetTemplate = value.take(256))) },
            label = { Text(stringResource(R.string.overlays_widget_template_label)) },
            placeholder = { Text(stringResource(R.string.overlays_widget_template_placeholder)) },
            singleLine = false,
            minLines = 1,
            maxLines = 4,
            modifier = Modifier.fillMaxWidth(),
        )
        // Variable-Chips: Klick fügt den Platzhalter ans Template-Ende an.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TEMPLATE_VARIABLES.forEach { variable ->
                AssistChip(
                    onClick = {
                        onChange(uiState.copy(widgetTemplate = (uiState.widgetTemplate + variable).take(256)))
                    },
                    label = { Text(variable) },
                )
            }
        }
        // Live-Vorschau: resolved Template mit Beispielwerten (gleicher Resolver
        // wie das echte Widget — eine Quelle der Wahrheit).
        val preview = WidgetVariableResolver.resolve(
            uiState.widgetTemplate,
            WidgetVariableResolver.currentValues(
                time = "14:05:32",
                date = "17.08.2026",
                speed = "52.3 km/h",
                altitude = "120 m",
                latitude = 52.52,
                longitude = 13.405,
                road = "Kurfürstendamm",
                city = "Berlin",
                country = "Deutschland",
                timer = "00:12:34",
                distance = "2,4 km",
                gforce = "0,8 g",
            ),
        )
        if (uiState.widgetTemplate.isNotBlank()) {
            Text(
                text = stringResource(R.string.overlays_widget_template_preview, preview),
                style = MaterialTheme.typography.bodyMedium,
            )
        }


    }
    if (type == WidgetType.CHAT) {
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
                onCheckedChange = { value -> onChange(uiState.copy(emotesBttvEnabled = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_emotes_ffz), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.emotesFfzEnabled,
                onCheckedChange = { value -> onChange(uiState.copy(emotesFfzEnabled = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_emotes_7tv), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.emotes7tvEnabled,
                onCheckedChange = { value -> onChange(uiState.copy(emotes7tvEnabled = value)) },
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
                onCheckedChange = { value -> onChange(uiState.copy(chatOverlayHideDeleted = value)) },
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
                onCheckedChange = { value -> onChange(uiState.copy(chatOverlayAnimateNewMessages = value)) },
            )
        }

        // Hype-Train-Anzeige im Chat-Overlay
        Text(stringResource(R.string.overlays_hype_train_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_hype_train_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_hype_train_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatOverlayHypeTrainEnabled,
                onCheckedChange = { value -> onChange(uiState.copy(chatOverlayHypeTrainEnabled = value)) },
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
            onValueChange = { onChange(uiState.copy(chatOverlayWidthDp = it.toInt().coerceIn(100, 400))) },
            valueRange = 100f..400f,
            steps = 5,
            modifier = Modifier.fillMaxWidth(),
        )
        // Höhe
        Text(stringResource(R.string.overlays_layout_height, uiState.chatOverlayHeightDp))
        Slider(
            value = uiState.chatOverlayHeightDp.toFloat(),
            onValueChange = { onChange(uiState.copy(chatOverlayHeightDp = it.toInt().coerceIn(100, 600))) },
            valueRange = 100f..600f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        // Hintergrund-Transparenz
        Text(stringResource(R.string.overlays_layout_background_alpha, (uiState.chatOverlayBackgroundAlpha * 100).toInt()))
        Slider(
            value = uiState.chatOverlayBackgroundAlpha,
            onValueChange = { onChange(uiState.copy(chatOverlayBackgroundAlpha = it.coerceIn(0f, 1f))) },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
        // Schriftgröße
        Text(stringResource(R.string.overlays_layout_font_size, uiState.chatOverlayFontSizeSp))
        Slider(
            value = uiState.chatOverlayFontSizeSp.toFloat(),
            onValueChange = { onChange(uiState.copy(chatOverlayFontSizeSp = it.toInt().coerceIn(8, 20))) },
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
                onCheckedChange = { value -> onChange(uiState.copy(chatOverlayShowTimestamp = value)) },
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
                onClick = { onChange(uiState.copy(chatOverlayPosition = ChatOverlayPosition.TOP_START)) },
                label = { Text(stringResource(R.string.overlays_position_top_start)) },
            )
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.TOP_END,
                onClick = { onChange(uiState.copy(chatOverlayPosition = ChatOverlayPosition.TOP_END)) },
                label = { Text(stringResource(R.string.overlays_position_top_end)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.BOTTOM_START,
                onClick = { onChange(uiState.copy(chatOverlayPosition = ChatOverlayPosition.BOTTOM_START)) },
                label = { Text(stringResource(R.string.overlays_position_bottom_start)) },
            )
            FilterChip(
                selected = uiState.chatOverlayPosition == ChatOverlayPosition.BOTTOM_END,
                onClick = { onChange(uiState.copy(chatOverlayPosition = ChatOverlayPosition.BOTTOM_END)) },
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
                onValueChange = { value -> onChange(uiState.copy(chatOverlayUsernameColorHex = value)) },
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
                onValueChange = { value -> onChange(uiState.copy(chatOverlayTextColorHex = value)) },
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
                onValueChange = { value -> onChange(uiState.copy(chatOverlayBackgroundColorHex = value)) },
                label = { Text(stringResource(R.string.overlays_color_hint)) },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }


    }
    if (type == WidgetType.GRID) {
        // Grid-Overlay (Positionierung)
        Text(stringResource(R.string.overlays_grid_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_grid_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Rasterabstand
        Text(stringResource(R.string.overlays_grid_spacing, uiState.gridOverlaySpacingDp))
        Slider(
            value = uiState.gridOverlaySpacingDp.toFloat(),
            onValueChange = { onChange(uiState.copy(gridOverlaySpacingDp = it.toInt().coerceIn(10, 100))) },
            valueRange = 10f..100f,
            steps = 17,
            modifier = Modifier.fillMaxWidth(),
        )


    }
    if (type == WidgetType.QR_CODE) {
        // QR-Code-Widget für Spenden-/Social-Links
        Text(stringResource(R.string.overlays_qr_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_qr_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.qrCodeWidgetContent,
            onValueChange = { value -> onChange(uiState.copy(qrCodeWidgetContent = value.take(2048))) },
            label = { Text(stringResource(R.string.overlays_qr_content)) },
            placeholder = { Text(stringResource(R.string.overlays_qr_content_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.overlays_qr_size, uiState.qrCodeWidgetSizeDp))
        Slider(
            value = uiState.qrCodeWidgetSizeDp.toFloat(),
            onValueChange = { onChange(uiState.copy(qrCodeWidgetSizeDp = it.toInt().coerceIn(120, 600))) },
            valueRange = 120f..600f,
            steps = 23,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.overlays_qr_opacity, (uiState.qrCodeWidgetOpacity * 100).toInt()))
        Slider(
            value = uiState.qrCodeWidgetOpacity,
            onValueChange = { value -> onChange(uiState.copy(qrCodeWidgetOpacity = value.coerceIn(0f, 1f))) },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )


    }
    if (type == WidgetType.SLIDESHOW) {
        // Slideshow-Widget
        Text(stringResource(R.string.overlays_slideshow_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_slideshow_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedTextField(
            value = uiState.slideshowWidgetUris,
            onValueChange = { value -> onChange(uiState.copy(slideshowWidgetUris = value.take(16_384))) },
            label = { Text(stringResource(R.string.overlays_slideshow_uris)) },
            placeholder = { Text(stringResource(R.string.overlays_slideshow_uris_hint)) },
            minLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.overlays_slideshow_interval, uiState.slideshowWidgetIntervalSeconds))
        Slider(
            value = uiState.slideshowWidgetIntervalSeconds.toFloat(),
            onValueChange = { onChange(uiState.copy(slideshowWidgetIntervalSeconds = it.toInt().coerceIn(5, 3600))) },
            valueRange = 5f..300f,
            steps = 58,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(stringResource(R.string.overlays_slideshow_size, uiState.slideshowWidgetSizeDp))
        Slider(
            value = uiState.slideshowWidgetSizeDp.toFloat(),
            onValueChange = { onChange(uiState.copy(slideshowWidgetSizeDp = it.toInt().coerceIn(120, 600))) },
            valueRange = 120f..600f,
            steps = 23,
            modifier = Modifier.fillMaxWidth(),
        )


    }
    if (type == WidgetType.BATTERY) {
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
            Text(stringResource(R.string.overlays_battery_show_icon), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.batteryShowIcon,
                onCheckedChange = { value -> onChange(uiState.copy(batteryShowIcon = value)) },
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.overlays_battery_show_percent), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.batteryShowPercent,
                onCheckedChange = { value -> onChange(uiState.copy(batteryShowPercent = value)) },
            )
        }
        // Low-Battery-Schwelle
        Text(stringResource(R.string.overlays_battery_threshold, uiState.batteryLowThresholdPercent))
        Slider(
            value = uiState.batteryLowThresholdPercent.toFloat(),
            onValueChange = { onChange(uiState.copy(batteryLowThresholdPercent = it.toInt().coerceIn(5, 50))) },
            valueRange = 5f..50f,
            steps = 8,
            modifier = Modifier.fillMaxWidth(),
        )


    }
    if (type == WidgetType.SUBTITLES) {
        // Untertitel (Speech-to-Text-Overlay, PARITY-Zeile 143)
        Text(stringResource(R.string.overlays_subtitles_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_subtitles_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )


    }
    if (type == WidgetType.POMODORO) {
        Text(stringResource(R.string.pomodoro_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.pomodoro_desc), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(stringResource(R.string.pomodoro_work_minutes, uiState.pomodoroWorkDurationMinutes))
        Slider(value = uiState.pomodoroWorkDurationMinutes.toFloat(),
            onValueChange = { onChange(uiState.copy(pomodoroWorkDurationMinutes = it.toInt().coerceIn(1, 120))) },
            valueRange = 1f..120f, steps = 118, modifier = Modifier.fillMaxWidth().testTag("pomodoro_work_duration"))
        Text(stringResource(R.string.pomodoro_break_minutes, uiState.pomodoroBreakDurationMinutes))
        Slider(value = uiState.pomodoroBreakDurationMinutes.toFloat(),
            onValueChange = { onChange(uiState.copy(pomodoroBreakDurationMinutes = it.toInt().coerceIn(1, 60))) },
            valueRange = 1f..60f, steps = 58, modifier = Modifier.fillMaxWidth().testTag("pomodoro_break_duration"))
    }
    if (type == WidgetType.IMAGE) {
        // Bild-Widget (Logo/Wasserzeichen)
        Text(stringResource(R.string.overlays_image_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.overlays_image_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Bild-URI (nur lesbar, wird über SAF-Picker gesetzt)
        OutlinedTextField(
            value = uiState.imageWidgetUri,
            onValueChange = { value -> onChange(uiState.copy(imageWidgetUri = value)) },
            label = { Text(stringResource(R.string.overlays_image_uri)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Bildgröße
        Text(stringResource(R.string.overlays_image_size, uiState.imageWidgetSizeDp))
        Slider(
            value = uiState.imageWidgetSizeDp.toFloat(),
            onValueChange = { onChange(uiState.copy(imageWidgetSizeDp = it.toInt().coerceIn(20, 400))) },
            valueRange = 20f..400f,
            steps = 37,
            modifier = Modifier.fillMaxWidth(),
        )
        // Transparenz
        Text(stringResource(R.string.overlays_image_opacity, (uiState.imageWidgetOpacity * 100).toInt()))
        Slider(
            value = uiState.imageWidgetOpacity,
            onValueChange = { onChange(uiState.copy(imageWidgetOpacity = it.coerceIn(0f, 1f))) },
            valueRange = 0f..1f,
            steps = 9,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
