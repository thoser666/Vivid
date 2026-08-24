package com.vivid.feature.chat.bot

import javax.inject.Inject

/**
 * Konfigurierbarer Obszönitäts-/Hass-Rede-Filter.
 *
 * Erkennt Blocklist-Wörter und 1337-Speak-Muster (z. B. "f.u.c.k", "s h i t",
 * "b!tch"). Unterstützt zwei Modi:
 * - **Kategorien-Modus**: Aktive Kategorien aus [ProfanityWordList]
 * - **Custom-Modus**: Benutzerdefinierte Wörter (zusätzlich, nicht statt)
 * - **Excluded**: Wörter, die vom Filter ausgeschlossen werden (False Positives)
 *
 * Der Filter prüft sowohl den Originaltext als auch eine kollabierte Variante
 * (alle Nicht-Buchstaben entfernt), um Trennzeichen-Verschleierung zu erkennen.
 */
class ProfanityFilter @Inject constructor() {

    private var enabled: Boolean = true
    private var activeCategories: Set<ProfanityCategory> = ProfanityCategory.entries.toSet()
    private var customWords: Set<String> = emptySet()
    private var excludedWords: Set<String> = emptySet()
    private var compiledWords: Set<String> = emptySet()
    private var compiledPatterns: List<Regex> = emptyList()

    /** Initialisierung aus [ChatBotConfig]. */
    fun configure(cfg: ChatBotConfig) {
        enabled = cfg.profanityEnabled
        activeCategories = cfg.profanityCategories
        customWords = cfg.profanityCustomWords
        excludedWords = cfg.profanityExcludedWords
        rebuild()
    }

    /**
     * Prüft den Text auf Obszönitäten.
     *
     * @return FilterResult mit [FilterResult.blocked] (true = Text enthält Obszönitäten)
     */
    fun check(text: String): FilterResult {
        if (!enabled || (compiledWords.isEmpty() && compiledPatterns.isEmpty())) {
            return FilterResult(blocked = false)
        }
        if (text.isBlank()) {
            return FilterResult(blocked = false)
        }

        val normalizedInput = text.lowercase().trim()

        // Prüfe 1: Wortliste auf Originaltext
        val wordHit = compiledWords.firstOrNull { word ->
            val pattern = "\\b${Regex.escape(word)}\\b"
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(normalizedInput)
        }
        if (wordHit != null) {
            val reason = "Inhalt nicht erlaubt (Filter)."
            log("Blocked by word list: '$wordHit' → $reason")
            return FilterResult(blocked = true, reason = reason)
        }

        // Prüfe 2: Regex-Patterns auf Originaltext (mit Excluded-Check)
        val patternHit = compiledPatterns.firstOrNull { rx ->
            val m = rx.find(normalizedInput)
            m != null && !isExcluded(m.value)
        }
        if (patternHit != null) {
            val reason = "Inhalt nicht erlaubt (Filter)."
            log("Blocked by pattern: ${patternHit.pattern} → $reason")
            return FilterResult(blocked = true, reason = reason)
        }

        // Prüfe 3: Kollabierter Text (Nicht-Buchstaben entfernt)
        val collapsed = normalizedInput.replace("[^a-z]".toRegex(), "")
        if (collapsed != normalizedInput) {
            val collapsedWordHit = compiledWords.firstOrNull { word ->
                collapsed.contains(word.lowercase()) &&
                    wasSplitBySeparators(word.lowercase(), normalizedInput)
            }
            if (collapsedWordHit != null) {
                val reason = "Inhalt nicht erlaubt (Filter)."
                log("Blocked by collapsed word: '$collapsedWordHit' → $reason")
                return FilterResult(blocked = true, reason = reason)
            }

            val collapsedPatternHit = compiledPatterns.firstOrNull { rx ->
                val m = rx.find(collapsed)
                m != null && !isExcluded(m.value) &&
                    wasSplitBySeparators(m.value.lowercase(), normalizedInput)
            }
            if (collapsedPatternHit != null) {
                val reason = "Inhalt nicht erlaubt (Filter)."
                log("Blocked by collapsed pattern: ${collapsedPatternHit.pattern} → $reason")
                return FilterResult(blocked = true, reason = reason)
            }
        }

        return FilterResult(blocked = false)
    }

    /** Prüft, ob ein getroffener Begriff in der Ausschluss-Liste steht. */
    private fun isExcluded(matchedText: String): Boolean {
        val lower = matchedText.lowercase().trim()
        return excludedWords.any { ex -> lower == ex || lower.contains(ex) }
    }

    /**
     * Prüft, ob die Zeichen von [word] in [originalText] in der richtigen
     * Reihenfolge vorkommen, aber durch mindestens ein Trennzeichen (Nicht-Buchstabe)
     * getrennt sind — z. B. "f.u.c.k" → f(10), u(12), c(14), k(16).
     * Bei "the assassin creed" → a(4), s(5), s(6) = keine Trennung → false.
     * Zusätzlich: Das gefundene Wort darf kein Teil eines größeren Wortes sein
     * (Zeichen davor/danach sind keine Buchstaben).
     */
    private fun wasSplitBySeparators(word: String, originalText: String): Boolean {
        var lastIdx = -1
        var firstIdx = -1
        var hadSeparator = false
        for (ch in word) {
            val idx = originalText.indexOf(ch, lastIdx + 1)
            if (idx < 0) return false
            if (firstIdx < 0) firstIdx = idx
            if (idx > lastIdx + 1) hadSeparator = true
            lastIdx = idx
        }
        if (!hadSeparator) return false
        if (firstIdx > 0 && originalText[firstIdx - 1].isLetter()) return false
        if (lastIdx < originalText.length - 1 && originalText[lastIdx + 1].isLetter()) return false
        return true
    }

    // ── Intern ────────────────────────────────────────────────────────────

    /** Baut die Wort- und Pattern-Listen aus aktiven Kategorien + Custom - Excluded. */
    private fun rebuild() {
        val words = mutableSetOf<String>()
        val patterns = mutableListOf<Regex>()

        for (cat in activeCategories) {
            ProfanityWordList.wordsByCategory[cat]?.let { words.addAll(it) }
            ProfanityWordList.patternsByCategory[cat]?.let { patterns.addAll(it) }
        }

        // Custom Words hinzufügen
        words.addAll(customWords.map { it.lowercase().trim() }.filter { it.isNotBlank() })

        // Excluded Words entfernen
        val excluded = excludedWords.map { it.lowercase().trim() }.toSet()
        words.removeAll { it in excluded }
        // Patterns auf Excluded Words filtern (vereinfacht: Patterns, die exakt einem excluded Wort entsprechen)
        // Da Regex-Patterns komplex sind, entfernen wir sie nicht automatisch.
        // User sollte bei Bedarf die Kategorien deaktivieren.

        compiledWords = words
        compiledPatterns = patterns
    }

    private fun log(msg: String) {
        println("ProfanityFilter: $msg")
    }

    data class FilterResult(val blocked: Boolean, val reason: String = "")
}
