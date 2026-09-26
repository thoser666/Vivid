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
import com.vivid.feature.chat.kick.KickChatReader
import com.vivid.feature.chat.kick.KickChatSender
import com.vivid.feature.chat.kick.KickSocketFactory
import com.vivid.feature.chat.kick.OkHttpKickSocketFactory
import com.vivid.feature.chat.youtube.YoutubeChatReader
import com.vivid.feature.chat.youtube.YoutubeChatSender
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
     * den Reader/Sender je [ChatPlatform] aus der injizierten Map. P2 ergänzt
     * den Kick-Eintrag — der Manager bleibt unverändert.
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

    /**
     * YouTube-Adapter (P1, Multi-Plattform-Skizze): Lesen anonym über
     * innertube-Polling; Senden ist bewusst noch nicht real (Google-OAuth,
     * P4) — der Platzhalter-Sender antwortet mit [ChatSendResult.Failed].
     */
    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.YOUTUBE)
    abstract fun bindYoutubeChatReaderIntoMap(
        reader: YoutubeChatReader,
    ): ChatReader

    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.YOUTUBE)
    abstract fun bindYoutubeChatSenderIntoMap(
        sender: YoutubeChatSender,
    ): ChatSender

    /**
     * Kick-Adapter (P2, Multi-Plattform-Skizze): Lesen anonym über das
     * Pusher-Protokoll; Senden ist bewusst noch nicht real (offizielle
     * OAuth 2.1 API, P4) — der Platzhalter-Sender antwortet mit
     * [ChatSendResult.Failed].
     */
    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.KICK)
    abstract fun bindKickChatReaderIntoMap(
        reader: KickChatReader,
    ): ChatReader

    @Binds
    @IntoMap
    @Singleton
    @ChatPlatformKey(ChatPlatform.KICK)
    abstract fun bindKickChatSenderIntoMap(
        sender: KickChatSender,
    ): ChatSender

    /** Pusher-WebSocket für den Kick-Reader (analog EventSubSocketFactory). */
    @Binds
    @Singleton
    abstract fun bindKickSocketFactory(
        factory: OkHttpKickSocketFactory,
    ): KickSocketFactory

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
