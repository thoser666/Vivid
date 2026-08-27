package com.vivid.feature.streaming

import androidx.annotation.StringRes
import com.vivid.feature.streaming.R

/**
 * Verfügbare Video-Effekte (OpenGL-Filter) für die Kamera-Vorschau und den Stream.
 *
 * Jeder Filter ordnet sich einem RootEncoder `BaseFilterRender`-Wrapper zu, der über
 * `glInterface.setFilter()` aktiviert wird. Der `NONE`-Filter entfernt alle Filter.
 *
 * Moblin-Parität: „Video-Effekte (Graustufen, Letterbox, Sepia, Rauschfilter)" —
 * httpss://github.com/eerimoq/moblin/blob/main/lib/graphics.dart
 */
enum class VideoFilter(
    /** String-Ressource für den Anzeigenamen im UI (i18n de/en/fr). */
    @StringRes val labelRes: Int,
) {
    /** Kein Filter — normaler Kamera-Output. */
    NONE(R.string.video_filter_none),

    /** Graustufen (Monochrom). */
    GRAYSCALE(R.string.video_filter_grayscale),

    /** Sepia-Ton (warmes Braun). */
    SEPIA(R.string.video_filter_sepia),

    /** Rauschen / Noise. */
    NOISE(R.string.video_filter_noise),

    /** Negativ-Invertierung. */
    NEGATIVE(R.string.video_filter_negative),

    /** Kantenverstärkung / Sketch. */
    EDGE_DETECTION(R.string.video_filter_edge_detection),

    /** Cartoon-Posterisierung. */
    CARTOON(R.string.video_filter_cartoon),

    /** Pixelierung (Mosaik-Effekt). */
    PIXELATED(R.string.video_filter_pixelated),

    /** Blur / Unschärfe. */
    BLUR(R.string.video_filter_blur),

    /** Schönheits-Filter (Hautglättung). */
    BEAUTY(R.string.video_filter_beauty),

    /** Duotone-Zweifarbigkeit. */
    DUOTONE(R.string.video_filter_duotone),
}
