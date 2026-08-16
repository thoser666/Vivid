package com.vivid.core.data

/**
 * Betriebsmodus des Chat-Bots.
 *
 * - [COMMAND]: Deterministische Chat-Befehle (wie der Bot von Moblin) —
 *   der Bot reagiert nur auf `!`-Befehle (`!help`, `!uptime`, `!bot` …),
 *   es ist **kein LLM nötig** (funktioniert nur mit einem Twitch-Chat-Token).
 * - [AUTONOMOUS]: Die KI entscheidet selbst — das LLM bewertet jede
 *   (freigegebene) Nachricht und entscheidet, ob und wie es antwortet
 *   (inklusive bewusstem Schweigen). Ein OpenAI-kompatibles LLM muss
 *   konfiguriert sein.
 */
enum class ChatBotMode {
    COMMAND,
    AUTONOMOUS,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [AUTONOMOUS]). */
        fun fromName(name: String?): ChatBotMode =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTONOMOUS
    }
}
