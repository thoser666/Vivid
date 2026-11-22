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
 * UI und Remote-Control verwenden dieselbe Startvorbereitung.
 */
@Singleton
class AndroidStreamingServiceLauncher @Inject constructor(
    @ApplicationContext private val context: Context,
) : StreamingServiceLauncher {

    override fun startStreaming(urls: List<String>) {
        StreamingService.start(context, urls)
    }

    override fun startSceneProjection(sourceId: String, resultCode: Int, data: android.content.Intent): Boolean {
        return try {
            StreamingService.grantSceneProjection(context, sourceId, resultCode, data)
            true
        } catch (failure: RuntimeException) {
            timber.log.Timber.w(failure, "Unable to start scene capture service")
            false
        }
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
