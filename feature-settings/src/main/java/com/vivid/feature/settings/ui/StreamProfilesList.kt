package com.vivid.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.vivid.feature.settings.R
import com.vivid.core.data.StreamProfile

/** The fixed list container owns the drag so moving a row cannot move the gesture coordinates. */
@Composable
internal fun StreamProfilesList(
    profiles: List<StreamProfile>,
    activeId: String?,
    scrollState: ScrollState,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    if (profiles.isEmpty()) return
    val savedIds = profiles.map { it.id }
    val dragState = rememberReorderDragState(savedIds)
    val currentReorder by rememberUpdatedState(onReorder)
    ReorderAutoScroll(dragState, scrollState)

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().testTag("stream_profiles_list")
                .reorderGestures(dragState) { currentReorder(it) },
        ) {
            val byId = profiles.associateBy { it.id }
            dragState.order.forEachIndexed { index, id ->
                val profile = byId.getValue(id)
                key(id) {
                    Box(
                        Modifier.zIndex(if (dragState.dragging == id) 1f else 0f)
                            .onGloballyPositioned {
                                dragState.centers[id] = it.positionInRoot().y + it.size.height / 2f
                                dragState.heights[id] = it.size.height
                            },
                    ) {
                        ListItem(
                            headlineContent = { Text(profile.name) },
                            supportingContent = { Text(stringResource(StreamPlatform.valueOf(profile.platform.name).labelRes)) },
                            leadingContent = {
                                val description = stringResource(R.string.stream_reorder, profile.name)
                                ReorderHandle(dragState, index, id, description, "stream_drag_$id") { currentReorder(it) }
                            },
                            trailingContent = {
                                StreamSwitchAction("stream_switch_divider_$id") {
                                    Switch(checked = activeId == id, onCheckedChange = { onToggle(id, it) },
                                        modifier = Modifier.testTag("stream_toggle_$id").semantics { contentDescription = profile.name })
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            modifier = Modifier.fillMaxWidth().testTag("stream_row_$id")
                                .reorderVisuals(dragState, id)
                                .clickable { onOpen(id) },
                        )
                    }
                    if (index < dragState.order.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
