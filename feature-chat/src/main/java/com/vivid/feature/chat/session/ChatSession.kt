package com.vivid.feature.chat.session

import com.vivid.feature.chat.model.ChatAlert
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.twitch.SendChatResult
import com.vivid.feature.chat.twitch.TwitchEventSubConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Lese-Adapter einer Chat-Plattform (P0 der Multi-Plattform-Skizze,
 * docs/architecture/multi-platform-chat.md, Abschnitt 4.2): liefert
 * Nachrichten und Verbindungsstatus einer Session.
 *
 * [alerts] ist nur zu implementieren, wenn die Plattform eine Alert-Quelle
 * hat — der Interface-Default [emptyFlow] gilt für Plattformen ohne Alerts
 * (P6 mergt die Alerts dann plattformübergreifend).
 *
 * Referenz-Implementierung (Twitch): `TwitchChatEventSubReader`.
 */
interface ChatReader {
    /** Nachrichten der Session (jeweils mit gesetztem [ChatMessage.platform]). */
    val messages: Flow<ChatMessage>

    /** Event-Alerts der Plattform — Default: Plattform hat keine Alert-Quelle. */
    val alerts: Flow<ChatAlert>
        get() = emptyFlow()

    /** Verbindungsstatus der Session. */
    val state: StateFlow<ChatConnectionState>

    /** Startet (oder startet neu) die Session für [config]. */
    fun start(config: ChatSessionConfig)

    /** Beendet die Session. */
    fun stop()
}

/**
 * Sende-Adapter einer Chat-Plattform (P0, Skizze Abschnitt 4.2).
 * [ChatSendResult] verallgemeinert das Helix-`drop_reason`-Verhalten
 * (Twitch) als Vertrag für alle Plattformen.
 */
interface ChatSender {
    suspend fun send(config: ChatSessionConfig, text: String): ChatSendResult
}

/**
 * Ergebnis eines Send-Aufrufs (plattformneutral, P0):
 * - [Sent] — die Nachricht wurde angenommen.
 * - [Dropped] — die Plattform hat sie abgelehnt (z. B. Slow-Mode,
 *   fehlende verifizierte E-Mail, Bann — Twitch `drop_reason`).
 * - [Failed] — technischer Fehler (Auth/Netz/Rate-Limit); Ursache als Text
 *   fürs Bot-Log (Entsprechung der [com.vivid.feature.chat.twitch.TwitchSendChatException]-Message).
 */
sealed interface ChatSendResult {
    data object Sent : ChatSendResult

    data class Dropped(val reason: String?) : ChatSendResult

    data class Failed(val cause: String) : ChatSendResult

    companion object {
        /**
         * Überführt die Twitch-Send-Felder ([SendChatResult.isSent]/[SendChatResult.dropReason],
         * Fehler als Text) in das plattformneutrale Ergebnis.
         */
        fun from(isSent: Boolean, dropReason: String?, error: String? = null): ChatSendResult =
            when {
                error != null -> Failed(error)
                isSent -> Sent
                else -> Dropped(dropReason)
            }
    }
}

/**
 * Session-Konfiguration je Plattform (sealed, P0): typsichere Plattform-
 * Diskriminierung ohne generische Interfaces (Skizze 4.2 — Hilt-Multibinding
 * und ein lesbarer ChatSessionManager statt `ChatReader<C>`-Wildwuchs).
 *
 * P0 führte nur die Twitch-Variante ein (Verhaltensneutralität); P1 ergänzt
 * `Youtube` (innertube-Polling, anonym), P2 `Kick` (Pusher/OAuth 2.1).
 */
sealed interface ChatSessionConfig {
    val platform: ChatPlatform
    val channel: String

    /** Twitch-Session: bestehende EventSub/Helix-Konfiguration. */
    data class Twitch(
        val twitch: TwitchEventSubConfig,
        override val channel: String = twitch.channel,
    ) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.TWITCH
    }

    /**
     * YouTube-Session (P1): nur die Kanal-ID (Format `UC…`) — gelesen wird
     * anonym über innertube-Polling ([com.vivid.feature.chat.youtube.YoutubeChatReader]),
     * kein Token nötig. [channel] ist die Kanal-ID selbst.
     */
    data class Youtube(
        override val channel: String,
    ) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.YOUTUBE
    }

    /**
     * Kick-Session (P2): nur der Kanal-Slug — gelesen wird anonym über das
     * Pusher-Protokoll ([com.vivid.feature.chat.kick.KickChatReader]), kein
     * Token nötig. [channel] ist der Kanal-Slug.
     */
    data class Kick(
        override val channel: String,
    ) : ChatSessionConfig {
        override val platform: ChatPlatform get() = ChatPlatform.KICK
    }
}
