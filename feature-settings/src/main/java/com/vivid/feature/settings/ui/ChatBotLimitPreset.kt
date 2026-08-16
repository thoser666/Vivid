package com.vivid.feature.settings.ui

/**
 * Schnellstart-Voreinstellungen für die Chat-Bot-Begrenzungen
 * (Per-Viewer-Cooldown, Per-Viewer-Cap, Stunden-Budget).
 *
 * Der Streamer wählt eine Stufe und die drei Limit-Felder werden damit
 * vorbefüllt (danach weiterhin frei anpassbar). Weichen die Felder von
 * allen Voreinstellungen ab, gilt „Eigene“.
 */
enum class ChatBotLimitPreset(
    val displayName: String,
    val perViewerCooldownSeconds: Long,
    val perViewerMaxReplies: Int,
    val maxRepliesPerHour: Int,
) {
    /** Viel Interaktion, minimaler Schutz: kurzer Cooldown, keine Caps. */
    LOCKER("Locker", 30, 0, 0),

    /** Standard: 60-s-Cooldown, moderater Viewer-Cap und Kosten-Deckel. */
    BALANCED("Balanced", 60, 10, 120),

    /** Strikt gegen Spam und LLM-Kosten: 3-min-Cooldown, kleiner Cap. */
    STRICT("Streng", 180, 5, 60),
    ;

    companion object {
        /** Gespeicherter Marker für „Eigene Werte“ (keine Voreinstellung aktiv). */
        const val CUSTOM = "CUSTOM"

        /** Liest einen gespeicherten Namen robust (unbekannt → null). */
        fun fromName(name: String?): ChatBotLimitPreset? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }

        /**
         * Liefert die Voreinstellung, die exakt zu den Werten passt (oder null = „Eigene“).
         */
        fun matching(
            perViewerCooldownSeconds: Long,
            perViewerMaxReplies: Int,
            maxRepliesPerHour: Int,
        ): ChatBotLimitPreset? = entries.firstOrNull {
            it.perViewerCooldownSeconds == perViewerCooldownSeconds &&
                it.perViewerMaxReplies == perViewerMaxReplies &&
                it.maxRepliesPerHour == maxRepliesPerHour
        }

        /**
         * Aktive Auswahl für die UI: Der gespeicherte Preset gewinnt (Wiederherstellung
         * beim App-Start); bei „Eigene“/fehlendem Wert fällt auf das Wert-Matching zurück.
         */
        fun selection(
            storedPreset: String?,
            perViewerCooldownSeconds: Long,
            perViewerMaxReplies: Int,
            maxRepliesPerHour: Int,
        ): ChatBotLimitPreset? = fromName(storedPreset) ?: matching(
            perViewerCooldownSeconds,
            perViewerMaxReplies,
            maxRepliesPerHour,
        )
    }
}
