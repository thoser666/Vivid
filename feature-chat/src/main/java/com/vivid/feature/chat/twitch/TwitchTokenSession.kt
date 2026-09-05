package com.vivid.feature.chat.twitch

/**
 * Gespeicherte Twitch-OAuth-Session.
 *
 * [accessToken] und [refreshToken] werden verschlüsselt persistiert
 * ([TwitchTokenStore]); [expiresAtMillis] ist der Ablaufzeitpunkt des
 * Access-Tokens (Epochen-Millis, aus `expires_in` berechnet).
 */
data class TwitchTokenSession(
    val accessToken: String,
    val refreshToken: String,
    val expiresAtMillis: Long,
    val scopes: List<String> = emptyList(),
) {
    val isExpired: Boolean
        get() = expiresAtMillis <= System.currentTimeMillis()

    companion object {
        /** Baut eine Session aus einer frischen Token-Antwort (Laufzeit ab jetzt). */
        fun from(
            response: TwitchOAuthTokenResponse,
            nowMillis: Long = System.currentTimeMillis(),
        ): TwitchTokenSession = TwitchTokenSession(
            accessToken = response.accessToken,
            refreshToken = response.refreshToken,
            expiresAtMillis = nowMillis + response.expiresInSeconds * 1000L,
            scopes = response.scopes,
        )
    }
}