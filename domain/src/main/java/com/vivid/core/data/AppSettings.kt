package com.vivid.core.data

// Diese Datenklasse MUSS alle Felder enthalten, die die UI braucht.
data class AppSettings(
    val streamUrl: String = "",
    val streamKey: String = "",
    // false = rtmp:// (Klartext), true = rtmps:// (RTMP über TLS)
    val streamUseTls: Boolean = false,
    // Optionales zweites Stream-Ziel (Multi-Streaming). Leer = deaktiviert.
    val secondaryStreamUrl: String = "",
    val secondaryStreamKey: String = "",
    val secondaryStreamUseTls: Boolean = false,
    val obsHost: String = "localhost",
    val obsPort: String = "4455",
    val obsPassword: String = "",
    // false = ws:// (Standard-OBS-LAN ohne TLS), true = wss:// (Remote mit TLS)
    val obsUseTls: Boolean = false,
    // Twitch-Kanal für das Chat-Overlay (ohne '#').
    val chatChannel: String = "",
    // Zeigt das Chat-Overlay über der Streaming-Vorschau.
    val chatOverlayEnabled: Boolean = false,
    // --- Chat-Bot (KI) ---
    // Schaltet den automatischen KI-Chat-Bot ein (verbindet sich bei Streamstart).
    val chatBotEnabled: Boolean = false,
    // OpenAI-kompatibler Endpunkt (ohne Pfad, z. B. "https://api.openai.com").
    val chatBotApiBaseUrl: String = "https://api.openai.com",
    val chatBotApiKey: String = "",
    val chatBotModel: String = "gpt-4o-mini",
    val chatBotSystemPrompt: String = "",
    // Minimaler Abstand zwischen zwei Bot-Antworten in Sekunden.
    val chatBotReplyCooldownSeconds: Long = 8,
    // Antwortet nur, wenn der Bot direkt angesprochen wird.
    val chatBotMentionsOnly: Boolean = true,
    val chatBotMaxRepliesPerMinute: Int = 10,
    // Twitch-Login des Bot-Kontos (ohne '@').
    val chatBotLogin: String = "",
    // Twitch-Chat-OAuth-Token des Bot-Kontos (chat:read + chat:send).
    val chatBotOauthToken: String = "",
)
