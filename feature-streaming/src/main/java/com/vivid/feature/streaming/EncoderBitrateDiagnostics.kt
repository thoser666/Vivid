package com.vivid.feature.streaming

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import com.pedro.encoder.BaseEncoder
import com.pedro.encoder.video.VideoEncoder
import com.pedro.library.base.Camera2Base

/** The codec's configured mode, distinct from adaptive changes to its target bitrate. */
enum class EncoderBitrateMode { CBR, VBR, CQ, UNKNOWN }

data class EncoderBitrateDiagnostics(
    val encoderName: String,
    val mode: EncoderBitrateMode,
    val cbrSupported: Boolean,
    val targetKbps: Int,
)

internal fun configuredBitrateMode(value: Int?): EncoderBitrateMode = when (value) {
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR -> EncoderBitrateMode.CBR
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_VBR -> EncoderBitrateMode.VBR
    MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CQ -> EncoderBitrateMode.CQ
    else -> EncoderBitrateMode.UNKNOWN
}

/**
 * RootEncoder 2.7.5 checks the selected codec's CBR capability and sets CBR before
 * configure(). Its fallback omits the mode. Read the actual configured format,
 * rather than guessing VBR from an unrelated codec in MediaCodecList.
 * RootEncoder has no public codec accessor; keep this version-specific bridge here.
 */
internal object RootEncoderBitrateDiagnostics {
    fun read(camera: Camera2Base): EncoderBitrateDiagnostics {
        val encoder = Camera2Base::class.java.getDeclaredField("videoEncoder").apply { isAccessible = true }
            .get(camera) as VideoEncoder
        val codec = BaseEncoder::class.java.getDeclaredField("codec").apply { isAccessible = true }
            .get(encoder) as MediaCodec
        return read(codec, encoder.type, encoder.bitRate / 1_000)
    }

    fun read(codec: MediaCodec, mime: String, targetKbps: Int): EncoderBitrateDiagnostics {
        val format = codec.outputFormat
        val supportsCbr = requireNotNull(codec.codecInfo.getCapabilitiesForType(mime).encoderCapabilities)
            .isBitrateModeSupported(MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
        val mode = configuredBitrateMode(
            if (format.containsKey(MediaFormat.KEY_BITRATE_MODE)) format.getInteger(MediaFormat.KEY_BITRATE_MODE) else null,
        )
        check(!supportsCbr || mode == EncoderBitrateMode.CBR) {
            "${codec.name} supports CBR but configured mode is $mode"
        }
        return EncoderBitrateDiagnostics(codec.name, mode, supportsCbr, targetKbps)
    }
}
