package com.vivid.feature.chat.twitch

import org.junit.jupiter.api.Assertions.assertEquals
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
    fun `parses successful and failed callbacks`() {
        val success = TwitchOAuth.parseCallback("vivid://oauth/twitch?code=abc&state=xyz")
        assertEquals("abc", success.code)
        assertEquals("xyz", success.state)
        assertEquals(null, success.error)

        val failure = TwitchOAuth.parseCallback("vivid://oauth/twitch?error=access_denied")
        assertEquals("access_denied", failure.error)
    }
}
