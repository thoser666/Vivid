package com.vivid.feature.chat.di

import com.vivid.feature.chat.ai.LlmClient
import com.vivid.feature.chat.ai.OpenAiCompatibleLlmClient
import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.session.ChatReader
import com.vivid.feature.chat.session.ChatSender
import com.vivid.feature.chat.bot.ChatStreamControl
import com.vivid.feature.chat.bot.ChatTtsSpeaker
import com.vivid.feature.chat.bot.AndroidTtsSpeaker
import com.vivid.feature.chat.media.ChatMediaController
import com.vivid.feature.chat.media.ChatMediaPlayer
import com.vivid.feature.chat.twitch.AndroidKeystoreTokenCipher
import com.vivid.feature.chat.twitch.DataStoreTwitchTokenStore
import com.vivid.feature.chat.twitch.EventSubSocketFactory
import com.vivid.feature.chat.twitch.OkHttpEventSubSocketFactory
import com.vivid.feature.chat.twitch.TwitchChatEventSubReader
import com.vivid.feature.chat.twitch.TwitchSendChatClient
import com.vivid.feature.chat.twitch.TokenCipher
import com.vivid.feature.chat.twitch.TwitchTokenStore
import dagger.Binds
import dagger.BindsOptionalOf
import dagger.MapKey
import dagger.Module
import dagger.multibindings.IntoMap
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

/** Dagger-MapKey für das Chat-Adapter-Multibinding (je Plattform ein Eintrag). */
@MapKey
@Retention(AnnotationRetention.BINARY)
annotation class ChatPlatformKey(val value: ChatPlatform)

@Module
@InstallIn(SingletonComponent::class)
abstract class ChatFeatureModule {

    @Binds
    @Singleton
    abstract fun bindEventSubSocketFactory(
        factory: OkHttpEventSubSocketFactory,
    ): EventSubSocketFactory

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
     * Chat-Adapter-Multibinding (P3-Vorgriff): der [ChatSessionManager] wählt
     * den Reader/Sender je [ChatPlatform] aus der injizierten Map. P1/P2
     * ergänzen die Youtube/Kick-Einträge — der Manager bleibt unverändert.
     */
    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.TWITCH)
    abstract fun bindTwitchChatReaderIntoMap(
        reader: TwitchChatEventSubReader,
    ): ChatReader

    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.TWITCH)
    abstract fun bindTwitchChatSenderIntoMap(
        sender: TwitchSendChatClient,
    ): ChatSender

    /** Verschlüsselung für die Twitch-OAuth-Token-Persistenz (Android Keystore). */
    @Binds
    @Singleton
    abstract fun bindTokenCipher(
        cipher: AndroidKeystoreTokenCipher,
    ): TokenCipher

    /** Persistenz der Twitch-OAuth-Session (verschlüsselt über [TokenCipher]). */
    @Binds
    @Singleton
    abstract fun bindTwitchTokenStore(
        store: DataStoreTwitchTokenStore,
    ): TwitchTokenStore

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
