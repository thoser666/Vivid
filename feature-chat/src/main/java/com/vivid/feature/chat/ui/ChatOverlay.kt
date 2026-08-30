package com.vivid.feature.chat.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.vivid.feature.chat.R
import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.emotes.ThirdPartyEmote
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

    val fontSize = uiState.overlayFontSizeSp.sp
    val emoteSize = with(LocalDensity.current) { (uiState.overlayFontSizeSp - 2).sp.toDp() }
    val badgeSize = with(LocalDensity.current) { (uiState.overlayFontSizeSp + 6).sp.toDp() }

    Column(
        modifier = modifier
            .width(uiState.overlayWidthDp.dp)
            .heightIn(max = uiState.overlayHeightDp.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Black.copy(alpha = uiState.overlayBackgroundAlpha))
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
            val visibleMessages = uiState.messages.filter { msg ->
                if (uiState.hideDeleted && uiState.deletedMessageIds.contains(msg.id)) {
                    false
                } else {
                    true
                }
            }
            visibleMessages.takeLast(6).forEach { message ->
                val isDeleted = uiState.deletedMessageIds.contains(message.id)
                ChatMessageRow(
                    message = message,
                    badges = uiState.badges,
                    thirdPartyEmotes = uiState.thirdPartyEmotes,
                    isDeleted = isDeleted,
                    fontSize = fontSize,
                    emoteSize = emoteSize,
                    badgeSize = badgeSize,
                    showTimestamp = uiState.overlayShowTimestamp,
                )
            }
        }
    }
}

/**
 * Eine Event-Alert-Zeile (Follow/Sub/Gift-Sub/Resub/Raid): Typ-Farbe +
 * lokalisierter Text aus den String-Ressourcen. Die Zusatzdaten
 * (Tier/Giftgeber/Viewer/Anzahl/Monate) kommen strukturiert im
 * [ChatAlert.detail] und werden hier in die Templates eingesetzt (keine
 * Lokalisierung in der Datenebene).
 */
@Composable
private fun AlertRow(alert: ChatAlert) {
    val color = when (alert.type) {
        ChatAlertType.FOLLOW -> Color(0xFF4CAF50)
        ChatAlertType.SUBSCRIBE -> Color(0xFFBA68C8)
        ChatAlertType.GIFT_SUB -> Color(0xFF4DD0E1)
        ChatAlertType.RESUB -> Color(0xFF64B5F6)
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
        ChatAlertType.GIFT_SUB -> {
            val count = alert.detail.count.coerceAtLeast(1)
            val base = if (alert.detail.isAnonymous) {
                pluralStringResource(R.plurals.chat_alert_gift_anonymous, count, count)
            } else {
                pluralStringResource(R.plurals.chat_alert_gift, count, alert.displayName, count)
            }
            val tier = tierLabel(alert.detail.tier)
            val withTier = if (tier != null) {
                "$base ($tier)"
            } else {
                base
            }
            if (alert.detail.cumulativeTotal > 0) {
                withTier + pluralStringResource(
                    R.plurals.chat_alert_gift_cumulative,
                    alert.detail.cumulativeTotal,
                    alert.detail.cumulativeTotal,
                )
            } else {
                withTier
            }
        }
        ChatAlertType.RESUB -> {
            val tier = tierLabel(alert.detail.tier)
            val base = if (tier != null) {
                stringResource(
                    R.string.chat_alert_resub,
                    alert.displayName,
                    tier,
                    alert.detail.months,
                )
            } else {
                stringResource(R.string.chat_alert_resub_plain, alert.displayName, alert.detail.months)
            }
            if (alert.detail.streakMonths > 0) {
                base + stringResource(R.string.chat_alert_resub_streak, alert.detail.streakMonths)
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
private fun ChatMessageRow(
    message: ChatMessage,
    badges: Map<String, ChatBadge>,
    thirdPartyEmotes: Map<String, String> = emptyMap(),
    isDeleted: Boolean = false,
    fontSize: TextUnit = 12.sp,
    emoteSize: Dp = 14.dp,
    badgeSize: Dp = 18.dp,
    showTimestamp: Boolean = true,
) {
    val nameColor = if (isDeleted) Color(0xFF666666) else (message.color?.let(::parseHexColor) ?: Color(0xFFB39DDB))
    val textColor = if (isDeleted) Color(0xFF666666) else Color.White
    val deletedAlpha = if (isDeleted) 0.5f else 1f
    val segments = parseMessageSegments(message.text, message.inlineEmotes, thirdPartyEmotes)

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        // Reply-Indikator: Pfeil + Name der Eltern-Nachricht (wenn vorhanden)
        if (message.replyParentUserLogin != null) {
            Text(
                text = "↩ ${message.replyParentUserLogin}: ",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize * 0.8f),
                color = Color(0xFF90CAF9),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Twitch-Badges (Broadcaster, Moderator, Subscriber, …)
        message.badges.forEach { key ->
            badges[key]?.let { badge ->
                AsyncImage(
                    model = badge.imageUrl,
                    contentDescription = badge.title,
                    modifier = Modifier
                        .width(badgeSize)
                        .height(badgeSize)
                        .clip(RoundedCornerShape(2.dp)),
                    contentScale = ContentScale.Fit,
                )
            }
        }
        // Username (direkt nach den Badges)
        val displayName = if (message.isAction) "* ${message.displayName}" else message.displayName
        Text(
            text = buildAnnotatedString {
                if (message.isAction) {
                    withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Normal, fontStyle = FontStyle.Italic)) {
                        append("$displayName ")
                    }
                } else {
                    withStyle(SpanStyle(color = nameColor, fontWeight = FontWeight.Bold)) {
                        append("${message.displayName}: ")
                    }
                }
            },
            style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
        )
        // Textsegmente und Emotes im Wechsel
        segments.forEach { segment ->
            when (segment) {
                is MessageSegment.Text -> {
                    Text(
                        text = segment.text,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = fontSize),
                        color = if (isDeleted) Color(0xFF666666) else if (message.isAction) Color(0xFFB0BEC5) else Color.White,
                    )
                }
                is MessageSegment.Emote -> {
                    AsyncImage(
                        model = segment.emote.url,
                        contentDescription = segment.emote.id,
                        modifier = Modifier
                            .width(emoteSize)
                            .height(emoteSize)
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
                is MessageSegment.ThirdPartyEmote -> {
                    AsyncImage(
                        model = segment.url,
                        contentDescription = segment.name,
                        modifier = Modifier
                            .width(emoteSize)
                            .height(emoteSize)
                            .clip(RoundedCornerShape(2.dp)),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
        }
        // Bits-Anzeige (Cheer): Stern-Symbol + Anzahl der Bits
        if (message.bitsAmount > 0) {
            Text(
                text = "⭐${message.bitsAmount}",
                style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize * 0.8f),
                color = Color(0xFFFFB74D),
                fontWeight = FontWeight.Bold,
            )
        }
        // Zeitstempel (optional)
        if (showTimestamp && message.timestamp > 0) {
            val timeStr = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
                .format(java.util.Date(message.timestamp))
            Text(
                text = timeStr,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize * 0.7f),
                color = Color.White.copy(alpha = 0.5f),
            )
        }
    }
}

/** Segmente einer Chat-Nachricht: reiner Text, Twitch-Inline-Emote oder Third-Party-Emote. */
internal sealed interface MessageSegment {
    data class Text(val text: String) : MessageSegment
    data class Emote(val emote: InlineEmote) : MessageSegment
    data class ThirdPartyEmote(val name: String, val url: String) : MessageSegment
}

/**
 * Zerlegt den Klartext einer Chat-Nachricht in [MessageSegment]e, indem
 * [inlineEmotes] an ihren Positionen aus dem Text herausgeschnitten werden.
 * Zusätzlich werden Third-Party-Emotes (BTTV/FFZ/7TV) aus [thirdPartyEmotes]
 * als Inline-Emote erkannt.
 */
internal fun parseMessageSegments(
    text: String,
    inlineEmotes: List<InlineEmote>,
    thirdPartyEmotes: Map<String, String> = emptyMap(),
): List<MessageSegment> {
    if (inlineEmotes.isEmpty() && thirdPartyEmotes.isEmpty()) {
        return listOf(MessageSegment.Text(text))
    }
    val segments = mutableListOf<MessageSegment>()
    var cursor = 0

    // Zuerst Twitch-Inline-Emotes (haben explizite Positionen)
    for (emote in inlineEmotes) {
        if (emote.start > cursor && emote.start <= text.length) {
            // Text zwischen dem letzten Cursor und dem Emote
            val textBefore = text.substring(cursor, emote.start)
            // Third-Party-Emotes im Text davor parsen
            segments.addAll(parseThirdPartyEmotesInText(textBefore, thirdPartyEmotes))
        }
        if (emote.end < text.length) {
            segments.add(MessageSegment.Emote(emote))
            cursor = emote.end + 1
        } else {
            segments.add(MessageSegment.Emote(emote))
            cursor = text.length
        }
    }

    // Rest-Text nach den letzten Twitch-Emotes
    if (cursor < text.length) {
        val remainingText = text.substring(cursor)
        segments.addAll(parseThirdPartyEmotesInText(remainingText, thirdPartyEmotes))
    }

    return segments.ifEmpty { listOf(MessageSegment.Text(text)) }
}

/**
 * Parst Third-Party-Emotes in einem Text-Block.
 * Ersetzt Emote-Namen durch [ThirdPartyEmote]-Segmente.
 */
private fun parseThirdPartyEmotesInText(
    text: String,
    thirdPartyEmotes: Map<String, String>,
): List<MessageSegment> {
    if (thirdPartyEmotes.isEmpty() || text.isBlank()) {
        return listOf(MessageSegment.Text(text))
    }

    val segments = mutableListOf<MessageSegment>()
    var remaining = text

    // Emote-Namen nach Länge absteigend sortieren
    val sortedEmotes = thirdPartyEmotes.entries.sortedByDescending { it.key.length }

    while (remaining.isNotEmpty()) {
        var found = false
        for ((name, url) in sortedEmotes) {
            if (remaining.startsWith(name, ignoreCase = true)) {
                segments.add(MessageSegment.ThirdPartyEmote(name, url))
                remaining = remaining.removePrefix(name)
                found = true
                break
            }
        }
        if (!found) {
            // Zeichen zum nächsten Emote oder Rest-Text
            val nextEmoteIndex = sortedEmotes.minOfOrNull { (name, _) ->
                remaining.indexOf(name, ignoreCase = true).takeIf { it >= 0 } ?: Int.MAX_VALUE
            } ?: Int.MAX_VALUE

            if (nextEmoteIndex == Int.MAX_VALUE) {
                segments.add(MessageSegment.Text(remaining))
                break
            } else if (nextEmoteIndex > 0) {
                segments.add(MessageSegment.Text(remaining.substring(0, nextEmoteIndex)))
                remaining = remaining.removeRange(0, nextEmoteIndex)
            }
        }
    }

    return segments.ifEmpty { listOf(MessageSegment.Text(text)) }
}

private fun parseHexColor(hex: String): Color? =
    runCatching {
        val rgb = hex.removePrefix("#").toLong(16)
        Color((rgb shl 8) or 0xFF)
    }.getOrNull()
