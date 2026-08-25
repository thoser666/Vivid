package com.vivid.irlbroadcaster

import android.app.Application
import coil.ImageLoader
import com.vivid.BuildConfig
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.request.ImageRequest
import com.vivid.core.data.SettingsRepository
import com.vivid.core.log.LogBuffer
import com.vivid.core.log.LogBufferTree
import com.vivid.core.log.LogStore
import com.vivid.core.remote.RemoteControlServer
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application(), ImageLoaderFactory {

    @Inject
    lateinit var remoteControlServer: RemoteControlServer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var logBuffer: LogBuffer

    @Inject
    lateinit var logStore: LogStore

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
        // In-App-Log: Timber-Trees pflanzen, damit die vorhandenen Timber.*-Aufrufe
        // erstmals wirksam werden. DebugTree schreibt in Debug-Builds nach Logcat,
        // LogBufferTree hält die letzten 500 Zeilen (geschwärzt) für den In-App-Viewer
        // und persistiert sie zusätzlich in die täglichen Log-Dateien (Rotation + Vorhaltezeit).
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val logBufferTree = LogBufferTree(logBuffer, store = logStore)
        Timber.plant(logBufferTree)
        // Abstürze deutlich markiert ins In-App-Log schreiben (isCrash), bevor der
        // vorherige Handler (Sentry) den Crash übernimmt — so bleiben sie auswertbar.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { logBufferTree.crash("Vivid", throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
        // Alte Log-Dateien gemäß Vorhaltezeit aufräumen (beim App-Start).
        applicationScope.launch {
            val retention = settingsRepository.appSettingsFlow.first().logsRetentionDays
            logStore.prune(retention)
        }
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
