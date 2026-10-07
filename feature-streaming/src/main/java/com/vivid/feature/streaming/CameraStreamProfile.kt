package com.vivid.feature.streaming

import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.core.data.toMimeType

/** Regular Camera2 sessions only; constrained high-speed modes require a different session. */
internal fun interface CameraCaptureCapabilities {
    fun supports(width: Int, height: Int, fps: Int): Boolean
}

internal class AndroidCameraCaptureCapabilities(
    private val characteristics: CameraCharacteristics?,
) : CameraCaptureCapabilities {
    override fun supports(width: Int, height: Int, fps: Int): Boolean = runCatching {
        val info = characteristics ?: return false
        val map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return false
        val size = map.getOutputSizes(SurfaceTexture::class.java)
            ?.firstOrNull { it.width == width && it.height == height } ?: return false
        val ranges = info.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: return false
        if (ranges.none { it.contains(fps) }) return false
        val frameDurationNs = map.getOutputMinFrameDuration(SurfaceTexture::class.java, size)
        // Zero means that the device does not report a duration, not unlimited frame rate.
        frameDurationNs > 0L && frameDurationNs <= (1_000_000_000.0 / fps).toLong() + 1L
    }.getOrDefault(false)
}

/** Resolve the intersection of the selected camera and encoder, never an unverified last resort. */
internal fun resolveCameraStreamProfile(
    requested: ResolvedEncoderConfig,
    autoFallback: Boolean,
    camera: CameraCaptureCapabilities,
    encoder: EncoderCapabilities,
): ResolvedEncoderConfig? {
    val preferredCodec = if (requested.codec == VideoCodecPreference.AUTO) VideoCodecPreference.H265 else requested.codec
    val codecs = if (autoFallback) listOf(preferredCodec, VideoCodecPreference.H264).distinct() else listOf(preferredCodec)
    val presets = if (autoFallback) EncoderPreset.entries.dropWhile { it != requested.preset } else listOf(requested.preset)
    for (codec in codecs) {
        for (preset in presets) {
            if (camera.supports(preset.width, preset.height, preset.fps) &&
                encoder.supports(codec.toMimeType(), preset.width, preset.height, preset.fps)
            ) {
                return ResolvedEncoderConfig(
                    codec, preset,
                    requested.fallbackApplied || codec != preferredCodec || preset != requested.preset,
                )
            }
        }
    }
    return null
}
