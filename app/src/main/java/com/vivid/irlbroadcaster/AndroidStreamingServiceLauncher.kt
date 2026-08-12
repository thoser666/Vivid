package com.vivid.irlbroadcaster

import android.content.Context
import com.vivid.feature.streaming.StreamingServiceLauncher
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Startet/stoppt den [StreamingService] über explizite Intents.
 *
 * `startForegroundService` muss aus dem Vordergrund aufgerufen werden —
 * das [StreamingViewModel] ruft es beim Go-Live-Tap (App im Vordergrund) auf.
 */
@Singleton
class AndroidStreamingServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : StreamingServiceLauncher {

    override fun startStreaming(url: String) {
        StreamingService.start(context, url)
    }

    override fun stopStreaming() {
        StreamingService.stop(context)
    }
}

@Module
@InstallIn(SingletonComponent::class)
object StreamingServiceModule {

    @Provides
    @Singleton
    fun provideStreamingServiceLauncher(
        impl: AndroidStreamingServiceLauncher,
    ): StreamingServiceLauncher = impl
}
