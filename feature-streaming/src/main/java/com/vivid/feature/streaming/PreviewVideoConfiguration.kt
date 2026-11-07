package com.vivid.feature.streaming

import com.vivid.core.data.AppSettings
import com.vivid.core.data.AppOrientation
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.core.data.resolveEncoderConfig

/** Only settings that affect the output canvas; capture stays on the ordinary 30-FPS path. */
internal data class PreviewVideoConfiguration(
    val preset: EncoderPreset,
    val codec: VideoCodecPreference,
    val fps: Int,
    val autoFallback: Boolean,
    val cameraMode: CameraMode,
    val orientation: AppOrientation,
) {
    constructor(settings: AppSettings) : this(
        settings.encoderPreset, settings.videoCodecPreference,
        settings.videoFps ?: settings.encoderPreset.fps,
        settings.encoderAutoFallback, settings.cameraMode, settings.appOrientation,
    )

    val usesExperimentalCapture: Boolean
        get() = fps != 30 && cameraMode == CameraMode.EXPERIMENTAL_60_FPS

    /** Match the start coordinator's encoder-only preflight before checking the selected lens. */
    fun requestedEncoder(capabilities: EncoderCapabilities): ResolvedEncoderConfig =
        if (autoFallback && cameraMode == CameraMode.STANDARD) {
            resolveEncoderConfig(codec, preset, orientedEncoderCapabilities(capabilities, orientation == AppOrientation.PORTRAIT), fps)
        } else ResolvedEncoderConfig(codec, preset, false, fps)
}
