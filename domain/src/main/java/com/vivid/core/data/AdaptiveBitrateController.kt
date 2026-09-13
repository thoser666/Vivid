package com.vivid.core.data

/**
 * Adaptive Bitrate (v0.6.0-Bucket): reine Entscheidungslogik ohne Android-
 * Abhängigkeit. Der Controller bekommt periodisch gemessene Sendebitraten
 * (RootEncoder `onNewBitrate`) und entscheidet, ob die Ziel-Bitrate angepasst
 * werden soll — AIMD-ähnlich (Additive Increase, Multiplicative Decrease):
 *
 * - **Abbau:** Liegt die Messung dauerhaft deutlich unter dem Ziel (>20 %
 *   Defizit, 3 Samples in Folge), geht die Netzwerkstrecke in die Sättigung —
 *   das Ziel fällt multiplikativ ([config.decreaseFactor], min.
 *   [AdaptiveBitrateConfig.minBitrateKbps]).
 * - **Aufbau:** Ist die Messung dauerhaft nahe am Ziel (<5 % Defizit, gesunde
 *   Streak), wird vorsichtig additiv erhöht ([config.increaseStepKbps], max.
 *   [AdaptiveBitrateConfig.maxBitrateKbps]).
 *
 * Die Klasse ist zustandsbehaftet und pro Stream zu verwenden; [reset] setzt
 * sie auf die Preset-Bitrate des nächsten Streams. Alle Methoden sind rein
 * synchron und single-threaded zu rufen (Engine-Callback-Thread).
 */
class AdaptiveBitrateController(
    private val config: AdaptiveBitrateConfig = AdaptiveBitrateConfig(),
) {

    /** Aktuelle Ziel-Bitrate in kbps (wird auf den Encoder angewendet). */
    var targetKbps: Int = config.minBitrateKbps
        private set

    private var lowStreak = 0
    private var healthyStreak = 0

    /** Setzt den Controller auf die Start-Bitrate [initialKbps] zurück. */
    fun reset(initialKbps: Int) {
        targetKbps = initialKbps.coerceIn(config.minBitrateKbps, config.maxBitrateKbps)
        lowStreak = 0
        healthyStreak = 0
    }

    /**
     * Verarbeitet eine gemessene Sendebitrate (kbps) und liefert die neue
     * Ziel-Bitrate — oder `null`, wenn keine Anpassung fällig ist.
     */
    fun onSample(measuredKbps: Long): Int? {
        val measured = measuredKbps.coerceAtLeast(0L)
        val deficitFraction = (targetKbps - measured) / targetKbps.toDouble()

        return if (deficitFraction > config.deficitDecreaseThreshold) {
            // Sättigung: erst nach [consecutiveLowSamples] Bestätigung reagieren.
            healthyStreak = 0
            lowStreak++
            if (lowStreak >= config.consecutiveLowSamples) {
                lowStreak = 0
                decrease()
            } else {
                null
            }
        } else if (deficitFraction < config.deficitIncreaseThreshold) {
            // Gesunde Strecke: vorsichtig additiv aufbauen.
            lowStreak = 0
            healthyStreak++
            if (healthyStreak >= config.consecutiveHealthySamples) {
                healthyStreak = 0
                increase()
            } else {
                null
            }
        } else {
            // Grauzone: Aufbau-Streak brechen, Abbau-Streak halten.
            healthyStreak = 0
            null
        }
    }

    private fun decrease(): Int {
        val next = (targetKbps * config.decreaseFactor).toInt()
            .coerceAtLeast(config.minBitrateKbps)
        targetKbps = next
        return next
    }

    private fun increase(): Int {
        val next = (targetKbps + config.increaseStepKbps)
            .coerceAtMost(config.maxBitrateKbps)
        targetKbps = next
        return next
    }
}

/** Parameter der adaptiven Bitratensteuerung (bewusst konservativ). */
data class AdaptiveBitrateConfig(
    /** Untergrenze der Ziel-Bitrate (kbps) — darunter bricht Video zusammen. */
    val minBitrateKbps: Int = 1_000,
    /** Obergrenze der Ziel-Bitrate (kbps) — das Preset-Bitrate des Streams. */
    val maxBitrateKbps: Int = 6_000,
    /** Defizit-Anteil am Ziel, ab dem ein Sample als „sättigt“ gilt (>0.20). */
    val deficitDecreaseThreshold: Double = 0.20,
    /** Defizit-Anteil, unter dem ein Sample als „gesund“ gilt (<0.05). */
    val deficitIncreaseThreshold: Double = 0.05,
    /** aufeinanderfolgende Sättigungs-Samples bis zum Abbau. */
    val consecutiveLowSamples: Int = 3,
    /** aufeinanderfolgende gesunde Samples bis zum Aufbau. */
    val consecutiveHealthySamples: Int = 10,
    /** multiplikativer Abbau-Faktor (klassisches AIMD). */
    val decreaseFactor: Double = 0.7,
    /** additiver Aufbau-Schritt (kbps). */
    val increaseStepKbps: Int = 500,
)
