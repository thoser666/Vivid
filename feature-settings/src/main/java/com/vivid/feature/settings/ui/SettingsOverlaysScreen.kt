package com.vivid.feature.settings.ui

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.vivid.feature.streaming.source.VideoSourceKind
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.layout.onSizeChanged
import kotlin.math.roundToInt
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.core.data.*
import com.vivid.feature.settings.R
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.StreamingViewModel
import com.vivid.feature.streaming.ui.ZoneEditorOverlay
import com.vivid.feature.widget.PomodoroWidgetViewModel
import com.vivid.feature.widget.PomodoroWidgetContent
import com.vivid.feature.widgets.R as WidgetR
import com.vivid.feature.streaming.R as StreamingR
import java.util.UUID

@Composable
fun SettingsOverlaysScreen(
    onBack: () -> Unit,
    scenesViewModel: StreamingViewModel = hiltViewModel(),
    pomodoroViewModel: PomodoroWidgetViewModel? = null,
) {
    val scenes by scenesViewModel.scenes.collectAsStateWithLifecycle(emptyList())
    val widgets by scenesViewModel.widgets.collectAsStateWithLifecycle(emptyList())
    val activeId by scenesViewModel.activeSceneId.collectAsStateWithLifecycle(null)
    val savedLayout by scenesViewModel.savedWidgetLayout.collectAsStateWithLifecycle(null)
    val initialized by scenesViewModel.libraryInitialized.collectAsStateWithLifecycle(false)
    val streaming by scenesViewModel.streamingEngine.streamingState.collectAsStateWithLifecycle()
    var sceneId by rememberSaveable { mutableStateOf<String?>(null) }
    var widgetId by rememberSaveable { mutableStateOf<String?>(null) }
    var placementId by rememberSaveable { mutableStateOf<String?>(null) }
    var zonesEditing by rememberSaveable { mutableStateOf(false) }
    var deletingWidgetId by rememberSaveable { mutableStateOf<String?>(null) }
    val names = WidgetType.entries.associateWith { stringResource(it.titleRes) }
    val defaultSceneName = stringResource(StreamingR.string.scene_default_name, 1)
    LaunchedEffect(Unit) { scenesViewModel.initializeWidgets(defaultSceneName, names) }
    val back = overlayBackAction(zonesEditing, widgetId, placementId, sceneId,
        { zonesEditing = false }, { widgetId = null }, { placementId = null }, { sceneId = null }, onBack)
    BackHandler(sceneId != null || widgetId != null || zonesEditing) { back() }
    val scene = scenes.find { it.id == sceneId }
    val widget = widgets.find { it.id == widgetId }
    val placement = scene?.widgets?.find { it.widgetId == placementId }
    val placedWidget = widgets.find { it.id == placementId }
    var draftLayout by remember(sceneId, placementId) { mutableStateOf<SceneWidget?>(null) }
    val deletingWidget = widgets.find { it.id == deletingWidgetId }
    DeleteLibraryWidgetDialog(deletingWidget, { deletingWidgetId = null }, scenesViewModel::deleteWidget)
    when {
        zonesEditing -> {
            val zones by scenesViewModel.privacyZones.collectAsStateWithLifecycle(emptyList())
            SettingsSectionScaffold(stringResource(StreamingR.string.privacy_zone_editor_title), back, null) {
                Box(Modifier.fillMaxWidth().height(500.dp)) {
                    ZoneEditorOverlay(zones, scenesViewModel::savePrivacyZones) { zonesEditing = false }
                }
            }
        }
        widget != null -> SharedWidgetEditor(widget, scenes, back, pomodoroViewModel) { updated, visibility ->
            scenesViewModel.saveWidgetChanges(updated, visibility)
            widgetId = null
        }
        placement != null && placedWidget != null -> PlacementEditor(
            widget = placedWidget, placement = draftLayout ?: placement, onEditSettings = { draftLayout = it; widgetId = placedWidget.id }, savedLayout = savedLayout, onSaveLayout = scenesViewModel::saveWidgetLayout, onBack = back,
            onSave = { updated ->
                scenesViewModel.updateScene(scene.withPlacement(updated))
                placementId = null
            },
        )
        scene != null -> SceneEditor(
            scene, widgets, activeId, streaming !is StreamingState.Streaming && streaming !is StreamingState.Preparing,
            scenesViewModel, back, onPlacement = { placementId = it },
        )
        else -> SceneWidgetLibrary(scenes, widgets, names, initialized,
            streaming !is StreamingState.Streaming && streaming !is StreamingState.Preparing,
            scenesViewModel, onBack, { sceneId = it }, { widgetId = it },
            { deletingWidgetId = it }, { zonesEditing = true })
    }
}

private fun overlayBackAction(
    zonesEditing: Boolean, widgetId: String?, placementId: String?, sceneId: String?,
    closeZones: () -> Unit, closeWidget: () -> Unit, closePlacement: () -> Unit,
    closeScene: () -> Unit, onBack: () -> Unit,
): () -> Unit = when {
    zonesEditing -> closeZones
    widgetId != null -> closeWidget
    placementId != null -> closePlacement
    sceneId != null -> closeScene
    else -> onBack
}

private fun StreamingViewModel.saveWidgetChanges(widget: StreamWidget, visibility: Map<String, Boolean>) {
    if (visibility.isEmpty()) updateWidget(widget) else updateWidgetScenes(widget, visibility)
}

private fun StreamScene.withPlacement(updated: SceneWidget) = copy(widgets = widgets.orEmpty().map {
    if (it.widgetId == updated.widgetId) it.copy(x = updated.x, y = updated.y, scale = updated.scale) else it
})

@Composable
private fun DeleteLibraryWidgetDialog(widget: StreamWidget?, onDismiss: () -> Unit, onDelete: (String) -> Unit) {
    if (widget != null) {
        AlertDialog(
            modifier = Modifier.testTag("library_widget_delete_dialog"),
            onDismissRequest = { onDismiss() },
            title = { Text(stringResource(R.string.scene_widget_delete_title)) },
            text = { Text(stringResource(R.string.scene_widget_delete_message, widget.name)) },
            confirmButton = {
                TextButton(modifier = Modifier.testTag("library_widget_delete_confirm"), onClick = {
                    onDelete(widget.id)
                    onDismiss()
                }) { Text(stringResource(R.string.scene_widget_delete)) }
            },
            dismissButton = {
                TextButton(modifier = Modifier.testTag("library_widget_delete_cancel"),
                    onClick = { onDismiss() }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun SceneWidgetLibrary(
    scenes: List<StreamScene>, widgets: List<StreamWidget>, names: Map<WidgetType, String>,
    initialized: Boolean, idle: Boolean, scenesViewModel: StreamingViewModel, onBack: () -> Unit,
    onScene: (String) -> Unit, onWidget: (String) -> Unit, onDeleteWidget: (String) -> Unit,
    onZones: () -> Unit,
) {
    val scroll = rememberScrollState()
    SettingsSectionScaffold(stringResource(R.string.cat_overlays_title), onBack, null, scrollState = scroll) {
        Text(stringResource(StreamingR.string.scene_bar_title), style = MaterialTheme.typography.titleLarge)
        SceneItemsList(scenes, { it.id }, { it.name }, { it.enabled }, scenesViewModel::deleteScene,
            scroll, onOpen = { onScene(it) },
            onToggle = { id, enabled -> scenes.find { it.id == id }?.let { scenesViewModel.updateScene(it.copy(enabled = enabled)) } },
            onReorder = scenesViewModel::reorderScenes)
        val newName = stringResource(StreamingR.string.scene_default_name, scenes.size + 1)
        OutlinedButton(onClick = { scenesViewModel.saveScene(newName) }, enabled = initialized, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scene_widget_add_scene))
        }
        Text(stringResource(R.string.scene_widget_library), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.scene_widget_library_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("widget_library_hint"))
        SceneItemsList(widgets, { it.id }, { it.name }, { it.enabled }, { onDeleteWidget(it) },
            scroll, onOpen = { onWidget(it) },
            onToggle = { id, enabled -> widgets.find { it.id == id }?.let { scenesViewModel.updateWidget(it.copy(enabled = enabled)) } },
            onReorder = scenesViewModel::reorderWidgets)
        var adding by remember { mutableStateOf(false) }
        Box {
            OutlinedButton(onClick = { adding = true }, enabled = initialized, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scene_widget_add))
            }
            DropdownMenu(expanded = adding, onDismissRequest = { adding = false }) {
                WidgetType.entries.forEach { type ->
                    DropdownMenuItem(text = { Text(names.getValue(type)) }, onClick = {
                        val id = UUID.randomUUID().toString()
                        scenesViewModel.updateWidget(StreamWidget(id, names.getValue(type), type))
                        onWidget(id)
                        adding = false
                    })
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        SceneAutomationAndPrivacy(scenesViewModel, idle, onZones)
    }
}

internal val WidgetType.titleRes: Int get() = when (this) {
    WidgetType.TEXT -> R.string.overlays_widget_title
    WidgetType.BROWSER -> R.string.scene_widget_browser
    WidgetType.IMAGE -> R.string.overlays_image_title
    WidgetType.SLIDESHOW -> R.string.overlays_slideshow_title
    WidgetType.QR_CODE -> R.string.overlays_qr_title
    WidgetType.CHAT -> R.string.overlays_chat_title
    WidgetType.GRID -> R.string.overlays_grid_title
    WidgetType.BATTERY -> R.string.overlays_battery_title
    WidgetType.SUBTITLES -> R.string.overlays_subtitles_title
    WidgetType.POMODORO -> R.string.pomodoro_title
}

@Composable
private fun SceneEditor(
    scene: StreamScene, widgets: List<StreamWidget>, activeId: String?, idle: Boolean,
    viewModel: StreamingViewModel, onBack: () -> Unit, onPlacement: (String) -> Unit,
) {
    var previousSource by remember { mutableStateOf(VideoSourceKind.CAMERA) }
    val currentScene by rememberUpdatedState(scene)
    val captureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (viewModel.streamingEngine.onScreenCaptureConsentResult(result.resultCode, result.data)) {
            viewModel.applyScene(currentScene)
        } else viewModel.streamingEngine.switchSource(previousSource)
    }
    fun activate() {
        val engine = viewModel.streamingEngine
        if (scene.videoSource == SceneVideoSource.SCREEN_CAPTURE) {
            previousSource = engine.activeSourceKind.value
            if (engine.switchSource(VideoSourceKind.SCREEN_CAPTURE)) {
                engine.createScreenCaptureConsentIntent()?.let(captureLauncher::launch)
            }
        } else viewModel.applyScene(scene)
    }
    val scroll = rememberScrollState()
    SettingsSectionScaffold(scene.name, onBack, null, scrollState = scroll) {
        OutlinedTextField(scene.name, { viewModel.updateScene(scene.copy(name = it)) },
            label = { Text(stringResource(StreamingR.string.scene_name_hint)) }, modifier = Modifier.fillMaxWidth())
        SourcePicker(scene) { viewModel.updateScene(it) }
        Button(onClick = ::activate, enabled = idle && scene.enabled, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (scene.id == activeId) R.string.scene_widget_active else R.string.scene_widget_activate))
        }
        Text(stringResource(R.string.scene_widget_scene_widgets), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.scene_widget_scene_widgets_hint),
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("scene_widgets_hint"))
        val placements = scene.widgets.orEmpty()
        SceneItemsList(placements, { it.widgetId }, { item -> widgets.find { it.id == item.widgetId }?.name.orEmpty() },
            { it.enabled }, onDelete = { id -> viewModel.updateScene(scene.copy(widgets = placements.filterNot { it.widgetId == id })) },
            scrollState = scroll, onOpen = onPlacement,
            onToggle = { id, enabled -> viewModel.updateScene(scene.copy(widgets = placements.map { if (it.widgetId == id) it.copy(enabled = enabled) else it })) },
            onReorder = { ids -> viewModel.updateScene(scene.copy(widgets = ids.mapNotNull { id -> placements.find { it.widgetId == id } })) })
        var adding by remember { mutableStateOf(false) }
        val available = widgets.filterNot { widget -> placements.any { it.widgetId == widget.id } }
        Box {
            OutlinedButton(onClick = { adding = true }, enabled = available.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.scene_widget_add_existing))
            }
            DropdownMenu(adding, { adding = false }) {
                available.forEach { widget ->
                    DropdownMenuItem(text = { Text(widget.name) }, onClick = {
                        viewModel.updateScene(scene.copy(widgets = placements + SceneWidget(widget.id)))
                        adding = false
                    })
                }
            }
        }
        if (widgets.isEmpty()) Text(stringResource(R.string.scene_widget_create_first))
    }
}

@Composable
private fun SourcePicker(scene: StreamScene, onChange: (StreamScene) -> Unit) {
    val context = LocalContext.current
    val cameras = remember(context) {
        runCatching {
            val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            manager.cameraIdList.map { id -> id to manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) }
        }.getOrDefault(emptyList())
    }
    val options = buildList {
        cameras.forEach { (id, facing) ->
            val label = stringResource(when (facing) {
                CameraCharacteristics.LENS_FACING_FRONT -> R.string.scene_widget_camera_front
                CameraCharacteristics.LENS_FACING_BACK -> R.string.scene_widget_camera_back
                else -> R.string.scene_widget_camera_other
            }, id)
            add(Triple(SceneVideoSource.CAMERA, id, label))
        }
        // The default camera stays selectable before the first camera permission grant.
        if (cameras.isEmpty()) add(Triple(SceneVideoSource.CAMERA, null, stringResource(R.string.scene_widget_camera_default)))
        add(Triple(SceneVideoSource.SCREEN_CAPTURE, null, stringResource(R.string.scene_widget_screen)))
        add(Triple(SceneVideoSource.EMPTY, null, stringResource(R.string.scene_widget_empty)))
        if (scene.videoSource == SceneVideoSource.REPLAY) add(Triple(SceneVideoSource.REPLAY, null, stringResource(R.string.scene_widget_replay)))
    }
    var expanded by remember { mutableStateOf(false) }
    val selected = options.find { it.first == scene.videoSource && it.second == scene.cameraId }
        ?: options.firstOrNull { it.first == scene.videoSource }
    Box {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scene_widget_source, selected?.third.orEmpty()))
        }
        DropdownMenu(expanded, { expanded = false }) {
            options.forEach { (source, id, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = {
                    onChange(scene.copy(videoSource = source, cameraId = id)); expanded = false
                })
            }
        }
    }
}

@Composable
private fun SharedWidgetEditor(widget: StreamWidget, scenes: List<StreamScene>, onBack: () -> Unit, pomodoroViewModel: PomodoroWidgetViewModel?, onSave: (StreamWidget, Map<String, Boolean>) -> Unit) {
    var name by rememberSaveable(widget.id) { mutableStateOf(widget.name) }
    var config by remember(widget.id) { mutableStateOf(widget.configuration) }
    var pageWidth by rememberSaveable(widget.id) { mutableStateOf(widget.configuration.browserWidth.toString()) }
    var pageHeight by rememberSaveable(widget.id) { mutableStateOf(widget.configuration.browserHeight.toString()) }
    var sceneChanges by rememberSaveable(widget.id) { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    val validViewport = pageWidth.toIntOrNull() in 1..4096 && pageHeight.toIntOrNull() in 1..4096
    SettingsSectionScaffold(widget.name, onBack, { onSave(widget.copy(name = name.trim(), configuration = config.copy(browserWidth = pageWidth.toInt(), browserHeight = pageHeight.toInt())), sceneChanges) }, saveEnabled = name.isNotBlank() && (widget.type != WidgetType.BROWSER || (isBrowserWidgetUrl(config.browserUrl) && validViewport))) {
        OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.scene_widget_name)) }, modifier = Modifier.fillMaxWidth())
        if (widget.type == WidgetType.BROWSER) {
            OutlinedTextField(config.browserUrl, { config = config.copy(browserUrl = it) },
                label = { Text(stringResource(R.string.scene_widget_url)) }, singleLine = true, isError = !isBrowserWidgetUrl(config.browserUrl), modifier = Modifier.fillMaxWidth())
            BrowserDimensionField(R.string.scene_widget_page_width, pageWidth) { pageWidth = it }
            BrowserDimensionField(R.string.scene_widget_page_height, pageHeight) { pageHeight = it }
        } else {
            WidgetSettingsEditor(widget.type, config.applyTo(AppSettings())) {
                config = WidgetConfiguration.from(it).copy(browserUrl = config.browserUrl,
                    browserWidth = config.browserWidth, browserHeight = config.browserHeight)
            }
        }
        if (widget.type == WidgetType.POMODORO) {
            PomodoroEditorControls(widget, name, config, sceneChanges, pomodoroViewModel)
        }
        HorizontalDivider(Modifier.padding(top = 8.dp))
        WidgetSceneSelection(widget, scenes, sceneChanges) { sceneChanges = it }
    }
}

@Composable
private fun PomodoroEditorControls(
    widget: StreamWidget, name: String, config: WidgetConfiguration, sceneChanges: Map<String, Boolean>,
    pomodoroViewModel: PomodoroWidgetViewModel?,
) {
    val timerViewModel = pomodoroViewModel ?: hiltViewModel<PomodoroWidgetViewModel>(key = "pomodoro-editor-${widget.id}")
    val timers by timerViewModel.states.collectAsStateWithLifecycle()
    val state = timers[widget.id] ?: PomodoroUiState(enabled = widget.enabled,
        remainingSeconds = config.pomodoroWorkDurationMinutes * 60)
    val editedWidget = widget.copy(name = name.trim(), configuration = config)
    Text(stringResource(R.string.pomodoro_controls_hint), style = MaterialTheme.typography.bodySmall)
    PomodoroWidgetContent(name, state.copy(enabled = widget.enabled && name.isNotBlank()),
        onToggle = {
            if (state.running) timerViewModel.pause(widget.id)
            else timerViewModel.saveAndStart(editedWidget, sceneChanges)
        },
        onSkip = { timerViewModel.skip(widget.id) }, onReset = { timerViewModel.reset(widget.id) },
        startLabelRes = WidgetR.string.pomodoro_save_start)
}

@Composable
private fun WidgetSceneSelection(
    widget: StreamWidget, scenes: List<StreamScene>, sceneChanges: Map<String, Boolean>,
    onChange: (Map<String, Boolean>) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("widget_scene_selection"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.scene_widget_show_in_scenes), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.scene_widget_show_in_scenes_hint), style = MaterialTheme.typography.bodySmall)
            if (scenes.isEmpty()) Text(stringResource(R.string.scene_widget_no_scenes))
            scenes.forEach { scene ->
                val enabled = scene.widgets.orEmpty().any { it.widgetId == widget.id && it.enabled }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(scene.name, Modifier.weight(1f))
                    Switch(
                        checked = sceneChanges[scene.id] ?: enabled,
                        onCheckedChange = { checked ->
                            onChange(if (checked == enabled) sceneChanges - scene.id else sceneChanges + (scene.id to checked))
                        },
                        modifier = Modifier.testTag("widget_scene_toggle_${scene.id}")
                            .semantics { contentDescription = scene.name },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserDimensionField(labelRes: Int, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, label = { Text(stringResource(labelRes)) }, singleLine = true,
        isError = value.toIntOrNull() !in 1..4096,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun IntegerField(labelRes: Int, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var raw by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(raw, { raw = it; it.toIntOrNull()?.takeIf { n -> n in range }?.let(onChange) },
        label = { Text(stringResource(labelRes)) }, singleLine = true,
        isError = raw.toIntOrNull()?.let { it !in range } ?: true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number),
        modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PlacementEditor(widget: StreamWidget, placement: SceneWidget, onEditSettings: (SceneWidget) -> Unit, savedLayout: SceneWidget?, onSaveLayout: (SceneWidget) -> Unit, onBack: () -> Unit, onSave: (SceneWidget) -> Unit) {
    var draft by remember(placement.widgetId) { mutableStateOf(placement) }
    var numeric by rememberSaveable { mutableStateOf(false) }
    SettingsSectionScaffold(widget.name, onBack, { onSave(draft.normalized()) }) {
        Text(stringResource(R.string.scene_widget_layout), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.scene_widget_layout_hint), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSaveLayout(draft) }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scene_widget_save_layout))
                }
                OutlinedButton(onClick = { savedLayout?.let { draft = it.copy(widgetId = draft.widgetId) } },
                    enabled = savedLayout != null, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.scene_widget_load_layout))
                }
            }
            Column {
                for (row in 0..2) Row {
                    for (column in 0..2) {
                        val x = column * 50f
                        val y = row * 50f
                        val anchorDescription = stringResource(R.string.scene_widget_anchor, row + 1, column + 1)
                        RadioButton(selected = draft.x == x && draft.y == y,
                            onClick = { draft = draft.copy(x = x, y = y) },
                            modifier = Modifier.semantics { contentDescription = anchorDescription })
                    }
                }
            }
        }
        // Percentages are relative to the free canvas space, so edge anchors remain visible.
        var labelSize by remember { mutableStateOf(IntSize.Zero) }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("scene_widget_canvas")
            .pointerInput(Unit) { detectDragGestures { change, amount ->
                change.consume()
                draft = draft.copy(x = draft.x + amount.x / (size.width - labelSize.width * draft.scale).coerceAtLeast(1f) * 100f,
                    y = draft.y + amount.y / (size.height - labelSize.height * draft.scale).coerceAtLeast(1f) * 100f).normalized()
            } }) {
            val canvasWidth = constraints.maxWidth
            val canvasHeight = constraints.maxHeight
            Text(widget.name, Modifier.offset {
                IntOffset(((canvasWidth - labelSize.width * draft.scale).coerceAtLeast(0f) * draft.x / 100f).roundToInt(),
                    ((canvasHeight - labelSize.height * draft.scale).coerceAtLeast(0f) * draft.y / 100f).roundToInt())
            }.onSizeChanged { labelSize = it }
                .graphicsLayer { scaleX = draft.scale; scaleY = draft.scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0f) }
                .background(MaterialTheme.colorScheme.primaryContainer).padding(8.dp)
                .testTag("scene_widget_position"))
        }
        OutlinedButton(onClick = { onEditSettings(draft) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.scene_widget_shared_settings))
        }
        Text(stringResource(R.string.scene_widget_shared_hint), style = MaterialTheme.typography.bodySmall)
        Row {
            Text(stringResource(R.string.scene_widget_numeric), Modifier.weight(1f))
            Switch(numeric, { numeric = it })
        }
        for ((label, value, update) in listOf(
            Triple(R.string.scene_widget_x, draft.x, { v: Float -> draft = draft.copy(x = v) }),
            Triple(R.string.scene_widget_y, draft.y, { v: Float -> draft = draft.copy(y = v) }),
            Triple(R.string.scene_widget_scale, draft.scale * 100f, { v: Float -> draft = draft.copy(scale = v / 100f) }),
        )) {
            Text(stringResource(label, value.toInt()))
            val range = if (label == R.string.scene_widget_scale) 10f..400f else 0f..100f
            if (numeric) IntegerField(label = stringResource(label, value.toInt()), value = value.toInt(), range = range.start.toInt()..range.endInclusive.toInt()) { update(it.toFloat()) }
            else Slider(value, update, valueRange = range)
        }
    }
}

@Composable
private fun IntegerField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var raw by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(raw, { raw = it; it.toIntOrNull()?.takeIf { n -> n in range }?.let(onChange) },
        label = { Text(label) }, singleLine = true, isError = raw.toIntOrNull()?.let { it !in range } ?: true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Number))
}

@Composable
private fun SceneAutomationAndPrivacy(viewModel: StreamingViewModel, idle: Boolean, onZones: () -> Unit) {
    val auto by viewModel.autoSwitchEnabled.collectAsStateWithLifecycle()
    val interval by viewModel.autoSwitchIntervalSeconds.collectAsStateWithLifecycle()
    val privacy by viewModel.privacyEnabled.collectAsStateWithLifecycle(false)
    val fullFrame by viewModel.fullFrameBlurEnabled.collectAsStateWithLifecycle(false)
    Text(stringResource(R.string.scene_widget_scene_management), style = MaterialTheme.typography.titleLarge)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(StreamingR.string.scene_auto_switch), Modifier.weight(1f))
        Switch(auto, viewModel::setAutoSwitchEnabled)
    }
    if (auto) IntegerField(StreamingR.string.scene_interval_seconds, interval.toInt(), 5..86400) { viewModel.setAutoSwitchIntervalSeconds(it.toLong()) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(StreamingR.string.privacy_master_toggle_label), Modifier.weight(1f))
        Switch(privacy, viewModel::setPrivacyEnabled, modifier = Modifier.testTag("privacy_toggle"))
    }
    if (privacy) {
        OutlinedButton(onClick = onZones, enabled = idle) { Text(stringResource(StreamingR.string.streaming_privacy_zones)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(StreamingR.string.privacy_full_frame_toggle_label), Modifier.weight(1f))
            Switch(fullFrame, viewModel::setFullFrameBlurEnabled, modifier = Modifier.testTag("full_frame_toggle"))
        }
    }
}
