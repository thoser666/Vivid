package com.vivid.feature.chat.bot

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Stream-Status für die Owner-Steuerung — eine schlanke, modulentkoppelte
 * Kopie des Engine-Status (feature-chat hängt bewusst NICHT an feature-streaming).
 */
sealed interface ChatStreamStatus {
    data object Idle : ChatStreamStatus
    data object Preparing : ChatStreamStatus
    data object Streaming : ChatStreamStatus
    data class Failed(val reason: String?) : ChatStreamStatus
}

/** Ein einzelner Diagnose-Check (deterministisch gesammelt). */
data class DiagnosticCheck(
    val label: String,
    val ok: Boolean,
    val detail: String = "",
)

/**
 * Ergebnis eines Diagnose-Laufs für die Owner-Befehle (`!diag` / `!ask`):
 * Stream-Status, OBS-Verbindung und ein Konfigurations-Checklist.
 * Wird einmal deterministisch gesammelt und optional an die Owner-KI
 * als Kontext übergeben (Empfehlungen).
 */
data class StreamDiagnostics(
    val status: ChatStreamStatus,
    val obsConnected: Boolean,
    val checks: List<DiagnosticCheck>,
) {
    /** Kompakte Chat-Antwort (deterministisch, ohne Owner-LLM). */
    fun summary(): String = buildString {
        appendLine(
            when (status) {
                is ChatStreamStatus.Idle -> "Stream: ⏹ aus"
                is ChatStreamStatus.Preparing -> "Stream: ⏳ startet…"
                is ChatStreamStatus.Streaming -> "Stream: 🔴 live"
                is ChatStreamStatus.Failed -> "Stream: ❌ Fehler${status.reason?.let { " ($it)" } ?: ""}"
            },
        )
        appendLine(if (obsConnected) "OBS: ✅ verbunden" else "OBS: ⚠️ nicht verbunden")
        val open = checks.filter { !it.ok }
        if (open.isEmpty()) {
            appendLine("Alle Konfigurations-Checks ✅")
        } else {
            appendLine("Offene Punkte:")
            open.forEach { appendLine("  • ${it.label}") }
        }
    }

    /** Roh-Fakten für die Owner-KI (kompakt, ohne Emoji). */
    fun factSheet(): String = buildString {
        appendLine(
            "stream_status=${when (status) {
                is ChatStreamStatus.Idle -> "idle"
                is ChatStreamStatus.Preparing -> "preparing"
                is ChatStreamStatus.Streaming -> "streaming"
                is ChatStreamStatus.Failed -> "failed${status.reason?.let { ":$it" } ?: ""}"
            }}",
        )
        appendLine("obs_connected=$obsConnected")
        checks.forEach { appendLine("check:${it.label}=${if (it.ok) "ok" else "MISSING"}" + if (it.detail.isNotBlank()) " (${it.detail})" else "") }
    }
}

/**
 * Owner-Steuerung des Streams (Start/Stopp + Diagnose + Fix). Die Implementierung
 * liegt in der App (`feature-streaming`-Abhängigkeit); feature-chat kennt nur
 * dieses Interface. Gebunden via Hilt-`@BindsOptionalOf` + App-`@Binds` —
 * ohne Implementierung greift der [NoOpChatStreamControl]-Fallback.
 */
interface ChatStreamControl {
    suspend fun start()

    fun stop()

    suspend fun diagnostics(): StreamDiagnostics

    /**
     * Versucht auto-fixbare Probleme zu beheben. Gibt die durchgeführten
     * Aktionen zurück (leer = nichts zu fixen).
     *
     * Fixbare Aktionen:
     * - Stream läuft nicht, sollte aber → Neustart
     * - OBS-Status inkonsistent → Hinweis (kein auto-Reconnect möglich)
     */
    suspend fun fix(): List<FixAction>

    /**
     * Schaltet die Taschenlampe (Torch/Lantern) um.
     *
     * @return true, wenn die Taschenlampe eingeschaltet ist; false, wenn sie
     *   aus ist oder das Gerät keine unterstützt.
     */
    fun toggleTorch(): Boolean

    /**
     * Setzt den Video-Filter (OpenGL-Effekt) auf den angegebenen Namen.
     * Unbekannte Namen werden ignoriert.
     *
     * @return Die Liste der verfügbaren Filter-Namen (für den Hinweis-Text).
     */
    fun setVideoFilter(filterName: String?): List<String>

    /**
     * Schaltet den Low-Light-Boost (Helligkeitsanhebung) um.
     *
     * @return true, wenn der Boost jetzt aktiv ist.
     */
    fun toggleLowLightBoost(): Boolean
}

/** Eine durchgeführte Fix-Aktion. */
data class FixAction(
    val label: String,
    val success: Boolean,
    val detail: String = "",
)

/** Fallback, wenn keine Implementierung gebunden ist (Tests / Module ohne Stream-Engine). */
object NoOpChatStreamControl : ChatStreamControl {
    override suspend fun start() = Unit
    override fun stop() = Unit
    override suspend fun diagnostics(): StreamDiagnostics =
        StreamDiagnostics(
            status = ChatStreamStatus.Idle,
            obsConnected = false,
            checks = emptyList(),
        )

    override suspend fun fix(): List<FixAction> = emptyList()

    override fun toggleTorch(): Boolean = false

    override fun setVideoFilter(filterName: String?): List<String> = emptyList()

    override fun toggleLowLightBoost(): Boolean = false
}
