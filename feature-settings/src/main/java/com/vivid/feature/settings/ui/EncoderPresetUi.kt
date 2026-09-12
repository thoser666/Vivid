package com.vivid.feature.settings.ui

import com.vivid.core.data.EncoderPreset
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.settings.R

/** UI-Helfer: Anzeigename eines Encoder-Presets (z. B. „1080p60“). */
fun EncoderPreset.displayName(): String = "${height}p$fps"

/** UI-Helfer: lokalisierte Bezeichnung der Codec-Präferenz. */
fun VideoCodecPreference.labelRes(): Int = when (this) {
    VideoCodecPreference.AUTO -> R.string.encoder_codec_auto
    VideoCodecPreference.H264 -> R.string.encoder_codec_h264
    VideoCodecPreference.H265 -> R.string.encoder_codec_h265
    VideoCodecPreference.AV1 -> R.string.encoder_codec_av1
}
