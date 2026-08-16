package com.vivid.feature.chat.bot

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.ChatMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Chat-Text-to-Speech (wie der `!tts`-Befehl des Moblin-Bots): Liest die
 * Nachrichten des laufenden Bot-Chats auf dem Gerät des Streamers laut vor.
 *
 * - Vorlesen startet nur, wenn der Bot aktiv ist ([start]) — der Flow ist der
 *   gleiche, den auch die [ChatBotEngine] konsumiert (SharedFlow-Multicast).
 * - Eigene Nachrichten des Bots und `!`-Befehle werden nicht vorgelesen
 *   (sonst würde der Bot seine eigene Bestätigung und den `!tts`-Toggle selbst
 *   vorlesen).
 * - Der An/Aus-Zustand überlebt Stream-Ende/-Start (wie bei Moblin bleibt TTS
 *   an, bis `!tts` es wieder ausschaltet).
 */
@Singleton
class ChatTtsController @Inject constructor(
    @param:ChatScope private val scope: CoroutineScope,
    private val speaker: ChatTtsSpeaker,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    private var collectorJob: Job? = null
    private var ownLogin: String? = null

    /** Startet das Vorlesen von [messages]; eigene Nachrichten von [ownLogin] werden übersprungen. */
    fun start(messages: Flow<ChatMessage>, ownLogin: String) {
        stop()
        this.ownLogin = ownLogin.lowercase()
        collectorJob = scope.launch {
            messages.collect { message -> maybeSpeak(message) }
        }
    }

    fun stop() {
        collectorJob?.cancel()
        collectorJob = null
        ownLogin = null
        // Der enabled-Zustand bleibt absichtlich erhalten (siehe Klassen-Kommentar).
    }

    /** Schaltet das Vorlesen um und liefert den neuen Zustand (für die Chat-Bestätigung). */
    fun toggle(): Boolean {
        _enabled.value = !_enabled.value
        return _enabled.value
    }

    fun setEnabled(enabled: Boolean) {
        _enabled.value = enabled
    }

    private fun maybeSpeak(message: ChatMessage) {
        val login = ownLogin ?: return
        if (!_enabled.value) return
        if (message.userLogin == login) return
        val text = message.text.trim()
        // Befehle (ein `!`-Token, auch mitten in der Nachricht wie "@bot !help")
        // werden nicht vorgelesen — sonst liest der Bot Toggles/Befehle selbst vor.
        if (text.isBlank() || containsCommand(text)) return
        val spoken = "${message.displayName}: ${text.take(MAX_SPOKEN_CHARS)}"
        speaker.speak(spoken)
    }

    private fun containsCommand(text: String): Boolean =
        text.split(Regex("\\s+")).any { it.startsWith("!") }

    companion object {
        /** Begrenzt die vorgelesene Nachrichtenlänge (Chat kann laut werden). */
        internal const val MAX_SPOKEN_CHARS = 200
    }
}
