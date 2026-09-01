package com.vivid.feature.chat.twitch

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom

data class TwitchOAuthAuthorizationRequest(val url: String, val state: String, val verifier: String)
data class TwitchOAuthCallback(val code: String?, val state: String?, val error: String?)

object TwitchOAuth {
    const val REDIRECT_URI = "vivid://oauth/twitch"
    const val AUTHORIZATION_ENDPOINT = "https://id.twitch.tv/oauth2/authorize"
    private const val BASE64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"

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
        val query = URI(uri).rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.mapNotNull { pair ->
            val parts = pair.split('=', limit = 2)
            if (parts.size != 2) null else decode(parts[0]) to decode(parts[1])
        }.toMap()
        return TwitchOAuthCallback(query["code"], query["state"], query["error"] ?: query["error_description"])
    }

    @Suppress("NewApi")
    private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")
    @Suppress("NewApi")
    private fun decode(value: String): String = URLDecoder.decode(value, "UTF-8")

    private fun encodeUrl(bytes: ByteArray): String {
        val out = StringBuilder((bytes.size * 4 + 2) / 3)
        var value = 0
        var bits = -6
        for (byte in bytes) {
            value = (value shl 8) or (byte.toInt() and 0xff)
            bits += 8
            while (bits >= 0) {
                out.append(BASE64[(value shr bits) and 0x3f])
                bits -= 6
            }
        }
        if (bits > -6) out.append(BASE64[((value shl 8) shr (bits + 8)) and 0x3f])
        return out.toString()
    }

    private fun randomBytes(random: SecureRandom, size: Int): String {
        val bytes = ByteArray(size)
        random.nextBytes(bytes)
        return encodeUrl(bytes)
    }
}
