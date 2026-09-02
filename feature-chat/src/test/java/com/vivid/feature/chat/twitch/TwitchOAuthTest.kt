package com.vivid.feature.chat.twitch

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
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
    fun `exchanges authorization code as form request`() = runTest {
        var requestBody = ""
        val http = HttpClient(MockEngine { request ->
            requestBody = request.body.toString()
            respond(
                """{"access_token":"access","refresh_token":"refresh","expires_in":3600,"scope":["user:read:chat"]}""",
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val result = TwitchOAuth.exchangeCode(http, TwitchOAuth.tokenRequest("client", "code", "verifier"))
        assertEquals("access", result.accessToken)
        assertEquals("refresh", result.refreshToken)
        assertTrue(requestBody.isNotBlank())
        http.close()
    }

    @Test
    fun `rejects unsuccessful token exchange`() = runTest {
        val http = HttpClient(MockEngine { request ->
            respond("{}", HttpStatusCode.BadRequest)
        })
        assertThrows(TwitchOAuthException::class.java) {
            kotlinx.coroutines.runBlocking {
                TwitchOAuth.exchangeCode(http, TwitchOAuth.tokenRequest("client", "code", "verifier"))
            }
        }
        http.close()
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
