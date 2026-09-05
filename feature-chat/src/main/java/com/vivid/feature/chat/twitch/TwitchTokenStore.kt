package com.vivid.feature.chat.twitch

/**
 * Persistiert die Twitch-OAuth-Session (Zugriffs- + Refresh-Token).
 *
 * Die Zugangsdaten werden verschlüsselt gespeichert; [loadSession] liefert
 * `null`, wenn keine gültige Session abgelegt wurde oder das Blob nicht
 * entschlüsselt werden kann (z. B. nach Keystore-Invalidierung).
 */
interface TwitchTokenStore {
    suspend fun loadSession(): TwitchTokenSession?
    suspend fun saveSession(session: TwitchTokenSession)
    suspend fun clear()
}