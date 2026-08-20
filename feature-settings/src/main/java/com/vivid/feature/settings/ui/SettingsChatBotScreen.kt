package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
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
import androidx.compose.ui.res.stringResource
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
        title = stringResource(R.string.cat_chatbot_title),
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        // Chat-Bot (KI): An/Aus + Betriebsmodus-Switch + alle Konfigurationsfelder
        Text(stringResource(R.string.chatbot_section_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.chatbot_section_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chatbot_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotEnabled,
                onCheckedChange = viewModel::onChatBotEnabledChange,
            )
        }
        Text(
            text = stringResource(R.string.chatbot_mode_title),
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
                    label = { Text(stringResource(mode.displayNameRes)) },
                )
            }
        }
        Text(
            text = stringResource(
                when (uiState.chatBotMode) {
                    ChatBotMode.COMMAND -> R.string.chatbot_mode_command_desc
                    ChatBotMode.AUTONOMOUS -> R.string.chatbot_mode_autonomous_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Bot-Konto (Twitch) + LLM-Zugang
        Text(stringResource(R.string.chatbot_account_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.chatBotLogin,
            onValueChange = viewModel::onChatBotLoginChange,
            label = { Text(stringResource(R.string.chatbot_login_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotOauthToken,
            onValueChange = viewModel::onChatBotOauthTokenChange,
            labelRes = R.string.chatbot_oauth_label,
        )
        OutlinedTextField(
            value = uiState.chatBotApiBaseUrl,
            onValueChange = viewModel::onChatBotApiBaseUrlChange,
            label = { Text(stringResource(R.string.chatbot_api_base_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotApiKey,
            onValueChange = viewModel::onChatBotApiKeyChange,
            labelRes = R.string.chatbot_api_key_label,
        )
        OutlinedTextField(
            value = uiState.chatBotModel,
            onValueChange = viewModel::onChatBotModelChange,
            label = { Text(stringResource(R.string.chatbot_model_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotSystemPrompt,
            onValueChange = viewModel::onChatBotSystemPromptChange,
            label = { Text(stringResource(R.string.chatbot_system_prompt_label)) },
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        // Bot-Verhalten
        Text(stringResource(R.string.chatbot_behavior_title), style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = uiState.chatBotReplyCooldownSeconds.toString(),
            onValueChange = viewModel::onChatBotReplyCooldownSecondsChange,
            label = { Text(stringResource(R.string.chatbot_cooldown_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chatbot_mentions_only), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotMentionsOnly,
                onCheckedChange = viewModel::onChatBotMentionsOnlyChange,
            )
        }
        OutlinedTextField(
            value = uiState.chatBotMaxRepliesPerMinute.toString(),
            onValueChange = viewModel::onChatBotMaxRepliesPerMinuteChange,
            label = { Text(stringResource(R.string.chatbot_max_per_minute_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Begrenzungen: pro Viewer + Kosten-Budget (0 = aus/unbegrenzt)
        Text(
            text = stringResource(R.string.chatbot_limits_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.chatbot_preset_title),
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
                    label = { Text(stringResource(preset?.displayNameRes ?: R.string.chatbot_preset_custom)) },
                )
            }
        }
        OutlinedTextField(
            value = uiState.chatBotPerViewerCooldownSeconds.toString(),
            onValueChange = viewModel::onChatBotPerViewerCooldownSecondsChange,
            label = { Text(stringResource(R.string.chatbot_per_viewer_cooldown_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotPerViewerMaxReplies.toString(),
            onValueChange = viewModel::onChatBotPerViewerMaxRepliesChange,
            label = { Text(stringResource(R.string.chatbot_per_viewer_max_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = uiState.chatBotMaxRepliesPerHour.toString(),
            onValueChange = viewModel::onChatBotMaxRepliesPerHourChange,
            label = { Text(stringResource(R.string.chatbot_budget_label)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        // Live-Verbrauch: Kosten-Budget beobachten (nur solange der Bot aktiv ist)
        Text(
            text = stringResource(R.string.chatbot_usage_title),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = stringResource(
                R.string.chatbot_usage_per_hour,
                if (botUsage.hourlyBudget > 0) {
                    "${botUsage.repliesThisHour} / ${botUsage.hourlyBudget}"
                } else {
                    "${botUsage.repliesThisHour} ${stringResource(R.string.chatbot_usage_no_budget)}"
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.chatbot_usage_per_stream, botUsage.totalRepliesThisStream),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (botUsage.topViewers.isNotEmpty()) {
            Text(
                text = stringResource(
                    R.string.chatbot_usage_top_viewers,
                    botUsage.topViewers.joinToString { "${it.displayName} (${it.replies})" },
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = stringResource(R.string.chatbot_usage_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        // Koexistenz mit anderen Bots (z. B. Rivulet)
        Text(stringResource(R.string.chatbot_coexistence_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.chatbot_coexistence_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotIgnoreBots,
            onValueChange = viewModel::onChatBotIgnoreBotsChange,
            label = { Text(stringResource(R.string.chatbot_ignore_bots_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.chatbot_scope_title),
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
                    label = { Text(stringResource(scope.displayNameRes)) },
                )
            }
        }
        Text(
            text = stringResource(
                when (uiState.chatBotCommandScope) {
                    ChatBotCommandScope.ALL -> R.string.chatbot_scope_all_desc
                    ChatBotCommandScope.MENTION -> R.string.chatbot_scope_mention_desc
                    ChatBotCommandScope.PREFIX -> R.string.chatbot_scope_prefix_desc
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (uiState.chatBotCommandScope == ChatBotCommandScope.PREFIX) {
            OutlinedTextField(
                value = uiState.chatBotCommandPrefix,
                onValueChange = viewModel::onChatBotCommandPrefixChange,
                label = { Text(stringResource(R.string.chatbot_prefix_label)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // Owner-Zugriff (nur der Streamer): Allow-List + eigene Owner-KI
        Text(stringResource(R.string.chatbot_owner_title), style = MaterialTheme.typography.titleMedium)
        Text(
            text = stringResource(R.string.chatbot_owner_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLogins,
            onValueChange = viewModel::onChatBotOwnerLoginsChange,
            label = { Text(stringResource(R.string.chatbot_owner_logins_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = stringResource(R.string.chatbot_owner_llm_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLlmBaseUrl,
            onValueChange = viewModel::onChatBotOwnerLlmBaseUrlChange,
            label = { Text(stringResource(R.string.chatbot_owner_llm_url_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        SecretField(
            value = uiState.chatBotOwnerLlmApiKey,
            onValueChange = viewModel::onChatBotOwnerLlmApiKeyChange,
            labelRes = R.string.chatbot_owner_llm_key_label,
        )
        OutlinedTextField(
            value = uiState.chatBotOwnerLlmModel,
            onValueChange = viewModel::onChatBotOwnerLlmModelChange,
            label = { Text(stringResource(R.string.chatbot_owner_llm_model_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        // Aktive KI-Quelle der Owner-Befehle (abgeleitet — identische
        // Auswahl wie die Engine: eigene Owner-KI → Viewer-KI → deterministisch).
        val ownerLlmSource = viewModel.ownerLlmSource
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.chatbot_owner_source_label, stringResource(ownerLlmSource.labelRes)),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = when (ownerLlmSource) {
                    OwnerLlmSource.OWNER -> Color(0xFF43A047) // grün: exklusive Owner-KI aktiv
                    OwnerLlmSource.VIEWER_FALLBACK -> Color(0xFFF57C00) // amber: Viewer-KI als Fallback
                    OwnerLlmSource.DETERMINISTIC -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Text(
                text = stringResource(ownerLlmSource.descriptionRes),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.chatbot_owner_whisper), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.chatBotOwnerWhisperReplies,
                onCheckedChange = viewModel::onChatBotOwnerWhisperRepliesChange,
            )
        }
        Text(
            text = stringResource(R.string.chatbot_owner_whisper_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = uiState.chatBotTwitchClientId,
            onValueChange = viewModel::onChatBotTwitchClientIdChange,
            label = { Text(stringResource(R.string.chatbot_client_id_label)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        // Media-Player-Steuerung: braucht Benachrichtigungszugriff
        Text(
            text = stringResource(R.string.chatbot_media_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedButton(
            onClick = {
                runCatching { context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.chatbot_notification_access_button))
        }
    }
}
