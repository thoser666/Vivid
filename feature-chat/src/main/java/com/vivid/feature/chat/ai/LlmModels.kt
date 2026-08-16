package com.vivid.feature.chat.ai

import kotlinx.serialization.Serializable

@Serializable
data class LlmMessage(
    val role: String,
    val content: String,
) {
    companion object {
        const val ROLE_SYSTEM = "system"
        const val ROLE_USER = "user"
        const val ROLE_ASSISTANT = "assistant"
    }
}

@Serializable
data class LlmCompletionRequest(
    val model: String,
    val messages: List<LlmMessage>,
    val temperature: Double = 0.7,
    val max_tokens: Int? = 256,
)

@Serializable
data class LlmUsage(
    val prompt_tokens: Int = 0,
    val completion_tokens: Int = 0,
    val total_tokens: Int = 0,
)

@Serializable
data class LlmChoice(
    val index: Int = 0,
    val message: LlmMessage = LlmMessage(role = "", content = ""),
)

@Serializable
data class LlmCompletionResponse(
    val choices: List<LlmChoice> = emptyList(),
    val usage: LlmUsage? = null,
) {
    /** Erste Antwort des Assistenten, oder null, wenn das Modell nichts liefert. */
    val content: String?
        get() = choices.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
}
