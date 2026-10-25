package com.vivid.feature.streaming.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.vivid.core.data.CameraControlSettings
import com.vivid.core.data.SceneVideoSource
import com.vivid.core.data.StreamScene
import com.vivid.feature.streaming.R
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.ZoomRange
import com.vivid.feature.streaming.source.VideoSourceKind
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.abs

internal data class ZoomPickerCameraState(
    val front: Boolean = false,
    val range: ZoomRange? = null,
    val zoom: Float? = null,
)

/** Local controls are mounted only above the preview, never in SceneLayerHost. */
@Composable
internal fun PreviewSelectionControls(
    scenes: List<StreamScene>,
    activeSceneId: String?,
    cameraSettings: CameraControlSettings,
    engine: StreamingEngine,
    source: VideoSourceKind,
    onScene: (StreamScene) -> Unit,
    modifier: Modifier = Modifier,
    showZoom: Boolean = true,
    showScenes: Boolean = true,
) {
    val cameraVisible = showZoom && source == VideoSourceKind.CAMERA &&
        scenes.find { it.id == activeSceneId }?.videoSource != SceneVideoSource.EMPTY
    val owner = LocalLifecycleOwner.current
    val camera by produceState(ZoomPickerCameraState(), engine, owner, cameraVisible) {
        if (cameraVisible) owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // Read the actual zoom so pinch, reset and camera switches update selection too.
            while (isActive) {
                value = ZoomPickerCameraState(engine.isFrontCamera(), engine.cameraZoomRange(), engine.currentCameraZoom())
                delay(100)
            }
        } else value = ZoomPickerCameraState()
    }
    PreviewSelectionRows(scenes, activeSceneId, cameraSettings, if (cameraVisible) camera else ZoomPickerCameraState(),
        onScene = onScene, onZoom = { engine.applyZoomPreset(camera.front, it) }, modifier = modifier,
        showScenes = showScenes)
}

@Composable
internal fun PreviewSelectionRows(
    scenes: List<StreamScene>,
    activeSceneId: String?,
    cameraSettings: CameraControlSettings,
    camera: ZoomPickerCameraState,
    onScene: (StreamScene) -> Unit,
    onZoom: (Float) -> Unit,
    modifier: Modifier = Modifier,
    showScenes: Boolean = true,
) {
    val enabledScenes = scenes.filter { it.enabled }
    val presets = if (camera.front) cameraSettings.frontZoomPresets else cameraSettings.rearZoomPresets
    val selectedPreset = camera.zoom?.let { zoom ->
        presets.minByOrNull { abs(it - zoom) }?.takeIf { abs(it - zoom) < 0.025f }
    }
    val locale = LocalLocale.current.platformLocale
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (camera.range != null && presets.isNotEmpty()) {
            SelectionRail(stringResource(R.string.preview_zoom_picker), "preview_zoom_picker") {
                presets.forEach { preset ->
                    val value = java.math.BigDecimal(preset.toString()).stripTrailingZeros().toPlainString()
                        .replace('.', java.text.DecimalFormatSymbols(locale).decimalSeparator)
                    SelectionButton(stringResource(R.string.preview_zoom_ratio, value),
                        selected = preset == selectedPreset,
                        enabled = preset in camera.range.min..camera.range.max,
                        tag = "preview_zoom_$preset", onClick = { onZoom(preset) })
                }
            }
        }
        if (showScenes && enabledScenes.isNotEmpty()) {
            SelectionRail(stringResource(R.string.scene_bar_title), "preview_scene_picker") {
                enabledScenes.forEach { scene ->
                    key(scene.id) {
                        SelectionButton(scene.name, scene.id == activeSceneId,
                            tag = "preview_scene_${scene.id}", onClick = { onScene(scene) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionRail(label: String, tag: String, content: @Composable RowScope.() -> Unit) {
    Surface(shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        modifier = Modifier.testTag(tag).semantics { contentDescription = label }) {
        Row(Modifier.horizontalScroll(rememberScrollState()).selectableGroup().padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), content = content)
    }
}

@Composable
private fun SelectionButton(label: String, selected: Boolean, tag: String,
    enabled: Boolean = true, onClick: () -> Unit) {
    Surface(shape = RoundedCornerShape(12.dp),
        color = if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent,
        contentColor = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            else if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.widthIn(min = 64.dp, max = 168.dp).heightIn(min = 48.dp)
            .testTag(tag).selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)) {
        Box(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
            Text(label, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
    }
}
