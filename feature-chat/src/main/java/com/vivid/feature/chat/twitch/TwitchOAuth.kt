package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class TwitchOAuthAuthorizationRequest(val url: String, val state: String, val verifier: String)
data class TwitchOAuthCallback(val code: String?, val state: String?, val error: String?)
data class TwitchOAuthTokenRequest(
    val clientId: String,
    val clientSecret: String? = null,
    val code: String,
    val redirectUri: String = TwitchOAuth.REDIRECT_URI,
    val verifier: String,
)

data class TwitchOAuthTokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val expiresInSeconds: Long,
    val scopes: List<String> = emptyList(),
)

class TwitchOAuthException(message: String) : IllegalArgumentException(message)

@Serializable
private data class TwitchTokenPayload(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresInSeconds: Long = 0,
    val scope: List<String> = emptyList(),
)

object TwitchOAuth {
    const val REDIRECT_URI = "vivid://oauth/twitch"
    const val AUTHORIZATION_ENDPOINT = "https://id.twitch.tv/oauth2/authorize"
    const val TOKEN_ENDPOINT = "https://id.twitch.tv/oauth2/token"
    private const val BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private val json = Json { ignoreUnknownKeys = true }

    fun authorizationRequest(clientId: String, scopes: List<String>, random: SecureRandom = SecureRandom()): TwitchOAuthAuthorizationRequest {
        require(clientId.isNotBlank()) { "clientId must not be blank" }
        val verifier = randomBytes(random, 32)
        val state = randomBytes(random, 24)
        val challenge = encodeUrl(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(StandardCharsets.US_ASCII)))
        val params = linkedMapOf(
            "client_id" to clientId.trim(), "redirect_uri" to REDIRECT_URI, "response_type" to "code",
            "scope" to scopes.joinToString(" "), "state" to state, "code_challenge" to challenge,
            "code_challenge_method" to "S256",
        )
        val query = params.entries.joinToString("&") { (key, value) -> "${encode(key)}=${encode(value)}" }
        return TwitchOAuthAuthorizationRequest("$AUTHORIZATION_ENDPOINT?$query", state, verifier)
    }

    fun parseCallback(uri: String): TwitchOAuthCallback {
        val parsed = URI(uri)
        require(parsed.scheme == "vivid" && parsed.host == "oauth" && parsed.path == "/twitch") {
            "Unexpected Twitch OAuth callback URI"
        }
        val query = parsed.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) null else decode(parts[0]) to decode(parts[1])
        }.toMap()
        return TwitchOAuthCallback(query["code"], query["state"], query["error"] ?: query["error_description"])
    }

    fun validateCallback(callback: TwitchOAuthCallback, expectedState: String): String {
        if (callback.error != null) throw TwitchOAuthException("Twitch OAuth abgelehnt: ${callback.error}")
        if (callback.state.isNullOrBlank() || !MessageDigest.isEqual(
                callback.state.toByteArray(StandardCharsets.UTF_8), expectedState.toByteArray(StandardCharsets.UTF_8),
            )
        ) throw TwitchOAuthException("Ungültiger OAuth-State; Anmeldung verworfen.")
        return callback.code?.takeIf { it.isNotBlank() }
            ?: throw TwitchOAuthException("Twitch OAuth lieferte keinen Authorization-Code.")
    }

    fun tokenRequest(clientId: String, code: String, verifier: String, clientSecret: String? = null): TwitchOAuthTokenRequest {
        if (clientId.isBlank() || code.isBlank() || verifier.isBlank()) {
            throw TwitchOAuthException("Client-ID, Code und PKCE-Verifier dürfen nicht leer sein.")
        }
        return TwitchOAuthTokenRequest(clientId.trim(), clientSecret?.trim()?.takeIf { it.isNotEmpty() }, code, REDIRECT_URI, verifier)
    }

    suspend fun exchangeCode(http: HttpClient, request: TwitchOAuthTokenRequest): TwitchOAuthTokenResponse {
        val response = http.submitForm(
            url = TOKEN_ENDPOINT,
            formParameters = Parameters.build {
                append("client_id", request.clientId)
                request.clientSecret?.let { append("client_secret", it) }
                append("code", request.code)
                append("grant_type", "authorization_code")
                append("redirect_uri", request.redirectUri)
                append("code_verifier", request.verifier)
            },
        )
        val body = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw TwitchOAuthException("Twitch-Token-Austausch fehlgeschlagen (HTTP ${response.status.value}).")
        }
        return try {
            val payload = json.decodeFromString<TwitchTokenPayload>(body)
            validateTokenResponse(payload.accessToken, payload.refreshToken, payload.expiresInSeconds, payload.scope)
        } catch (error: TwitchOAuthException) {
            throw error
        } catch (_: Exception) {
            throw TwitchOAuthException("Twitch lieferte eine ungültige Token-Antwort.")
        }
    }

    fun validateTokenResponse(accessToken: String, refreshToken: String, expiresInSeconds: Long, scopes: List<String> = emptyList()): TwitchOAuthTokenResponse {
        if (accessToken.isBlank() || refreshToken.isBlank()) throw TwitchOAuthException("Twitch lieferte kein vollständiges Token-Paar.")
        if (expiresInSeconds <= 0) throw TwitchOAuthException("Twitch lieferte eine ungültige Token-Laufzeit.")
        return TwitchOAuthTokenResponse(accessToken.trim(), refreshToken.trim(), expiresInSeconds, scopes)
    }

    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")
    private fun encodeUrl(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var value = 0
        var bits = -6
        for (byte in bytes) {
            value = (value shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 0) { out.append(BASE64[(value shr bits) and 0x3f]); bits -= 6 }
        }
        if (bits > -6) out.append(BASE64[((value shl 8) shr (bits + 8)) and 0x3f])
        return out.toString()
    }
    private fun randomBytes(random: SecureRandom, size: Int): String = ByteArray(size).also(random::nextBytes).let(::encodeUrl)
}
