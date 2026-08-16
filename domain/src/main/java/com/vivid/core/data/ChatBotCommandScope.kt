package com.vivid.core.data

/**
 * Wer darf die `!`-Befehle des Chat-Bots auslösen?
 *
 * Dient der **Koexistenz mit anderen Bots** im selben Kanal (z. B. dem
 * Rivulet-Bot): Zwei Bots, die beide auf generische `!`-Befehle reagieren,
 * antworten doppelt und führen Aktionen (`!tts`, `!pause`) doppelt aus.
 * Mit einem restriktiven Scope übernimmt der andere Bot die generischen
 * Befehle und Vivid antwortet nur auf direkt adressierte Befehle.
 *
 * - [ALL]: Jeder `!`-Befehl wird beantwortet (Moblin-Stil, Standard).
 * - [MENTION]: Befehle nur, wenn der Bot direkt angesprochen wird
 *   (z. B. `@vividbot !help` bzw. der Bot-Login als Wort in der Nachricht).
 * - [PREFIX]: Nur Befehle mit eigenem Präfix werden beantwortet
 *   (z. B. `!v!help` bei Präfix `v`) — generische `!`-Befehle bleiben dem
 *   anderen Bot überlassen.
 */
enum class ChatBotCommandScope {
    ALL,
    MENTION,
    PREFIX,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt → [ALL]). */
        fun fromName(name: String?): ChatBotCommandScope =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: ALL
    }
}
