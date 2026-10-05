package com.vivid.feature.streaming

import com.vivid.core.data.CameraMode

/** Applied capture path, independent of the saved request and measured output FPS. */
data class CameraModeStatus(
    val applied: CameraMode? = null,
    val preparing: Boolean = false,
    val notice: CameraModeNotice? = null,
)

enum class CameraModeNotice { UNSUPPORTED, PREPARATION_FAILED, CAPTURE_FAILED, RECORDING_FAILED }
