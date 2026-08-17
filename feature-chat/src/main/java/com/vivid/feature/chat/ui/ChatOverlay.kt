package com.vivid.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage

/**
 * Chat-Overlay über der Streaming-Vorschau. Zeigt die letzten Nachrichten des
 * konfigurierten Twitch-Kanals und blendet sich aus, wenn das Overlay in den
 * Einstellungen deaktiviert oder kein Kanal gesetzt ist.
 */
@Composable
fun ChatOverlay(
    modifier: Modifier = Modifier,
    viewModel: ChatOverlayViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    if (!uiState.enabled) return

    Column(
        modifier = modifier
            .widthIn(max = 240.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = 0.5f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (uiState.messages.isEmpty()) {
            Text(
                text = when {
                    // Seit dem IRC-Ausstieg liest das Overlay über EventSub und
                    // braucht deshalb die Bot-Zugangsdaten (kein anonymes Lesen).
                    !uiState.configured -> "Chat-Overlay: nicht konfiguriert (Bot-Login + Token + Client-ID in den Einstellungen)"
                    uiState.connection == ChatConnectionState.Connecting -> "Verbinde…"
                    else -> "Chat: ${uiState.channel}"
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        } else {
            // Nur die neuesten Nachrichten anzeigen, damit das Overlay klein bleibt.
            uiState.messages.takeLast(6).forEach { message ->
                ChatMessageRow(message)
            }
        }
    }
}

@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val nameColor = message.color?.let(::parseHexColor) ?: Color(0xFFB39DDB)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                append("${message.displayName}: ")
            }
            withStyle(SpanStyle(color = Color.White)) {
                append(message.text)
            }
        },
        style = MaterialTheme.typography.bodySmall,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

private fun parseHexColor(hex: String): Color? =
    runCatching {
        val rgb = hex.removePrefix("#").toLong(16)
        Color((rgb shl 8) or 0xFF)
    }.getOrNull()
