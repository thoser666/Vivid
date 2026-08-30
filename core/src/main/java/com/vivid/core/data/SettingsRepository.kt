package com.vivid.core.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

// SettingsRepository.kt
@Singleton
class SettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    private object PrefKeys {
        val STREAM_URL = stringPreferencesKey("stream_url")
        val STREAM_KEY = stringPreferencesKey("stream_key")
        val STREAM_USE_TLS = booleanPreferencesKey("stream_use_tls")
        val SECONDARY_STREAM_URL = stringPreferencesKey("secondary_stream_url")
        val SECONDARY_STREAM_KEY = stringPreferencesKey("secondary_stream_key")
        val SECONDARY_STREAM_USE_TLS = booleanPreferencesKey("secondary_stream_use_tls")
        val OBS_HOST = stringPreferencesKey("obs_host")
        val OBS_PORT = stringPreferencesKey("obs_port")
        val OBS_PASSWORD = stringPreferencesKey("obs_password")
        val OBS_USE_TLS = booleanPreferencesKey("obs_use_tls")
        val CHAT_CHANNEL = stringPreferencesKey("chat_channel")
        val CHAT_OVERLAY_ENABLED = booleanPreferencesKey("chat_overlay_enabled")
        val CHAT_BOT_ENABLED = booleanPreferencesKey("chat_bot_enabled")
        val CHAT_BOT_API_BASE_URL = stringPreferencesKey("chat_bot_api_base_url")
        val CHAT_BOT_API_KEY = stringPreferencesKey("chat_bot_api_key")
        val CHAT_BOT_MODEL = stringPreferencesKey("chat_bot_model")
        val CHAT_BOT_SYSTEM_PROMPT = stringPreferencesKey("chat_bot_system_prompt")
        val CHAT_BOT_REPLY_COOLDOWN_SECONDS = longPreferencesKey("chat_bot_reply_cooldown_seconds")
        val CHAT_BOT_MENTIONS_ONLY = booleanPreferencesKey("chat_bot_mentions_only")
        val CHAT_BOT_MAX_REPLIES_PER_MINUTE = intPreferencesKey("chat_bot_max_replies_per_minute")
        val CHAT_BOT_MODE = stringPreferencesKey("chat_bot_mode")
        val CHAT_BOT_LOGIN = stringPreferencesKey("chat_bot_login")
        val CHAT_BOT_OAUTH_TOKEN = stringPreferencesKey("chat_bot_oauth_token")
        val CHAT_BOT_IGNORE_BOTS = stringPreferencesKey("chat_bot_ignore_bots")
        val CHAT_BOT_COMMAND_SCOPE = stringPreferencesKey("chat_bot_command_scope")
        val CHAT_BOT_COMMAND_PREFIX = stringPreferencesKey("chat_bot_command_prefix")
        val CHAT_BOT_PER_VIEWER_COOLDOWN_SECONDS = longPreferencesKey("chat_bot_per_viewer_cooldown_seconds")
        val CHAT_BOT_PER_VIEWER_MAX_REPLIES = intPreferencesKey("chat_bot_per_viewer_max_replies")
        val CHAT_BOT_MAX_REPLIES_PER_HOUR = intPreferencesKey("chat_bot_max_replies_per_hour")
        val CHAT_BOT_LIMIT_PRESET = stringPreferencesKey("chat_bot_limit_preset")
        val CHAT_BOT_OWNER_LOGINS = stringPreferencesKey("chat_bot_owner_logins")
        val CHAT_BOT_OWNER_LLM_BASE_URL = stringPreferencesKey("chat_bot_owner_llm_base_url")
        val CHAT_BOT_OWNER_LLM_API_KEY = stringPreferencesKey("chat_bot_owner_llm_api_key")
        val CHAT_BOT_OWNER_LLM_MODEL = stringPreferencesKey("chat_bot_owner_llm_model")
        val CHAT_BOT_OWNER_WHISPER_REPLIES = booleanPreferencesKey("chat_bot_owner_whisper_replies")
        val CHAT_BOT_TWITCH_CLIENT_ID = stringPreferencesKey("chat_bot_twitch_client_id")
        val CHAT_BOT_PROFANITY_ENABLED = booleanPreferencesKey("chat_bot_profanity_enabled")
        val CHAT_BOT_PROFANITY_CATEGORIES = stringPreferencesKey("chat_bot_profanity_categories")
        val CHAT_BOT_PROFANITY_CUSTOM_WORDS = stringPreferencesKey("chat_bot_profanity_custom_words")
        val CHAT_BOT_PROFANITY_EXCLUDED_WORDS = stringPreferencesKey("chat_bot_profanity_excluded_words")
        val WIDGET_ENABLED = booleanPreferencesKey("widget_enabled")
        val WIDGET_SHOW_TIME = booleanPreferencesKey("widget_show_time")
        val WIDGET_SHOW_LOCATION = booleanPreferencesKey("widget_show_location")
        val WIDGET_SHOW_SPEED = booleanPreferencesKey("widget_show_speed")
        val WIDGET_SHOW_ALTITUDE = booleanPreferencesKey("widget_show_altitude")
        val WIDGET_TEMPLATE = stringPreferencesKey("widget_template")
        val SENTRY_ENABLED = booleanPreferencesKey("sentry_enabled")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val THEME_ACCENT = stringPreferencesKey("theme_accent")
        val LOGS_RETENTION_DAYS = intPreferencesKey("logs_retention_days")
        val EMOTES_BTTV_ENABLED = booleanPreferencesKey("emotes_bttv_enabled")
        val EMOTES_FFZ_ENABLED = booleanPreferencesKey("emotes_ffz_enabled")
        val EMOTES_7TV_ENABLED = booleanPreferencesKey("emotes_7tv_enabled")
        val CHAT_OVERLAY_HIDE_DELETED = booleanPreferencesKey("chat_overlay_hide_deleted")
        val CHAT_OVERLAY_WIDTH_DP = intPreferencesKey("chat_overlay_width_dp")
        val CHAT_OVERLAY_HEIGHT_DP = intPreferencesKey("chat_overlay_height_dp")
        val CHAT_OVERLAY_BACKGROUND_ALPHA = floatPreferencesKey("chat_overlay_background_alpha")
        val CHAT_OVERLAY_FONT_SIZE_SP = intPreferencesKey("chat_overlay_font_size_sp")
        val CHAT_OVERLAY_SHOW_TIMESTAMP = booleanPreferencesKey("chat_overlay_show_timestamp")
        val CHAT_OVERLAY_USERNAME_COLOR_HEX = stringPreferencesKey("chat_overlay_username_color_hex")
        val CHAT_OVERLAY_TEXT_COLOR_HEX = stringPreferencesKey("chat_overlay_text_color_hex")
        val CHAT_OVERLAY_BACKGROUND_COLOR_HEX = stringPreferencesKey("chat_overlay_background_color_hex")
    }

    // WICHTIG: Dies ist jetzt der EINZIGE Flow, den das ViewModel braucht.
    // Er kombiniert die Daten für Stream (primär + sekundär), OBS, Chat,
    // Chat-Bot, Widget (innerer 5-Flow-Combine — combine hat nur typisierte
    // Overloads bis 5) sowie Darstellung + Sentry-Opt-out (äußerer Combine).
    val appSettingsFlow: Flow<AppSettings> = combine(
        combine(
        // Flow für Stream-Daten
        dataStore.data.map { prefs ->
            StreamPrefs(
                url = prefs[PrefKeys.STREAM_URL] ?: "",
                key = prefs[PrefKeys.STREAM_KEY] ?: "",
                useTls = prefs[PrefKeys.STREAM_USE_TLS] ?: false,
                secondaryUrl = prefs[PrefKeys.SECONDARY_STREAM_URL] ?: "",
                secondaryKey = prefs[PrefKeys.SECONDARY_STREAM_KEY] ?: "",
                secondaryUseTls = prefs[PrefKeys.SECONDARY_STREAM_USE_TLS] ?: false,
            )
        },
        // Flow für OBS-Daten
        dataStore.data.map { prefs ->
            ObsPrefs(
                host = prefs[PrefKeys.OBS_HOST] ?: "localhost",
                port = prefs[PrefKeys.OBS_PORT] ?: "4455",
                password = prefs[PrefKeys.OBS_PASSWORD] ?: "",
                useTls = prefs[PrefKeys.OBS_USE_TLS] ?: false,
            )
        },
        // Flow für Chat-Overlay-Daten
        dataStore.data.map { prefs ->
            ChatPrefs(
                channel = prefs[PrefKeys.CHAT_CHANNEL] ?: "",
                overlayEnabled = prefs[PrefKeys.CHAT_OVERLAY_ENABLED] ?: false,
            )
        },
        // Flow für Chat-Bot-Daten
        dataStore.data.map { prefs ->
            ChatBotPrefs(
                enabled = prefs[PrefKeys.CHAT_BOT_ENABLED] ?: false,
                apiBaseUrl = prefs[PrefKeys.CHAT_BOT_API_BASE_URL] ?: "https://api.openai.com",
                apiKey = prefs[PrefKeys.CHAT_BOT_API_KEY] ?: "",
                model = prefs[PrefKeys.CHAT_BOT_MODEL] ?: "gpt-4o-mini",
                systemPrompt = prefs[PrefKeys.CHAT_BOT_SYSTEM_PROMPT] ?: "",
                replyCooldownSeconds = prefs[PrefKeys.CHAT_BOT_REPLY_COOLDOWN_SECONDS] ?: 8L,
                mentionsOnly = prefs[PrefKeys.CHAT_BOT_MENTIONS_ONLY] ?: true,
                maxRepliesPerMinute = prefs[PrefKeys.CHAT_BOT_MAX_REPLIES_PER_MINUTE] ?: 10,
                mode = ChatBotMode.fromName(prefs[PrefKeys.CHAT_BOT_MODE]),
                login = prefs[PrefKeys.CHAT_BOT_LOGIN] ?: "",
                oauthToken = prefs[PrefKeys.CHAT_BOT_OAUTH_TOKEN] ?: "",
                ignoreBots = prefs[PrefKeys.CHAT_BOT_IGNORE_BOTS] ?: "",
                commandScope = ChatBotCommandScope.fromName(prefs[PrefKeys.CHAT_BOT_COMMAND_SCOPE]),
                commandPrefix = prefs[PrefKeys.CHAT_BOT_COMMAND_PREFIX] ?: "",
                perViewerCooldownSeconds = prefs[PrefKeys.CHAT_BOT_PER_VIEWER_COOLDOWN_SECONDS] ?: 60L,
                perViewerMaxReplies = prefs[PrefKeys.CHAT_BOT_PER_VIEWER_MAX_REPLIES] ?: 0,
                maxRepliesPerHour = prefs[PrefKeys.CHAT_BOT_MAX_REPLIES_PER_HOUR] ?: 0,
                limitPreset = prefs[PrefKeys.CHAT_BOT_LIMIT_PRESET] ?: "CUSTOM",
                ownerLogins = prefs[PrefKeys.CHAT_BOT_OWNER_LOGINS] ?: "",
                ownerLlmBaseUrl = prefs[PrefKeys.CHAT_BOT_OWNER_LLM_BASE_URL] ?: "",
                ownerLlmApiKey = prefs[PrefKeys.CHAT_BOT_OWNER_LLM_API_KEY] ?: "",
                ownerLlmModel = prefs[PrefKeys.CHAT_BOT_OWNER_LLM_MODEL] ?: "",
                ownerWhisperReplies = prefs[PrefKeys.CHAT_BOT_OWNER_WHISPER_REPLIES] ?: true,
                twitchClientId = prefs[PrefKeys.CHAT_BOT_TWITCH_CLIENT_ID] ?: "",
                profanityEnabled = prefs[PrefKeys.CHAT_BOT_PROFANITY_ENABLED] ?: true,
                profanityCategories = prefs[PrefKeys.CHAT_BOT_PROFANITY_CATEGORIES] ?: "SLURS,SEXUAL,HOSTILITY,PROFANITY",
                profanityCustomWords = prefs[PrefKeys.CHAT_BOT_PROFANITY_CUSTOM_WORDS] ?: "",
                profanityExcludedWords = prefs[PrefKeys.CHAT_BOT_PROFANITY_EXCLUDED_WORDS] ?: "",
            )
        },
        // Flow für Widget-Daten (Text-/Info-Widget)
        dataStore.data.map { prefs ->
            WidgetPrefs(
                enabled = prefs[PrefKeys.WIDGET_ENABLED] ?: false,
                showTime = prefs[PrefKeys.WIDGET_SHOW_TIME] ?: true,
                showLocation = prefs[PrefKeys.WIDGET_SHOW_LOCATION] ?: true,
                showSpeed = prefs[PrefKeys.WIDGET_SHOW_SPEED] ?: true,
                showAltitude = prefs[PrefKeys.WIDGET_SHOW_ALTITUDE] ?: false,
                template = prefs[PrefKeys.WIDGET_TEMPLATE] ?: "",
            )
        },
    ) { streamData, obsData, chatData, chatBotData, widgetData ->
        // Baue das komplette AppSettings-Objekt zusammen
        AppSettings(
            streamUrl = streamData.url,
            streamKey = streamData.key,
            streamUseTls = streamData.useTls,
            secondaryStreamUrl = streamData.secondaryUrl,
            secondaryStreamKey = streamData.secondaryKey,
            secondaryStreamUseTls = streamData.secondaryUseTls,
            obsHost = obsData.host,
            obsPort = obsData.port,
            obsPassword = obsData.password,
            obsUseTls = obsData.useTls,
            chatChannel = chatData.channel,
            chatOverlayEnabled = chatData.overlayEnabled,
            chatBotEnabled = chatBotData.enabled,
            chatBotApiBaseUrl = chatBotData.apiBaseUrl,
            chatBotApiKey = chatBotData.apiKey,
            chatBotModel = chatBotData.model,
            chatBotSystemPrompt = chatBotData.systemPrompt,
            chatBotReplyCooldownSeconds = chatBotData.replyCooldownSeconds,
            chatBotMentionsOnly = chatBotData.mentionsOnly,
            chatBotMaxRepliesPerMinute = chatBotData.maxRepliesPerMinute,
            chatBotMode = chatBotData.mode,
            chatBotLogin = chatBotData.login,
            chatBotOauthToken = chatBotData.oauthToken,
            chatBotIgnoreBots = chatBotData.ignoreBots,
            chatBotCommandScope = chatBotData.commandScope,
            chatBotCommandPrefix = chatBotData.commandPrefix,
            chatBotPerViewerCooldownSeconds = chatBotData.perViewerCooldownSeconds,
            chatBotPerViewerMaxReplies = chatBotData.perViewerMaxReplies,
            chatBotMaxRepliesPerHour = chatBotData.maxRepliesPerHour,
            chatBotLimitPreset = chatBotData.limitPreset,
            chatBotOwnerLogins = chatBotData.ownerLogins,
            chatBotOwnerLlmBaseUrl = chatBotData.ownerLlmBaseUrl,
            chatBotOwnerLlmApiKey = chatBotData.ownerLlmApiKey,
            chatBotOwnerLlmModel = chatBotData.ownerLlmModel,
            chatBotOwnerWhisperReplies = chatBotData.ownerWhisperReplies,
            chatBotTwitchClientId = chatBotData.twitchClientId,
            chatBotProfanityEnabled = chatBotData.profanityEnabled,
            chatBotProfanityCategories = chatBotData.profanityCategories,
            chatBotProfanityCustomWords = chatBotData.profanityCustomWords,
            chatBotProfanityExcludedWords = chatBotData.profanityExcludedWords,
            widgetEnabled = widgetData.enabled,
            widgetShowTime = widgetData.showTime,
            widgetShowLocation = widgetData.showLocation,
            widgetShowSpeed = widgetData.showSpeed,
            widgetShowAltitude = widgetData.showAltitude,
            widgetTemplate = widgetData.template,
        )
    },
        // 6. Flow: Darstellung (Theme-Modus + Akzentfarbe)
        dataStore.data.map { prefs ->
            ThemePrefs(
                mode = ThemeMode.fromName(prefs[PrefKeys.THEME_MODE]),
                accent = AccentColor.fromName(prefs[PrefKeys.THEME_ACCENT]),
            )
        },
        // 7. Flow: Sentry-Opt-out (Datenschutz) — Default: an
        dataStore.data.map { prefs -> prefs[PrefKeys.SENTRY_ENABLED] ?: true },
        // 8. Flow: In-App-Log-Vorhaltezeit (Tage)
        dataStore.data.map { prefs -> prefs[PrefKeys.LOGS_RETENTION_DAYS] ?: 7 },
        // 9. Flow: Third-Party-Emote-Quellen (BTTV/FFZ/7TV)
        dataStore.data.map { prefs ->
            EmotePrefs(
                bttvEnabled = prefs[PrefKeys.EMOTES_BTTV_ENABLED] ?: true,
                ffzEnabled = prefs[PrefKeys.EMOTES_FFZ_ENABLED] ?: true,
                sevenTvEnabled = prefs[PrefKeys.EMOTES_7TV_ENABLED] ?: true,
                chatOverlayHideDeleted = prefs[PrefKeys.CHAT_OVERLAY_HIDE_DELETED] ?: true,
                chatOverlayWidthDp = prefs[PrefKeys.CHAT_OVERLAY_WIDTH_DP] ?: 240,
                chatOverlayHeightDp = prefs[PrefKeys.CHAT_OVERLAY_HEIGHT_DP] ?: 300,
                chatOverlayBackgroundAlpha = prefs[PrefKeys.CHAT_OVERLAY_BACKGROUND_ALPHA] ?: 0.5f,
                chatOverlayFontSizeSp = prefs[PrefKeys.CHAT_OVERLAY_FONT_SIZE_SP] ?: 12,
                chatOverlayShowTimestamp = prefs[PrefKeys.CHAT_OVERLAY_SHOW_TIMESTAMP] ?: true,
                chatOverlayUsernameColorHex = prefs[PrefKeys.CHAT_OVERLAY_USERNAME_COLOR_HEX] ?: "#B39DDB",
                chatOverlayTextColorHex = prefs[PrefKeys.CHAT_OVERLAY_TEXT_COLOR_HEX] ?: "#FFFFFF",
                chatOverlayBackgroundColorHex = prefs[PrefKeys.CHAT_OVERLAY_BACKGROUND_COLOR_HEX] ?: "#000000",
            )
        },
    ) { settings, themeData, sentryEnabled, logsRetentionDays, emotePrefs ->
        settings.copy(
            sentryEnabled = sentryEnabled,
            themeMode = themeData.mode,
            themeAccent = themeData.accent,
            logsRetentionDays = logsRetentionDays,
            emotesBttvEnabled = emotePrefs.bttvEnabled,
            emotesFfzEnabled = emotePrefs.ffzEnabled,
            emotes7tvEnabled = emotePrefs.sevenTvEnabled,
            chatOverlayHideDeleted = emotePrefs.chatOverlayHideDeleted,
            chatOverlayWidthDp = emotePrefs.chatOverlayWidthDp,
            chatOverlayHeightDp = emotePrefs.chatOverlayHeightDp,
            chatOverlayBackgroundAlpha = emotePrefs.chatOverlayBackgroundAlpha,
            chatOverlayFontSizeSp = emotePrefs.chatOverlayFontSizeSp,
            chatOverlayShowTimestamp = emotePrefs.chatOverlayShowTimestamp,
            chatOverlayUsernameColorHex = emotePrefs.chatOverlayUsernameColorHex,
            chatOverlayTextColorHex = emotePrefs.chatOverlayTextColorHex,
            chatOverlayBackgroundColorHex = emotePrefs.chatOverlayBackgroundColorHex,
        )
    }

    // Update-Funktionen bleiben getrennt, das ist in Ordnung.
    // useTls: false = rtmp:// (Klartext), true = rtmps:// (RTMP über TLS).
    suspend fun updateStreamSettings(url: String, key: String, useTls: Boolean = false) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.STREAM_URL] = url
            prefs[PrefKeys.STREAM_KEY] = key
            prefs[PrefKeys.STREAM_USE_TLS] = useTls
        }
    }

    // Zweites (optionales) Stream-Ziel für Multi-Streaming.
    suspend fun updateSecondaryStreamSettings(url: String, key: String, useTls: Boolean = false) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.SECONDARY_STREAM_URL] = url
            prefs[PrefKeys.SECONDARY_STREAM_KEY] = key
            prefs[PrefKeys.SECONDARY_STREAM_USE_TLS] = useTls
        }
    }

    // useTls: false = ws:// (Standard-OBS-LAN), true = wss:// (Remote mit TLS).
    // WICHTIG: immer explizit übergeben, sonst wird ein gespeichertes wss://
    // still auf ws:// zurückgesetzt.
    suspend fun updateObsSettings(host: String, port: String, password: String, useTls: Boolean = false) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.OBS_HOST] = host
            prefs[PrefKeys.OBS_PORT] = port
            prefs[PrefKeys.OBS_PASSWORD] = password
            prefs[PrefKeys.OBS_USE_TLS] = useTls
        }
    }

    suspend fun updateChatSettings(channel: String, overlayEnabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_CHANNEL] = channel
            prefs[PrefKeys.CHAT_OVERLAY_ENABLED] = overlayEnabled
        }
    }

    suspend fun updateChatBotSettings(
        enabled: Boolean,
        apiBaseUrl: String,
        apiKey: String,
        model: String,
        systemPrompt: String,
        replyCooldownSeconds: Long,
        mentionsOnly: Boolean,
        maxRepliesPerMinute: Int,
        mode: ChatBotMode = ChatBotMode.AUTONOMOUS,
        login: String,
        oauthToken: String,
        ignoreBots: String = "",
        commandScope: ChatBotCommandScope = ChatBotCommandScope.ALL,
        commandPrefix: String = "",
        perViewerCooldownSeconds: Long = 60L,
        perViewerMaxReplies: Int = 0,
        maxRepliesPerHour: Int = 0,
        limitPreset: String = "CUSTOM",
        ownerLogins: String = "",
        ownerLlmBaseUrl: String = "",
        ownerLlmApiKey: String = "",
        ownerLlmModel: String = "",
        ownerWhisperReplies: Boolean = true,
        twitchClientId: String = "",
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_BOT_ENABLED] = enabled
            prefs[PrefKeys.CHAT_BOT_API_BASE_URL] = apiBaseUrl
            prefs[PrefKeys.CHAT_BOT_API_KEY] = apiKey
            prefs[PrefKeys.CHAT_BOT_MODEL] = model
            prefs[PrefKeys.CHAT_BOT_SYSTEM_PROMPT] = systemPrompt
            prefs[PrefKeys.CHAT_BOT_REPLY_COOLDOWN_SECONDS] = replyCooldownSeconds
            prefs[PrefKeys.CHAT_BOT_MENTIONS_ONLY] = mentionsOnly
            prefs[PrefKeys.CHAT_BOT_MAX_REPLIES_PER_MINUTE] = maxRepliesPerMinute
            prefs[PrefKeys.CHAT_BOT_MODE] = mode.name
            prefs[PrefKeys.CHAT_BOT_LOGIN] = login
            prefs[PrefKeys.CHAT_BOT_OAUTH_TOKEN] = oauthToken
            prefs[PrefKeys.CHAT_BOT_IGNORE_BOTS] = ignoreBots
            prefs[PrefKeys.CHAT_BOT_COMMAND_SCOPE] = commandScope.name
            prefs[PrefKeys.CHAT_BOT_COMMAND_PREFIX] = commandPrefix
            prefs[PrefKeys.CHAT_BOT_PER_VIEWER_COOLDOWN_SECONDS] = perViewerCooldownSeconds
            prefs[PrefKeys.CHAT_BOT_PER_VIEWER_MAX_REPLIES] = perViewerMaxReplies
            prefs[PrefKeys.CHAT_BOT_MAX_REPLIES_PER_HOUR] = maxRepliesPerHour
            prefs[PrefKeys.CHAT_BOT_LIMIT_PRESET] = limitPreset
            prefs[PrefKeys.CHAT_BOT_OWNER_LOGINS] = ownerLogins
            prefs[PrefKeys.CHAT_BOT_OWNER_LLM_BASE_URL] = ownerLlmBaseUrl
            prefs[PrefKeys.CHAT_BOT_OWNER_LLM_API_KEY] = ownerLlmApiKey
            prefs[PrefKeys.CHAT_BOT_OWNER_LLM_MODEL] = ownerLlmModel
            prefs[PrefKeys.CHAT_BOT_OWNER_WHISPER_REPLIES] = ownerWhisperReplies
            prefs[PrefKeys.CHAT_BOT_TWITCH_CLIENT_ID] = twitchClientId
        }
    }

    private data class StreamPrefs(
        val url: String,
        val key: String,
        val useTls: Boolean,
        val secondaryUrl: String,
        val secondaryKey: String,
        val secondaryUseTls: Boolean,
    )

    private data class ObsPrefs(
        val host: String,
        val port: String,
        val password: String,
        val useTls: Boolean,
    )

    private data class ChatPrefs(
        val channel: String,
        val overlayEnabled: Boolean,
    )

    private data class ChatBotPrefs(
        val enabled: Boolean,
        val apiBaseUrl: String,
        val apiKey: String,
        val model: String,
        val systemPrompt: String,
        val replyCooldownSeconds: Long,
        val mentionsOnly: Boolean,
        val maxRepliesPerMinute: Int,
        val mode: ChatBotMode,
        val login: String,
        val oauthToken: String,
        val ignoreBots: String,
        val commandScope: ChatBotCommandScope,
        val commandPrefix: String,
        val perViewerCooldownSeconds: Long,
        val perViewerMaxReplies: Int,
        val maxRepliesPerHour: Int,
        val limitPreset: String,
        val ownerLogins: String,
        val ownerLlmBaseUrl: String,
        val ownerLlmApiKey: String,
        val ownerLlmModel: String,
        val ownerWhisperReplies: Boolean,
        val twitchClientId: String,
        val profanityEnabled: Boolean,
        val profanityCategories: String,
        val profanityCustomWords: String,
        val profanityExcludedWords: String,
    )

    private data class WidgetPrefs(
        val enabled: Boolean,
        val showTime: Boolean,
        val showLocation: Boolean,
        val showSpeed: Boolean,
        val showAltitude: Boolean,
        val template: String,
    )

    private data class ThemePrefs(
        val mode: ThemeMode,
        val accent: AccentColor,
    )

    private data class EmotePrefs(
        val bttvEnabled: Boolean,
        val ffzEnabled: Boolean,
        val sevenTvEnabled: Boolean,
        val chatOverlayHideDeleted: Boolean,
        val chatOverlayWidthDp: Int,
        val chatOverlayHeightDp: Int,
        val chatOverlayBackgroundAlpha: Float,
        val chatOverlayFontSizeSp: Int,
        val chatOverlayShowTimestamp: Boolean,
        val chatOverlayUsernameColorHex: String,
        val chatOverlayTextColorHex: String,
        val chatOverlayBackgroundColorHex: String,
    )

    /** Sentry-Opt-out speichern (false = keine Fehlerberichte senden). */
    suspend fun updateSentryEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.SENTRY_ENABLED] = enabled
        }
    }

    /** Darstellung speichern: Design-Modus + Akzentfarbe. */
    suspend fun updateThemeSettings(themeMode: ThemeMode, accentColor: AccentColor) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.THEME_MODE] = themeMode.name
            prefs[PrefKeys.THEME_ACCENT] = accentColor.name
        }
    }

    /** Text-/Info-Widget-Einstellungen speichern. */
    suspend fun updateWidgetSettings(
        enabled: Boolean,
        showTime: Boolean,
        showLocation: Boolean,
        showSpeed: Boolean,
        showAltitude: Boolean,
        template: String = "",
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.WIDGET_ENABLED] = enabled
            prefs[PrefKeys.WIDGET_SHOW_TIME] = showTime
            prefs[PrefKeys.WIDGET_SHOW_LOCATION] = showLocation
            prefs[PrefKeys.WIDGET_SHOW_SPEED] = showSpeed
            prefs[PrefKeys.WIDGET_SHOW_ALTITUDE] = showAltitude
            prefs[PrefKeys.WIDGET_TEMPLATE] = template
        }
    }

    /** Nur das Widget-Template aktualisieren. */
    suspend fun updateWidgetTemplate(template: String) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.WIDGET_TEMPLATE] = template
        }
    }

    /** Vorhaltezeit der täglichen Log-Rotation speichern (1–30 Tage, Default 7). */
    suspend fun updateLogsRetentionDays(days: Int) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.LOGS_RETENTION_DAYS] = days.coerceIn(1, 30)
        }
    }

    /** Third-Party-Emote-Quellen (BTTV/FFZ/7TV) speichern. */
    suspend fun updateEmoteSettings(
        bttvEnabled: Boolean,
        ffzEnabled: Boolean,
        sevenTvEnabled: Boolean,
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.EMOTES_BTTV_ENABLED] = bttvEnabled
            prefs[PrefKeys.EMOTES_FFZ_ENABLED] = ffzEnabled
            prefs[PrefKeys.EMOTES_7TV_ENABLED] = sevenTvEnabled
        }
    }

    /** Gelöschte Nachrichten im Chat-Overlay ausblenden. */
    suspend fun updateChatOverlayHideDeleted(hideDeleted: Boolean) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_OVERLAY_HIDE_DELETED] = hideDeleted
        }
    }

    /** Chat-Overlay-Layout-Einstellungen speichern. */
    suspend fun updateChatOverlayLayout(
        widthDp: Int,
        heightDp: Int,
        backgroundAlpha: Float,
        fontSizeSp: Int,
        showTimestamp: Boolean,
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_OVERLAY_WIDTH_DP] = widthDp.coerceIn(100, 400)
            prefs[PrefKeys.CHAT_OVERLAY_HEIGHT_DP] = heightDp.coerceIn(100, 600)
            prefs[PrefKeys.CHAT_OVERLAY_BACKGROUND_ALPHA] = backgroundAlpha.coerceIn(0f, 1f)
            prefs[PrefKeys.CHAT_OVERLAY_FONT_SIZE_SP] = fontSizeSp.coerceIn(8, 20)
            prefs[PrefKeys.CHAT_OVERLAY_SHOW_TIMESTAMP] = showTimestamp
        }
    }

    /** Chat-Overlay-Farben speichern. */
    suspend fun updateChatOverlayColors(
        usernameColorHex: String,
        textColorHex: String,
        backgroundColorHex: String,
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_OVERLAY_USERNAME_COLOR_HEX] = usernameColorHex.takeIf { it.startsWith("#") && it.length == 7 } ?: "#B39DDB"
            prefs[PrefKeys.CHAT_OVERLAY_TEXT_COLOR_HEX] = textColorHex.takeIf { it.startsWith("#") && it.length == 7 } ?: "#FFFFFF"
            prefs[PrefKeys.CHAT_OVERLAY_BACKGROUND_COLOR_HEX] = backgroundColorHex.takeIf { it.startsWith("#") && it.length == 7 } ?: "#000000"
        }
    }

    /** Chat-Bot Obszönitätsfilter-Einstellungen speichern. */
    suspend fun updateProfanitySettings(
        profanityEnabled: Boolean,
        profanityCategories: String,
        profanityCustomWords: String,
        profanityExcludedWords: String,
    ) {
        dataStore.edit { prefs ->
            prefs[PrefKeys.CHAT_BOT_PROFANITY_ENABLED] = profanityEnabled
            prefs[PrefKeys.CHAT_BOT_PROFANITY_CATEGORIES] = profanityCategories
            prefs[PrefKeys.CHAT_BOT_PROFANITY_CUSTOM_WORDS] = profanityCustomWords
            prefs[PrefKeys.CHAT_BOT_PROFANITY_EXCLUDED_WORDS] = profanityExcludedWords
        }
    }
}
