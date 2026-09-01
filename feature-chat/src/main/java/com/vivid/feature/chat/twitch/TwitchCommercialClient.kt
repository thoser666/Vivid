package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.http.HttpHeaders
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

data class TwitchCommercialConfig(
    val broadcasterId: String,
    val oauthToken: String,
    val clientId: String,
) {
    val isConfigured: Boolean
        get() = broadcasterId.isNotBlank() && oauthToken.isNotBlank() && clientId.isNotBlank()
}

class TwitchCommercialException(override val message: String) : Exception(message)

@Singleton
/** Startet optionale Twitch-Werbeunterbrechungen für Affiliate-/Partner-Kanäle. */
class TwitchCommercialClient @Inject constructor(
    private val http: HttpClient,
) {
    suspend fun startCommercial(config: TwitchCommercialConfig, lengthSeconds: Int = 60) {
        if (!config.isConfigured) throw TwitchCommercialException("Start-Ads sind nicht konfiguriert.")
        val length = lengthSeconds.coerceIn(30, 180)
        val response = http.post("$HELIX_API/channels/commercial") {
            parameter("broadcaster_id", config.broadcasterId)
            parameter("length", length)
            header(HttpHeaders.Authorization, "Bearer ${config.oauthToken.trim().removePrefix("oauth:")}")
            header("Client-Id", config.clientId.trim())
        }
        if (!response.status.isSuccess()) {
            val reason = when (response.status.value) {
                401 -> "OAuth-Token oder Client-ID ungültig."
                403 -> "Kanal ist kein Affiliate/Partner oder der Scope channel:edit:commercial fehlt."
                429 -> "Twitch-Rate-Limit erreicht."
                else -> "Twitch antwortete mit HTTP ${response.status.value}."
            }
            throw TwitchCommercialException("Start-Ad konnte nicht ausgelöst werden: $reason")
        }
    }

    private companion object { const val HELIX_API = "https://api.twitch.tv/helix" }
}
