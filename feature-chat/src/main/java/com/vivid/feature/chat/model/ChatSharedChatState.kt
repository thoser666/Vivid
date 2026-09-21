package com.vivid.feature.chat.model

/**
 * Zustand der Twitch-Shared-Chat-Session des konfigurierten Kanals — von den
 * EventSub-Topics `channel.shared_chat.begin`/`update`/`end` (Version 1) des
 * selben WebSocket wie die Chat-Nachrichten. `begin`/`update` tragen die
 * Teilnehmerliste (inkl. des eigenen Kanals, laut Twitch-Doku), `end` nicht.
 *
 * Der Chat-Overlay-Hinweis („🔗 Gemeinsamer Chat …") zeigt [Active] dezent an
 * und verschwindet bei [Inactive].
 */
sealed interface ChatSharedChatState {

    /** Keine Shared-Chat-Session aktiv (Standard, auch nach `end`). */
    data object Inactive : ChatSharedChatState

    /**
     * Session aktiv.
     *
     * @param sessionId Twitch-Session-ID (stabil über begin → update).
     * @param hostLogin Login des Session-Hosts (kleingeschrieben; Fallback:
     *   der eigene Broadcaster, wenn Twitch den Host nicht angibt).
     * @param participants Logins aller Teilnehmer (kleingeschrieben, inkl.
     *   des eigenen Kanals — die erste Ausstrahlung laut Twitch-Doku).
     */
    data class Active(
        val sessionId: String,
        val hostLogin: String,
        val participants: List<String>,
    ) : ChatSharedChatState
}
