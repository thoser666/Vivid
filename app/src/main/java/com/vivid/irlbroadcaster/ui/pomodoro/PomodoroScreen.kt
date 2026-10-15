package com.vivid.irlbroadcaster.ui.pomodoro

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.vivid.R
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect

/**
 * In-App-Pomodoro-Timer (PARITY-Zeile 143). Countdown mit Arbeits-/Pausenphase,
 * Automatik-Loop und Vibration am Phasenende — bewusst KEIN Overlay-Widget.
 *
 * Der Screen ist zustandslos (nimmt [uiState] + Callbacks); die Phasen-Enden
 * kommen als [phaseComplete]-Flow herein, dessen Emission der Screen über
 * [LocalContext] in eine kurze Vibration übersetzt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PomodoroScreen(
    uiState: PomodoroUiState,
    phaseComplete: Flow<PomodoroPhase>,
    onToggleRunning: () -> Unit,
    onSkip: () -> Unit,
    onReset: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        phaseComplete.collect { vibratePhaseComplete(context) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pomodoro_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.pomodoro_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val isWork = uiState.phase == PomodoroPhase.WORK
            Text(
                text = stringResource(
                    if (isWork) R.string.pomodoro_phase_work else R.string.pomodoro_phase_break,
                ),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (isWork) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.secondary
                },
            )

            Text(
                text = formatMmSs(uiState.remainingSeconds),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = stringResource(
                    R.string.pomodoro_completed_sessions,
                    uiState.completedWorkSessions,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (!uiState.enabled) {
                Text(
                    text = stringResource(R.string.pomodoro_disabled_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                )
                Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.pomodoro_open_settings))
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onToggleRunning,
                enabled = uiState.enabled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    stringResource(
                        if (uiState.running) R.string.pomodoro_pause else R.string.pomodoro_start,
                    ),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onSkip,
                    enabled = uiState.enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.pomodoro_skip))
                }
                OutlinedButton(
                    onClick = onReset,
                    enabled = uiState.enabled,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.pomodoro_reset))
                }
            }
        }
    }
}

/** Kurze Vibration am Phasenende; schluckt fehlende Hardware/Berechtigung. */
private fun vibratePhaseComplete(context: Context) {
    runCatching {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE),
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(400L)
            }
        }
    }
}
