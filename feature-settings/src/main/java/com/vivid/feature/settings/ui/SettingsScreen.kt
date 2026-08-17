package com.vivid.feature.settings.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vivid.core.data.ChatBotCommandScope
import com.vivid.core.data.ChatBotMode
import com.vivid.core.update.UpdateCheckResult
import com.vivid.feature.chat.bot.ChatBotUsage
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavHostController, // Nicht mehr optional, da wir ihn brauchen
    installedVersionName: String = "", // aus der Nav-Route (App-BuildConfig), für den Update-Indikator
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val updateState by viewModel.updateState.collectAsState()
    val remoteControl by viewModel.remoteControl.collectAsState()
    val botUsage by viewModel.botUsage.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    // Ab Android 17 (API 37, targetSdk 37) braucht die Web-Remote-Control die
    // ACCESS_LOCAL_NETWORK-Runtime-Berechtigung. Wird sie hier erteilt, wird
    // der LAN-Server neu gestartet, damit er sie übernimmt.
    val context = LocalContext.current
    val localNetworkPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.restartRemoteControlServer()
        }
    }
    val needsLocalNetworkPermission =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.CINNAMON_BUN &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_LOCAL_NETWORK,
            ) != PackageManager.PERMISSION_GRANTED

    // Einmaliger Update-Check beim Öffnen der Einstellungen (für den Obtainium-Test).
    LaunchedEffect(key1 = installedVersionName) {
        viewModel.checkForUpdates(installedVersionName)
    }

    // Lauscht auf das saveEvent vom ViewModel
    LaunchedEffect(key1 = Unit) {
        viewModel.saveEvent.collectLatest {
            // Zeige eine Bestätigung an
            scope.launch {
                snackbarHostState.showSnackbar("Einstellungen gespeichert")
            }
            // Navigiere zurück zum vorherigen Bildschirm
            navController.popBackStack()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues) // Verwende paddingValues vom Scaffold
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Stream-Einstellungen
            Text("Stream-Einstellungen", style = MaterialTheme.typography.titleLarge)

            // Plattform-Vorlagen: füllen die Ingest-URL und aktivieren RTMPS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StreamPlatform.entries.forEach { platform ->
                    FilterChip(
                        selected = uiState.streamUrl == platform.ingestUrl,
                        onClick = { viewModel.applyPlatformPreset(platform) },
                        label = { Text(platform.label) },
                    )
                }
            }

            OutlinedTextField(
                value = uiState.streamUrl,
                onValueChange = viewModel::onStreamUrlChange,
                label = { Text("Stream-URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.streamKey,
                onValueChange = viewModel::onStreamKeyChange,
                label = { Text("Stream-Schlüssel") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (RTMPS)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.streamUseTls,
                    onCheckedChange = viewModel::onStreamUseTlsChange,
                )
            }

            // Multi-Streaming: optionales zweites Ziel (parallel zum primären)
            Text("Multi-Streaming (optional)", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Zweites Stream-Ziel: Das Signal wird gleichzeitig zu beiden Zielen gesendet. Leer lassen, um nur das primäre Ziel zu nutzen.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.secondaryStreamUrl,
                onValueChange = viewModel::onSecondaryStreamUrlChange,
                label = { Text("Zweite Stream-URL") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.secondaryStreamKey,
                onValueChange = viewModel::onSecondaryStreamKeyChange,
                label = { Text("Zweiter Stream-Schlüssel") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (RTMPS)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.secondaryStreamUseTls,
                    onCheckedChange = viewModel::onSecondaryStreamUseTlsChange,
                )
            }

            // OBS-Einstellungen
            Text("OBS-Einstellungen", style = MaterialTheme.typography.titleLarge)
            OutlinedTextField(
                value = uiState.obsHost,
                onValueChange = viewModel::onObsHostChange,
                label = { Text("OBS Host") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.obsPort,
                onValueChange = viewModel::onObsPortChange,
                label = { Text("OBS Port") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = uiState.obsPassword,
                onValueChange = viewModel::onObsPasswordChange,
                label = { Text("OBS Passwort") },
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Sichere Verbindung (wss://)", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.obsUseTls,
                    onCheckedChange = viewModel::onObsUseTlsChange,
                )
            }

            // Chat-Overlay über der Streaming-Vorschau
            Text("Chat-Overlay", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Zeigt den Twitch-Chat des angegebenen Kanals über der Streaming-Vorschau an (anonym gelesen).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = uiState.chatChannel,
                onValueChange = viewModel::onChatChannelChange,
                label = { Text("Twitch-Kanal (ohne #)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Chat-Overlay anzeigen", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.chatOverlayEnabled,
                    onCheckedChange = viewModel::onChatOverlayEnabledChange,
                )
            }

            // Text-/Info-Widget über der Streaming-Vorschau (Uhrzeit/GPS/Geschwindigkeit)
            Text("Text-/Info-Widget", style = MaterialTheme.typography.titleLarge)
            Text(
                text = "Zeigt Uhrzeit, GPS-Koordinaten und Geschwindigkeit als Overlay rechts unten über der Streaming-Vorschau an. Für GPS/Geschwindigkeit wird die Standort-Berechtigung benötigt.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Widget anzeigen", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.widgetEnabled,
                    onCheckedChange = viewModel::onWidgetEnabledChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Uhrzeit", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.widgetShowTime,
                    onCheckedChange = viewModel::onWidgetShowTimeChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("GPS-Koordinaten", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.widgetShowLocation,
                    onCheckedChange = viewModel::onWidgetShowLocationChange,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Geschwindigkeit", modifier = Modifier.weight(1f))
                Switch(
                    checked = uiState.widgetShowSpeed,
                    onCheckedChange = viewModel::onWidgetShowSpeedChange,
                )
            }

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
                label = "Twitch-OAuth-Token (chat:read + chat:send)",
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

            // Web-Remote-Control: Zugangsdaten für den LAN-Server
            Text("Web-Remote-Control", style = MaterialTheme.typography.titleLarge)
            if (remoteControl.token.isNotBlank()) {
                Text(
                    text = "Im gleichen WLAN erreichbar unter http://<GERÄTE-IP>:${remoteControl.port}/status — Aktionen via Authorization: Bearer <token>.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = remoteControl.token,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Remote-Token") },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Text(
                    text = "Token wird geladen…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (needsLocalNetworkPermission) {
                OutlinedButton(
                    onClick = { localNetworkPermissionLauncher.launch(Manifest.permission.ACCESS_LOCAL_NETWORK) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("LAN-Zugriff für Remote-Control erlauben")
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Version + Update-Status — direkt sichtbar, ohne in den About-Screen zu gehen
            if (installedVersionName.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Version $installedVersionName",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    when (val result = updateState.result) {
                        is UpdateCheckResult.UpdateAvailable -> {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            ) {
                                Text(
                                    text = "⬆ Update verfügbar: ${result.latestVersion}",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                        is UpdateCheckResult.UpToDate -> {
                            Text(
                                text = "· aktuell",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        else -> Unit // Checking / Fehler: still bleiben
                    }
                }
            }

            OutlinedButton(
                onClick = { navController.navigate("about_route") },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Über Vivid & Updates")
            }

            Button(
                onClick = viewModel::saveSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Speichern")
            }
        }
    }
}

/** Anzeigename des Chat-Bot-Betriebsmodus (UI-spezifisch). */
private val ChatBotMode.displayName: String
    get() = when (this) {
        ChatBotMode.COMMAND -> "Bot (wie Moblin)"
        ChatBotMode.AUTONOMOUS -> "KI autonom"
    }

/** Anzeigename des Befehlsscopes (UI-spezifisch). */
private val ChatBotCommandScope.displayName: String
    get() = when (this) {
        ChatBotCommandScope.ALL -> "Alle !-Befehle"
        ChatBotCommandScope.MENTION -> "Nur Erwähnung"
        ChatBotCommandScope.PREFIX -> "Eigenes Präfix"
    }

/**
 * Passwort-/Token-Eingabefeld mit Sichtbarkeits-Toggle (Auge).
 * Genutzt für Twitch-OAuth-Token und LLM-API-Key.
 */
@Composable
private fun SecretField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = if (visible) "Eingabe ausblenden" else "Eingabe anzeigen",
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    )
}
