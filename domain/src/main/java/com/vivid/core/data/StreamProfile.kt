package com.vivid.core.data

import kotlinx.serialization.Serializable

@Serializable
enum class StreamPlatformType { Twitch, YouTube, Kick, Custom }

/** A saved destination and its independent streaming/recording configuration. */
@Serializable
data class StreamProfile(
    val id: String,
    val name: String,
    val platform: StreamPlatformType = StreamPlatformType.Custom,
    val streamUrl: String = "",
    val streamKey: String = "",
    val streamUseTls: Boolean = false,
    val secondaryStreamUrl: String = "",
    val secondaryStreamKey: String = "",
    val secondaryStreamUseTls: Boolean = false,
    val encoderPreset: EncoderPreset = EncoderPreset.FHD30,
    val videoCodecPreference: VideoCodecPreference = VideoCodecPreference.AUTO,
    val encoderAutoFallback: Boolean = true,
    val adaptiveBitrateEnabled: Boolean = false,
    val videoFps: Int = 30,
    val videoBitrateKbps: Int = 6_000,
    val keyframeIntervalSeconds: Int = 2,
    val lowLightBoostEnabled: Boolean = false,
    val replayAudioMode: ReplayAudioMode = ReplayAudioMode.ALL,
    val twitchChannelOauthToken: String = "",
    val twitchStreamTitle: String = "",
    val twitchStreamCategory: String = "",
    val obsRemoteEnabled: Boolean = false,
    val obsHost: String = "localhost",
    val obsPort: String = "4455",
    val obsPassword: String = "",
    val obsUseTls: Boolean = false,
)

fun StreamProfile.applyTo(settings: AppSettings): AppSettings = settings.copy(
    streamUrl = streamUrl, streamKey = streamKey, streamUseTls = streamUseTls,
    secondaryStreamUrl = secondaryStreamUrl, secondaryStreamKey = secondaryStreamKey,
    secondaryStreamUseTls = secondaryStreamUseTls,
    encoderPreset = encoderPreset, videoCodecPreference = videoCodecPreference,
    encoderAutoFallback = encoderAutoFallback, adaptiveBitrateEnabled = adaptiveBitrateEnabled,
    videoFps = videoFps, videoBitrateKbps = videoBitrateKbps,
    keyframeIntervalSeconds = keyframeIntervalSeconds, lowLightBoostEnabled = lowLightBoostEnabled,
    replayAudioMode = replayAudioMode, twitchChannelOauthToken = twitchChannelOauthToken,
    twitchStreamTitle = twitchStreamTitle, twitchStreamCategory = twitchStreamCategory,
    obsRemoteEnabled = obsRemoteEnabled, obsHost = obsHost, obsPort = obsPort,
    obsPassword = obsPassword, obsUseTls = obsUseTls,
)

fun AppSettings.toStreamProfile(id: String, name: String, platform: StreamPlatformType) = StreamProfile(
    id = id, name = name, platform = platform,
    streamUrl = streamUrl, streamKey = streamKey, streamUseTls = streamUseTls,
    secondaryStreamUrl = secondaryStreamUrl, secondaryStreamKey = secondaryStreamKey,
    secondaryStreamUseTls = secondaryStreamUseTls,
    encoderPreset = encoderPreset, videoCodecPreference = videoCodecPreference,
    encoderAutoFallback = encoderAutoFallback, adaptiveBitrateEnabled = adaptiveBitrateEnabled,
    videoFps = videoFps ?: if (cameraMode == CameraMode.EXPERIMENTAL_60_FPS) 60 else encoderPreset.fps,
    videoBitrateKbps = (videoBitrateKbps ?: encoderPreset.videoBitrateKbps).coerceIn(1_000, 15_000),
    keyframeIntervalSeconds = keyframeIntervalSeconds, lowLightBoostEnabled = lowLightBoostEnabled,
    replayAudioMode = replayAudioMode, twitchChannelOauthToken = twitchChannelOauthToken,
    twitchStreamTitle = twitchStreamTitle, twitchStreamCategory = twitchStreamCategory,
    obsRemoteEnabled = obsRemoteEnabled, obsHost = obsHost, obsPort = obsPort,
    obsPassword = obsPassword, obsUseTls = obsUseTls,
)
