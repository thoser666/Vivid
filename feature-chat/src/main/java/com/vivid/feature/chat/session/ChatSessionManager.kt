package com.vivid.feature.chat.session

import com.vivid.feature.chat.di.ChatScope
import com.vivid.feature.chat.model.ChatConnectionState
import com.vivid.feature.chat.model.ChatMessage
import com.vivid.feature.chat.model.ChatPlatform
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

/**
 * Eine aktive Chat-Session im [ChatSessionManager]: Konfiguration + der
 * Reader/Sender-Adapter der Plattform.
 */
data class ActiveChatSession(
    val config: ChatSessionConfig,
    val reader: ChatReader,
    val sender: ChatSender,
)

/**
 * Verwaltet N parallele Chat-Sessions (eine pro Plattform) und merge die
 * Nachrichten-/Alert-Flows für Overlay und Bot (P3-Vorgriff der Skizze
 * docs/architecture/multi-platform-chat.md, Abschnitt 4.3).
 *
 * **Vertrags-Entscheidung (P3-Preview):** [setSessions] ist deklarativ
 * (Soll-Zustand) und **startet jede angegebene Session immer neu**
 * (Always-Restart) — exakt das Verhalten des heutigen Bot-Starts
 * (`chatReader.start()` → Stop+Start im Reader). Ein equality-basierter
 * No-op-Re-Start für einen schnellen Settings-Loop kommt mit P3; dadurch
 * bleibt der erste Einsatz am [ChatBotController] verhaltensneutral.
 * Sessions auf Plattformen, die nicht mehr in der Liste stehen, werden
 * gestoppt.
 *
 * Die Manager-Flows ([messages], [alerts], [states]) folgen dem
 * Session-Registry-Stand via `flatMapLatest` — Kollektoren (Engine, TTS,
 * Overlay) können sich jederzeit an- und abmelden, wie bei einem SharedFlow.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@Singleton
class ChatSessionManager @Inject constructor(
    private val readers: Map<ChatPlatform, @JvmSuppressWildcards ChatReader>,
    private val senders: Map<ChatPlatform, @JvmSuppressWildcards ChatSender>,
    @param:ChatScope private val scope: kotlinx.coroutines.CoroutineScope,
) {
    private val _sessions = MutableStateFlow<Map<ChatPlatform, ActiveChatSession>>(emptyMap())

    /** Aktive Sessions je Plattform (beobachtbar für Diagnose/Settings). */
    val sessions: StateFlow<Map<ChatPlatform, ActiveChatSession>> = _sessions

    /** Merge der Nachrichten aller aktiven Sessions (im Eingangszeitpunkt). */
    val messages: Flow<ChatMessage> = _sessions.flatMapLatest { map ->
        if (map.isEmpty()) emptyFlow() else map.values.map { it.reader.messages }.merge()
    }

    /** Merge der Event-Alerts aller aktiven Sessions (P6: mehr als Twitch). */
    val alerts: Flow<com.vivid.feature.chat.model.ChatAlert> = _sessions.flatMapLatest { map ->
        if (map.isEmpty()) emptyFlow() else map.values.map { it.reader.alerts }.merge()
    }

    /** Verbindungsstatus je Plattform (leere Map = keine aktive Session). */
    val states: StateFlow<Map<ChatPlatform, ChatConnectionState>> = _sessions
        .flatMapLatest { map ->
            if (map.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    map.entries.map { (platform, session) ->
                        session.reader.state.map { state -> platform to state }
                    },
                ) { pairs -> pairs.toMap() }
            }
        }
        .stateIn(scope, SharingStarted.Eagerly, emptyMap())

    /** Plattformen mit aktiver Session. */
    val activePlatforms: StateFlow<Set<ChatPlatform>> =
        _sessions.map { it.keys }.stateIn(scope, SharingStarted.Eagerly, emptySet())

    /**
     * Setzt den Soll-Zustand der Sessions: Jede angegebene Konfiguration
     * startet (immer neu — Always-Restart, siehe KDoc), Sessions auf
     * Plattformen außerhalb der Liste werden gestoppt.
     */
    fun setSessions(configs: List<ChatSessionConfig>) {
        val byPlatform = configs.associateBy { it.platform }
        // Gestoppte Plattformen: aktiv, aber nicht mehr im Soll-Zustand.
        val removed = _sessions.value.filterKeys { it !in byPlatform }
        removed.values.forEach { it.reader.stop() }
        val next = _sessions.value.toMutableMap()
        next.keys.removeAll(removed.keys)
        for ((platform, config) in byPlatform) {
            val reader = readers.getValue(platform)
            val sender = senders.getValue(platform)
            reader.start(config)
            next[platform] = ActiveChatSession(config, reader, sender)
        }
        _sessions.value = next
    }

    /**
     * Sendet [text] auf der Plattform der [origin]-Nachricht (Bot-Default
     * „Antwort auf der Ursprungs-Plattform“). `null`, wenn dafür keine
     * Session aktiv ist.
     */
    suspend fun sendToOrigin(text: String, origin: ChatMessage): ChatSendResult? =
        send(text, origin.platform)

    /** Sendet [text] auf [target]; `null`, wenn dort keine Session aktiv ist. */
    suspend fun send(text: String, target: ChatPlatform): ChatSendResult? {
        val session = _sessions.value[target] ?: return null
        return session.sender.send(session.config, text)
    }

    /** Stoppt alle Sessions und leert die Registry (Bot-Down). */
    fun stopAll() {
        _sessions.value.values.forEach { it.reader.stop() }
        _sessions.update { emptyMap() }
    }
}
