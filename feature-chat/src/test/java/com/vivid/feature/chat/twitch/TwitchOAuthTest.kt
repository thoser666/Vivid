package com.vivid.feature.chat.twitch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchOAuthTest {
    @Test
    fun `builds authorization request with pkce`() {
        val request = TwitchOAuth.authorizationRequest("client", listOf("user:read:chat"))
        assertTrue(request.url.contains("code_challenge_method=S256"))
        assertTrue(request.url.contains("client_id=client"))
        assertTrue(request.url.contains("redirect_uri=vivid%3A%2F%2Foauth%2Ftwitch"))
        assertTrue(request.url.contains("code_challenge="))
    }

    @Test
    fun `validates callback state and returns code`() {
        val callback = TwitchOAuth.parseCallback("vivid://oauth/twitch?code=abc&state=xyz")
        assertEquals("abc", TwitchOAuth.validateCallback(callback, "xyz"))
        assertThrows(TwitchOAuthException::class.java) {
            TwitchOAuth.validateCallback(callback, "wrong")
        }
    }

    @Test
    fun `rejects callbacks from unexpected URI`() {
        assertThrows(IllegalArgumentException::class.java) {
            TwitchOAuth.parseCallback("https://example.com/callback?code=abc")
        }
    }

    @Test
    fun `rejects incomplete token responses`() {
        assertThrows(TwitchOAuthException::class.java) {
            TwitchOAuth.validateTokenResponse("", "refresh", 3600)
        }
        assertThrows(TwitchOAuthException::class.java) {
            TwitchOAuth.validateTokenResponse("access", "refresh", 0)
        }
        assertEquals("access", TwitchOAuth.validateTokenResponse("access", "refresh", 3600).accessToken)
    }

    @Test
    fun `parses successful and failed callbacks`() {
        val success = TwitchOAuth.parseCallback("vivid://oauth/twitch?code=abc&state=xyz")
        assertEquals("abc", success.code)
        assertEquals("xyz", success.state)
        assertEquals(null, success.error)

        val failure = TwitchOAuth.parseCallback("vivid://oauth/twitch?error=access_denied")
        assertEquals("access_denied", failure.error)
    }
}
