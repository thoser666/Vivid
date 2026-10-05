package com.vivid.feature.streaming.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.vivid.core.data.CameraMode
import com.vivid.feature.streaming.CameraModeNotice
import com.vivid.feature.streaming.CameraModeStatus
import com.vivid.feature.streaming.R

@Composable
internal fun CameraModeFeedback(status: CameraModeStatus) {
    if (status.preparing) Text(stringResource(R.string.camera_mode_preparing), style = MaterialTheme.typography.bodySmall)
    status.applied?.let {
        Text(stringResource(R.string.camera_mode_applied, stringResource(
            if (it == CameraMode.STANDARD) R.string.camera_mode_standard else R.string.camera_mode_experimental)),
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.testTag("applied_camera_mode"))
    }
    status.notice?.let {
        Text(stringResource(when (it) {
            CameraModeNotice.UNSUPPORTED -> R.string.camera_mode_fallback_unsupported
            CameraModeNotice.PREPARATION_FAILED -> R.string.camera_mode_fallback_preparation
            CameraModeNotice.CAPTURE_FAILED -> R.string.camera_mode_capture_failed
            CameraModeNotice.RECORDING_FAILED -> R.string.camera_mode_recording_failed
        }), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
}
