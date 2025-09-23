package com.vivid.feature.streaming.di

import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.feature.streaming.StreamingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    @Provides
    @Singleton
    fun provideObsWebSocketClient(): OBSWebSocketClient {
        return OBSWebSocketClient()
    }

    // Die StreamingEngine hat jetzt einen einfachen, von Hilt bereitgestellten Konstruktor.
    // Wir müssen sie nicht mehr manuell bereitstellen.
    // Falls du sie doch brauchst (z.B. für ein Interface):
    @Provides
    @Singleton
    fun provideStreamingEngine(): StreamingEngine {
        return StreamingEngine()
    }
}
