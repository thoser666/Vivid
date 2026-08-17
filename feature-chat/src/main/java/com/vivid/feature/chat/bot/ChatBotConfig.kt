package com.vivid.feature.chat.bot

import com.vivid.core.data.AppSettings
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.feature.chat.ai.LlmConfig

/** Fertig aufbereitete Bot-Konfiguration aus den App-Settings. */
data class ChatBotConfig(
    val channel: String,
    val login: String,
    val oauthToken: String,
    val systemPrompt: String,
    val mentionsOnly: Boolean,
    val replyCooldownMillis: Long,
    val maxRepliesPerMinute: Int,
    val maxReplyLength: Int = 500,
    val historySize: Int = 20,
    val mode: ChatBotMode = ChatBotMode.AUTONOMOUS,
    val llm: LlmConfig,
    // --- Koexistenz mit anderen Bots ---
    // Wer darf !-Befehle auslösen (siehe ChatBotCommandScope).
    val commandScope: ChatBotCommandScope = ChatBotCommandScope.ALL,
    // Eigenes Befehls-Präfix für den PREFIX-Scope (z. B. "v" → !v!help).
    val commandPrefix: String = "",
    // Logins anderer Bots (normalisiert, ohne '@'), deren Nachrichten ignoriert werden.
    val ignoreBots: Set<String> = emptySet(),
    // --- Begrenzungen (pro Viewer + Kosten) ---
    // Wartezeit pro Viewer nach einer Antwort (0 = aus). Mods umgehen das.
    val perViewerCooldownMillis: Long = 0L,
    // Max. Antworten pro Viewer pro Stream (0 = unbegrenzt). Mods umgehen das.
    val perViewerMaxReplies: Int = 0,
    // Kosten-Budget: max. Antworten pro Stunde global (0 = unbegrenzt).
    val maxRepliesPerHour: Int = 0,
    // --- Owner-Zugriff (nur der Streamer) ---
    // Logins (normalisiert, ohne '@'), die zusätzlich zum Broadcaster als
    // „Owner" gelten und die Owner-Befehle !start/!stop/!diag/!ask nutzen dürfen.
    val ownerLogins: Set<String> = emptySet(),
    // Separater LLM-Endpunkt, der **exklusiv** für die Owner-Befehle
    // (!start/!stop/!diag/!ask) erreichbar ist — z. B. ein leistungsfähigeres
    // oder eigenes Modell-Konto des Streamers. Leer = keine eigene Owner-KI:
    // die Owner-Befehle fallen dann auf die Viewer-KI ([llm]) zurück; nur wenn
    // auch die nicht konfiguriert ist, liefert !diag die deterministische
    // Checkliste und !ask einen Konfigurations-Hinweis.
    val ownerLlm: LlmConfig = LlmConfig(baseUrl = "", apiKey = "", model = ""),
    // Owner-Antworten privat per Twitch-Whisper statt öffentlich in den Chat
    // senden (Standard: an). Dafür muss der Bot-Token den Scope
    // user:manage:whispers haben und [twitchClientId] gesetzt sein; sonst
    // fällt die Antwort auf den öffentlichen Chat zurück.
    val ownerWhisperReplies: Boolean = true,
    // Twitch-App-Client-ID für die Helix-Whisper-API (nur nötig bei Whisper).
    val twitchClientId: String = "",
) {
    /** Ist die Owner-KI konfiguriert (Endpunkt + Key + Modell)? */
    val isOwnerLlmReady: Boolean
        get() = ownerLlm.isConfigured

    /**
     * Owner-Erkennung: Der Kanal-Inhaber (Broadcaster-Badge) ist immer Owner;
     * zusätzlich können weitere Logins in [ownerLogins] freigegeben werden
     * (z. B. der Zweitaccount des Streamers). [userLogin] wird normalisiert
     * verglichen (trim + lowercase, ohne '@').
     */
    fun isOwner(userLogin: String, isBroadcaster: Boolean): Boolean =
        isBroadcaster || userLogin.trim().lowercase().removePrefix("@") in ownerLogins
    /**
     * Startbereit? Kanal, Login und Token werden immer gebraucht — das LLM
     * nur im AUTONOMOUS-Modus. Im COMMAND-Modus (deterministische Befehle,
     * wie der Bot von Moblin) funktioniert der Bot auch ohne LLM-Schlüssel.
     */
    val isReady: Boolean
        get() = channel.isNotBlank() && login.isNotBlank() && oauthToken.isNotBlank() &&
            (mode == ChatBotMode.COMMAND || llm.isConfigured)

    companion object {
        fun fromSettings(settings: AppSettings): ChatBotConfig =
            ChatBotConfig(
                channel = settings.chatChannel.trim().lowercase(),
                login = settings.chatBotLogin.trim().lowercase(),
                oauthToken = settings.chatBotOauthToken.trim().removePrefix("oauth:"),
                systemPrompt = settings.chatBotSystemPrompt,
                mentionsOnly = settings.chatBotMentionsOnly,
                replyCooldownMillis = settings.chatBotReplyCooldownSeconds * 1000,
                maxRepliesPerMinute = settings.chatBotMaxRepliesPerMinute,
                mode = settings.chatBotMode,
                commandScope = settings.chatBotCommandScope,
                commandPrefix = settings.chatBotCommandPrefix.trim(),
                ignoreBots = settings.chatBotIgnoreBots
                    .split(',')
                    .map { it.trim().lowercase().removePrefix("@") }
                    .filter { it.isNotBlank() }
                    .toSet(),
                perViewerCooldownMillis = settings.chatBotPerViewerCooldownSeconds * 1000,
                perViewerMaxReplies = settings.chatBotPerViewerMaxReplies,
                maxRepliesPerHour = settings.chatBotMaxRepliesPerHour,
                ownerLogins = settings.chatBotOwnerLogins
                    .split(',')
                    .map { it.trim().lowercase().removePrefix("@") }
                    .filter { it.isNotBlank() }
                    .toSet(),
                ownerLlm = LlmConfig(
                    baseUrl = settings.chatBotOwnerLlmBaseUrl,
                    apiKey = settings.chatBotOwnerLlmApiKey,
                    model = settings.chatBotOwnerLlmModel,
                ),
                ownerWhisperReplies = settings.chatBotOwnerWhisperReplies,
                twitchClientId = settings.chatBotTwitchClientId.trim(),
                llm = LlmConfig(
                    baseUrl = settings.chatBotApiBaseUrl,
                    apiKey = settings.chatBotApiKey,
                    model = settings.chatBotModel,
                ),
            )
    }
}
