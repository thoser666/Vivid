package com.vivid.feature.streaming

import android.hardware.camera2.CameraCharacteristics
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.VideoCodecPreference
import com.vivid.core.data.toMimeType

/** Only advertised fixed high-speed ranges are candidates; an accepted request is not FPS evidence. */
internal data class HighSpeedCameraSize(val width: Int, val height: Int, val fixedFps: List<Int>)
internal data class ExperimentalCameraProfile(
    val output: ResolvedEncoderConfig,
    val captureFps: Int,
    val captureEncoder: String,
    val decoder: String,
)

internal fun experimentalOutputPreset(preset: EncoderPreset): EncoderPreset? = when (preset) {
    EncoderPreset.FHD30, EncoderPreset.FHD60 -> EncoderPreset.FHD60
    EncoderPreset.S_4K60 -> EncoderPreset.S_4K60
    else -> null
}

internal fun selectHighSpeedFps(sizes: List<HighSpeedCameraSize>, width: Int, height: Int): Int? =
    sizes.firstOrNull { it.width == width && it.height == height }?.fixedFps
        ?.filter { it >= 120 && it % 60 == 0 }?.minOrNull()

internal fun highSpeedSizes(info: CameraCharacteristics?): List<HighSpeedCameraSize> = runCatching {
    if (info?.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            ?.contains(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO) != true
    ) return emptyList()
    val map = info.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: return emptyList()
    map.highSpeedVideoSizes.map { size ->
        HighSpeedCameraSize(size.width, size.height,
            map.getHighSpeedVideoFpsRangesFor(size).filter { it.lower == it.upper }.map { it.upper })
    }
}.getOrDefault(emptyList())

internal fun resolveExperimentalCameraProfile(
    requested: ResolvedEncoderConfig,
    characteristics: CameraCharacteristics?,
): ExperimentalCameraProfile? {
    // Surface-input frame limiting and hardware codec identification require Android 10.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
    val preset = experimentalOutputPreset(requested.preset) ?: return null
    val fps = selectHighSpeedFps(highSpeedSizes(characteristics), preset.width, preset.height) ?: return null
    val infos = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isHardwareAccelerated }
    fun find(mime: String, encoder: Boolean): String? = infos.firstOrNull { info ->
        info.isEncoder == encoder && runCatching {
            val caps = info.getCapabilitiesForType(mime)
            caps.videoCapabilities?.areSizeAndRateSupported(preset.width, preset.height, 60.0) == true &&
                (!encoder || caps.colorFormats.contains(android.media.MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface))
        }.getOrDefault(false)
    }?.name
    val capture = find(MediaFormat.MIMETYPE_VIDEO_AVC, true) ?: return null
    val decoder = find(MediaFormat.MIMETYPE_VIDEO_AVC, false) ?: return null
    val codecs = if (requested.codec == VideoCodecPreference.AUTO) {
        listOf(VideoCodecPreference.H265, VideoCodecPreference.H264)
    } else listOf(requested.codec)
    val codec = codecs.firstOrNull { find(it.toMimeType(), true) != null } ?: return null
    return ExperimentalCameraProfile(
        ResolvedEncoderConfig(codec, preset, codec != requested.codec && requested.codec != VideoCodecPreference.AUTO),
        fps, capture, decoder,
    )
}
