package com.vivid.feature.settings.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vivid.core.data.CameraControlSettings
import com.vivid.core.data.StabilizationMode
import com.vivid.feature.settings.R
import androidx.compose.ui.platform.LocalLocale

@Composable
internal fun CameraAdditionalControls(
    state: CameraControlsUiState,
    onZoom: () -> Unit,
    onChange: ((CameraControlSettings) -> CameraControlSettings) -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth().clickable(onClick = onZoom).padding(vertical = 8.dp)
                .testTag("camera_zoom_settings"), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.camera_zoom_title), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
            HorizontalDivider()
            StabilizationDropdown(state, onChange)
            HorizontalDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.camera_mirror_front), Modifier.weight(1f))
                Switch(checked = state.settings.mirrorFrontCamera, enabled = state.loaded,
                    onCheckedChange = { enabled -> onChange { it.copy(mirrorFrontCamera = enabled) } },
                    modifier = Modifier.testTag("camera_mirror_front"))
            }
        }
    }
}

@Composable
private fun StabilizationDropdown(
    state: CameraControlsUiState,
    onChange: ((CameraControlSettings) -> CameraControlSettings) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .clickable(enabled = state.loaded) { expanded = true }
            .testTag("camera_stabilization"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.camera_stabilization_title), Modifier.weight(1f),
            fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(state.settings.stabilization.labelResource()),
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                StabilizationMode.entries.forEach { mode ->
                    DropdownMenuItem(text = { Text(stringResource(mode.labelResource()), fontWeight = FontWeight.Normal) },
                        enabled = mode in state.stabilizationModes,
                        onClick = { onChange { it.copy(stabilization = mode) }; expanded = false },
                        modifier = Modifier.testTag("camera_stabilization_${mode.name}"))
                }
            }
        }
    }
    Text(stringResource(R.string.camera_stabilization_hint), style = MaterialTheme.typography.bodySmall)
}

private fun StabilizationMode.labelResource(): Int = when (this) {
    StabilizationMode.OFF -> R.string.camera_stabilization_off
    StabilizationMode.STANDARD -> R.string.camera_stabilization_standard
    StabilizationMode.CINEMATIC -> R.string.camera_stabilization_cinematic
}

@Composable
internal fun CameraZoomScreen(
    state: CameraControlsUiState,
    onBack: () -> Unit,
    onChange: ((CameraControlSettings) -> CameraControlSettings) -> Unit,
) {
    BackHandler(onBack = onBack)
    val scrollState = rememberScrollState()
    SettingsSectionScaffold(title = stringResource(R.string.camera_zoom_title), onBack = onBack,
        onSave = null, scrollState = scrollState) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                var speed by remember(state.settings.zoomSpeed) { mutableFloatStateOf(state.settings.zoomSpeed) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stringResource(R.string.camera_zoom_speed))
                    Text(String.format(LocalLocale.current.platformLocale, "%.1f", speed))
                }
                Slider(value = speed, onValueChange = { speed = it }, enabled = state.loaded,
                    onValueChangeFinished = { onChange { it.copy(zoomSpeed = speed) } },
                    valueRange = 0.1f..10f, modifier = Modifier.testTag("camera_zoom_speed"))
            }
        }
        for (front in listOf(false, true)) {
            Text(stringResource(if (front) R.string.camera_zoom_front_presets else R.string.camera_zoom_rear_presets),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Normal)
            ZoomPresetList(front, state, scrollState, onChange)
        }
        Text(stringResource(R.string.camera_zoom_reorder_hint), style = MaterialTheme.typography.bodySmall)
        Text(stringResource(R.string.camera_zoom_settings_hint), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ZoomPresetList(
    front: Boolean,
    state: CameraControlsUiState,
    scrollState: ScrollState,
    onChange: ((CameraControlSettings) -> CameraControlSettings) -> Unit,
) {
    val presets = if (front) state.settings.frontZoomPresets else state.settings.rearZoomPresets
    var editing by rememberSaveable { mutableStateOf(false) }
    var editIndex by rememberSaveable { mutableIntStateOf(-1) }
    var input by rememberSaveable { mutableStateOf("") }
    val prefix = if (front) "front" else "rear"
    Card(Modifier.fillMaxWidth()) {
        Column {
            ReorderableZoomPresets(presets, prefix, state.loaded, scrollState, onReorder = { order ->
                onChange { current -> current.withPresets(front) { values ->
                    if (values.size == order.size && values.toSet() == order.toSet()) order else values
                } }
            }) { index, value ->
                TextButton(onClick = { editIndex = presets.indexOf(value); input = value.toString(); editing = true },
                    enabled = state.loaded, modifier = Modifier.weight(1f).testTag("${prefix}_zoom_edit_$index")) {
                    Text(stringResource(R.string.camera_zoom_ratio, value.toString()), fontWeight = FontWeight.Normal)
                    Spacer(Modifier.weight(1f))
                }
                IconButton(onClick = { onChange { current ->
                    current.withPresets(front) { values -> values.filter { it != value } }
                } }, enabled = state.loaded, modifier = Modifier.testTag("${prefix}_zoom_delete_$index")) {
                    Icon(Icons.Default.Delete, stringResource(R.string.camera_zoom_delete))
                }
            }
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            TextButton(onClick = { editIndex = -1; input = ""; editing = true },
                enabled = state.loaded && presets.size < 20,
                modifier = Modifier.fillMaxWidth().testTag("${prefix}_zoom_create")) {
                Text(stringResource(R.string.camera_zoom_create), fontWeight = FontWeight.Normal)
            }
        }
    }
    if (editing) {
        val number = input.replace(',', '.').toFloatOrNull()
        val valid = number != null && number.isFinite() && number in 0.1f..100f &&
            presets.filterIndexed { i, _ -> i != editIndex }.none { it == number }
        AlertDialog(onDismissRequest = { editing = false },
            title = { Text(stringResource(if (editIndex < 0) R.string.camera_zoom_create else R.string.camera_zoom_edit)) },
            text = {
                Column {
                    OutlinedTextField(value = input, onValueChange = { input = it }, singleLine = true,
                        label = { Text(stringResource(R.string.camera_zoom_value)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        isError = input.isNotEmpty() && !valid, modifier = Modifier.testTag("camera_zoom_value"))
                    Text(stringResource(R.string.camera_zoom_value_hint), style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = {
                val index = editIndex
                val value = requireNotNull(number)
                onChange { current -> current.withPresets(front) { values ->
                    if (index < 0) values + value else values.mapIndexed { i, old -> if (i == index) value else old }
                } }
                editing = false
            }) { Text(stringResource(R.string.settings_save_button), fontWeight = FontWeight.Normal) } },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel), fontWeight = FontWeight.Normal) } })
    }
}

private fun CameraControlSettings.withPresets(front: Boolean, update: (List<Float>) -> List<Float>): CameraControlSettings =
    if (front) copy(frontZoomPresets = update(frontZoomPresets)) else copy(rearZoomPresets = update(rearZoomPresets))
