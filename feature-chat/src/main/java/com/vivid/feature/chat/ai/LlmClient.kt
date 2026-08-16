package com.vivid.feature.chat.ai

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import javax.inject.Inject
import javax.inject.Singleton

/** Konfiguration für einen OpenAI-kompatiblen LLM-Endpunkt (Chat-Completions). */
data class LlmConfig(
    val baseUrl: String,
    val apiKey: String,
    val model: String,
    val temperature: Double = 0.7,
    val maxTokens: Int? = 256,
) {
    val isConfigured: Boolean
        get() = baseUrl.isNotBlank() && apiKey.isNotBlank() && model.isNotBlank()
}

interface LlmClient {
    /** Ruft den Assistenten auf und gibt die erste Antwort als Text zurück. */
    suspend fun complete(config: LlmConfig, messages: List<LlmMessage>): String
}

class LlmException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * OpenAI-kompatibler Chat-Completions-Client. Funktioniert mit jedem Endpunkt,
 * der das `/v1/chat/completions`-Schema spricht (OpenAI, Gemini, Groq,
 * DeepSeek, Ollama im LAN u. a.).
 */
@Singleton
class OpenAiCompatibleLlmClient @Inject constructor(
    private val http: HttpClient,
) : LlmClient {

    override suspend fun complete(config: LlmConfig, messages: List<LlmMessage>): String {
        if (!config.isConfigured) {
            throw LlmException("LLM nicht konfiguriert (Basis-URL, API-Key und Modell fehlen)")
        }
        try {
            val response = http.post("${config.baseUrl.trimEnd('/')}/v1/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
                setBody(
                    LlmCompletionRequest(
                        model = config.model,
                        messages = messages,
                        temperature = config.temperature,
                        max_tokens = config.maxTokens,
                    )
                )
            }
            if (!response.status.isSuccess()) {
                throw LlmException("LLM-Anfrage fehlgeschlagen (HTTP ${response.status.value})")
            }
            val completion = response.body<LlmCompletionResponse>()
            return completion.content ?: throw LlmException("LLM hat keine Antwort geliefert")
        } catch (e: LlmException) {
            throw e
        } catch (e: Exception) {
            throw LlmException("LLM-Anfrage fehlgeschlagen: ${e.message}", e)
        }
    }
}
