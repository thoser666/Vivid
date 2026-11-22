package com.vivid.feature.streaming.scene

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vivid.core.data.SceneRepository
import com.vivid.core.data.SettingsRepository
import com.vivid.core.location.LocationProvider
import com.vivid.feature.chat.emotes.ThirdPartyEmoteService
import com.vivid.feature.chat.kick.KickChatReader
import com.vivid.feature.chat.twitch.TwitchBadgeClient
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.ui.ChatOverlayViewModel
import com.vivid.feature.chat.youtube.YoutubeChatReader
import com.vivid.feature.streaming.ui.SceneWidgetsOverlayViewModel
import com.vivid.feature.widget.*
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** The encoder owns these ViewModels, independently of Activity/navigation lifetimes. */
class SceneWidgetViewModelFactory @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settings: SettingsRepository,
    private val scenes: SceneRepository,
    private val location: LocationProvider,
    private val geocoder: GeocoderResolver,
    private val pomodoro: PomodoroWidgetManager,
    private val twitch: TwitchChatEventSubReader,
    private val youtube: YoutubeChatReader,
    private val kick: KickChatReader,
    private val badges: TwitchBadgeClient,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T = when (modelClass) {
        SceneWidgetsOverlayViewModel::class.java -> SceneWidgetsOverlayViewModel(scenes)
        TextInfoWidgetViewModel::class.java -> TextInfoWidgetViewModel(settings, location, geocoder)
        ImageWidgetViewModel::class.java -> ImageWidgetViewModel(context, settings)
        QrCodeWidgetViewModel::class.java -> QrCodeWidgetViewModel(settings)
        SlideshowWidgetViewModel::class.java -> SlideshowWidgetViewModel(settings)
        GridOverlayViewModel::class.java -> GridOverlayViewModel(settings)
        BatteryWidgetViewModel::class.java -> BatteryWidgetViewModel(context, settings)
        SubtitleWidgetViewModel::class.java -> SubtitleWidgetViewModel(settings, AndroidSpeechToTextEngine(context))
        ChatOverlayViewModel::class.java -> ChatOverlayViewModel(twitch.independent(), youtube.independent(),
            kick.independent(), settings, badges, ThirdPartyEmoteService())
        PomodoroWidgetViewModel::class.java -> PomodoroWidgetViewModel(scenes, pomodoro)
        else -> error("Unsupported scene ViewModel: ${modelClass.name}")
    } as T
}
