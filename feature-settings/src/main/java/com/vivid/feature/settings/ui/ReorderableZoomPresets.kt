package com.vivid.feature.settings.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vivid.feature.settings.R

/** The stationary column owns the gesture while keyed rows move below the held handle. */
@Composable
internal fun ReorderableZoomPresets(
    presets: List<Float>,
    prefix: String,
    enabled: Boolean,
    scrollState: ScrollState,
    onReorder: (List<Float>) -> Unit,
    rowContent: @Composable RowScope.(Int, Float) -> Unit,
) {
    val dragState = rememberReorderDragState(presets)
    val currentReorder by rememberUpdatedState(onReorder)
    ReorderAutoScroll(dragState, scrollState, requireMove = true)

    Column(Modifier.fillMaxWidth().testTag("${prefix}_zoom_presets")
        .reorderGestures(dragState, enabled, handlesOnly = true) { currentReorder(it) }) {
        dragState.order.forEachIndexed { index, value ->
            key(value) {
                Row(Modifier.fillMaxWidth().zIndex(if (dragState.dragging == value) 1f else 0f)
                    .onGloballyPositioned { dragState.centers[value] = it.positionInRoot().y + it.size.height / 2f }
                    .reorderVisuals(dragState, value)
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                    .padding(horizontal = 8.dp).testTag("${prefix}_zoom_row_$value"),
                    verticalAlignment = Alignment.CenterVertically) {
                    val description = stringResource(R.string.camera_zoom_reorder,
                        stringResource(R.string.camera_zoom_ratio, value.toString()))
                    ReorderHandle(dragState, index, value, description, "${prefix}_zoom_drag_$value", enabled) { currentReorder(it) }
                    rowContent(index, value)
                }
            }
            if (index < dragState.order.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
        }
    }
}
