package com.vivid.feature.chat.twitch

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TwitchCommercialClientTest {
    @Test
    fun `clamps duration and sends commercial request`() = runTest {
        var length = ""
        val http = HttpClient(MockEngine { request ->
            length = request.url.parameters["length"].orEmpty()
            respond("", HttpStatusCode.NoContent)
        })
        TwitchCommercialClient(http).startCommercial(
            TwitchCommercialConfig("123", "oauth:token", "client"),
            999,
        )
        assertEquals("180", length)
    }

    @Test
    fun `rejects missing configuration`() = runTest {
        val client = TwitchCommercialClient(HttpClient(MockEngine { respond("", HttpStatusCode.NoContent) }))
        val error = runCatching {
            client.startCommercial(TwitchCommercialConfig("", "token", "client"))
        }.exceptionOrNull()
        assertTrue(error is TwitchCommercialException)
    }
}
