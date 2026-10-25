package com.vivid.feature.streaming.ui.scene

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.media.projection.MediaProjectionManager
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.vivid.core.data.*
import com.vivid.feature.streaming.R
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.compositor.SceneProducerState
import com.vivid.feature.streaming.compositor.SceneProducerStatus
import com.vivid.feature.streaming.scene.editor.*
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.ui.StreamingSourcePreview
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SceneEditorScreen(
    state: SceneEditorState,
    engine: StreamingEngine,
    sourceKind: VideoSourceKind,
    sourceStates: Map<String, SceneProducerState>,
    catalog: List<SceneSource>,
    privacyEnabled: Boolean,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onProjectionResult: (String, Int, Intent) -> Boolean,
) {
    val context = LocalContext.current
    var picker by rememberSaveable { mutableStateOf<String?>(null) }
    var textDialog by rememberSaveable { mutableStateOf<String?>(null) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf(false) }
    var retryCameraId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCamera by remember { mutableStateOf<SceneSource?>(null) }
    var pendingProjection by rememberSaveable { mutableStateOf<String?>(null) }
    var cameras by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    var output by remember { mutableStateOf(engine.scenePreviewSize()) }
    val imageName = stringResource(R.string.scene_editor_image)
    val videoName = stringResource(R.string.scene_editor_video)
    val clockName = stringResource(R.string.scene_editor_clock)
    val screenName = stringResource(R.string.scene_editor_screen)
    val hint = stringResource(R.string.scene_editor_gesture_hint)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pendingCamera?.let { source -> onEdit { if (!addSource(source)) error = true } }
            retryCameraId?.let { engine.retrySceneSource(it) }
        } else error = true
        pendingCamera = null
        retryCameraId = null
    }
    fun addCamera(candidate: SceneSource) {
        val source = state.composition.sources.firstOrNull {
            it.type == candidate.type && it.locator == candidate.locator
        } ?: candidate
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            onEdit { if (!addSource(source)) error = true }
        } else { pendingCamera = source; permission.launch(Manifest.permission.CAMERA) }
    }
    fun addDocument(uri: android.net.Uri?, type: SceneSourceType, defaultName: String) {
        if (uri == null) return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val existing = state.composition.sources.find { it.type == type && it.locator == uri.toString() }
            val source = existing ?: SceneSource(UUID.randomUUID().toString(), type, uri.toString(),
                uri.lastPathSegment?.substringAfterLast('/')?.take(80).orEmpty().ifBlank { defaultName })
            onEdit { if (!addSource(source)) error = true }
        } catch (_: SecurityException) { error = true }
    }
    val images = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        addDocument(it, SceneSourceType.IMAGE, imageName)
    }
    val videos = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) {
        addDocument(it, SceneSourceType.VIDEO, videoName)
    }
    val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val sourceId = pendingProjection
        if (sourceId != null && result.resultCode == Activity.RESULT_OK && result.data != null) {
            error = !onProjectionResult(sourceId, result.resultCode, result.data!!)
        } else error = true
        pendingProjection = null
    }
    fun authorizeScreen(sourceId: String) {
        pendingProjection = sourceId
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(manager.createScreenCaptureIntent())
    }
    LaunchedEffect(picker) {
        if (picker == "camera") cameras = withContext(Dispatchers.IO) { engine.sceneCameraOptions() }
    }
    BackHandler { if (!state.saving) onCancel() }
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.scene_editor_title)) }, navigationIcon = {
            IconButton(onClick = onCancel, enabled = !state.saving) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.scene_editor_cancel))
            }
        }, actions = {
            IconButton(onClick = { onEdit { undo() } }, enabled = state.canUndo && !state.saving) {
                Icon(Icons.AutoMirrored.Filled.Undo, stringResource(R.string.scene_editor_undo))
            }
            IconButton(onClick = { onEdit { redo() } }, enabled = state.canRedo && !state.saving) {
                Icon(Icons.AutoMirrored.Filled.Redo, stringResource(R.string.scene_editor_redo))
            }
            TextButton(onClick = onSave, enabled = !state.saving) { Text(stringResource(R.string.scene_editor_save)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).testTag("scene_editor")) {
            Box(Modifier.fillMaxWidth().weight(0.42f).background(androidx.compose.ui.graphics.Color.Black), Alignment.Center) {
                BoxWithConstraints(Modifier.heightIn(max = 320.dp).aspectRatio(output.first.toFloat() / output.second)) {
                    val density = androidx.compose.ui.platform.LocalDensity.current
                    val previewSize = with(density) { IntSize(maxWidth.roundToPx(), maxHeight.roundToPx()) }
                    StreamingSourcePreview(engine, sourceKind, previewSize, 0, showComposition = true) { }
                    SceneEditorCanvas(state, hint, MaterialTheme.colorScheme.primary, onEdit, Modifier.fillMaxSize())
                    LaunchedEffect(previewSize, state.composition) { output = engine.scenePreviewSize() }
                }
            }
            LazyColumn(Modifier.fillMaxWidth().weight(0.58f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(state.scene.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { textDialog = "scene"; input = state.scene.name }, enabled = !state.saving) {
                            Icon(Icons.Default.Edit, stringResource(R.string.scene_editor_rename))
                        }
                        FilledTonalButton(onClick = { picker = "types" },
                            enabled = !state.saving && state.composition.layers.size < SceneComposition.MAX_LAYERS) {
                            Text(stringResource(R.string.scene_editor_add_source))
                        }
                    }
                    Text(stringResource(R.string.scene_editor_live_hint), style = MaterialTheme.typography.bodySmall)
                    if (privacyEnabled) Text(stringResource(R.string.scene_editor_privacy_hint), style = MaterialTheme.typography.bodySmall)
                    if (state.saveFailed || error) Text(stringResource(R.string.scene_editor_error), color = MaterialTheme.colorScheme.error)
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.composition.layers.asReversed().forEach { layer ->
                            val source = state.composition.sources.first { it.id == layer.sourceId }
                            FilterChip(selected = layer.id == state.selectedLayerId,
                                onClick = { onEdit { select(layer.id) } }, enabled = !state.saving,
                                label = { Text(source.name.ifBlank { sourceLabel(source.type) }) })
                        }
                    }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SceneLayoutPreset.entries.forEach { preset ->
                            TextButton(onClick = { onEdit { layout(preset) } }, enabled = !state.saving &&
                                (preset == SceneLayoutPreset.FULLSCREEN || state.composition.layers.size >= 2)) {
                                Text(stringResource(when (preset) {
                                    SceneLayoutPreset.FULLSCREEN -> R.string.scene_editor_fullscreen
                                    SceneLayoutPreset.PICTURE_IN_PICTURE -> R.string.scene_editor_pip
                                    SceneLayoutPreset.SIDE_BY_SIDE -> R.string.scene_editor_split
                                }))
                            }
                        }
                    }
                }
                state.selectedLayer?.let { layer ->
                    val source = state.composition.sources.first { it.id == layer.sourceId }
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            EditorIcon(R.string.scene_editor_duplicate, Icons.Default.ContentCopy, !state.saving) { onEdit { duplicateSelected() } }
                            EditorIcon(R.string.scene_editor_raise, Icons.Default.ArrowUpward, !state.saving) { onEdit { moveSelected(1) } }
                            EditorIcon(R.string.scene_editor_lower, Icons.Default.ArrowDownward, !state.saving) { onEdit { moveSelected(-1) } }
                            EditorIcon(R.string.scene_editor_remove, Icons.Default.Delete, !state.saving) { onEdit { deleteSelected() } }
                            EditorIcon(R.string.scene_editor_rename, Icons.Default.Edit, !state.saving) { textDialog = "source"; input = source.name }
                            Text(stringResource(R.string.scene_editor_visible))
                            Switch(layer.visible, { onEdit { setVisible(it) } }, enabled = !state.saving)
                        }
                        sourceStates[source.id]?.let { status ->
                            Text(stringResource(when (status.status) {
                                SceneProducerStatus.ACTIVE -> R.string.scene_editor_active
                                SceneProducerStatus.OFFLINE -> R.string.scene_editor_offline
                                SceneProducerStatus.PERMISSION_REQUIRED -> R.string.scene_editor_permission
                                SceneProducerStatus.SUSPENDED -> R.string.scene_editor_suspended
                            }))
                            if (status.status != SceneProducerStatus.ACTIVE) TextButton(onClick = {
                                when (source.type) {
                                    SceneSourceType.SCREEN_CAPTURE -> authorizeScreen(source.id)
                                    SceneSourceType.CAMERA, SceneSourceType.UVC -> {
                                        pendingCamera = null
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
                                            engine.retrySceneSource(source.id) else { retryCameraId = source.id; permission.launch(Manifest.permission.CAMERA) }
                                    }
                                    else -> engine.retrySceneSource(source.id)
                                }
                            }) { Text(stringResource(R.string.scene_editor_retry)) }
                        }
                        if (source.type == SceneSourceType.SCREEN_CAPTURE) TextButton(onClick = { authorizeScreen(source.id) }) {
                            Text(stringResource(R.string.scene_editor_authorize_screen))
                        }
                        if (source.type == SceneSourceType.WIDGET && source.locator.startsWith("text:")) {
                            TextButton(onClick = { textDialog = "text-edit"; input = source.locator.removePrefix("text:") }) {
                                Text(stringResource(R.string.scene_editor_text))
                            }
                        }
                    }
                    item { EditorSlider(R.string.scene_editor_x, layer.transform.x, -1f..1f, onEdit) {
                        setTransform(state.selectedLayer!!.transform.copy(x = it)) } }
                    item { EditorSlider(R.string.scene_editor_y, layer.transform.y, -1f..1f, onEdit) {
                        setTransform(state.selectedLayer!!.transform.copy(y = it)) } }
                    item { EditorSlider(R.string.scene_editor_width, layer.transform.width, 0.02f..2f, onEdit) {
                        setTransform(state.selectedLayer!!.transform.copy(width = it)) } }
                    item { EditorSlider(R.string.scene_editor_height, layer.transform.height, 0.02f..2f, onEdit) {
                        setTransform(state.selectedLayer!!.transform.copy(height = it)) } }
                    item { EditorSlider(R.string.scene_editor_rotation, layer.transform.rotationDegrees, -180f..180f, onEdit, degrees = true) {
                        setTransform(state.selectedLayer!!.transform.copy(rotationDegrees = it)) } }
                    item { EditorSlider(R.string.scene_editor_opacity, layer.opacity, 0f..1f, onEdit) { setOpacity(it) } }
                    val crop = layer.transform.crop
                    item { EditorSlider(R.string.scene_editor_crop_left, crop.left, 0f..java.lang.Math.nextDown(crop.right).coerceAtLeast(crop.left), onEdit) { setCrop(crop.copy(left = it)) } }
                    item { EditorSlider(R.string.scene_editor_crop_top, crop.top, 0f..java.lang.Math.nextDown(crop.bottom).coerceAtLeast(crop.top), onEdit) { setCrop(crop.copy(top = it)) } }
                    item { EditorSlider(R.string.scene_editor_crop_right, crop.right, java.lang.Math.nextUp(crop.left).coerceAtMost(crop.right)..1f, onEdit) { setCrop(crop.copy(right = it)) } }
                    item { EditorSlider(R.string.scene_editor_crop_bottom, crop.bottom, java.lang.Math.nextUp(crop.top).coerceAtMost(crop.bottom)..1f, onEdit) { setCrop(crop.copy(bottom = it)) } }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.scene_editor_fade), Modifier.weight(1f))
                        Switch(state.composition.transition.type == SceneTransitionType.FADE,
                            { onEdit { setTransition(if (it) SceneTransitionType.FADE else SceneTransitionType.CUT) } }, enabled = !state.saving)
                    }
                    if (state.composition.transition.type == SceneTransitionType.FADE) EditorSlider(
                        R.string.scene_editor_duration, state.composition.transition.durationMillis.toFloat(), 0f..2000f, onEdit, millis = true,
                    ) { setTransition(SceneTransitionType.FADE, it.toLong()) }
                }
            }
        }
    }
    if (picker != null) AlertDialog(onDismissRequest = { picker = null }, title = { Text(stringResource(R.string.scene_editor_add_source)) },
        text = {
            Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                when (picker) {
                    "types" -> {
                        TextButton(onClick = { picker = "camera" }) { Text(stringResource(R.string.scene_editor_camera)) }
                        TextButton(onClick = { picker = null; images.launch(arrayOf("image/*")) }) { Text(imageName) }
                        TextButton(onClick = { picker = null; videos.launch(arrayOf("video/*")) }) { Text(videoName) }
                        TextButton(onClick = { picker = null; textDialog = "text"; input = "" }) { Text(stringResource(R.string.scene_editor_text)) }
                        TextButton(onClick = { picker = null; onEdit { if (!addSource(SceneSource(UUID.randomUUID().toString(), SceneSourceType.WIDGET, "clock", clockName))) error = true } }) { Text(clockName) }
                        TextButton(onClick = {
                            picker = null
                            val id = UUID.randomUUID().toString()
                            onEdit {
                                if (addSource(SceneSource(id, SceneSourceType.SCREEN_CAPTURE, name = screenName))) authorizeScreen(id)
                                else error = true
                            }
                        }) { Text(screenName) }
                        TextButton(onClick = { picker = "reuse" }) { Text(stringResource(R.string.scene_editor_reuse)) }
                    }
                    "camera" -> {
                        if (cameras.isEmpty()) Text(stringResource(R.string.scene_editor_no_sources))
                        cameras.forEach { (id, facing) ->
                            val label = stringResource(when (facing) {
                                CameraCharacteristics.LENS_FACING_FRONT -> R.string.scene_editor_camera_front
                                CameraCharacteristics.LENS_FACING_EXTERNAL -> R.string.scene_editor_camera_external
                                else -> R.string.scene_editor_camera_back
                            }, id)
                            TextButton(onClick = {
                                picker = null
                                addCamera(SceneSource("camera:$id", if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL)
                                    SceneSourceType.UVC else SceneSourceType.CAMERA, id, label))
                            }) { Text(label) }
                        }
                    }
                    "reuse" -> {
                        if (catalog.isEmpty()) Text(stringResource(R.string.scene_editor_no_sources))
                        catalog.distinctBy { it.id }.forEach { source ->
                            TextButton(onClick = { picker = null; onEdit { if (!addSource(source)) error = true } }) {
                                Text(source.name.ifBlank { sourceLabel(source.type) })
                            }
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.scene_editor_cancel)) } })
    if (textDialog != null) AlertDialog(onDismissRequest = { textDialog = null },
        title = { Text(stringResource(R.string.scene_editor_text)) }, text = {
            OutlinedTextField(input, { input = it.take(2048) }, modifier = Modifier.testTag("scene_editor_text_input"))
        }, confirmButton = {
            TextButton(enabled = input.isNotBlank(), onClick = {
                val text = input
                when (textDialog) {
                    "scene" -> onEdit { rename(text) }
                    "source" -> state.selectedLayer?.let { layer ->
                        onEdit { editSource(state.composition.sources.first { it.id == layer.sourceId }.copy(name = text)) }
                    }
                    "text-edit" -> state.selectedLayer?.let { layer ->
                        onEdit { editSource(state.composition.sources.first { it.id == layer.sourceId }.copy(locator = "text:$text")) }
                    }
                    else -> onEdit { if (!addSource(SceneSource(UUID.randomUUID().toString(), SceneSourceType.WIDGET, "text:$text", text.take(32)))) error = true }
                }
                textDialog = null
            }) { Text(stringResource(R.string.scene_editor_save)) }
        }, dismissButton = { TextButton(onClick = { textDialog = null }) { Text(stringResource(R.string.scene_editor_cancel)) } })
}

@Composable
private fun EditorIcon(label: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, click: () -> Unit) {
    IconButton(onClick = click, enabled = enabled) { Icon(icon, stringResource(label)) }
}

@Composable
private fun EditorSlider(label: Int, value: Float, range: ClosedFloatingPointRange<Float>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit, degrees: Boolean = false, millis: Boolean = false,
    change: SceneEditorSession.(Float) -> Unit) {
    val name = stringResource(label)
    val display = stringResource(when {
        degrees -> R.string.scene_editor_degrees
        millis -> R.string.scene_editor_millis
        else -> R.string.scene_editor_percent
    }, (if (degrees || millis) value else value * 100).roundToInt())
    Text(stringResource(R.string.scene_editor_property_value, name, display))
    Slider(value.coerceIn(range.start, range.endInclusive), {
        onEdit { beginGesture(); change(it) }
    }, valueRange = range, onValueChangeFinished = { onEdit { endGesture() } },
        modifier = Modifier.semantics { contentDescription = name })
}

@Composable
private fun sourceLabel(type: SceneSourceType) = stringResource(when (type) {
    SceneSourceType.CAMERA, SceneSourceType.UVC -> R.string.scene_editor_camera
    SceneSourceType.IMAGE -> R.string.scene_editor_image
    SceneSourceType.VIDEO -> R.string.scene_editor_video
    SceneSourceType.SCREEN_CAPTURE -> R.string.scene_editor_screen
    SceneSourceType.WIDGET -> R.string.scene_editor_text
})
