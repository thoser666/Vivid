package com.vivid.feature.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.data.PomodoroPhase
import com.vivid.core.data.PomodoroUiState
import com.vivid.core.data.StreamWidget
import com.vivid.core.data.formatMmSs
import com.vivid.feature.widgets.R

@Composable
fun PomodoroWidget(widget: StreamWidget, viewModel: PomodoroWidgetViewModel = hiltViewModel()) {
    val states by viewModel.states.collectAsStateWithLifecycle()
    val state = states[widget.id] ?: PomodoroUiState(enabled = widget.enabled,
        remainingSeconds = widget.configuration.pomodoroWorkDurationMinutes * 60)
    PomodoroWidgetContent(widget.name, state, { viewModel.toggle(widget) },
        { viewModel.skip(widget.id) }, { viewModel.reset(widget.id) })
}

@Composable
fun PomodoroWidgetContent(
    name: String, state: PomodoroUiState, onToggle: () -> Unit, onSkip: () -> Unit, onReset: () -> Unit,
    startLabelRes: Int = R.string.pomodoro_start,
) {
    Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.9f),
        modifier = Modifier.testTag("pomodoro_widget")) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(name, style = MaterialTheme.typography.titleSmall)
            Text(stringResource(if (state.phase == PomodoroPhase.WORK) R.string.pomodoro_phase_work else R.string.pomodoro_phase_break))
            Text(formatMmSs(state.remainingSeconds), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text(stringResource(R.string.pomodoro_completed_sessions, state.completedWorkSessions), style = MaterialTheme.typography.bodySmall)
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onToggle, enabled = state.enabled, modifier = Modifier.testTag("pomodoro_toggle")) {
                    Text(stringResource(if (state.running) R.string.pomodoro_pause else startLabelRes))
                }
                IconButton(onClick = onSkip, enabled = state.enabled) {
                    Icon(Icons.Default.SkipNext, stringResource(R.string.pomodoro_skip))
                }
                IconButton(onClick = onReset, enabled = state.enabled) {
                    Icon(Icons.Default.RestartAlt, stringResource(R.string.pomodoro_reset))
                }
            }
        }
    }
}
