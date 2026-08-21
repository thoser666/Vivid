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
import androidx.compose.ui.res.pluralStringResource
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
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.model.ChatBadge
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
        // Event-Alerts (Follow/Sub/Raid) oberhalb der Chat-Nachrichten — jede
        // Zeile in der Farbe des Alert-Typs, verschwindet nach der TTL von
        // selbst (im ViewModel geregelt).
        uiState.alerts.forEach { alert ->
            AlertRow(alert)
        }
        if (uiState.messages.isEmpty() && uiState.alerts.isEmpty()) {
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
                ChatMessageRow(message, uiState.badges)
            }
        }
    }
}

/**
 * Eine Event-Alert-Zeile (Follow/Sub/Raid): Typ-Farbe + lokalisierter Text
 * aus den String-Ressourcen. Die Zusatzdaten (Tier/Giftgeber/Viewer) kommen
 * strukturiert im [ChatAlert.detail] und werden hier in die Templates
 * eingesetzt (keine Lokalisierung in der Datenebene).
 */
@Composable
private fun AlertRow(alert: ChatAlert) {
    val color = when (alert.type) {
        ChatAlertType.FOLLOW -> Color(0xFF4CAF50)
        ChatAlertType.SUBSCRIBE -> Color(0xFFBA68C8)
        ChatAlertType.RAID -> Color(0xFFFFB74D)
    }
    val text = when (alert.type) {
        ChatAlertType.FOLLOW -> stringResource(R.string.chat_alert_follow, alert.displayName)
        ChatAlertType.SUBSCRIBE -> {
            val tier = tierLabel(alert.detail.tier)
            val base = if (tier != null) {
                stringResource(R.string.chat_alert_subscribe, alert.displayName, tier)
            } else {
                stringResource(R.string.chat_alert_subscribe_plain, alert.displayName)
            }
            if (alert.detail.gifterName.isNotBlank()) {
                base + stringResource(R.string.chat_alert_sub_gift, alert.detail.gifterName)
            } else {
                base
            }
        }
        ChatAlertType.RAID -> pluralStringResource(
            R.plurals.chat_alert_raid,
            alert.detail.viewerCount,
            alert.displayName,
            alert.detail.viewerCount,
        )
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = color,
    )
}

/** Twitch-Tier-Wert (`1000`/`2000`/`3000`) → Anzeige-Label „Tier 1/2/3“ (unverändert lokalisiert). */
private fun tierLabel(tier: String): String? = when (tier) {
    "1000" -> "Tier 1"
    "2000" -> "Tier 2"
    "3000" -> "Tier 3"
    else -> null
}

/**
 * Eine Chat-Zeile: Twitch-Badges (Broadcaster/Mod/Sub) vor dem Username,
 * danach Textsegmente im Wechsel mit Twitch-CDN-Emote-Bildern (via Coil).
 * [badges] ist die `"set_id/version_id" → [ChatBadge]`-Map aus dem
 * [ChatOverlayViewModel.ChatOverlayUiState] — unbekannte Badges werden
 * einfach übersprungen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageRow(message: ChatMessage, badges: Map<String, ChatBadge>) {
    val nameColor = message.color?.let(::parseHexColor) ?: Color(0xFFB39DDB)
    val emoteSizeDp = with(LocalDensity.current) { 14.sp.toDp() }
    val badgeSizeDp = with(LocalDensity.current) { 18.sp.toDp() }
    val segments = parseMessageSegments(message.text, message.inlineEmotes)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Twitch-Badges (Broadcaster, Moderator, Subscriber, …)
        message.badges.forEach { key ->
            badges[key]?.let { badge ->
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.title,
                    modifier = Modifier
                        .width(badgeSizeDp)
                        .height(badgeSizeDp)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        // Username (direkt nach den Badges)
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
