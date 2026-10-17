package com.vivid.feature.streaming.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vivid.feature.streaming.R
import com.vivid.feature.streaming.ReplayState
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.source.uvc.*

@Composable
internal fun UvcSourceDialog(engine: StreamingEngine, onDismiss: () -> Unit) {
    val manager = engine.uvcManager
    val devices by manager.devices.collectAsStateWithLifecycle()
    val state by manager.connectionState.collectAsStateWithLifecycle()
    val notice by manager.notice.collectAsStateWithLifecycle()
    val stream by engine.streamingState.collectAsStateWithLifecycle()
    val recording by engine.replayState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraDenied by remember { mutableStateOf(false) }
    var connecting by rememberSaveable { mutableStateOf(false) }
    val busy = stream is StreamingState.Streaming || stream is StreamingState.Preparing || recording is ReplayState.Recording
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        cameraDenied = !granted
        if (granted) devices.firstOrNull { it.stableSessionId == selectedId }?.let {
            connecting = true
            engine.connectUsbDevice(it)
        }
    }
    LaunchedEffect(state) {
        if (state is UvcConnectionState.Ready && connecting) {
            engine.switchSource(VideoSourceKind.USB_UVC)
            connecting = false
        }
    }
    val description = when (val current = state) {
        is UvcConnectionState.Ready -> current.device
        is UvcConnectionState.Starting -> current.device
        is UvcConnectionState.Capturing -> current.device
        else -> null
    }
    val selected = devices.firstOrNull { it.stableSessionId == selectedId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.uvc_source)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()).testTag("uvc_picker"), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(if (manager.hasUsbHostFeature()) R.string.uvc_choose_device else R.string.uvc_host_unavailable))
                if (devices.isEmpty()) Text(stringResource(R.string.uvc_no_device))
                devices.forEachIndexed { index, device ->
                    FilterChip(
                        selected = selectedId == device.stableSessionId,
                        enabled = !busy && state !is UvcConnectionState.Opening && state !is UvcConnectionState.PermissionRequired,
                        onClick = { selectedId = device.stableSessionId },
                        label = { Text(stringResource(R.string.uvc_device_label, device.productLabel, index + 1)) },
                        modifier = Modifier.testTag("uvc_device_$index"),
                    )
                }
                val status = stringResource(uvcStatusResource(state))
                Text(status, Modifier.testTag("uvc_status").semantics { stateDescription = status })
                if (cameraDenied) Text(stringResource(R.string.uvc_permission_denied))
                notice?.let { Text(stringResource(uvcFailureResource(it)), Modifier.testTag("uvc_notice")) }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = selected != null && !busy && state !is UvcConnectionState.Opening && state !is UvcConnectionState.PermissionRequired,
                        modifier = Modifier.testTag("uvc_connect"),
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                                cameraPermission.launch(Manifest.permission.CAMERA)
                            } else if (selected != null) {
                                connecting = true
                                engine.connectUsbDevice(selected)
                            }
                        },
                    ) { Text(stringResource(if (state is UvcConnectionState.Error) R.string.uvc_retry else R.string.uvc_connect)) }
                    TextButton(onClick = engine::disconnectUsbDevice, enabled = state !is UvcConnectionState.Disconnected && state !is UvcConnectionState.Error) {
                        Text(stringResource(R.string.uvc_disconnect))
                    }
                }
                description?.let { device ->
                    Text(stringResource(R.string.uvc_mode_heading))
                    device.modes.forEach { mode ->
                        val activeMode = when (val current = state) {
                            is UvcConnectionState.Ready -> current.mode
                            is UvcConnectionState.Starting -> current.mode
                            is UvcConnectionState.Capturing -> current.mode
                            else -> null
                        }
                        FilterChip(
                            selected = mode == activeMode,
                            enabled = !busy,
                            onClick = { connecting = true; engine.setUsbCaptureMode(mode) },
                            label = { Text(stringResource(R.string.uvc_mode, mode.width, mode.height, mode.fps, mode.format.name)) },
                            modifier = Modifier.testTag("uvc_mode_${mode.preferenceString()}"),
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) } },
        dismissButton = { TextButton(onClick = engine::refreshUsbDevices) { Text(stringResource(R.string.uvc_refresh)) } },
    )
}

internal fun uvcStatusResource(state: UvcConnectionState): Int = when (state) {
    UvcConnectionState.Disconnected -> R.string.uvc_disconnected
    is UvcConnectionState.Attached -> R.string.uvc_connect
    is UvcConnectionState.PermissionRequired -> R.string.uvc_permission_required
    is UvcConnectionState.Opening, is UvcConnectionState.Starting -> R.string.uvc_opening
    is UvcConnectionState.Ready -> R.string.uvc_ready
    is UvcConnectionState.Capturing -> R.string.uvc_capturing
    is UvcConnectionState.Error -> uvcFailureResource(state.reason)
}

internal fun uvcFailureResource(failure: UvcFailure): Int = when (failure) {
    UvcFailure.PermissionDenied -> R.string.uvc_permission_denied
    UvcFailure.DeviceBusy -> R.string.uvc_busy
    UvcFailure.UnsupportedMode -> R.string.uvc_unsupported_mode
    UvcFailure.DetachedDuringCapture -> R.string.uvc_disconnected
    UvcFailure.NativeLibraryMissing -> R.string.uvc_native_unavailable
    UvcFailure.UnsupportedHost -> R.string.uvc_host_unavailable
    UvcFailure.OpenFailed, UvcFailure.PreviewFailed -> R.string.uvc_open_failed
}
