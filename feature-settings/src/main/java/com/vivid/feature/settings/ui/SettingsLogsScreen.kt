package com.vivid.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.core.log.LogDates
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.feature.settings.R

/**
 * Kategorie „Logs & Diagnose“: zeigt die App-Logs innerhalb der konfigurierbaren
 * Vorhaltezeit (tägliche Rotation) — Live-Puffer + persistierte Tages-Dateien.
 * Abstürze ([LogEntry.isCrash]) sind deutlich markiert, ein Filter zeigt nur
 * Fehler/Crashes, die Vorhaltezeit ist direkt einstellbar. Kopieren/Teilen/Leeren
 * vom Screen aus; sensible Werte sind durch den Redaktor geschwärzt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLogsScreen(
    onBack: () -> Unit,
    viewModel: SettingsLogsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cat_logs_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.settings_back),
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.logs_redacted_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            RetentionField(
                retentionDays = state.retentionDays,
                onRetentionChange = viewModel::setRetentionDays,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilterChip(
                    selected = !state.errorsOnly,
                    onClick = { if (state.errorsOnly) viewModel.toggleErrorsOnly() },
                    label = { Text(stringResource(R.string.logs_filter_all)) },
                )
                FilterChip(
                    selected = state.errorsOnly,
                    onClick = { if (!state.errorsOnly) viewModel.toggleErrorsOnly() },
                    label = { Text(stringResource(R.string.logs_filter_errors)) },
                )
                Text(
                    text = stringResource(R.string.logs_crash_summary, state.crashCount, state.retentionDays),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (state.crashCount > 0) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            LogList(logs = state.entries, modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { copyLogs(context, state.entries) },
                    modifier = Modifier.weight(1f),
                    enabled = state.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(stringResource(R.string.logs_copy), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { shareLogs(context, state.entries) },
                    modifier = Modifier.weight(1f),
                    enabled = state.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.logs_share), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = viewModel::clearLogs,
                    enabled = state.entries.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
    }
}

/** Vorhaltezeit-Feld (1–30 Tage): speichert bei gültiger Eingabe (Enter/Bestätigen). */
@Composable
private fun RetentionField(
    retentionDays: Int,
    onRetentionChange: (Int) -> Unit,
) {
    var text by remember(retentionDays) { mutableStateOf(retentionDays.toString()) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { new ->
                text = new.filter { it.isDigit() }.take(2)
                text.toIntOrNull()?.let { onRetentionChange(it) }
            },
            label = { Text(stringResource(R.string.logs_retention_title)) },
            singleLine = true,
            modifier = Modifier.width(120.dp),
        )
        Text(
            text = stringResource(R.string.logs_retention_days_unit),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = stringResource(R.string.logs_retention_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun LogList(logs: List<LogEntry>, modifier: Modifier = Modifier) {
    if (logs.isEmpty()) {
        Text(
            text = stringResource(R.string.logs_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        return
    }

    // Neueste zuerst, gruppiert nach Kalendertag (Tages-Sektionen „Heute“/„Gestern“/Datum).
    val grouped = logs.groupBy { LogDates.dayKey(it.timestampMillis) }.toSortedMap(compareByDescending { it })

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        grouped.forEach { (dayKey, dayEntries) ->
            item(key = "day-$dayKey") {
                Text(
                    text = dayHeader(dayKey),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
                )
            }
            items(dayEntries, key = { it.timestampMillis to it.message }) { entry ->
                LogRow(entry)
            }
        }
    }
}

@Composable
private fun dayHeader(dayKey: String): String = when {
    LogDates.isToday(dayKey) -> stringResource(R.string.logs_today)
    LogDates.isYesterday(dayKey) -> stringResource(R.string.logs_yesterday)
    else -> LogDates.formatDate(dayKey)
}

/** Eine Log-Zeile; Abstürze sind deutlich markiert (Hintergrund + CRASH-Badge). */
@Composable
private fun LogRow(entry: LogEntry) {
    val background = if (entry.isCrash) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        if (entry.isCrash) {
            Text(
                text = stringResource(R.string.logs_crash_badge),
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(end = 6.dp),
            )
        }
        Text(
            text = entry.format(),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            lineHeight = 14.sp,
            color = entry.level.color(),
        )
    }
}

/** Farbe je Log-Stufe: Fehler rot, Warnung orange, Rest gedämpft. */
@Composable
private fun LogLevel.color(): Color = when (this) {
    LogLevel.ERROR, LogLevel.ASSERT -> MaterialTheme.colorScheme.error
    LogLevel.WARN -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private fun copyLogs(context: Context, logs: List<LogEntry>) {
    if (logs.isEmpty()) return
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.logs_clip_label), exportText(logs)))
    Toast.makeText(context, context.getString(R.string.logs_copied), Toast.LENGTH_SHORT).show()
}

private fun shareLogs(context: Context, logs: List<LogEntry>) {
    if (logs.isEmpty()) return
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.logs_share_subject))
        putExtra(Intent.EXTRA_TEXT, exportText(logs))
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.logs_share)))
}

private fun exportText(logs: List<LogEntry>): String =
    logs.joinToString(separator = "\n") { it.format() }
