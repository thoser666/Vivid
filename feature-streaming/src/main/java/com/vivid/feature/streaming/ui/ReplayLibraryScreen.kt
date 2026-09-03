package com.vivid.feature.streaming.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.vivid.feature.playback.StreamPlayer
import com.vivid.feature.streaming.R
import com.vivid.feature.streaming.ReplayItem
import com.vivid.feature.streaming.ReplayLibraryViewModel
import java.text.DateFormat
import java.util.Date

/**
 * Replay-Bibliothek: Liste der gespeicherten MP4-Replays mit Inline-Wiedergabe
 * (Media3-Player), Löschen (einzelnt/alles mit Bestätigung) und Teilen über
 * den System-Share-Sheet (FileProvider).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReplayLibraryScreen(
    navController: NavController,
    viewModel: ReplayLibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.replay_library_share)

    LaunchedEffect(Unit) { viewModel.refresh() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.replay_library_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.replay_library_back),
                        )
                    }
                },
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = viewModel::confirmDeleteAll) {
                            Icon(
                                imageVector = Icons.Filled.DeleteForever,
                                contentDescription = stringResource(R.string.replay_library_delete_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                uiState.loading && uiState.items.isEmpty() -> CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )

                uiState.items.isEmpty() -> Text(
                    text = stringResource(R.string.replay_library_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.align(Alignment.Center),
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(uiState.items, key = { it.file.absolutePath }) { item ->
                        ReplayItemCard(
                            item = item,
                            onPlay = { viewModel.open(item) },
                            onShare = {
                                viewModel.shareIntent(item)?.let { intent ->
                                    context.startActivity(
                                        android.content.Intent.createChooser(
                                            intent,
                                            shareTitle,
                                        ),
                                    )
                                }
                            },
                            onDelete = { viewModel.requestDelete(item) },
                        )
                    }
                }
            }

            // Inline-Player: vollflächig über der Liste, Back/Close kehrt zurück.
            uiState.playing?.let { playing ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    Column {
                        StreamPlayer(
                            streamUrl = playing.file.absolutePath,
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(16f / 9f),
                        )
                        TextButton(onClick = viewModel::close) {
                            Text(stringResource(R.string.replay_library_close_player))
                        }
                    }
                }
            }
        }
    }

    // Lösch-Bestätigung (einzelne Datei).
    uiState.deleteCandidate?.let { candidate ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.replay_library_delete_title)) },
            text = { Text(stringResource(R.string.replay_library_delete_text, candidate.name)) },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.replay_library_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.replay_library_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReplayItemCard(
    item: ReplayItem,
    onPlay: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = formatReplayMetadata(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                FilledTonalButton(onClick = onPlay, enabled = item.sizeBytes > 0) {
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.replay_library_play))
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Filled.Share,
                        contentDescription = stringResource(R.string.replay_library_share),
                    )
                }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.replay_library_delete),
                    )
                }
            }
        }
    }
}

private fun formatReplayMetadata(item: ReplayItem): String =
    "${DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(item.lastModified))} · " +
        "${item.sizeBytes / 1024} KB"
