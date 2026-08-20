package com.vivid.irlbroadcaster.ui.about

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.vivid.core.update.AppVersion
import com.vivid.core.update.UpdateCheckResult
import com.vivid.core.update.ReleaseChannel
import com.vivid.R

/** Einstiegs-Link-Ziele für den About-Screen. */
private object Links {
    const val RELEASES = "https://github.com/thoser666/Vivid/releases"
    const val CHANGELOG = "https://github.com/thoser666/Vivid/blob/develop/CHANGELOG.md"
    const val PRIVACY = "https://github.com/thoser666/Vivid/blob/develop/PRIVACY.md"
    const val REPO = "https://github.com/thoser666/Vivid"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(
    navController: NavHostController,
    viewModel: AboutViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.about_title)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.about_back),
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            VersionCard(
                versionName = viewModel.installedVersionName,
                versionCode = viewModel.installedVersionCode,
            )

            UpdateCheckCard(
                checking = uiState.checking,
                result = uiState.result,
                onCheck = viewModel::checkForUpdates,
                onOpenRelease = { uriHandler.openUri(it) },
            )

            LinksCard(
                onOpenUri = uriHandler::openUri,
            )

            Text(
                text = stringResource(R.string.about_source_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VersionCard(versionName: String, versionCode: Int) {
    val channel = AppVersion.parse(versionName)?.channel ?: ReleaseChannel.STABLE
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                ChannelBadge(channel = channel)
            }
            Text(
                text = stringResource(R.string.about_version_line, versionName, versionCode),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(R.string.about_obtainium_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChannelBadge(channel: ReleaseChannel) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = when (channel) {
            ReleaseChannel.NIGHTLY -> MaterialTheme.colorScheme.secondaryContainer
            ReleaseChannel.ALPHA -> MaterialTheme.colorScheme.tertiaryContainer
            ReleaseChannel.BETA -> MaterialTheme.colorScheme.tertiaryContainer
            ReleaseChannel.RC -> MaterialTheme.colorScheme.primaryContainer
            ReleaseChannel.STABLE -> MaterialTheme.colorScheme.primaryContainer
        },
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
    ) {
        Text(
            text = channel.label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

@Composable
private fun UpdateCheckCard(
    checking: Boolean,
    result: UpdateCheckResult?,
    onCheck: () -> Unit,
    onOpenRelease: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.about_updates_title), style = MaterialTheme.typography.titleMedium)
            Button(
                onClick = onCheck,
                enabled = !checking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (checking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.about_checking))
                } else {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(
                            if (result == null) R.string.about_check_button else R.string.about_recheck_button,
                        ),
                    )
                }
            }
            result?.let { UpdateResultRow(it, onOpenRelease) }
        }
    }
}

@Composable
private fun UpdateResultRow(result: UpdateCheckResult, onOpenRelease: (String) -> Unit) {
    when (result) {
        is UpdateCheckResult.UpToDate -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(stringResource(R.string.about_up_to_date, result.latestVersion))
            }
        }
        is UpdateCheckResult.UpdateAvailable -> {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(
                        Icons.Default.SystemUpdate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(stringResource(R.string.about_update_available, result.latestVersion), fontWeight = FontWeight.SemiBold)
                }
                val notes = cleanReleaseNotes(result.releaseNotes)
                if (notes.isNotBlank()) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.about_whats_new),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.heightIn(max = 160.dp).verticalScroll(rememberScrollState()),
                            )
                        }
                    }
                }
                OutlinedButton(onClick = { onOpenRelease(result.releaseUrl) }) {
                    Text(stringResource(R.string.about_release_page))
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
        is UpdateCheckResult.Error -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
                Text(
                    text = stringResource(result.messageRes, *result.formatArgs.toTypedArray()),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun LinksCard(onOpenUri: (String) -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            LinkRow(stringResource(R.string.about_link_releases), Links.RELEASES, onOpenUri)
            HorizontalDivider()
            LinkRow(stringResource(R.string.about_link_changelog), Links.CHANGELOG, onOpenUri)
            HorizontalDivider()
            LinkRow(stringResource(R.string.about_link_privacy), Links.PRIVACY, onOpenUri)
            HorizontalDivider()
            LinkRow(stringResource(R.string.about_link_repo), Links.REPO, onOpenUri)
        }
    }
}

@Composable
/** Reduziert GitHub-Markdown auf lesbaren Klartext (Header, Links, Fett, Code, Bullets). */
private fun cleanReleaseNotes(notes: String): String =
    notes
        .replace(Regex("\\[([^]]+)]\\([^)]*\\)"), "\$1") // [text](url) → text
        .replace(Regex("^#{1,6}\\s*", RegexOption.MULTILINE), "") // ###-Header raus
        .replace(Regex("^[-*+]\\s+", RegexOption.MULTILINE), "• ") // Bullets → •
        .replace(Regex("\\*\\*|__|`"), "") // **bold**, __bold__, `code` raus
        .trim()

@Composable
private fun LinkRow(label: String, url: String, onOpenUri: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = stringResource(R.string.about_link_open, label),
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
