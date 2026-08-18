package com.vivid.feature.settings.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AppSettings
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.feature.chat.bot.ChatBotUsage

/**
 * Kategorie „Chat-Bot & KI“: Betriebsmodus, Bot-Konto/LLM, Verhalten,
 * Begrenzungen (Schnellstart-Presets + Live-Verbrauch), Koexistenz,
 * Owner-Zugriff und Media-Befehle.
 */
@Composable
fun SettingsChatBotScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    botUsage: ChatBotUsage,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsSectionScaffold(
        title = "Chat-Bot & KI",
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Chat-Bot (KI): An/Aus + Betriebsmodus-Switch + alle Konfigurationsfelder
        Text("Chat-Bot (KI)", style = MaterialTheme.typography.titleLarge)
        Text(
            text = "Automatischer Bot im Twitch-Chat: entweder deterministische Chat-Befehle (wie der Bot von Moblin) oder eine KI, die selbst entscheidet. Alle Felder lassen sich hier konfigurieren — Details: docs/ai-chat-bot.md.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Chat-Bot aktivieren", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotEnabled,
                onCheckedChange = viewModel::onChatBotEnabledChange,
            )
        }
        Text(
            text = "Betriebsmodus",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChatBotMode.entries.forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = uiState.chatBotMode == mode,
                    onClick = { viewModel.onChatBotModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ChatBotMode.entries.size,
                    ),
                    label = { Text(mode.displayName) },
                )
            }
        }
        Text(
            text = when (uiState.chatBotMode) {
                ChatBotMode.COMMAND -> "Bot wie Moblin: reagiert nur auf Befehle wie !help, !uptime, !tts und !bot — kein LLM nötig, funktioniert ohne KI-Schlüssel."
                ChatBotMode.AUTONOMOUS -> "KI entscheidet selbst: Das LLM bewertet jede (freigegebene) Nachricht und entscheidet, ob und wie es antwortet — inklusive bewusstem Schweigen."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Bot-Konto (Twitch) + LLM-Zugang
        Text("Bot-Konto & LLM", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.chatBotLogin,
            onValueChange = viewModel::onChatBotLoginChange,
            label = { Text("Bot-Login (Twitch, ohne @)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotOauthToken,
            onValueChange = viewModel::onChatBotOauthTokenChange,
            label = "Twitch-OAuth-Token (user:read:chat + user:write:chat)",
        )
        OutlinedTextField(
            value = uiState.chatBotApiBaseUrl,
            onValueChange = viewModel::onChatBotApiBaseUrlChange,
            label = { Text("LLM API-Basis-URL (OpenAI-kompatibel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotApiKey,
            onValueChange = viewModel::onChatBotApiKeyChange,
            label = "LLM API-Key",
        )
        OutlinedTextField(
            value = uiState.chatBotModel,
            onValueChange = viewModel::onChatBotModelChange,
            label = { Text("LLM-Modell") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotSystemPrompt,
            onValueChange = viewModel::onChatBotSystemPromptChange,
            label = { Text("System-Prompt (optional)") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // Bot-Verhalten
        Text("Bot-Verhalten", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.chatBotReplyCooldownSeconds.toString(),
            onValueChange = viewModel::onChatBotReplyCooldownSecondsChange,
            label = { Text("Antwort-Cooldown (Sekunden, 0 = aus)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Nur auf Erwähnungen antworten", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotMentionsOnly,
                onCheckedChange = viewModel::onChatBotMentionsOnlyChange,
            )
        }
        OutlinedTextField(
            value = uiState.chatBotMaxRepliesPerMinute.toString(),
            onValueChange = viewModel::onChatBotMaxRepliesPerMinuteChange,
            label = { Text("Max. Antworten pro Minute (0 = unbegrenzt)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Begrenzungen: pro Viewer + Kosten-Budget (0 = aus/unbegrenzt)
        Text(
            text = "Begrenzungen (0 = aus/unbegrenzt) — Cooldown und Cap gelten pro Viewer (funktionieren auf allen Plattformen: Twitch, Kick, YouTube …); Moderatoren umgehen die Per-Viewer-Limits. Das Stunden-Budget deckelt die LLM-Kosten global.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Voreinstellung (Schnellstart)",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            // Drei Voreinstellungen + „Eigene“. Auswahl = gespeicherter Preset
            // (Wiederherstellung beim App-Start), Fallback auf Wert-Matching.
            val active = ChatBotLimitPreset.selection(
                uiState.chatBotLimitPreset,
                uiState.chatBotPerViewerCooldownSeconds,
                uiState.chatBotPerViewerMaxReplies,
                uiState.chatBotMaxRepliesPerHour,
            )
            val options: List<ChatBotLimitPreset?> = ChatBotLimitPreset.entries + listOf(null)
            options.forEachIndexed { index, preset ->
                val selected = active == preset
                SegmentedButton(
                    selected = selected,
                    onClick = { preset?.let(viewModel::onChatBotLimitPresetChange) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = options.size,
                    ),
                    label = { Text(preset?.displayName ?: "Eigene") },
                )
            }
        }
        OutlinedTextField(
            value = uiState.chatBotPerViewerCooldownSeconds.toString(),
            onValueChange = viewModel::onChatBotPerViewerCooldownSecondsChange,
            label = { Text("Per-Viewer-Cooldown (Sekunden, 0 = aus)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotPerViewerMaxReplies.toString(),
            onValueChange = viewModel::onChatBotPerViewerMaxRepliesChange,
            label = { Text("Max. Antworten pro Viewer (pro Stream, 0 = unbegrenzt)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotMaxRepliesPerHour.toString(),
            onValueChange = viewModel::onChatBotMaxRepliesPerHourChange,
            label = { Text("Kosten-Budget: Max. Antworten pro Stunde (0 = unbegrenzt)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Live-Verbrauch: Kosten-Budget beobachten (nur solange der Bot aktiv ist)
        Text(
            text = "Live-Verbrauch",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = "Antworten diese Stunde: " +
                if (botUsage.hourlyBudget > 0) {
                    "${botUsage.repliesThisHour} / ${botUsage.hourlyBudget}"
                } else {
                    "${botUsage.repliesThisHour} (kein Budget)"
                },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "Antworten in diesem Stream: ${botUsage.totalRepliesThisStream}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (botUsage.topViewers.isNotEmpty()) {
            Text(
                text = "Top-Viewer: " + botUsage.topViewers.joinToString { "${it.displayName} (${it.replies})" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = "Wird nur angezeigt, solange der Bot aktiv ist — Zähler setzen bei Stream-Ende zurück.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Koexistenz mit anderen Bots (z. B. Rivulet)
        Text("Koexistenz mit anderen Bots", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Läuft neben dem Bot eines anderen Tools (z. B. Rivulet) im selben Kanal, lassen sich Kollisionen vermeiden: Andere Bots ignorieren und den Befehlsscope eingrenzen, damit nicht beide auf dieselben !-Befehle antworten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotIgnoreBots,
            onValueChange = viewModel::onChatBotIgnoreBotsChange,
            label = { Text("Andere Bots ignorieren (Logins, kommasepariert)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Befehlsscope",
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            ChatBotCommandScope.entries.forEachIndexed { index, scope ->
                SegmentedButton(
                    selected = uiState.chatBotCommandScope == scope,
                    onClick = { viewModel.onChatBotCommandScopeChange(scope) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = ChatBotCommandScope.entries.size,
                    ),
                    label = { Text(scope.displayName) },
                )
            }
        }
        Text(
            text = when (uiState.chatBotCommandScope) {
                ChatBotCommandScope.ALL -> "Jeder !-Befehl wird beantwortet (Standard, wie der Bot von Moblin)."
                ChatBotCommandScope.MENTION -> "Nur Befehle, die den Bot direkt ansprechen (z. B. @vividbot !help) — generische Befehle bleiben dem anderen Bot."
                ChatBotCommandScope.PREFIX -> "Nur Befehle mit eigenem Präfix (z. B. !v!help) — generische Befehle bleiben dem anderen Bot."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.chatBotCommandScope == ChatBotCommandScope.PREFIX) {
            OutlinedTextField(
                value = uiState.chatBotCommandPrefix,
                onValueChange = viewModel::onChatBotCommandPrefixChange,
                label = { Text("Eigenes Befehls-Präfix (z. B. v → !v!help)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Owner-Zugriff (nur der Streamer): Allow-List + eigene Owner-KI
        Text("Owner-Zugriff (nur Streamer)", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "Nur du (und eingetragene Logins) kannst während eines aktiven Streams die Owner-Befehle !start, !stop, !diag und !ask nutzen — Viewer sehen nur einen Hinweis. Der Kanal-Inhaber ist automatisch Owner (Broadcaster-Badge); trage hier zusätzliche Logins ein, z. B. deinen Zweitaccount.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLogins,
            onValueChange = viewModel::onChatBotOwnerLoginsChange,
            label = { Text("Owner-Logins (kommasepariert, ohne @)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "Owner-KI (optional, exklusiv für Streamer-Befehle): !ask und die Diagnose-Empfehlungen von !diag laufen über diesen Endpunkt. Ohne eigene Owner-KI fallen die Befehle als Fallback auf die normale Bot-KI zurück (nur wenn auch die fehlt, liefert !diag die Checkliste direkt und !ask einen Hinweis).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLlmBaseUrl,
            onValueChange = viewModel::onChatBotOwnerLlmBaseUrlChange,
            label = { Text("Owner-LLM API-Basis-URL (OpenAI-kompatibel)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotOwnerLlmApiKey,
            onValueChange = viewModel::onChatBotOwnerLlmApiKeyChange,
            label = "Owner-LLM API-Key",
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLlmModel,
            onValueChange = viewModel::onChatBotOwnerLlmModelChange,
            label = { Text("Owner-LLM-Modell") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Aktive KI-Quelle der Owner-Befehle (abgeleitet — identische
        // Auswahl wie die Engine: eigene Owner-KI → Viewer-KI → deterministisch).
        val ownerLlmSource = viewModel.ownerLlmSource
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "KI-Quelle für Owner-Befehle: ${ownerLlmSource.label}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when (ownerLlmSource) {
                    OwnerLlmSource.OWNER -> Color(0xFF43A047) // grün: exklusive Owner-KI aktiv
                    OwnerLlmSource.VIEWER_FALLBACK -> Color(0xFFF57C00) // amber: Viewer-KI als Fallback
                    OwnerLlmSource.DETERMINISTIC -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = ownerLlmSource.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Antworten privat senden (Whisper)", modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotOwnerWhisperReplies,
                onCheckedChange = viewModel::onChatBotOwnerWhisperRepliesChange,
            )
        }
        Text(
            text = "Owner-Antworten (!start/!stop/!diag/!ask) kommen per Twitch-Whisper statt in den öffentlichen Chat. Dafür braucht der Bot-Token den Scope user:manage:whispers und die Twitch-App-Client-ID unten; schlägt der Whisper fehl (z. B. Empfänger blockt Fremde), antwortet der Bot öffentlich.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotTwitchClientId,
            onValueChange = viewModel::onChatBotTwitchClientIdChange,
            label = { Text("Twitch-App-Client-ID (für Whisper)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Media-Player-Steuerung: braucht Benachrichtigungszugriff
        Text(
            text = "Media-Befehle (!song / !next / !pause / !play / !prev) steuern den aktiven Musik-Player — dafür muss Vivid Benachrichtigungszugriff haben (liest keine Benachrichtigungen aus).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Benachrichtigungszugriff aktivieren")
        }
    }
}
