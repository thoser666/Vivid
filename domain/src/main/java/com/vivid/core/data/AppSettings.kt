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
    // Betriebsmodus: COMMAND = deterministische !-Befehle (wie der Bot von Moblin,
    // kein LLM nötig); AUTONOMOUS = die KI entscheidet selbst, wann sie antwortet.
    val chatBotMode: ChatBotMode = ChatBotMode.AUTONOMOUS,
    // Twitch-Login des Bot-Kontos (ohne '@').
    val chatBotLogin: String = "",
    // Twitch-Chat-OAuth-Token des Bot-Kontos (user:read:chat + user:write:chat).
    val chatBotOauthToken: String = "",
    // --- Chat-Bot: Koexistenz mit anderen Bots (z. B. Rivulet) ---
    // Logins anderer Bots (kommasepariert, ohne '@'), deren Nachrichten
    // komplett ignoriert werden (keine Befehle, kein LLM-Input, kein TTS).
    val chatBotIgnoreBots: String = "",
    // Wer darf !-Befehle auslösen: ALL (jeder) | MENTION (nur @-Erwähnung) |
    // PREFIX (nur Befehle mit eigenem Präfix, z. B. "v" → !v!help).
    val chatBotCommandScope: ChatBotCommandScope = ChatBotCommandScope.ALL,
    // Eigenes Befehls-Präfix für den PREFIX-Scope (ohne '!', z. B. "v").
    val chatBotCommandPrefix: String = "",
    // --- Chat-Bot: Begrenzungen (pro Viewer + Kosten) ---
    // Wie lange ein Viewer nach einer Bot-Antwort warten muss, bevor der Bot
    // ihm erneut antwortet (0 = aus). Moderatoren umgehen das.
    val chatBotPerViewerCooldownSeconds: Long = 60,
    // Maximale Bot-Antworten pro Viewer pro Stream (0 = unbegrenzt).
    val chatBotPerViewerMaxReplies: Int = 0,
    // Kosten-Budget: maximale Bot-Antworten pro Stunde global (0 = unbegrenzt).
    val chatBotMaxRepliesPerHour: Int = 0,
    // Zuletzt gewählte Limit-Voreinstellung (LOCKER/BALANCED/STRICT) oder
    // "CUSTOM" bei manuell angepassten Werten — für die Wiederherstellung beim
    // App-Start (nur Auswahl-Marker, die Werte selbst liegen in den drei Feldern).
    // String statt Enum, damit das Domain-Modul nicht von feature-settings abhängt.
    val chatBotLimitPreset: String = "CUSTOM",
    // --- Chat-Bot: Owner-Zugriff (nur der Streamer) ---
    // Logins (kommasepariert, ohne '@'), die als „Owner" gelten und die
    // Owner-Befehle !start/!stop/!diag/!ask nutzen dürfen. Der Kanal-Inhaber
    // (Broadcaster-Badge) ist zusätzlich immer Owner. Leer = nur Broadcaster.
    val chatBotOwnerLogins: String = "",
    // Separater LLM-Endpunkt, exklusiv für Owner-Befehle (z. B. !ask, Diagnose
    // mit Empfehlungen). Leer = keine eigene Owner-KI → Fallback auf die
    // Viewer-KI; nur wenn auch die fehlt, liefern die Befehle deterministische
    // Antworten (Checkliste/Hinweis).
    val chatBotOwnerLlmBaseUrl: String = "",
    val chatBotOwnerLlmApiKey: String = "",
    val chatBotOwnerLlmModel: String = "",
    // Owner-Antworten privat per Twitch-Whisper statt öffentlich in den Chat
    // senden (Standard: an). Dafür muss der Bot-Token den Scope
    // user:manage:whispers haben und die Client-ID unten gesetzt sein; sonst
    // fällt die Antwort auf den öffentlichen Chat zurück.
    val chatBotOwnerWhisperReplies: Boolean = true,
    // Twitch-App-Client-ID (Pflicht-Header für die Helix-Whisper-API).
    val chatBotTwitchClientId: String = "",
    // --- Chat-Bot: Obszönitätsfilter (!ask) ---
    // Schaltet den Filter ein/aus (Nachrichten mit Obszönitäten werden vor
    // dem KI-Aufruf blockiert).
    val chatBotProfanityEnabled: Boolean = true,
    // Aktive Kategorien (kommasepariert): SLURS, SEXUAL, HOSTILITY, PROFANITY.
    val chatBotProfanityCategories: String = "SLURS,SEXUAL,HOSTILITY,PROFANITY",
    // Eigene Begriffe (kommasepariert), zusätzlich zur Basis-Liste.
    val chatBotProfanityCustomWords: String = "",
    // Begriffe ausschließen (kommasepariert, False Positives).
    val chatBotProfanityExcludedWords: String = "",
    // --- Text-/Info-Widget (Overlay) ---
    // Zeigt das Text-/Info-Widget über der Streaming-Vorschau (Uhrzeit, GPS, Geschwindigkeit).
    val widgetEnabled: Boolean = false,
    // Welche Felder das Widget anzeigt (unabhängig vom Haupt-Toggle).
    val widgetShowTime: Boolean = true,
    val widgetShowLocation: Boolean = true,
    val widgetShowSpeed: Boolean = true,
    val widgetShowAltitude: Boolean = false,
    // optionales Template mit Variablen ({time}, {date}, {speed}, {altitude}, {lat}, {lon}).
    val widgetTemplate: String = "",
    // --- Akku-Anzeige-Widget (Overlay) ---
    // Zeigt den Batteriestatus als Overlay über der Streaming-Vorschau.
    val batteryEnabled: Boolean = false,
    // Lade-Icon anzeigen (Standard: an).
    val batteryShowIcon: Boolean = true,
    // Prozentwert anzeigen (Standard: an).
    val batteryShowPercent: Boolean = true,
    // Schwellenwert für Low-Battery-Warnung im Chat (in %, Standard: 15).
    val batteryLowThresholdPercent: Int = 15,
    // --- Datenschutz (Sentry) ---
    // Fehler-/Crash-Berichte an Sentry senden (Standard: an). Aus = Opt-out:
    // beforeSend verwirft dann alle Events — es wird nichts an Sentry übertragen.
    val sentryEnabled: Boolean = true,
    // --- Darstellung (Theme) ---
    // Design-Modus: SYSTEM (System folgen) | LIGHT | DARK | AMOLED (schwarze Flächen).
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    // Kuratierte Akzentfarbe (Standard: Vivid-Grün — unverändert gegenüber Stufe 1).
    val themeAccent: AccentColor = AccentColor.VIVID_GREEN,
    // --- Third-Party-Emotes (Chat-Overlay) ---
    // Welche Third-Party-Emote-Quellen aktiv sind (Standard: alle drei).
    val emotesBttvEnabled: Boolean = true,
    val emotesFfzEnabled: Boolean = true,
    val emotes7tvEnabled: Boolean = true,
    // Gelöschte Nachrichten im Chat-Overlay ausblenden (Standard: an).
    val chatOverlayHideDeleted: Boolean = true,
    // Fade-In-Animation für neue Nachrichten im Chat-Overlay (Standard: an).
    val chatOverlayAnimateNewMessages: Boolean = true,
    // --- Chat-Layout-Einstellungen (Overlay) ---
    // Breite des Chat-Overlays in dp (100–400, Standard: 240).
    val chatOverlayWidthDp: Int = 240,
    // Maximale Höhe des Chat-Overlays in dp (100–600, Standard: 300).
    val chatOverlayHeightDp: Int = 300,
    // Hintergrund-Alpha (0.0–1.0, Standard: 0.5 = halbtransparent).
    val chatOverlayBackgroundAlpha: Float = 0.5f,
    // Schriftgröße in sp (8–20, Standard: 12).
    val chatOverlayFontSizeSp: Int = 12,
    // Zeitstempel anzeigen (Standard: an).
    val chatOverlayShowTimestamp: Boolean = true,
    // Username-Farbe (Hex, Standard: #B39DDB = Hell-Lila).
    val chatOverlayUsernameColorHex: String = "#B39DDB",
    // Text-Farbe (Hex, Standard: #FFFFFF = Weiß).
    val chatOverlayTextColorHex: String = "#FFFFFF",
    // Overlay-Hintergrund-Farbe (Hex, Standard: #000000 = Schwarz).
    val chatOverlayBackgroundColorHex: String = "#000000",
    // Position des Chat-Overlays (Standard: oben-rechts).
    val chatOverlayPosition: ChatOverlayPosition = ChatOverlayPosition.TOP_END,
    // --- Grid-Overlay (Positionierung) ---
    // Raster auf der Vorschau einblenden für einfacheres Positionieren von Widgets/Bildern.
    val gridOverlayEnabled: Boolean = false,
    // Rasterabstand in dp (Standard: 40).
    val gridOverlaySpacingDp: Int = 40,
    // --- Bild-Widget (Logo/Wasserzeichen) ---
    // Ein Bild auf dem Stream anzeigen (Logo, Wasserzeichen, Overlays).
    val imageWidgetEnabled: Boolean = false,
    // Content-URI des ausgewählten Bildes (SAF).
    val imageWidgetUri: String = "",
    // Größe des Bildes in dp (Standard: 100).
    val imageWidgetSizeDp: Int = 100,
    // Transparenz (0.0–1.0, Standard: 0.8).
    val imageWidgetOpacity: Float = 0.8f,
    // --- In-App-Logs ---
    // Vorhaltezeit der täglichen Log-Rotation in Tagen (1–30, Default 7):
    // Logs wechseln täglich, ältere Tage werden beim Laden/Prune gelöscht.
    val logsRetentionDays: Int = 7,
)
