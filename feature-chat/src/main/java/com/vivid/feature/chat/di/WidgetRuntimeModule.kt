package com.vivid.feature.chat.di

import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.youtube.YoutubeChatReader
import com.vivid.feature.chat.kick.KickChatReader
import com.vivid.feature.chat.emotes.ThirdPartyEmoteService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.scopes.ViewModelScoped
import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class WidgetRuntime

/** Each widget can connect to its own channel without restarting another widget or the bot. */
@Module
@InstallIn(ViewModelComponent::class)
object WidgetRuntimeModule {
    @Provides @ViewModelScoped @WidgetRuntime
    fun twitch(reader: TwitchChatEventSubReader) = reader.independent()
    @Provides @ViewModelScoped @WidgetRuntime
    fun youtube(reader: YoutubeChatReader) = reader.independent()
    @Provides @ViewModelScoped @WidgetRuntime
    fun kick(reader: KickChatReader) = reader.independent()
    @Provides @ViewModelScoped @WidgetRuntime
    fun emotes() = ThirdPartyEmoteService()
}
