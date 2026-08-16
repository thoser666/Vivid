package com.vivid.feature.chat.ai

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OpenAiCompatibleLlmClientTest {

    private val config = LlmConfig(
        baseUrl = "https://llm.example",
        apiKey = "secret-key",
        model = "test-model",
    )

    private fun client(engine: MockEngine): OpenAiCompatibleLlmClient =
        OpenAiCompatibleLlmClient(
            HttpClient(engine) {
                install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
            }
        )

    @Test
    fun `sends bearer auth and returns the assistant content`() = runTest {
        var requested: String? = null
        var authorization: String? = null
        val engine = MockEngine { request ->
            requested = request.body.toByteArray().toString(Charsets.UTF_8)
            authorization = request.headers[HttpHeaders.Authorization]
            respond(
                content = """{"choices":[{"message":{"role":"assistant","content":"Hallo!"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        val result = client(engine).complete(
            config,
            listOf(LlmMessage(LlmMessage.ROLE_USER, "Hi")),
        )

        assertEquals("Hallo!", result)
        assertEquals("Bearer secret-key", authorization)
        assertTrue(requested!!.contains("\"model\":\"test-model\""))
        assertTrue(requested!!.contains("\"role\":\"user\""))
        assertTrue(requested!!.contains("\"content\":\"Hi\""))
    }

    @Test
    fun `posts to the v1 chat completions endpoint`() = runTest {
        var url: String? = null
        val engine = MockEngine { request ->
            url = request.url.toString()
            respond(
                content = """{"choices":[{"message":{"role":"assistant","content":"ok"}}]}""",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        client(engine).complete(config, emptyList())

        assertEquals("https://llm.example/v1/chat/completions", url)
    }

    @Test
    fun `throws when the http status is not successful`() = runTest {
        val engine = MockEngine { respond("{}", HttpStatusCode.BadRequest) }

        val exception = runCatching { client(engine).complete(config, emptyList()) }.exceptionOrNull()

        assertTrue(exception is LlmException)
    }

    @Test
    fun `throws when the model returns no choices`() = runTest {
        val engine = MockEngine { respond("""{"choices":[]}""") }

        val exception = runCatching { client(engine).complete(config, emptyList()) }.exceptionOrNull()

        assertTrue(exception is LlmException)
    }

    @Test
    fun `throws when the configuration is incomplete`() = runTest {
        val engine = MockEngine { respond("""{"choices":[]}""") }
        val brokenConfig = config.copy(apiKey = "")

        val exception = runCatching { client(engine).complete(brokenConfig, emptyList()) }.exceptionOrNull()

        assertTrue(exception is LlmException)
    }
}
