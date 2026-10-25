package com.vivid.feature.settings.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.foundation.clickable
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material.icons.filled.Delete
import com.vivid.feature.settings.R

/** The fixed list container owns the drag so moving a row cannot move the gesture coordinates. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun <T> SceneItemsList(
    items: List<T>,
    itemId: (T) -> String,
    itemName: (T) -> String,
    itemEnabled: (T) -> Boolean,
    onDelete: (String) -> Unit,
    scrollState: ScrollState,
    onOpen: (String) -> Unit,
    onToggle: (String, Boolean) -> Unit,
    onReorder: (List<String>) -> Unit,
) {
    if (items.isEmpty()) return
    val savedIds = items.map(itemId)
    val dragState = rememberReorderDragState(savedIds)
    val currentReorder by rememberUpdatedState(onReorder)
    ReorderAutoScroll(dragState, scrollState)

    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            Modifier.fillMaxWidth().testTag("scene_item_profiles_list")
                .reorderGestures(dragState) { currentReorder(it) },
        ) {
            val byId = items.associateBy(itemId)
            dragState.order.forEachIndexed { index, id ->
                val item = byId.getValue(id)
                key(id) {
                    Box(
                        Modifier.zIndex(if (dragState.dragging == id) 1f else 0f)
                            .onGloballyPositioned {
                                dragState.centers[id] = it.positionInRoot().y + it.size.height / 2f
                                dragState.heights[id] = it.size.height
                            },
                    ) {
                        var dismissAttempt by remember { mutableIntStateOf(0) }
                        key(dismissAttempt) {
                            val dismissState = rememberSwipeToDismissBoxState()
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    onDelete(id)
                                    // Replace the completed swipe state; cancelling a library dialog keeps this row usable.
                                    dismissAttempt++
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                enableDismissFromStartToEnd = false,
                                enableDismissFromEndToStart = dragState.dragging == null,
                                backgroundContent = {
                                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.CenterEnd) {
                                        Icon(Icons.Default.Delete, stringResource(R.string.scene_widget_delete), tint = MaterialTheme.colorScheme.error)
                                    }
                                },
                            ) {
                            ListItem(
                                headlineContent = { Text(itemName(item)) },

                                leadingContent = {
                                    val description = stringResource(R.string.scene_widget_reorder, itemName(item))
                                    ReorderHandle(dragState, index, id, description, "scene_item_drag_$id") { currentReorder(it) }
                                },
                                trailingContent = {
                                    StreamSwitchAction("scene_item_switch_divider_$id") {
                                        Switch(checked = itemEnabled(item), onCheckedChange = { onToggle(id, it) },
                                            modifier = Modifier.testTag("scene_item_toggle_$id").semantics { contentDescription = itemName(item) })
                                    }
                                },
                                colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                                modifier = Modifier.fillMaxWidth().testTag("scene_item_row_$id")
                                    .reorderVisuals(dragState, id)
                                    .clickable { onOpen(id) },
                            )
                            }
                        }
                    }
                    if (index < dragState.order.lastIndex) HorizontalDivider(Modifier.padding(horizontal = 16.dp))
                }
            }
        }
    }
}
