package com.vivid.irlbroadcaster

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.ImageRequest
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteControlServer
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var remoteControlServer: RemoteControlServer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Coil ImageLoader mit 25MB Disk-Cache für Twitch-Emotes. */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .diskCache {
            DiskCache.Builder()
                .directory(cacheDir.resolve("emote_cache"))
                .maxSizeBytes(25L * 1024 * 1024)
                .build()
        }
        .respectCacheHeaders(false)
        .build()

    /**
     * Aktueller Sentry-Opt-out-Stand für den beforeSend-Callback
     * (synchron lesbar, kein suspend im Callback möglich). Wird vom
     * appSettingsFlow gespiegelt; Default: an.
     */
    @Volatile
    private var sentryEnabled = true

    override fun onCreate() {
        super.onCreate()
        // Sentry explizit initialisieren (Auto-Init im Manifest deaktiviert):
        //  - sendDefaultPii=false → keine IP-/Gerätename-Erhebung
        //  - beforeSend → verwirft alle Events, wenn der Nutzer das
        //    Fehler-Reporting in den Settings deaktiviert hat (Opt-out)
        SentryAndroid.init(this) { options ->
            // JavaBean-Accessor: isSendDefaultPii (keine IP-/Gerätename-Erhebung)
            options.isSendDefaultPii = false
            options.beforeSend = sentryBeforeSendCallback { sentryEnabled }
        }
        // Opt-out-Stand live verfolgen (für beforeSend).
        applicationScope.launch {
            settingsRepository.appSettingsFlow.collect { settings ->
                sentryEnabled = settings.sentryEnabled
            }
        }
        // Web-Remote-Control über LAN starten — fehlertolerant, damit ein
        // Port-Konflikt die App nicht crashen lässt.
        applicationScope.launch {
            runCatching { remoteControlServer.start() }
                .onFailure { Timber.e(it, "Web-Remote-Control-Server konnte nicht gestartet werden") }
        }
    }
}
