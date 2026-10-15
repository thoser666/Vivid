package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.vivid.core.data.AppSettings

/**
 * Kategorie „Pomodoro“: In-App-Konzentrations-Timer (Pomodoro-Technik) mit
 * konfigurierbarer Arbeits-/Pausendauer. Der eigentliche Countdown läuft auf
 * einem eigenen Screen ([onOpenTimer]); hier werden nur Freischaltung und
 * Zeiten gesetzt. Bewusst KEIN Overlay-Widget (PARITY-Zeile 143: In-App-Timer).
 */
@Composable
fun SettingsPomodoroScreen(
    uiState: AppSettings,
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onOpenTimer: () -> Unit,
) {
    SettingsSectionScaffold(
        title = stringResource(R.string.cat_pomodoro_title),
        onBack = onBack,
        onSave = viewModel::saveSettings,
    ) {
        Text(stringResource(R.string.pomodoro_title), style = MaterialTheme.typography.titleLarge)
        Text(
            text = stringResource(R.string.pomodoro_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.pomodoro_enabled), modifier = Modifier.weight(1f))
            Switch(
                checked = uiState.pomodoroEnabled,
                onCheckedChange = viewModel::onPomodoroEnabledChange,
            )
        }

        // Arbeitsphase: 1–120 min (Slider rastet minutengenau ein).
        Text(stringResource(R.string.pomodoro_work_minutes, uiState.pomodoroWorkDurationMinutes))
        Slider(
            value = uiState.pomodoroWorkDurationMinutes.toFloat(),
            onValueChange = { viewModel.onPomodoroWorkDurationChange(it.toInt()) },
            valueRange = 1f..120f,
            steps = 118,
            modifier = Modifier.fillMaxWidth(),
        )

        // Pause: 1–60 min.
        Text(stringResource(R.string.pomodoro_break_minutes, uiState.pomodoroBreakDurationMinutes))
        Slider(
            value = uiState.pomodoroBreakDurationMinutes.toFloat(),
            onValueChange = { viewModel.onPomodoroBreakDurationChange(it.toInt()) },
            valueRange = 1f..60f,
            steps = 58,
            modifier = Modifier.fillMaxWidth(),
        )

        Button(
            onClick = onOpenTimer,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Filled.Timer, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.pomodoro_open_timer))
        }
    }
}
