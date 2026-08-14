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
    }

    // WICHTIG: Dies ist jetzt der EINZIGE Flow, den das ViewModel braucht.
    // Er kombiniert die Daten für Stream (primär + sekundär) und OBS.
    val appSettingsFlow: Flow<AppSettings> = combine(
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
    ) { streamData, obsData, chatData ->
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
}
