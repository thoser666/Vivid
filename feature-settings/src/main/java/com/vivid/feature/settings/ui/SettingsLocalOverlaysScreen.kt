package com.vivid.feature.settings.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.data.LocalOverlaySettings
import com.vivid.feature.settings.R

@Composable
fun SettingsLocalOverlaysRoute(onBack: () -> Unit, viewModel: SettingsLocalOverlaysViewModel = hiltViewModel()) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    SettingsLocalOverlaysScreen(settings, onBack, viewModel::update)
}

@Composable
internal fun SettingsLocalOverlaysScreen(
    settings: LocalOverlaySettings?,
    onBack: () -> Unit,
    onChange: ((LocalOverlaySettings) -> LocalOverlaySettings) -> Unit,
) {
    val current = settings ?: LocalOverlaySettings()
    SettingsSectionScaffold(stringResource(R.string.local_overlays_title), onBack, onSave = null) {
        Text(stringResource(R.string.local_overlays_description), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                OverlaySwitch(stringResource(R.string.local_overlays_scenes), current.showScenes, settings != null,
                    "local_overlay_scenes") { enabled -> onChange { it.copy(showScenes = enabled) } }
                HorizontalDivider()
                OverlaySwitch(stringResource(R.string.local_overlays_zoom), current.showZoom, settings != null,
                    "local_overlay_zoom") { enabled -> onChange { it.copy(showZoom = enabled) } }
                HorizontalDivider()
                OverlaySwitch(stringResource(R.string.local_overlays_fps), current.showFps, settings != null,
                    "local_overlay_fps") { enabled -> onChange { it.copy(showFps = enabled) } }
                HorizontalDivider()
                OverlaySwitch(stringResource(R.string.local_overlays_bitrate), current.showBitrate, settings != null,
                    "local_overlay_bitrate") { enabled -> onChange { it.copy(showBitrate = enabled) } }
                HorizontalDivider()
                OverlaySwitch(stringResource(R.string.local_overlays_duration), current.showDuration, settings != null,
                    "local_overlay_duration") { enabled -> onChange { it.copy(showDuration = enabled) } }
            }
        }
    }
}

@Composable
private fun OverlaySwitch(label: String, checked: Boolean, enabled: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag)
        .toggleable(checked, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onCheckedChange = null, enabled = enabled)
    }
}
