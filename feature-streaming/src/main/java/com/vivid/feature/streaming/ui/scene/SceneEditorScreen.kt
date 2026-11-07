package com.vivid.feature.streaming.ui.scene

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.media.projection.MediaProjectionManager
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
import com.vivid.feature.streaming.compositor.capture.SceneUsbDevices
import com.vivid.feature.streaming.compositor.capture.SceneUsbLocator
import com.vivid.feature.streaming.compositor.capture.SceneUsbPermissionActivity
import com.vivid.feature.streaming.compositor.capture.SceneUsbSources
import com.vivid.feature.streaming.compositor.matchesSceneIdentity
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
    val dialogs = rememberSceneEditorDialogs()
    with(dialogs) {
        val actions = rememberSceneEditorActions(state, engine, catalog, onEdit, onProjectionResult)
        SceneEditorBody(state, engine, sourceKind, sourceStates, privacyEnabled, onEdit, onSave, onCancel, actions)
        SceneEditorPicker(catalog, onEdit, actions)
        SceneEditorTextDialog(state, onEdit)
    }
}

private class SceneEditorDialogs(
    picker: MutableState<String?>,
    textDialog: MutableState<String?>,
    input: MutableState<String>,
    error: MutableState<Boolean>,
) {
    var picker by picker
    var textDialog by textDialog
    var input by input
    var error by error
}

@Composable
private fun rememberSceneEditorDialogs() =
    SceneEditorDialogs(
        rememberSaveable { mutableStateOf<String?>(null) },
        rememberSaveable { mutableStateOf<String?>(null) },
        rememberSaveable { mutableStateOf("") },
        rememberSaveable { mutableStateOf(false) },
    )

private data class SceneEditorActions(
    val cameras: List<Pair<String, Int>>,
    val usbNames: List<Pair<String, String>>,
    val addCamera: (SceneSource) -> Unit,
    val authorizeUsb: (String, String?) -> Unit,
    val authorizeScreen: (String) -> Unit,
    val pickImage: () -> Unit,
    val pickVideo: () -> Unit,
    val retry: (SceneSource) -> Unit,
)

@Composable
private fun SceneEditorDialogs.rememberSceneEditorActions(
    state: SceneEditorState,
    engine: StreamingEngine,
    catalog: List<SceneSource>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    onProjectionResult: (String, Int, Intent) -> Boolean,
): SceneEditorActions {
    val camera = rememberEditorCameraActions(state, engine, onEdit)
    val usb = rememberEditorUsbActions(state, engine, catalog, onEdit)
    val authorizeScreen = rememberEditorProjection(onProjectionResult)
    val images = rememberEditorDocumentPicker(state, engine, onEdit, SceneSourceType.IMAGE, R.string.scene_editor_image)
    val videos = rememberEditorDocumentPicker(state, engine, onEdit, SceneSourceType.VIDEO, R.string.scene_editor_video)
    var cameras by remember { mutableStateOf<List<Pair<String, Int>>>(emptyList()) }
    LaunchedEffect(picker) {
        if (picker == SCENE_CAMERA) cameras = withContext(Dispatchers.IO) { engine.sceneCameraOptions() }
    }
    return SceneEditorActions(
        cameras,
        usb.names,
        camera.add,
        usb.authorize,
        authorizeScreen,
        images,
        videos,
        { retryEditorSource(it, engine, camera, usb, authorizeScreen) },
    )
}

private data class EditorCameraActions(
    val add: (SceneSource) -> Unit,
    val retry: (String) -> Unit,
)

@Composable
private fun SceneEditorDialogs.rememberEditorCameraActions(
    state: SceneEditorState,
    engine: StreamingEngine,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
): EditorCameraActions {
    val context = LocalContext.current
    var retryCameraId by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingCamera by rememberSaveable(stateSaver = sceneCameraSourceSaver) { mutableStateOf<SceneSource?>(null) }

    fun cameraSource(candidate: SceneSource) =
        reusableSceneCameraSource(
            state.composition.sources,
            candidate,
            engine::sceneCameraIdentity,
        )

    fun addCameraView(source: SceneSource) {
        onEdit {
            if (!addSource(source)) error = true else engine.retrySceneSource(source.id)
        }
    }
    val permission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                pendingCamera?.let { addCameraView(cameraSource(it)) }
                retryCameraId?.let { engine.retrySceneSource(it) }
            } else {
                error = true
            }
            pendingCamera = null
            retryCameraId = null
        }

    fun addCamera(candidate: SceneSource) {
        val source = cameraSource(candidate)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            addCameraView(source)
        } else {
            pendingCamera = candidate
            permission.launch(Manifest.permission.CAMERA)
        }
    }
    return EditorCameraActions(::addCamera, { sourceId ->
        pendingCamera = null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            engine.retrySceneSource(sourceId)
        } else {
            retryCameraId = sourceId
            permission.launch(Manifest.permission.CAMERA)
        }
    })
}

private data class EditorUsbActions(
    val names: List<Pair<String, String>>,
    val sources: SceneUsbSources,
    val authorize: (String, String?) -> Unit,
)

@Composable
private fun SceneEditorDialogs.rememberEditorUsbActions(
    state: SceneEditorState,
    engine: StreamingEngine,
    catalog: List<SceneSource>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
): EditorUsbActions {
    val context = LocalContext.current
    val usbDevices = remember(context) { SceneUsbDevices(context) }
    val usbSources = remember(usbDevices) { SceneUsbSources(usbDevices) }
    var pendingUsbName by rememberSaveable { mutableStateOf<String?>(null) }
    var retryUsbId by rememberSaveable { mutableStateOf<String?>(null) }
    var usbNames by remember { mutableStateOf<List<Pair<String, String>>>(emptyList()) }
    val usbPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val name = pendingUsbName
            val retryId = retryUsbId
            pendingUsbName = null
            retryUsbId = null
            completeUsbResult(EditorUsbResult(result, name, retryId), usbDevices, usbSources, state, engine, catalog, onEdit)
        }

    fun authorizeUsb(
        name: String,
        retryId: String?,
    ) {
        if (pendingUsbName != null) return
        pendingUsbName = name
        retryUsbId = retryId
        usbPermission.launch(
            Intent(context, SceneUsbPermissionActivity::class.java)
                .putExtra(SceneUsbPermissionActivity.EXTRA_DEVICE_NAME, name),
        )
    }
    LaunchedEffect(picker) {
        if (picker == "usb") {
            usbNames =
                withContext(Dispatchers.IO) {
                    usbDevices.videoDevices().map {
                        it.deviceName to
                            it.productName
                                ?.take(80)
                                .orEmpty()
                                .ifBlank { it.deviceName }
                    }
                }
        }
    }
    return EditorUsbActions(usbNames, usbSources, ::authorizeUsb)
}

private data class EditorUsbResult(
    val result: androidx.activity.result.ActivityResult,
    val deviceName: String?,
    val retrySourceId: String?,
)

private fun SceneEditorDialogs.completeUsbResult(
    completion: EditorUsbResult,
    usbDevices: SceneUsbDevices,
    usbSources: SceneUsbSources,
    state: SceneEditorState,
    engine: StreamingEngine,
    catalog: List<SceneSource>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    val (result, name, retryId) = completion
    val device = name?.let(usbDevices::deviceNamed)
    if (name == null || result.resultCode != Activity.RESULT_OK ||
        result.data?.getStringExtra(SceneUsbPermissionActivity.EXTRA_DEVICE_NAME) != name ||
        device == null || !usbDevices.hasPermission(device)
    ) {
        error = true
    } else if (retryId != null) {
        val source = state.composition.sources.firstOrNull { it.id == retryId }
        if (source != null && usbSources.matchesGrantedDevice(source, name)) {
            engine.retrySceneSource(retryId)
        } else {
            error = true
        }
    } else {
        val source = usbSources.sourceForGrantedDevice(name, state.composition.sources + catalog)
        if (source == null) error = true else onEdit { if (!addSource(source)) error = true }
    }
}

@Composable
private fun SceneEditorDialogs.rememberEditorProjection(onProjectionResult: (String, Int, Intent) -> Boolean): (String) -> Unit {
    val context = LocalContext.current
    var pendingProjection by rememberSaveable { mutableStateOf<String?>(null) }
    val projection =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val sourceId = pendingProjection
            if (sourceId != null && result.resultCode == Activity.RESULT_OK && result.data != null) {
                error = !onProjectionResult(sourceId, result.resultCode, result.data!!)
            } else {
                error = true
            }
            pendingProjection = null
        }

    fun authorizeScreen(sourceId: String) {
        pendingProjection = sourceId
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projection.launch(manager.createScreenCaptureIntent())
    }
    return ::authorizeScreen
}

@Composable
private fun SceneEditorDialogs.rememberEditorDocumentPicker(
    state: SceneEditorState,
    engine: StreamingEngine,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    type: SceneSourceType,
    label: Int,
): () -> Unit {
    val context = LocalContext.current
    val name = stringResource(label)
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let { addDocument(context, it, type, name, state, engine, onEdit) }
        }
    return { launcher.launch(arrayOf(if (type == SceneSourceType.IMAGE) "image/*" else "video/*")) }
}

private fun SceneEditorDialogs.addDocument(
    context: Context,
    uri: android.net.Uri,
    type: SceneSourceType,
    defaultName: String,
    state: SceneEditorState,
    engine: StreamingEngine,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    try {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val existing =
            state.composition.sources.find {
                it.matchesSceneIdentity(
                    type,
                    uri.toString(),
                    engine::sceneCameraIdentity,
                    videoLoop = (type == SceneSourceType.VIDEO).takeIf { it },
                )
            }
        val source =
            existing ?: SceneSource(
                UUID.randomUUID().toString(),
                type,
                uri.toString(),
                uri.lastPathSegment
                    ?.substringAfterLast('/')
                    ?.take(80)
                    .orEmpty()
                    .ifBlank { defaultName },
            )
        onEdit { if (!addSource(source)) error = true }
    } catch (_: SecurityException) {
        error = true
    }
}

private fun SceneEditorDialogs.retryEditorSource(
    source: SceneSource,
    engine: StreamingEngine,
    camera: EditorCameraActions,
    usb: EditorUsbActions,
    authorizeScreen: (String) -> Unit,
) {
    when (source.type) {
        SceneSourceType.SCREEN_CAPTURE -> authorizeScreen(source.id)
        SceneSourceType.CAMERA, SceneSourceType.UVC -> {
            val locator = source.locator.takeIf { source.type == SceneSourceType.UVC }?.let(SceneUsbLocator::parse)
            if (locator == null) {
                camera.retry(source.id)
            } else {
                val name = usb.sources.permissionDevice(source)
                if (name != null) usb.authorize(name, source.id) else error = true
            }
        }
        else -> engine.retrySceneSource(source.id)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneEditorDialogs.SceneEditorBody(
    state: SceneEditorState,
    engine: StreamingEngine,
    sourceKind: VideoSourceKind,
    sourceStates: Map<String, SceneProducerState>,
    privacyEnabled: Boolean,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    actions: SceneEditorActions,
) {
    var output by remember { mutableStateOf(engine.scenePreviewSize()) }
    val hint = stringResource(R.string.scene_editor_gesture_hint)
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
                    StreamingSourcePreview(engine, sourceKind, previewSize, 0)
                    SceneEditorCanvas(state, hint, MaterialTheme.colorScheme.primary, onEdit, Modifier.fillMaxSize())
                    LaunchedEffect(previewSize, state.composition) { output = engine.scenePreviewSize() }
                }
            }
            LazyColumn(
                Modifier.fillMaxWidth().weight(0.58f).padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp),
            ) {
                editorHeading(state, privacyEnabled, this@SceneEditorBody)
                editorLayerChips(state, onEdit)
                editorLayouts(state, onEdit)
                editorSelectedLayer(state, sourceStates, onEdit, actions, this@SceneEditorBody)
                editorTransition(state, onEdit)
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.editorHeading(
    state: SceneEditorState,
    privacyEnabled: Boolean,
    dialogs: SceneEditorDialogs,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.scene.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            IconButton(onClick = {
                dialogs.textDialog = "scene"
                dialogs.input = state.scene.name
            }, enabled = !state.saving) {
                Icon(Icons.Default.Edit, stringResource(R.string.scene_editor_rename))
            }
            FilledTonalButton(
                onClick = { dialogs.picker = "types" },
                enabled = !state.saving && state.composition.layers.size < SceneComposition.MAX_LAYERS,
            ) {
                Text(stringResource(R.string.scene_editor_add_source))
            }
        }
        Text(stringResource(R.string.scene_editor_live_hint), style = MaterialTheme.typography.bodySmall)
        if (privacyEnabled) Text(stringResource(R.string.scene_editor_privacy_hint), style = MaterialTheme.typography.bodySmall)
        if (state.saveFailed ||
            dialogs.error
        ) {
            Text(stringResource(R.string.scene_editor_error), color = MaterialTheme.colorScheme.error)
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.editorLayerChips(
    state: SceneEditorState,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.composition.layers.asReversed().forEach { layer ->
                val source = state.composition.sources.first { it.id == layer.sourceId }
                FilterChip(
                    selected = layer.id == state.selectedLayerId,
                    onClick = { onEdit { select(layer.id) } },
                    enabled = !state.saving,
                    label = { Text(source.name.ifBlank { sourceLabel(source.type) }) },
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.editorLayouts(
    state: SceneEditorState,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SceneLayoutPreset.entries.forEach { preset ->
                TextButton(
                    onClick = { onEdit { layout(preset) } },
                    enabled =
                        !state.saving &&
                            (preset == SceneLayoutPreset.FULLSCREEN || state.composition.layers.size >= 2),
                ) {
                    Text(
                        stringResource(
                            when (preset) {
                                SceneLayoutPreset.FULLSCREEN -> R.string.scene_editor_fullscreen
                                SceneLayoutPreset.PICTURE_IN_PICTURE -> R.string.scene_editor_pip
                                SceneLayoutPreset.SIDE_BY_SIDE -> R.string.scene_editor_split
                            },
                        ),
                    )
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.editorSelectedLayer(
    state: SceneEditorState,
    sourceStates: Map<String, SceneProducerState>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    actions: SceneEditorActions,
    dialogs: SceneEditorDialogs,
) {
    state.selectedLayer?.let { layer ->
        val source = state.composition.sources.first { it.id == layer.sourceId }
        item { SelectedLayerControls(state, source, layer, sourceStates[source.id], onEdit, actions, dialogs) }
        item {
            EditorSlider(R.string.scene_editor_x, layer.transform.x, -1f..1f, onEdit) {
                setTransform(state.selectedLayer!!.transform.copy(x = it))
            }
        }
        item {
            EditorSlider(R.string.scene_editor_y, layer.transform.y, -1f..1f, onEdit) {
                setTransform(state.selectedLayer!!.transform.copy(y = it))
            }
        }
        item {
            EditorSlider(R.string.scene_editor_width, layer.transform.width, 0.02f..2f, onEdit) {
                setTransform(state.selectedLayer!!.transform.copy(width = it))
            }
        }
        item {
            EditorSlider(R.string.scene_editor_height, layer.transform.height, 0.02f..2f, onEdit) {
                setTransform(state.selectedLayer!!.transform.copy(height = it))
            }
        }
        item {
            EditorSlider(R.string.scene_editor_rotation, layer.transform.rotationDegrees, -180f..180f, onEdit, degrees = true) {
                setTransform(state.selectedLayer!!.transform.copy(rotationDegrees = it))
            }
        }
        item { EditorSlider(R.string.scene_editor_opacity, layer.opacity, 0f..1f, onEdit) { setOpacity(it) } }
        val crop = layer.transform.crop
        item {
            EditorSlider(
                R.string.scene_editor_crop_left,
                crop.left,
                0f..java.lang.Math
                    .nextDown(crop.right)
                    .coerceAtLeast(crop.left),
                onEdit,
            ) { setCrop(crop.copy(left = it)) }
        }
        item {
            EditorSlider(
                R.string.scene_editor_crop_top,
                crop.top,
                0f..java.lang.Math
                    .nextDown(crop.bottom)
                    .coerceAtLeast(crop.top),
                onEdit,
            ) { setCrop(crop.copy(top = it)) }
        }
        item {
            EditorSlider(
                R.string.scene_editor_crop_right,
                crop.right,
                java.lang.Math
                    .nextUp(crop.left)
                    .coerceAtMost(crop.right)..1f,
                onEdit,
            ) { setCrop(crop.copy(right = it)) }
        }
        item {
            EditorSlider(
                R.string.scene_editor_crop_bottom,
                crop.bottom,
                java.lang.Math
                    .nextUp(crop.top)
                    .coerceAtMost(crop.bottom)..1f,
                onEdit,
            ) { setCrop(crop.copy(bottom = it)) }
        }
    }
}

@Composable
private fun SelectedLayerControls(
    state: SceneEditorState,
    source: SceneSource,
    layer: SceneLayer,
    status: SceneProducerState?,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    actions: SceneEditorActions,
    dialogs: SceneEditorDialogs,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.horizontalScroll(rememberScrollState())) {
        EditorIcon(
            R.string.scene_editor_duplicate,
            Icons.Default.ContentCopy,
            !state.saving,
        ) { onEdit { duplicateSelected() } }
        EditorIcon(R.string.scene_editor_raise, Icons.Default.ArrowUpward, !state.saving) { onEdit { moveSelected(1) } }
        EditorIcon(
            R.string.scene_editor_lower,
            Icons.Default.ArrowDownward,
            !state.saving,
        ) { onEdit { moveSelected(-1) } }
        EditorIcon(R.string.scene_editor_remove, Icons.Default.Delete, !state.saving) { onEdit { deleteSelected() } }
        EditorIcon(R.string.scene_editor_rename, Icons.Default.Edit, !state.saving) {
            dialogs.textDialog = "source"
            dialogs.input =
                source.name
        }
        Text(stringResource(R.string.scene_editor_visible))
        Switch(layer.visible, { onEdit { setVisible(it) } }, enabled = !state.saving)
    }
    status?.let { currentStatus ->
        Text(
            stringResource(
                when (currentStatus.status) {
                    SceneProducerStatus.ACTIVE -> R.string.scene_editor_active
                    SceneProducerStatus.OFFLINE -> R.string.scene_editor_offline
                    SceneProducerStatus.PERMISSION_REQUIRED -> R.string.scene_editor_permission
                    SceneProducerStatus.SUSPENDED -> R.string.scene_editor_suspended
                },
            ),
        )
        if (currentStatus.status != SceneProducerStatus.ACTIVE) {
            TextButton(onClick = {
                actions.retry(source)
            }) { Text(stringResource(R.string.scene_editor_retry)) }
        }
    }
    if (source.type == SceneSourceType.SCREEN_CAPTURE) {
        TextButton(onClick = { actions.authorizeScreen(source.id) }) {
            Text(stringResource(R.string.scene_editor_authorize_screen))
        }
    }
    if (source.type == SceneSourceType.WIDGET && source.locator.startsWith("text:")) {
        TextButton(onClick = {
            dialogs.textDialog = "text-edit"
            dialogs.input = source.locator.removePrefix("text:")
        }) {
            Text(stringResource(R.string.scene_editor_text))
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.editorTransition(
    state: SceneEditorState,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    item {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.scene_editor_fade), Modifier.weight(1f))
            Switch(
                state.composition.transition.type == SceneTransitionType.FADE,
                { onEdit { setTransition(if (it) SceneTransitionType.FADE else SceneTransitionType.CUT) } },
                enabled = !state.saving,
            )
        }
        if (state.composition.transition.type == SceneTransitionType.FADE) {
            EditorSlider(
                R.string.scene_editor_duration,
                state.composition.transition.durationMillis
                    .toFloat(),
                0f..2000f,
                onEdit,
                millis = true,
            ) { setTransition(SceneTransitionType.FADE, it.toLong()) }
        }
    }
}

@Composable
private fun SceneEditorDialogs.SceneEditorPicker(
    catalog: List<SceneSource>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    actions: SceneEditorActions,
) {
    if (picker !=
        null
    ) {
        AlertDialog(
            onDismissRequest = { picker = null },
            title = { Text(stringResource(R.string.scene_editor_add_source)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    when (picker) {
                        "types" -> SceneEditorSourceTypes(onEdit, actions)
                        SCENE_CAMERA -> SceneEditorCameraChoices(actions)
                        "usb" -> {
                            if (actions.usbNames.isEmpty()) Text(stringResource(R.string.scene_editor_no_sources))
                            actions.usbNames.forEach { (name, label) ->
                                TextButton(onClick = {
                                    picker = null
                                    actions.authorizeUsb(name, null)
                                }) { Text(stringResource(R.string.scene_editor_usb_device, label, name)) }
                            }
                        }
                        "reuse" -> {
                            if (catalog.isEmpty()) Text(stringResource(R.string.scene_editor_no_sources))
                            catalog.distinctBy { it.id }.forEach { source ->
                                TextButton(onClick = {
                                    picker = null
                                    onEdit { if (!addSource(source)) error = true }
                                }) {
                                    Text(source.name.ifBlank { sourceLabel(source.type) })
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { picker = null }) { Text(stringResource(R.string.scene_editor_cancel)) } },
        )
    }
}

@Composable
private fun SceneEditorDialogs.SceneEditorSourceTypes(
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    actions: SceneEditorActions,
) {
    val imageName = stringResource(R.string.scene_editor_image)
    val videoName = stringResource(R.string.scene_editor_video)
    val clockName = stringResource(R.string.scene_editor_clock)
    val screenName = stringResource(R.string.scene_editor_screen)
    TextButton(onClick = { picker = SCENE_CAMERA }) { Text(stringResource(R.string.scene_editor_camera)) }
    TextButton(onClick = { picker = "usb" }) { Text(stringResource(R.string.scene_editor_usb)) }
    TextButton(onClick = {
        picker = null
        actions.pickImage()
    }) { Text(imageName) }
    TextButton(onClick = {
        picker = null
        actions.pickVideo()
    }) { Text(videoName) }
    TextButton(onClick = {
        picker = null
        textDialog = "text"
        input = ""
    }) { Text(stringResource(R.string.scene_editor_text)) }
    TextButton(onClick = {
        picker = null
        onEdit {
            if (!addSource(SceneSource(UUID.randomUUID().toString(), SceneSourceType.WIDGET, "clock", clockName))) {
                error =
                    true
            }
        }
    }) { Text(clockName) }
    TextButton(onClick = {
        picker = null
        val id = UUID.randomUUID().toString()
        onEdit {
            if (addSource(SceneSource(id, SceneSourceType.SCREEN_CAPTURE, name = screenName))) {
                actions.authorizeScreen(id)
            } else {
                error = true
            }
        }
    }) { Text(screenName) }
    TextButton(onClick = { picker = "reuse" }) { Text(stringResource(R.string.scene_editor_reuse)) }
}

@Composable
private fun SceneEditorDialogs.SceneEditorCameraChoices(actions: SceneEditorActions) {
    if (actions.cameras.isEmpty()) Text(stringResource(R.string.scene_editor_no_sources))
    actions.cameras.forEach { (id, facing) ->
        val label =
            stringResource(
                when (facing) {
                    CameraCharacteristics.LENS_FACING_FRONT -> R.string.scene_editor_camera_front
                    CameraCharacteristics.LENS_FACING_EXTERNAL -> R.string.scene_editor_camera_external
                    else -> R.string.scene_editor_camera_back
                },
                id,
            )
        TextButton(onClick = {
            picker = null
            actions.addCamera(
                SceneSource(
                    "camera:$id",
                    if (facing == CameraCharacteristics.LENS_FACING_EXTERNAL) {
                        SceneSourceType.UVC
                    } else {
                        SceneSourceType.CAMERA
                    },
                    id,
                    label,
                ),
            )
        }) { Text(label) }
    }
}

@Composable
private fun SceneEditorDialogs.SceneEditorTextDialog(
    state: SceneEditorState,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
) {
    if (textDialog != null) {
        AlertDialog(
            onDismissRequest = { textDialog = null },
            title = { Text(stringResource(R.string.scene_editor_text)) },
            text = {
                OutlinedTextField(input, { input = it.take(2048) }, modifier = Modifier.testTag("scene_editor_text_input"))
            },
            confirmButton = {
                TextButton(enabled = input.isNotBlank(), onClick = {
                    val text = input
                    when (textDialog) {
                        "scene" -> onEdit { rename(text) }
                        "source" ->
                            state.selectedLayer?.let { layer ->
                                onEdit {
                                    editSource(
                                        state.composition.sources
                                            .first { it.id == layer.sourceId }
                                            .copy(name = text),
                                    )
                                }
                            }
                        "text-edit" ->
                            state.selectedLayer?.let { layer ->
                                onEdit {
                                    editSource(
                                        state.composition.sources
                                            .first { it.id == layer.sourceId }
                                            .copy(locator = "text:$text"),
                                    )
                                }
                            }
                        else ->
                            onEdit {
                                if (!addSource(
                                        SceneSource(UUID.randomUUID().toString(), SceneSourceType.WIDGET, "text:$text", text.take(32)),
                                    )
                                ) {
                                    error =
                                        true
                                }
                            }
                    }
                    textDialog = null
                }) { Text(stringResource(R.string.scene_editor_save)) }
            },
            dismissButton = { TextButton(onClick = { textDialog = null }) { Text(stringResource(R.string.scene_editor_cancel)) } },
        )
    }
}

@Composable
private fun EditorIcon(
    label: Int,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    click: () -> Unit,
) {
    IconButton(onClick = click, enabled = enabled) { Icon(icon, stringResource(label)) }
}

@Composable
private fun EditorSlider(
    label: Int,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onEdit: (SceneEditorSession.() -> Unit) -> Unit,
    degrees: Boolean = false,
    millis: Boolean = false,
    change: SceneEditorSession.(Float) -> Unit,
) {
    val name = stringResource(label)
    val display =
        stringResource(
            when {
                degrees -> R.string.scene_editor_degrees
                millis -> R.string.scene_editor_millis
                else -> R.string.scene_editor_percent
            },
            (if (degrees || millis) value else value * 100).roundToInt(),
        )
    Text(stringResource(R.string.scene_editor_property_value, name, display))
    Slider(
        value.coerceIn(range.start, range.endInclusive),
        {
            onEdit {
                beginGesture()
                change(it)
            }
        },
        valueRange = range,
        onValueChangeFinished = { onEdit { endGesture() } },
        modifier = Modifier.semantics { contentDescription = name },
    )
}

@Composable
private fun sourceLabel(type: SceneSourceType) =
    stringResource(
        when (type) {
            SceneSourceType.CAMERA, SceneSourceType.UVC -> R.string.scene_editor_camera
            SceneSourceType.IMAGE -> R.string.scene_editor_image
            SceneSourceType.VIDEO -> R.string.scene_editor_video
            SceneSourceType.SCREEN_CAPTURE -> R.string.scene_editor_screen
            SceneSourceType.WIDGET -> R.string.scene_editor_text
        },
    )

private const val SCENE_CAMERA = "camera"
