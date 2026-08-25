package com.vivid.feature.settings.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.vivid.core.log.LogEntry
import com.vivid.core.log.LogLevel
import com.vivid.feature.settings.R

/**
 * Kategorie „Logs & Diagnose“: zeigt die letzten 500 App-Log-Zeilen aus dem
 * app-weiten [com.vivid.core.log.LogBuffer] (geschwärzt durch den Redaktor —
 * Stream-Keys/Tokens/Passwörter sind nie sichtbar). Kopieren, Teilen und
 * Leeren direkt vom Screen aus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsLogsScreen(
    onBack: () -> Unit,
    viewModel: SettingsLogsViewModel = hiltViewModel(),
) {
    val logs by viewModel.logs.collectAsState()
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

            LogList(logs = logs, modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { copyLogs(context, logs) },
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Text(stringResource(R.string.logs_copy), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = { shareLogs(context, logs) },
                    modifier = Modifier.weight(1f),
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null)
                    Text(stringResource(R.string.logs_share), modifier = Modifier.padding(start = 4.dp))
                }
                OutlinedButton(
                    onClick = viewModel::clearLogs,
                    enabled = logs.isNotEmpty(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null)
                }
            }
        }
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items(logs, key = { it.timestampMillis to it.message }) { entry ->
            Text(
                text = entry.format(),
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                lineHeight = 14.sp,
                color = entry.level.color(),
            )
        }
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