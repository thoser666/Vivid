package com.vivid.feature.streaming

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Build
import org.junit.Assume

/**
 * Hardware-encoder capability of the device under test.
 *
 * The instrumented suite runs on x86_64 emulator images in CI, and those images
 * only ship software encoders (`c2.android.avc.encoder`). Two tests assert
 * *hardware* behaviour — the bitrate mode a hardware encoder reports and the IDR
 * cadence of a hardware encoder — so on such a device they cannot produce a
 * meaningful result: `forceCodecType(HARDWARE)` fails and `prepareVideoEncoder`
 * returns false. Failing there would redden the whole `connectedDebugAndroidTest`
 * job for an environment that simply lacks the capability (every tag run since
 * 2026-11-27, run 45959068232 on API 34/35/36).
 *
 * Instead of asserting, the tests skip through a JUnit assumption: the report
 * lists them as "skipped" and carries the reason plus the encoder names that were
 * actually found, so the gap stays visible instead of hiding as a pass.
 */
internal object HardwareVideoEncoder {

    /** Encoder names that advertise H.264 on this device, for diagnostics. */
    fun h264EncoderNames(): List<String> =
        MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
            .map { it.name }
            .sorted()

    /**
     * True when at least one H.264 encoder is hardware-accelerated.
     * `isHardwareAccelerated` needs Android 10; below that the name heuristic
     * decides (software codecs are the `c2.android.*` / `OMX.google.*` families).
     */
    fun hasHardwareH264Encoder(): Boolean {
        val encoders: List<MediaCodecInfo> = MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
            .filter { info -> info.isEncoder && info.supportedTypes.any { it.equals(MediaFormat.MIMETYPE_VIDEO_AVC, true) } }
        if (encoders.isEmpty()) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            encoders.any { it.isHardwareAccelerated }
        } else {
            encoders.any { info -> !isSoftwareCodecName(info.name) }
        }
    }

    /**
     * Skips the calling test with a visible reason when the device has no
     * hardware H.264 encoder. Call it before building the encoder, so the skip
     * happens without touching RootEncoder at all.
     */
    fun assumeHardwareH264Encoder() {
        val names = h264EncoderNames()
        Assume.assumeTrue(
            "Skipped: no hardware H.264 encoder on this device — encoders found: " +
                (names.ifEmpty { listOf("<none>") }.joinToString()) +
                ". Emulator images ship only software codecs, and this test asserts hardware behaviour " +
                "(bitrate mode / IDR cadence); it runs on real devices.",
            hasHardwareH264Encoder(),
        )
    }

    private fun isSoftwareCodecName(name: String): Boolean =
        name.startsWith("c2.android.") || name.startsWith("OMX.google.") || name.contains(".sw.")
}
