package com.vivid.feature.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.data.SubtitleError
import com.vivid.feature.widgets.R

/**
 * Untertitel-Overlay (Speech-to-Text) über der Streaming-Vorschau (PARITY-Zeile 143).
 * Blendet sich aus, wenn die Untertitel in den Einstellungen deaktiviert sind; ohne
 * Erkennungsdienst auf dem Gerät erscheint stattdessen ein Hinweis (Capability-Fallback).
 * Fehlernachrichten (Mikrofon belegt, Berechtigung fehlt …) werden inline angezeigt,
 * damit der Streamer den Grund sieht, statt eines stummen leeren Overlays.
 */
@Composable
fun SubtitlesOverlay(
    modifier: Modifier = Modifier,
    viewModel: SubtitleWidgetViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.enabled) return

    val container = Color.Black.copy(alpha = 0.55f)
    Column(
        modifier = modifier
            .fillMaxWidth(0.75f)
            .clip(RoundedCornerShape(12.dp))
            .background(container)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
    ) {
        if (!state.available) {
            Text(
                text = stringResource(R.string.subtitles_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            return@Column
        }

        state.error?.let { error ->
            Text(
                text = errorLabelText(error),
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFFFC107),
                textAlign = TextAlign.Center,
            )
        }

        for (line in state.displayLines) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Lokalisierte Fehlermeldung je Fehlerklasse. */
@Composable
private fun errorLabelText(error: SubtitleError): String = when (error) {
    SubtitleError.NETWORK -> stringResource(R.string.subtitles_error_network)
    SubtitleError.AUDIO -> stringResource(R.string.subtitles_error_audio)
    SubtitleError.PERMISSION -> stringResource(R.string.subtitles_error_permission)
    SubtitleError.BUSY -> stringResource(R.string.subtitles_error_busy)
    SubtitleError.SPEECH_TIMEOUT -> stringResource(R.string.subtitles_error_speech_timeout)
    SubtitleError.GENERIC, SubtitleError.UNAVAILABLE ->
        stringResource(R.string.subtitles_error_generic)
}
