package com.vivid.feature.chat.di

import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.OpenAiCompatibleLlmClient
import com.vivid.feature.chat.bot.ChatStreamControl
import com.vivid.feature.chat.bot.ChatTtsSpeaker
import com.vivid.feature.chat.bot.AndroidTtsSpeaker
import com.vivid.feature.chat.media.ChatMediaController
import com.vivid.feature.chat.media.ChatMediaPlayer
import com.vivid.feature.chat.twitch.IrcConnectionFactory
import com.vivid.feature.chat.twitch.SocketIrcConnectionFactory
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ChatScope

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatFeatureModule {

    @Binds
    @Singleton
    abstract fun bindIrcConnectionFactory(
        factory: SocketIrcConnectionFactory,
    ): IrcConnectionFactory

    @Binds
    @Singleton
    abstract fun bindLlmClient(
        client: OpenAiCompatibleLlmClient,
    ): LlmClient

    @Binds
    @Singleton
    abstract fun bindChatTtsSpeaker(
        speaker: AndroidTtsSpeaker,
    ): ChatTtsSpeaker

    @Binds
    @Singleton
    abstract fun bindChatMediaPlayer(
        player: ChatMediaController,
    ): ChatMediaPlayer

    /**
     * Owner-Steuerung des Streams: optional gebunden, damit feature-chat und
     * abhängige Module (feature-settings) kompilieren, ohne von
     * feature-streaming abzuhängen. Die App bindet die echte Implementierung
     * (AppChatStreamControl) — dann gewinnt die konkrete Bindung.
     */
    @BindsOptionalOf
    abstract fun optionalChatStreamControl(): ChatStreamControl
}

@Module
@InstallIn(SingletonComponent::class)
object ChatFeatureScopeModule {

    @Provides
    @Singleton
    @ChatScope
    fun provideChatScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
