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
import com.vivid.core.startup.CrashAdvisoryReporter
import com.vivid.core.startup.CrashLoopGuard
import com.vivid.core.startup.CrashLoopPolicy
import com.vivid.core.remote.RemoteControlServer
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineExceptionHandler
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

    @Inject
    lateinit var crashLoopGuard: CrashLoopGuard

    // Fehlertoleranter Hintergrund-Scope: Start-Jobs (Log-Retention, Sentry-
    // Spiegel, Remote-Control-Server) dürfen den Prozess nie crashen. Der
    // Handler ist die letzte Verteidigung gegen Residual-Rennen, die das
    // runCatching beim Aufrufer umgehen (z. B. Ktor-Bind nach Port-Probe).
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Unbehandelter Fehler im Application-Hintergrund-Scope")
            },
    )

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
        // Startup-Safe-Mode (Crash-Schleifen-Erkennung): Vor allem anderen
        // die Frage beantworten, ob dieser Start ueberhaupt die volle UI
        // versuchen darf. Bei SAFE_MODE wird die Diagnose-Activity per
        // CrashSafeModeState aktiviert und der schwergewichtige Rest
        // (Sentry, Remote-Control-Server) uebersprungen - die Diagnose
        // bleibt so unabhaengig von jeder moeglichen Crash-Ursache.
        val attempts = crashLoopGuard.currentAttempts()
        val safeMode = CrashLoopPolicy.decide(attempts) ==
            CrashLoopPolicy.Decision.SAFE_MODE
        CrashSafeModeState.active = safeMode
        if (!safeMode) {
            crashLoopGuard.recordAttempt()
        }
        // In-App-Log: Timber-Trees pflanzen, damit die vorhandenen Timber.*-Aufrufe
        // erstmals wirksam werden. DebugTree schreibt in Debug-Builds nach Logcat,
        // LogBufferTree hält die letzten 500 Zeilen (geschwärzt) für den In-App-Viewer
        // und persistiert sie zusätzlich in die täglichen Log-Dateien (Rotation + Vorhaltezeit).
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val logBufferTree = LogBufferTree(logBuffer, store = logStore)
        Timber.plant(logBufferTree)
        // Crash-Advisory (versionsgebundene, bekannte Crash-Kandidaten):
        // Beim Start gegen die installierte Version pruefen und bei Treffer
        // deutlich (CRASH-markiert) ins In-App-Log melden - direkt nach dem
        // Log-Aufbau, fehlertolerant (runCatching), damit die Diagnose selbst
        // Diagnose selbst niemals zum Startabsturz wird.
        runCatching {
            CrashAdvisoryReporter(logBuffer).reportIfAny(BuildConfig.VERSION_CODE)
        }
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
        //  - FOSS_BUILD → kein Sentry für F-Droid (kein Tracking, kein Telemetry)
        if (!BuildConfig.FOSS_BUILD && !safeMode) {
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
        } else {
            // FOSS Build: Sentry deaktiviert für F-Droid-Konformität
            Timber.i("FOSS build - Sentry disabled for F-Droid compliance")
        }
        // Web-Remote-Control über LAN starten — fehlertolerant und nur, wenn
        // das Setting an ist (Kill-Switch gegen den EADDRINUSE-Startcrash,
        // Kandidat REMOTE-EADDRINUSE-STARTUP). Im Safe-Mode uebersprungen
        // (Diagnose braucht keinen Server).
        if (!safeMode) {
            applicationScope.launch {
                val remoteEnabled = runCatching {
                    settingsRepository.appSettingsFlow.first().remoteControlEnabled
                }.getOrDefault(true)
                if (!remoteEnabled) {
                    Timber.i("Web-Remote-Control per Einstellung deaktiviert - kein Server-Bind")
                    return@launch
                }
                runCatching { remoteControlServer.start() }
                    .onFailure { Timber.e(it, "Web-Remote-Control-Server konnte nicht gestartet werden") }
            }
        }
    }
}
