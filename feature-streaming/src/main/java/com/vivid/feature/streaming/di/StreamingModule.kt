package com.vivid.feature.streaming.di // Oder ein anderer passender Paketname

import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.OpenGlView
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.feature.streaming.StreamingEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt-Modul, das Abhängigkeiten für das Streaming und die OBS-Konnektivität bereitstellt.
 * Die Abhängigkeiten werden als Singletons bereitgestellt, was bedeutet, dass für die gesamte
 * Lebensdauer der Anwendung nur eine Instanz jeder Klasse existiert.
 */
@Module
@InstallIn(SingletonComponent::class)
object StreamingModule {

    /**
     * Stellt eine Singleton-Instanz des OBSWebSocketClient bereit.
     * Dieser Client verwaltet die WebSocket-Verbindung zu OBS Studio.
     */
    @Provides
    @Singleton
    fun provideObsWebSocketClient(): OBSWebSocketClient {
        return OBSWebSocketClient()
    }

    /**
     * Stellt eine Factory-Funktion für RtmpCamera2 bereit.
     * Anstatt die StreamingEngine direkt von RtmpCamera2 abhängig zu machen,
     * injizieren wir eine Factory. Dies ermöglicht es uns, in Unit-Tests eine
     * gemockte Kamera-Instanz zu erstellen und zu übergeben, was die Testbarkeit
     * der StreamingEngine erheblich verbessert.
     *
     * @return Eine Lambda-Funktion, die eine OpenGlView und einen ConnectChecker
     *         akzeptiert und eine neue Instanz von RtmpCamera2 zurückgibt.
     */
    @Provides
    fun provideRtmpCameraFactory(): (OpenGlView, ConnectChecker) -> RtmpCamera2 {
        return { view, checker -> RtmpCamera2(view, checker) }
    }

    /**
     * Stellt eine Singleton-Instanz der StreamingEngine bereit.
     * Diese Klasse ist die zentrale Logik für die Verwaltung der Gerätekamera,
     * des Encodings und des RTMP-Streamings.
     *
     * @param cameraFactory Die oben definierte Factory zur Erstellung von RtmpCamera2-Instanzen.
     */
    @Provides
    @Singleton
    fun provideStreamingEngine(
        // Hilt injiziert hier automatisch die Factory aus der provideRtmpCameraFactory()-Methode

        cameraFactory: @JvmSuppressWildcards (OpenGlView, ConnectChecker) -> RtmpCamera2
    ): StreamingEngine {
        return StreamingEngine(cameraFactory)
    }
}