package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotMode
import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.LlmConfig
import com.vivid.feature.chat.ai.LlmMessage
import com.vivid.feature.chat.media.ChatMediaPlayer
import com.vivid.feature.chat.model.ChatAlertType
import com.vivid.feature.chat.model.ChatMessage
import java.util.Optional
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
 * Live-Zählerstand des Bots für den Settings-Screen (Kosten-Budget beobachten).
 * [hourlyBudget] ist 0, wenn kein Budget gesetzt ist (unbegrenzt).
 */
data class ChatBotUsage(
    val repliesThisHour: Int = 0,
    val hourlyBudget: Int = 0,
    val totalRepliesThisStream: Int = 0,
    val topViewers: List<ViewerUsage> = emptyList(),
) {
    data class ViewerUsage(val displayName: String, val replies: Int)
}

/**
 * Reaktions-Engine des Chat-Bots mit zwei Betriebsmodi (siehe [ChatBotMode]):
 *
 * - **COMMAND** („Bot wie Moblin"): Nur deterministische `!`-Befehle
 *   ([BotCommandProcessor]) — kein LLM-Aufruf, funktioniert ohne LLM-Schlüssel.
 * - **AUTONOMOUS** („KI entscheidet selbst"): Bekannte Befehle werden weiterhin
 *   sofort deterministisch beantwortet; alle übrigen (freigegebenen) Nachrichten
 *   bewertet das LLM und entscheidet selbst, ob — und wie — es antwortet
 *   (inklusive bewusstem Schweigen via [NO_REPLY_MARKER]).
 *
 * Gemeinsame Sicherungen: eigene Nachrichten werden ignoriert, Cooldown und
 * Rate-Limit gelten für alle Antworten, und jede Antwort wird auf
 * [ChatBotConfig.maxReplyLength] gekürzt.
 */
@Singleton
class ChatBotEngine @Inject constructor(
    private val llmClient: LlmClient,
    private val commandProcessor: BotCommandProcessor,
    private val chatTts: ChatTtsController,
    private val media: ChatMediaPlayer,
    private val chatStreamControl: Optional<ChatStreamControl>,
) {
    /** Uhrenfunktion (für Tests ersetzbar). */
    internal var now: () -> Long = System::currentTimeMillis

    /**
     * Owner-Steuerung des Streams: In der App ist die echte Implementierung
     * gebunden (Hilt-@Binds); ohne Bindung (Tests, Module ohne Stream-Engine)
     * greift der NoOp-Fallback.
     */
    private val streamControl: ChatStreamControl
        get() = chatStreamControl.orElse(NoOpChatStreamControl)

    private val _state = MutableStateFlow(ChatBotState.Disabled)
    val state: StateFlow<ChatBotState> = _state.asStateFlow()

    private val _logs = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val logs: SharedFlow<String> = _logs.asSharedFlow()

    private var collectorJob: Job? = null
    private var config: ChatBotConfig? = null
    private var sender: ChatSender? = null
    private var moderation: ChatModeration = NoOpChatModeration
    private var alertTrigger: ChatAlertTrigger = NoOpChatAlertTrigger
    private var streamStartedAtMillis = 0L

    // Ringpuffer der zuletzt gesehenen Kanal-Nachrichten-IDs — Grundlage für
    // `!delete <anzahl>` (gelöscht werden kann nur, was der Bot gesehen hat).
    private val recentMessageIds = ArrayDeque<String>()
    private val history = ArrayDeque<LlmMessage>()
    private val replyTimes = ArrayDeque<Long>()
    private var lastReplyAt = 0L

    // Per-Viewer-Begrenzungen: userId (plattformneutral) → Zeitstempel der
    // letzten Antwort bzw. Anzahl der Antworten in diesem Stream.
    private val lastReplyByUser = mutableMapOf<String, Long>()
    private val userReplyCounts = mutableMapOf<String, Int>()

    // Anzeigename je userId (für die Top-Viewer-Anzeige im Settings-Screen).
    private val userReplyNames = mutableMapOf<String, String>()
    private var totalRepliesThisStream = 0

    private val _usage = MutableStateFlow(ChatBotUsage())
    val usage: StateFlow<ChatBotUsage> = _usage.asStateFlow()

    /**
     * Startet die Engine. [messages] muss ein heißer Flow sein (z. B. die
     * Nachrichten des Bot-Clients), der bereits vor dem Connect besammelt wird.
     * [streamStartedAtMillis] ist der Stream-Start-Zeitstempel (für `!uptime`).
     */
    fun start(
        messages: Flow<ChatMessage>,
        config: ChatBotConfig,
        sender: ChatSender,
        scope: CoroutineScope,
        streamStartedAtMillis: Long = 0L,
        moderation: ChatModeration = NoOpChatModeration,
        alertTrigger: ChatAlertTrigger = NoOpChatAlertTrigger,
    ) {
        stop()
        if (!config.isReady) return
        this.config = config
        this.sender = sender
        this.moderation = moderation
        this.alertTrigger = alertTrigger
        this.streamStartedAtMillis = streamStartedAtMillis
        history.clear()
        replyTimes.clear()
        lastReplyAt = 0L
        lastReplyByUser.clear()
        userReplyCounts.clear()
        userReplyNames.clear()
        recentMessageIds.clear()
        totalRepliesThisStream = 0
        updateUsage()
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
        moderation = NoOpChatModeration
        alertTrigger = NoOpChatAlertTrigger
        streamStartedAtMillis = 0L
        lastReplyByUser.clear()
        userReplyCounts.clear()
        userReplyNames.clear()
        recentMessageIds.clear()
        totalRepliesThisStream = 0
        updateUsage()
        _state.value = ChatBotState.Disabled
    }

    private suspend fun process(message: ChatMessage) {
        val cfg = config ?: return
        val snd = sender ?: return
        if (message.text.isBlank()) return
        // Letzte Kanal-Nachrichten-IDs tracken (Ringpuffer) — Grundlage für
        // `!delete <anzahl>`: gelöscht werden kann nur, was der Bot gesehen hat.
        if (!message.isWhisper && message.id.isNotBlank()) {
            recentMessageIds.addLast(message.id)
            while (recentMessageIds.size > MAX_TRACKED_MESSAGE_IDS) recentMessageIds.removeFirst()
        }
        if (message.userLogin == cfg.login) return
        // Koexistenz: Nachrichten anderer Bots (Ignore-Liste, z. B. Rivulet-Bot)
        // werden komplett ignoriert — keine Befehle, kein LLM-Input.
        if (message.userLogin in cfg.ignoreBots) return

        // Privat eingegangene Nachrichten (Twitch-Whisper per EventSub): nur
        // Owner-Befehle werden beantwortet (Antwort geht als Whisper zurück) —
        // Whispers werden nie in die Viewer-/LLM-Pfade eingespeist.
        if (message.isWhisper) {
            handleWhisper(cfg, snd, message)
            return
        }

        // Befehle werden in BEIDEN Modi deterministisch beantwortet (Moblin-Stil).
        val commandReply = when (val result = commandProcessor.handle(
            message.text,
            streamStartedAtMillis,
            cfg.commandScope,
            cfg.commandPrefix,
            cfg.login,
        )) {
            is BotCommandProcessor.Result.Reply -> result.text
            is BotCommandProcessor.Result.ToggleTts -> {
                // !tts: Chat-Vorlesen umschalten und im Chat bestätigen.
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    val nowEnabled = chatTts.toggle()
                    send(snd, if (nowEnabled) TTS_ON_TEXT else TTS_OFF_TEXT, message)
                }
                return
            }
            is BotCommandProcessor.Result.MediaNowPlaying -> {
                // !song: aktuellen Titel melden (bzw. Zugriffs-Hinweis).
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    send(snd, mediaStatusReply(), message)
                }
                return
            }
            is BotCommandProcessor.Result.MediaNext -> {
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    send(snd, mediaActionReply({ media.skipToNext() }, MEDIA_NEXT_TEXT), message)
                }
                return
            }
            is BotCommandProcessor.Result.MediaPause -> {
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    send(snd, mediaActionReply({ media.pause() }, MEDIA_PAUSE_TEXT), message)
                }
                return
            }
            is BotCommandProcessor.Result.MediaPlay -> {
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    send(snd, mediaActionReply({ media.play() }, MEDIA_PLAY_TEXT), message)
                }
                return
            }
            is BotCommandProcessor.Result.MediaPrevious -> {
                if (canReplyFor(cfg, message.userId, message.isModerator)) {
                    send(snd, mediaActionReply({ media.skipToPrevious() }, MEDIA_PREVIOUS_TEXT), message)
                }
                return
            }
            // Owner-Befehle: nur der Streamer (Broadcaster oder Allow-List).
            is BotCommandProcessor.Result.OwnerStart -> {
                handleOwnerAction(cfg, message, snd) {
                    streamControl.start()
                    STREAM_START_TEXT
                }
                return
            }
            is BotCommandProcessor.Result.OwnerStop -> {
                handleOwnerAction(cfg, message, snd) {
                    streamControl.stop()
                    STREAM_STOP_TEXT
                }
                return
            }
            is BotCommandProcessor.Result.OwnerDiagnose -> {
                handleOwnerDiagnose(cfg, message, snd)
                return
            }
            is BotCommandProcessor.Result.OwnerAsk -> {
                handleOwnerAsk(cfg, message, snd, result.text)
                return
            }
            is BotCommandProcessor.Result.TestAlert -> {
                handleTestAlert(cfg, message, snd, result.type)
                return
            }
            is BotCommandProcessor.Result.Ban -> {
                handleModeration(cfg, message, snd) {
                    if (result.userLogin.isBlank()) {
                        MODERATION_MISSING_USER_TEXT
                    } else {
                        moderation.ban(result.userLogin)
                    }
                }
                return
            }
            is BotCommandProcessor.Result.Timeout -> {
                handleModeration(cfg, message, snd) {
                    if (result.userLogin.isBlank()) {
                        MODERATION_MISSING_USER_TEXT
                    } else {
                        moderation.timeout(result.userLogin, result.durationMinutes)
                    }
                }
                return
            }
            is BotCommandProcessor.Result.Delete -> {
                handleModeration(cfg, message, snd) {
                    moderation.deleteRecent(result.count, recentMessageIds.toList())
                }
                return
            }
            is BotCommandProcessor.Result.Unknown ->
                if (cfg.mode == ChatBotMode.COMMAND) {
                    "Unbekannter Befehl „${result.command}“ — Tipp: !help"
                } else {
                    null // AUTONOMOUS: die KI entscheidet über unbekannte Befehle
                }
            BotCommandProcessor.Result.None -> null
        }
        if (commandReply != null) {
            if (canReplyFor(cfg, message.userId, message.isModerator)) send(snd, commandReply, message)
            return
        }
        // COMMAND-Modus: ohne Befehl passiert nichts (kein LLM-Aufruf).
        if (cfg.mode == ChatBotMode.COMMAND) return

        // AUTONOMOUS-Modus: Die KI entscheidet selbst über die Nachricht.
        if (cfg.mentionsOnly && !isMentioned(message.text, cfg.login)) return
        if (!canReplyFor(cfg, message.userId, message.isModerator)) return

        _state.value = ChatBotState.Thinking
        try {
            appendUserMessage(cfg, message)
            val reply = llmClient.complete(cfg.llm, conversation(cfg))
            val trimmed = reply.trim().replace(Regex("\\s+"), " ").take(cfg.maxReplyLength)
            if (trimmed.isEmpty() || trimmed.equals(NO_REPLY_MARKER, ignoreCase = true)) return
            appendAssistantMessage(cfg, trimmed)
            send(snd, trimmed, message)
        } catch (e: Exception) {
            _logs.tryEmit("Fehler: ${e.message}")
        } finally {
            _state.value = ChatBotState.Idle
        }
    }

    /** „Aktuell läuft …“ oder ein Hinweis, wenn nichts läuft / kein Zugriff. */
    private fun mediaStatusReply(): String = when {
        !media.hasAccess() -> MEDIA_NO_ACCESS_TEXT
        else -> media.nowPlaying()?.let { "Aktuell läuft: $it" } ?: "Gerade läuft kein Song."
    }

    /** Führt eine Media-Aktion aus oder antwortet mit dem Zugriffs-Hinweis. */
    private fun mediaActionReply(action: () -> Unit, confirm: String): String {
        if (!media.hasAccess()) return MEDIA_NO_ACCESS_TEXT
        action()
        return confirm
    }

    /** Owner-Gate: nur der Streamer (Broadcaster-Badge oder Allow-List) darf Owner-Befehle. */
    private fun isOwner(cfg: ChatBotConfig, message: ChatMessage): Boolean =
        cfg.isOwner(message.userLogin, message.isBroadcaster)

    /**
     * Privat eingegangene Nachrichten (Twitch-Whisper): nur Owner-Befehle
     * werden beantwortet — die Antwort geht als Whisper an den Absender
     * zurück (nie öffentlich). Andere Befehle und Freitext werden ignoriert:
     * Whispers sind kein Viewer-Kanal.
     */
    private suspend fun handleWhisper(cfg: ChatBotConfig, snd: ChatSender, message: ChatMessage) {
        when (val result = commandProcessor.handle(
            message.text,
            streamStartedAtMillis,
            cfg.commandScope,
            cfg.commandPrefix,
            cfg.login,
        )) {
            is BotCommandProcessor.Result.OwnerStart ->
                handleOwnerAction(cfg, message, snd) { streamControl.start(); STREAM_START_TEXT }
            is BotCommandProcessor.Result.OwnerStop ->
                handleOwnerAction(cfg, message, snd) { streamControl.stop(); STREAM_STOP_TEXT }
            is BotCommandProcessor.Result.OwnerDiagnose -> handleOwnerDiagnose(cfg, message, snd)
            is BotCommandProcessor.Result.OwnerAsk -> handleOwnerAsk(cfg, message, snd, result.text)
            is BotCommandProcessor.Result.TestAlert -> handleTestAlert(cfg, message, snd, result.type)
            is BotCommandProcessor.Result.Ban ->
                handleModeration(cfg, message, snd) {
                    if (result.userLogin.isBlank()) MODERATION_MISSING_USER_TEXT else moderation.ban(result.userLogin)
                }
            is BotCommandProcessor.Result.Timeout ->
                handleModeration(cfg, message, snd) {
                    if (result.userLogin.isBlank()) MODERATION_MISSING_USER_TEXT else moderation.timeout(result.userLogin, result.durationMinutes)
                }
            is BotCommandProcessor.Result.Delete ->
                handleModeration(cfg, message, snd) { moderation.deleteRecent(result.count, recentMessageIds.toList()) }
            else -> Unit
        }
    }

    /**
     * Führt eine Moderation-Aktion aus (`!ban`/`!timeout`/`!delete`). Nur der
     * Streamer (Owner-Gate); Nicht-Owner bekommen den üblichen Hinweis. Owner
     * umgehen Cooldown und Per-Viewer-Limits; das globale Rate-Limit
     * (Kosten-Schutz) gilt weiterhin. Die Aktion liefert die fertige
     * Chat-Antwort; Fehler werden gefangen und als Fehlerhinweis beantwortet
     * (die Engine bleibt für nachfolgende Nachrichten verfügbar).
     */
    private suspend fun handleModeration(
        cfg: ChatBotConfig,
        message: ChatMessage,
        snd: ChatSender,
        action: suspend () -> String,
    ) {
        if (!isOwner(cfg, message)) {
            sendOwnerOnlyHint(snd, message)
            return
        }
        if (rateLimited(cfg)) return
        try {
            sendOwnerReply(cfg, snd, message, action())
        } catch (e: Exception) {
            _logs.tryEmit("Moderation fehlgeschlagen: ${e.message}")
            sendOwnerReply(
                cfg,
                snd,
                message,
                "❌ Moderation fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}",
            )
        }
    }

    /**
     * `!testalert <follow|sub|raid>`: löst über die [ChatAlertTrigger]-Schnitt-
     * stelle einen synthetischen Event-Alert aus (erscheint im Chat-Overlay),
     * damit der Streamer vor dem Go-Live das Overlay-Rendering prüfen kann.
     * Owner-Gate + Rate-Limit wie bei den anderen Owner-Befehlen; ohne/ungülti-
     * gen Typ antwortet der Bot mit dem Nutzungs-Hinweis.
     */
    private suspend fun handleTestAlert(
        cfg: ChatBotConfig,
        message: ChatMessage,
        snd: ChatSender,
        type: ChatAlertType?,
    ) {
        if (!isOwner(cfg, message)) {
            sendOwnerOnlyHint(snd, message)
            return
        }
        if (rateLimited(cfg)) return
        if (type == null) {
            sendOwnerReply(cfg, snd, message, TEST_ALERT_USAGE_TEXT)
            return
        }
        try {
            alertTrigger.triggerTestAlert(type)
            sendOwnerReply(cfg, snd, message, testAlertConfirmation(type))
        } catch (e: Exception) {
            _logs.tryEmit("Test-Alert fehlgeschlagen: ${e.message}")
            sendOwnerReply(
                cfg,
                snd,
                message,
                "❌ Test-Alert fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}",
            )
        }
    }

    /** Bestätigungstext für einen ausgelösten Test-Alert. */
    private fun testAlertConfirmation(type: ChatAlertType): String =
        "✅ Test-Alert (${type.name.lowercase()}) ausgelöst — erscheint im Chat-Overlay, sobald der Streaming-Screen offen ist."

    /**
     * Führt eine Owner-Aktion aus (Stream starten/stoppen). Nicht-Owner
     * bekommen einen Hinweis. Owner umgehen Cooldown und Per-Viewer-Limits;
     * das globale Rate-Limit (Kosten-Schutz) gilt weiterhin.
     */
    private suspend fun handleOwnerAction(
        cfg: ChatBotConfig,
        message: ChatMessage,
        snd: ChatSender,
        action: suspend () -> String,
    ) {
        if (!isOwner(cfg, message)) {
            sendOwnerOnlyHint(snd, message)
            return
        }
        if (rateLimited(cfg)) return
        try {
            sendOwnerReply(cfg, snd, message, action())
        } catch (e: Exception) {
            _logs.tryEmit("Owner-Aktion fehlgeschlagen: ${e.message}")
            sendOwnerReply(
                cfg,
                snd,
                message,
                "❌ Aktion fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}",
            )
        }
    }

    /**
     * `!diag`: Diagnose-Fact-Sheet deterministisch sammeln; Bewertung + Empfehlungen
     * kommen von der **Owner-KI**, ohne eigene Owner-KI als **Fallback von der
     * Viewer-KI**; nur wenn gar keine KI konfiguriert ist, die Checkliste direkt.
     */
    private suspend fun handleOwnerDiagnose(cfg: ChatBotConfig, message: ChatMessage, snd: ChatSender) {
        if (!isOwner(cfg, message)) {
            sendOwnerOnlyHint(snd, message)
            return
        }
        if (rateLimited(cfg)) return
        val diagnostics = streamControl.diagnostics()
        val llm = ownerLlm(cfg)
        val sourceLine = ownerLlmSourceLine(cfg, llm)
        if (llm == null) {
            sendOwnerReply(
                cfg,
                snd,
                message,
                appendSourceLine(diagnostics.summary(), sourceLine, cfg),
            )
            return
        }
        _state.value = ChatBotState.Thinking
        try {
            val reply = llmClient.complete(llm, ownerDiagnoseConversation(cfg, diagnostics))
            val trimmed = trimReply(reply, cfg)
            if (trimmed.isEmpty() || trimmed.equals(NO_REPLY_MARKER, ignoreCase = true)) return
            sendOwnerReply(cfg, snd, message, appendSourceLine(trimmed, sourceLine, cfg))
        } catch (e: Exception) {
            _logs.tryEmit("Owner-Diagnose fehlgeschlagen: ${e.message}")
            // Die deterministische Checkliste kommt trotzdem durch — mit
            // Quellen-Zeile, damit sichtbar ist, welche KI ausgefallen ist.
            val note = diagnostics.summary() + " (KI-Auswertung fehlgeschlagen: ${e.message})"
            sendOwnerReply(cfg, snd, message, appendSourceLine(note, sourceLine, cfg))
        } finally {
            _state.value = ChatBotState.Idle
        }
    }

    /**
     * Quellen-Zeile für die `!diag`-Ausgabe: zeigt an, welche KI geantwortet
     * hat (eigene Owner-KI, Viewer-KI als Fallback oder deterministisch).
     */
    private fun ownerLlmSourceLine(cfg: ChatBotConfig, llm: LlmConfig?): String {
        val source = when {
            llm == null -> "deterministisch (keine KI konfiguriert)"
            cfg.isOwnerLlmReady -> "eigene Owner-KI (exklusiv)"
            else -> "Viewer-KI (Fallback)"
        }
        return "🤖 Auswertung durch: $source"
    }

    /**
     * Hängt die Quellen-Zeile an [text] an und hält das Gesamtlimit ein — der
     * Platz für die Zeile wird reserviert, damit sie nie abgeschnitten wird.
     */
    private fun appendSourceLine(text: String, sourceLine: String, cfg: ChatBotConfig): String {
        val maxTotal = cfg.maxReplyLength
        val combined = "$text\n\n$sourceLine"
        if (combined.length <= maxTotal) return combined
        val budget = (maxTotal - sourceLine.length - 2).coerceAtLeast(0)
        return "${text.take(budget).trimEnd()}\n\n$sourceLine"
    }

    /** `!ask <frage>`: Frage an die Owner-KI, mit aktuellem Stream-Zustand als Kontext. */
    private suspend fun handleOwnerAsk(
        cfg: ChatBotConfig,
        message: ChatMessage,
        snd: ChatSender,
        text: String,
    ) {
        if (!isOwner(cfg, message)) {
            sendOwnerOnlyHint(snd, message)
            return
        }
        if (rateLimited(cfg)) return
        if (text.isBlank()) {
            sendOwnerReply(cfg, snd, message, OWNER_ASK_EMPTY_TEXT)
            return
        }
        val llm = ownerLlm(cfg)
        if (llm == null) {
            sendOwnerReply(cfg, snd, message, OWNER_LLM_NOT_CONFIGURED_TEXT)
            return
        }
        _state.value = ChatBotState.Thinking
        try {
            val diagnostics = streamControl.diagnostics()
            val reply = llmClient.complete(llm, ownerAskConversation(cfg, text, diagnostics))
            val trimmed = trimReply(reply, cfg)
            if (trimmed.isEmpty() || trimmed.equals(NO_REPLY_MARKER, ignoreCase = true)) return
            sendOwnerReply(cfg, snd, message, trimmed)
        } catch (e: Exception) {
            _logs.tryEmit("Owner-Frage fehlgeschlagen: ${e.message}")
            sendOwnerReply(cfg, snd, message, "❌ KI-Auswertung fehlgeschlagen: ${e.message}")
        } finally {
            _state.value = ChatBotState.Idle
        }
    }

    /**
     * KI für die Owner-Befehle: bevorzugt die **exklusive Owner-KI**
     * ([ChatBotConfig.ownerLlm]) — ein Endpunkt, der nur für die Streamer-
     * Befehle (`!start`/`!stop`/`!diag`/`!ask`) erreichbar ist. Ist keine
     * eigene Owner-KI hinterlegt, fällt der Befehl auf die **Viewer-KI**
     * ([ChatBotConfig.llm]) zurück. null nur, wenn gar keine KI konfiguriert
     * ist (z. B. COMMAND-Modus) — dann greifen die deterministischen Fallbacks.
     */
    private fun ownerLlm(cfg: ChatBotConfig): LlmConfig? =
        if (cfg.isOwnerLlmReady) cfg.ownerLlm else if (cfg.llm.isConfigured) cfg.llm else null

    /** Kürzt eine Antwort auf das konfigurierte Maximum (wie die Viewer-Antworten). */
    private fun trimReply(reply: String, cfg: ChatBotConfig): String =
        reply.trim().replace(Regex("\\s+"), " ").take(cfg.maxReplyLength)

    /** System-Prompt für die Owner-KI (zusätzlich zum konfigurierten Prompt). */
    private fun ownerSystemPrompt(cfg: ChatBotConfig): String = buildString {
        append(
            "Du bist der persönliche Streaming-Assistent des Streamers. Du bekommst " +
                "exklusiven Zugriff auf den Stream-Zustand (nur der Streamer kann dich " +
                "fragen). Antworte präzise und kompakt.",
        )
        if (cfg.systemPrompt.isNotBlank()) {
            append("\n\n")
            append(cfg.systemPrompt.trim())
        }
    }

    /** Konversation für `!diag` mit Owner-KI (Zustand → Bewertung + Empfehlungen). */
    private fun ownerDiagnoseConversation(cfg: ChatBotConfig, diagnostics: StreamDiagnostics): List<LlmMessage> =
        buildList {
            add(LlmMessage(LlmMessage.ROLE_SYSTEM, ownerSystemPrompt(cfg)))
            add(
                LlmMessage(
                    LlmMessage.ROLE_USER,
                    "Führe eine Diagnose des Streams durch. Aktueller Zustand:\n${diagnostics.factSheet()}\n\n" +
                        "Bewerte den Zustand und gib konkrete Empfehlungen (kompakt, max. ${cfg.maxReplyLength} Zeichen).",
                ),
            )
        }

    /** Konversation für `!ask` mit Owner-KI (Frage + aktueller Zustand). */
    private fun ownerAskConversation(
        cfg: ChatBotConfig,
        question: String,
        diagnostics: StreamDiagnostics,
    ): List<LlmMessage> = buildList {
        add(LlmMessage(LlmMessage.ROLE_SYSTEM, ownerSystemPrompt(cfg)))
        add(
            LlmMessage(
                LlmMessage.ROLE_USER,
                "Aktueller Stream-Zustand:\n${diagnostics.factSheet()}\n\nFrage vom Streamer: $question",
            ),
        )
    }

    /** Cooldown + Rate-Limit gelten für ALLE Antworten (auch Befehle). */
    private fun canReply(cfg: ChatBotConfig): Boolean {
        if (cfg.replyCooldownMillis > 0 && now() - lastReplyAt < cfg.replyCooldownMillis) return false
        return !rateLimited(cfg)
    }

    /**
     * Wie [canReply], plus Per-Viewer-Begrenzungen (Cooldown + Cap pro Stream).
     * Moderatoren umgehen die Per-Viewer-Limits, nicht aber das globale
     * Rate-Limit. Key ist die plattformneutrale `userId` (Twitch/YouTube/Kick).
     */
    private fun canReplyFor(cfg: ChatBotConfig, userId: String?, isModerator: Boolean): Boolean {
        if (!canReply(cfg)) return false
        if (userId == null) return true // kein stabiler ID-Schlüssel → nur global begrenzen
        if (isModerator) return true
        val timestamp = now()
        // Per-Viewer-Cooldown: nach einer Antwort erst nach Ablauf wieder antworten.
        if (cfg.perViewerCooldownMillis > 0) {
            val last = lastReplyByUser[userId] ?: 0L
            if (timestamp - last < cfg.perViewerCooldownMillis) return false
        }
        // Per-Viewer-Cap: max. Antworten pro Viewer pro Stream.
        if (cfg.perViewerMaxReplies > 0 && (userReplyCounts[userId] ?: 0) >= cfg.perViewerMaxReplies) return false
        return true
    }

    private suspend fun send(snd: ChatSender, text: String, message: ChatMessage? = null) {
        try {
            snd.send(text)
            registerReply(message)
            _logs.tryEmit(text)
        } catch (e: Exception) {
            // Senden fehlgeschlagen (z. B. Helix-Fehler, fehlende User-ID,
            // Rate-Limit): loggen statt die Engine-Coroutine zu beenden — der
            // Bot bleibt für nachfolgende Nachrichten verfügbar. Kein
            // registerReply: eine nicht gesendete Antwort verbraucht weder
            // Cooldown noch Limits.
            _logs.tryEmit("Senden fehlgeschlagen: ${e.message ?: "unbekannter Fehler"}")
        }
    }

    /**
     * Sendet eine private Antwort per Whisper an den Absender
     * ([ChatMessage.userLogin]). Gibt true zurück, wenn der Whisper gesendet
     * wurde. Bei Fehlern wird nur geloggt — eine private Anfrage wird nie
     * öffentlich beantwortet (kein Leaken in den Kanal).
     */
    private suspend fun whisperBack(snd: ChatSender, message: ChatMessage, text: String): Boolean =
        try {
            snd.sendWhisper(message.userLogin, text)
            registerReply(message)
            _logs.tryEmit(text)
            true
        } catch (e: Exception) {
            _logs.tryEmit("Whisper-Antwort fehlgeschlagen (${e.message ?: "unbekannter Fehler"}) — keine öffentliche Antwort.")
            false
        }

    /**
     * Antwortet an den Owner: Whispers (private Anfrage) immer privat;
     * normale Chat-Nachrichten privat, wenn [ChatBotConfig.ownerWhisperReplies]
     * aktiv ist (mit öffentlichem Fallback bei Whisper-Fehler), sonst öffentlich.
     */
    private suspend fun sendOwnerReply(
        cfg: ChatBotConfig,
        snd: ChatSender,
        message: ChatMessage,
        text: String,
    ) {
        if (message.isWhisper) {
            whisperBack(snd, message, text)
            return
        }
        if (cfg.ownerWhisperReplies && message.userLogin.isNotBlank()) {
            if (whisperBack(snd, message, text)) return
            _logs.tryEmit("Owner-Whisper fehlgeschlagen — öffentliche Antwort als Fallback.")
        }
        send(snd, text, message)
    }

    /** Nicht-Owner-Hinweis: bei privater Anfrage als Whisper zurück, sonst öffentlich. */
    private suspend fun sendOwnerOnlyHint(snd: ChatSender, message: ChatMessage) {
        if (message.isWhisper) whisperBack(snd, message, OWNER_ONLY_TEXT) else send(snd, OWNER_ONLY_TEXT, message)
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
        val prompt = buildString {
            if (cfg.systemPrompt.isNotBlank()) {
                append(cfg.systemPrompt.trim())
                append("\n\n")
            }
            if (cfg.mode == ChatBotMode.AUTONOMOUS) {
                append(
                    "Du bist ein autonomer Chat-Bot: Du entscheidest selbst, ob eine Antwort " +
                        "sinnvoll ist. Antworte nur, wenn es echten Mehrwert gibt (z. B. eine " +
                        "Frage oder eine direkte Ansprache). Willst du nicht antworten, " +
                        "antworte exakt mit: $NO_REPLY_MARKER",
                )
            }
        }
        if (prompt.isNotBlank()) {
            add(LlmMessage(LlmMessage.ROLE_SYSTEM, prompt.trim()))
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
        if (cfg.maxRepliesPerMinute <= 0 && cfg.maxRepliesPerHour <= 0) return false
        val timestamp = now()
        val hourLimit = timestamp - 3_600_000
        while (replyTimes.isNotEmpty() && replyTimes.first() < hourLimit) replyTimes.removeFirst()
        // Kosten-Budget: max. Antworten pro Stunde global.
        if (cfg.maxRepliesPerHour > 0 && replyTimes.size >= cfg.maxRepliesPerHour) return true
        if (cfg.maxRepliesPerMinute > 0) {
            val minuteLimit = timestamp - 60_000
            val inMinute = replyTimes.count { it >= minuteLimit }
            if (inMinute >= cfg.maxRepliesPerMinute) return true
        }
        return false
    }

    private fun registerReply(message: ChatMessage?) {
        val timestamp = now()
        lastReplyAt = timestamp
        replyTimes.addLast(timestamp)
        totalRepliesThisStream += 1
        val limit = timestamp - 3_600_000
        while (replyTimes.isNotEmpty() && replyTimes.first() < limit) replyTimes.removeFirst()
        val userId = message?.userId
        if (userId != null) {
            lastReplyByUser[userId] = timestamp
            userReplyCounts[userId] = (userReplyCounts[userId] ?: 0) + 1
            userReplyNames[userId] = message.displayName.ifBlank { message.userLogin }
            pruneUserState(timestamp)
        }
        updateUsage()
    }

    /** Aktualisiert den Live-Zählerstand (Antworten/Std, Budget, Total, Top-Viewer). */
    private fun updateUsage() {
        val top = userReplyCounts.entries
            .sortedByDescending { it.value }
            .take(TOP_VIEWERS)
            .map { ChatBotUsage.ViewerUsage(userReplyNames[it.key] ?: it.key, it.value) }
        _usage.value = ChatBotUsage(
            repliesThisHour = replyTimes.size,
            hourlyBudget = config?.maxRepliesPerHour ?: 0,
            totalRepliesThisStream = totalRepliesThisStream,
            topViewers = top,
        )
    }

    /** Entfernt veraltete Per-Viewer-Cooldown-Einträge (Cap-Zähler bleiben bis Stream-Ende). */
    private fun pruneUserState(timestamp: Long) {
        val cooldown = config?.perViewerCooldownMillis ?: 0L
        if (cooldown <= 0) return
        val iterator = lastReplyByUser.entries.iterator()
        while (iterator.hasNext()) {
            if (timestamp - iterator.next().value >= cooldown) iterator.remove()
        }
    }

    companion object {
        /**
         * Marker, mit dem das LLM im AUTONOMOUS-Modus signalisiert, dass es
         * bewusst nicht antworten möchte (die Antwort wird nicht gesendet).
         */
        internal const val NO_REPLY_MARKER = "[keine Antwort]"

        internal const val TTS_ON_TEXT = "TTS ist jetzt AN 🔊 — Chat wird vorgelesen."
        internal const val TTS_OFF_TEXT = "TTS ist jetzt AUS 🔇."

        internal const val MEDIA_NO_ACCESS_TEXT =
            "⚠️ Kein Media-Zugriff — bitte Benachrichtigungszugriff für Vivid aktivieren (Systemeinstellungen → Benachrichtigungen)."
        internal const val MEDIA_NEXT_TEXT = "⏭ Nächster Song."
        internal const val MEDIA_PAUSE_TEXT = "⏸ Pausiert."
        internal const val MEDIA_PLAY_TEXT = "▶ Wiedergabe gestartet."
        internal const val MEDIA_PREVIOUS_TEXT = "⏮ Vorheriger Song."

        internal const val OWNER_ONLY_TEXT = "⚠️ Dieser Befehl ist nur für den Streamer."
        internal const val OWNER_ASK_EMPTY_TEXT = "Bitte gib eine Frage an: !ask <frage>"
        internal const val OWNER_LLM_NOT_CONFIGURED_TEXT =
            "⚠️ Keine KI konfiguriert — !ask/!diag brauchen einen LLM-Endpunkt (eigene Owner-KI oder Fallback: die normale Bot-KI)."
        internal const val MODERATION_MISSING_USER_TEXT =
            "Bitte gib einen Benutzernamen an, z. B. !ban <user> oder !timeout <user> <minuten?>"
        internal const val TEST_ALERT_USAGE_TEXT = "Nutzung: !testalert follow|sub|raid"
        internal const val STREAM_START_TEXT = "▶️ Stream wird gestartet…"
        internal const val STREAM_STOP_TEXT = "⏹ Stream wird gestoppt."

        /** Wie viele Top-Viewer der Live-Verbrauch im Settings-Screen zeigt. */
        internal const val TOP_VIEWERS = 5

        /** Wie viele Kanal-Nachrichten-IDs der Bot für `!delete` merkt. */
        internal const val MAX_TRACKED_MESSAGE_IDS = 50
    }
}
