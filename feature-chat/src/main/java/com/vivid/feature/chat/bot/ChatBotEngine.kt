package com.vivid.feature.chat.bot

import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.LlmMessage
import com.vivid.feature.chat.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ChatBotState {
    Disabled,
    Idle,
    Thinking,
}

/**
 * Reaktions-Engine des KI-Chat-Bots: filtert eingehende Nachrichten
 * (nur-Erwähnung, Cooldown, Rate-Limit), ignoriert eigene Nachrichten,
 * baut den Prompt-Kontext auf, fragt das LLM an und schickt die Antwort
 * über den [ChatSender].
 */
@Singleton
class ChatBotEngine @Inject constructor(
    private val llmClient: LlmClient,
) {
    /** Uhrenfunktion (für Tests ersetzbar). */
    internal var now: () -> Long = System::currentTimeMillis

    private val _state = MutableStateFlow(ChatBotState.Disabled)
    val state: StateFlow<ChatBotState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private var collectorJob: Job? = null
    private var config: ChatBotConfig? = null
    private var sender: ChatSender? = null
    private val history = ArrayDeque<LlmMessage>()
    private val replyTimes = ArrayDeque<Long>()
    private var lastReplyAt = 0L

    /**
     * Startet die Engine. [messages] muss ein heißer Flow sein (z. B. die
     * Nachrichten des Bot-Clients), der bereits vor dem Connect besammelt wird.
     */
    fun start(messages: Flow<ChatMessage>, config: ChatBotConfig, sender: ChatSender, scope: CoroutineScope) {
        stop()
        if (!config.isReady) return
        this.config = config
        this.sender = sender
        history.clear()
        replyTimes.clear()
        lastReplyAt = 0L
        _state.value = ChatBotState.Idle
        collectorJob = scope.launch {
            messages.collect { message -> process(message) }
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        config = null
        sender = null
        _state.value = ChatBotState.Disabled
    }

    private suspend fun process(message: ChatMessage) {
        val cfg = config ?: return
        val snd = sender ?: return
        if (message.text.isBlank()) return
        if (message.userLogin == cfg.login) return
        if (cfg.mentionsOnly && !isMentioned(message.text, cfg.login)) return
        if (cfg.replyCooldownMillis > 0 && now() - lastReplyAt < cfg.replyCooldownMillis) return
        if (rateLimited(cfg)) return

        _state.value = ChatBotState.Thinking
        try {
            appendUserMessage(cfg, message)
            val reply = llmClient.complete(cfg.llm, conversation(cfg))
            appendAssistantMessage(cfg, reply)
            val trimmed = reply.trim().replace(Regex("\\s+"), " ").take(cfg.maxReplyLength)
            if (trimmed.isEmpty()) return
            snd.send(trimmed)
            registerReply()
            _logs.tryEmit(trimmed)
        } catch (e: Exception) {
            _logs.tryEmit("Fehler: ${e.message}")
        } finally {
            _state.value = ChatBotState.Idle
        }
    }

    private fun appendUserMessage(cfg: ChatBotConfig, message: ChatMessage) {
        history.addLast(LlmMessage(LlmMessage.ROLE_USER, "${message.displayName}: ${message.text}"))
        trimHistory(cfg)
    }

    private fun appendAssistantMessage(cfg: ChatBotConfig, reply: String) {
        history.addLast(LlmMessage(LlmMessage.ROLE_ASSISTANT, reply.trim()))
        trimHistory(cfg)
    }

    private fun conversation(cfg: ChatBotConfig): List<LlmMessage> = buildList {
        if (cfg.systemPrompt.isNotBlank()) {
            add(LlmMessage(LlmMessage.ROLE_SYSTEM, cfg.systemPrompt))
        }
        addAll(history)
    }

    private fun trimHistory(cfg: ChatBotConfig) {
        while (history.size > cfg.historySize) history.removeFirst()
    }

    private fun isMentioned(text: String, login: String): Boolean {
        val lowered = text.lowercase()
        return lowered.contains(login) || lowered.contains("!bot")
    }

    private fun rateLimited(cfg: ChatBotConfig): Boolean {
        if (cfg.maxRepliesPerMinute <= 0) return false
        val limit = now() - 60_000
        while (replyTimes.isNotEmpty() && replyTimes.first() < limit) replyTimes.removeFirst()
        return replyTimes.size >= cfg.maxRepliesPerMinute
    }

    private fun registerReply() {
        val timestamp = now()
        lastReplyAt = timestamp
        replyTimes.addLast(timestamp)
        val limit = timestamp - 60_000
        while (replyTimes.isNotEmpty() && replyTimes.first() < limit) replyTimes.removeFirst()
    }
}
