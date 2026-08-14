package com.vivid.feature.chat.di

import com.vivid.feature.chat.twitch.IrcConnectionFactory
import com.vivid.feature.chat.twitch.SocketIrcConnectionFactory
import dagger.Binds
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
