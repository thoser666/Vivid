package com.vivid.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vivid.feature.chat.R
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.InlineEmote

/**
 * Chat-Overlay über der Streaming-Vorschau. Zeigt die letzten Nachrichten des
 * konfigurierten Twitch-Kanals mit Inline-Emotes (Twitch CDN) und blendet
 * sich aus, wenn das Overlay in den Einstellungen deaktiviert oder kein
 * Kanal gesetzt ist.
 */
@OptIn(ExperimentalLayoutApi::class)
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
                    !uiState.configured -> stringResource(R.string.chat_overlay_not_configured)
                    uiState.connection == ChatConnectionState.Connecting -> stringResource(R.string.chat_overlay_connecting)
                    else -> stringResource(R.string.chat_overlay_channel, uiState.channel)
                },
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.7f),
            )
        } else {
            uiState.messages.takeLast(6).forEach { message ->
                ChatMessageRow(message)
            }
        }
    }
}

/**
 * Eine Chat-Zeile mit Inline-Emotes: Username in Farbe, danach Textsegmente
 * im Wechsel mit Twitch-CDN-Emote-Bildern (via Coil).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageRow(message: ChatMessage) {
    val nameColor = message.color?.let(::parseHexColor) ?: Color(0xFFB39DDB)
    val emoteSizeDp = with(LocalDensity.current) { 14.sp.toDp() }
    val segments = parseMessageSegments(message.text, message.inlineEmotes)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Username (immer als erstes)
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                    append("${message.displayName}: ")
                }
            },
            style = MaterialTheme.typography.bodySmall,
        )
        // Textsegmente und Emotes im Wechsel
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White,
                    )
                }
                is MessageSegment.Emote -> {
                    AsyncImage(
                        model = segment.emote.url,
                        contentDescription = segment.emote.id,
                        modifier = Modifier
                            .width(emoteSizeDp)
                            .height(emoteSizeDp)
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
    }
}

/** Segmente einer Chat-Nachricht: reiner Text oder ein Inline-Emote. */
internal sealed interface MessageSegment {
    data class Text(val text: String) : MessageSegment
    data class Emote(val emote: InlineEmote) : MessageSegment
}

/**
 * Zerlegt den Klartext einer Chat-Nachricht in [MessageSegment]e, indem
 * [inlineEmotes] an ihren Positionen aus dem Text herausgeschnitten werden.
 */
internal fun parseMessageSegments(
    text: String,
    inlineEmotes: List<InlineEmote>,
): List<MessageSegment> {
    if (inlineEmotes.isEmpty()) return listOf(MessageSegment.Text(text))
    val segments = mutableListOf<MessageSegment>()
    var cursor = 0
    for (emote in inlineEmotes) {
        if (emote.start > cursor && emote.start <= text.length) {
            segments.add(MessageSegment.Text(text.substring(cursor, emote.start)))
        }
        if (emote.end < text.length) {
            segments.add(MessageSegment.Emote(emote))
            cursor = emote.end + 1
        } else {
            segments.add(MessageSegment.Emote(emote))
            cursor = text.length
        }
    }
    if (cursor < text.length) {
        segments.add(MessageSegment.Text(text.substring(cursor)))
    }
    return segments
}

private fun parseHexColor(hex: String): Color? =
    runCatching {
        val rgb = hex.removePrefix("#").toLong(16)
        Color((rgb shl 8) or 0xFF)
    }.getOrNull()
