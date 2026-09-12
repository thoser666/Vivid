package com.vivid.core.data

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.util.Range

/**
 * Encoder-Preset (Auflösung/FPS) für den Video-Encoder — Moblin-Parität
 * „4K/60fps + HEVC“ (Roadmap-Bucket Streaming-Erweiterung, v0.6.0).
 *
 * RootEncoder nimmt die Werte über `prepareVideo(width, height, fps, bitrate,
 * iFrameInterval, rotation)` an; der bisherige Code rief `prepareVideo()` ohne
 * Argumente auf (RootEncoder-Default 640×480@30). Mit diesem Preset wird der
 * Encoder konfiguriert — vor dem ersten `prepareVideo`-Aufruf (Codec-Wechsel
 * und Auflösung sind nur vor dem Start wirksam).
 *
 * - [S_4K60]: 2160p @ 60 fps, High Bitrate (Leistungsfähige Geräte)
 * - [FHD60]: 1080p @ 60 fps (Standard für moderne Streams)
 * - [FHD30]: 1080p @ 30 fps
 * - [HD30]: 720p @ 30 fps (schwache Geräte / Uploads)
 * - [SD30]: 480p @ 30 fps (Notfall-Fallback)
 */
enum class EncoderPreset(
    val width: Int,
    val height: Int,
    val fps: Int,
    val videoBitrateKbps: Int,
) {
    S_4K60(3840, 2160, 60, 24_000),
    FHD60(1920, 1080, 60, 9_000),
    FHD30(1920, 1080, 30, 6_000),
    HD30(1280, 720, 30, 4_000),
    SD30(854, 480, 30, 2_000),
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt/null → [FHD30]). */
        fun fromName(name: String?): EncoderPreset =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: FHD30
    }
}

/**
 * Bevorzugter Video-Codec (RootEncoder `VideoCodec`-Äquivalent im Domain-Layer).
 *
 * Die feature-streaming-Schicht mappt die Werte auf `com.pedro.common.VideoCodec`.
 * [AUTO] heißt: HEVC, wenn die Hardware es kann, sonst H.264 (Fallback-Kette).
 */
enum class VideoCodecPreference {
    AUTO,
    H264,
    H265,
    AV1,
    ;

    companion object {
        /** Liest einen gespeicherten Namen robust (unbekannt/null → [AUTO]). */
        fun fromName(name: String?): VideoCodecPreference =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: AUTO
    }
}

/**
 * Abstraktion über die Gerätes-Fähigkeiten (MediaCodecList), damit die
 * Fallback-Kette und das UI-Gating testbar bleiben. Die echte Implementierung
 * ([AndroidEncoderCapabilities]) fragt `MediaCodecList(REGULAR_CODECS)` ab.
 */
interface EncoderCapabilities {
    /** Kann die Hardware den MIME-Typ in der Auflösung @ fps encodieren? */
    fun supports(mime: String, width: Int, height: Int, fps: Int): Boolean
}

/** Echte Fähigkeiten-Erkennung über die MediaCodec-Registry (API 21+). */
class AndroidEncoderCapabilities : EncoderCapabilities {

    override fun supports(mime: String, width: Int, height: Int, fps: Int): Boolean =
        codecInfos.any { info ->
            try {
                val caps = info.getCapabilitiesForType(mime)
                val video = caps.videoCapabilities ?: return@any false
                // FPS-Range existiert seit API 21 — aber manche Encodern melden
                // keine Ranges; dann gilt die Auflösungs-Prüfung allein.
                val fpsOk = runCatching { video.supportedFrameRates }
                    .getOrNull()?.contains(fps) ?: true
                video.isSizeSupported(width, height) && fpsOk
            } catch (_: IllegalArgumentException) {
                false // Codec unterstützt den MIME-Typ nicht
            }
        }

    /** Alle Encoder, die den MIME-Typ beherrschen (lazy, einmalig pro Instanz). */
    private val codecInfos: List<MediaCodecInfo> by lazy {
        try {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .filter { it.isEncoder }
                .toList()
        } catch (_: Exception) {
            emptyList() // Defensiv: Registry nicht lesbar → keine Fähigkeit melden
        }
    }
}

/**
 * MIME-Typ der Codec-Präferenz (für die [EncoderCapabilities]-Prüfung).
 *
 * Pure Funktion im Domain-Layer — keine RootEncoder-Abhängigkeit.
 */
fun VideoCodecPreference.toMimeType(): String = when (this) {
    VideoCodecPreference.H264 -> MediaFormat.MIMETYPE_VIDEO_AVC
    VideoCodecPreference.H265 -> MediaFormat.MIMETYPE_VIDEO_HEVC
    // Konstante erst ab API 29 im SDK — Literal (wird ohnehin inline kompiliert),
    // damit der InlinedApi-Lint bei minSdk 24 nicht anschlägt.
    VideoCodecPreference.AV1 -> "video/av01"
    // AUTO wird über die Fallback-Kette aufgelöst und hat keinen eigenen MIME.
    VideoCodecPreference.AUTO -> ""
}

/**
 * Reine Fallback-Kette (testbar ohne Android-Framework): Löst die Codec-
 * Präferenz gegen die Geräte-Fähigkeiten und das gewählte Preset auf.
 *
 * Reihenfolge der Kette:
 *  1. Explizite Präferenz (H264/H265/AV1) — wenn (Preset × Codec) nicht
 *     unterstützt wird, Fallback auf H.264 in einem kleineren Preset.
 *  2. [AUTO]: HEVC → H.264, jeweils erst im gewählten Preset, dann über die
 *     Preset-Abstufungen abwärts bis [EncoderPreset.SD30].
 *  3. Letzter Ausweg: H.264 im gewählten Preset, auch ohne bestätigte
 *     Fähigkeit (Encoder-Fehler zeigt RootEncoder zur Laufzeit).
 *
 * @return Aufgelöster [ResolvedEncoderConfig] — niemals null.
 */
fun resolveEncoderConfig(
    preference: VideoCodecPreference,
    preset: EncoderPreset,
    capabilities: EncoderCapabilities,
): ResolvedEncoderConfig {
    val codecChain: List<VideoCodecPreference> = when (preference) {
        VideoCodecPreference.AUTO -> listOf(VideoCodecPreference.H265, VideoCodecPreference.H264)
        else -> listOf(preference, VideoCodecPreference.H264).distinct()
    }

    val presetChain: List<EncoderPreset> =
        EncoderPreset.entries.dropWhile { it != preset } + EncoderPreset.SD30

    // 1. Bevorzugter Codec, bevorzugtes Preset; Abstufung des Presets, wenn der
    //    Codec grundsätzlich vorhanden ist, die Auflösung/FPS aber nicht geht.
    // Wunsch-Codec: AUTO wünscht sich HEVC (H264 ist bereits Fallback).
    val wishedCodec = if (preference == VideoCodecPreference.AUTO) {
        VideoCodecPreference.H265
    } else {
        preference
    }

    for (codec in codecChain) {
        val mime = codec.toMimeType()
        if (mime.isEmpty()) continue
        if (capabilities.supports(mime, preset.width, preset.height, preset.fps)) {
            return ResolvedEncoderConfig(
                codec,
                preset,
                fallbackApplied = codec != wishedCodec,
            )
        }
        val smaller = presetChain.firstOrNull {
            capabilities.supports(mime, it.width, it.height, it.fps)
        }
        if (smaller != null) {
            // Fallback, wenn das Preset abgestuft wurde ODER der Codec vom
            // Wunsch abweicht (AUTO wünscht HEVC — H264 ist schon Fallback).
            return ResolvedEncoderConfig(
                codec,
                smaller,
                fallbackApplied = smaller != preset || codec != wishedCodec,
            )
        }
    }

    // 2. Kein Fähigkeits-Nachweis (z. B. Registry leer) → H.264 im Wunsch-Preset
    //    betreiben; RootEncoder meldet Encoder-Fehler zur Laufzeit als FAILED.
    return ResolvedEncoderConfig(
        codec = VideoCodecPreference.H264,
        preset = preset,
        fallbackApplied = preference != VideoCodecPreference.H264,
    )
}

/** Ergebnis der Fallback-Kette: endgültiger Codec + Preset. */
data class ResolvedEncoderConfig(
    val codec: VideoCodecPreference,
    val preset: EncoderPreset,
    /** true = Geräte-Fähigkeiten haben Codec/Preset abweichend vom Wunsch gesetzt. */
    val fallbackApplied: Boolean,
)
