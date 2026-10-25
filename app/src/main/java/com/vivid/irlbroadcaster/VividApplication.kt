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
import com.vivid.core.startup.SentryReplayPolicy
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

    // Fault-tolerant background scope: startup jobs (log retention, Sentry
    // mirror, remote-control server) must never crash the process. The handler
    // is the last line of defense against residual races that bypass the
    // caller's runCatching (e.g. Ktor bind after the port probe).
    private val applicationScope = CoroutineScope(
        SupervisorJob() + Dispatchers.Default +
            CoroutineExceptionHandler { _, throwable ->
                Timber.e(throwable, "Unhandled error in application background scope")
            },
    )

    /** Coil ImageLoader with a 25 MB disk cache for Twitch emotes. */
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
     * Current Sentry opt-out state for the beforeSend callback
     * (readable synchronously; the callback cannot suspend).
     * Mirrored from appSettingsFlow; default: enabled.
     */
    @Volatile
    private var sentryEnabled = true

    override fun onCreate() {
        super.onCreate()
        // Startup safe mode (crash-loop detection): before anything else,
        // answer whether this launch may even attempt the full UI. In
        // SAFE_MODE the diagnostics activity is activated via
        // CrashSafeModeState and the heavyweight remainder (Sentry,
        // remote-control server) is skipped - the diagnostics therefore
        // stay independent of any possible crash cause.
        val attempts = crashLoopGuard.currentAttempts()
        val safeMode = CrashLoopPolicy.decide(attempts) ==
            CrashLoopPolicy.Decision.SAFE_MODE
        CrashSafeModeState.active = safeMode
        if (!safeMode) {
            crashLoopGuard.recordAttempt()
        }
        // In-app log: plant the Timber trees so the existing Timber.* calls
        // take effect for the first time. DebugTree writes to logcat in debug
        // builds; LogBufferTree keeps the last 500 lines (redacted) for the
        // in-app viewer and additionally persists them into the daily log
        // files (rotation + retention).
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        val logBufferTree = LogBufferTree(logBuffer, store = logStore)
        Timber.plant(logBufferTree)
        // Crash advisory (version-bound known crash candidates): check
        // against the installed version at startup and report hits clearly
        // marked CRASH to the in-app log - right after the log is set up,
        // fault-tolerantly (runCatching), so the diagnosis itself never
        // becomes a startup crash.
        runCatching {
            CrashAdvisoryReporter(logBuffer).reportIfAny(BuildConfig.VERSION_CODE)
        }
        // Write crashes clearly marked to the in-app log (isCrash) before the
        // previous handler (Sentry) picks up the crash - that keeps them evaluable.
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching { logBufferTree.crash("Vivid", throwable) }
            previousHandler?.uncaughtException(thread, throwable)
        }
        // Clean up old log files according to the retention period (on app start).
        applicationScope.launch {
            val retention = settingsRepository.appSettingsFlow.first().logsRetentionDays
            logStore.prune(retention)
        }
        // Explicit Sentry initialization (auto-init disabled in the manifest):
        //  - sendDefaultPii=false → no IP / device name collection
        //  - beforeSend → drops all events if the user has disabled error
        //    reporting in settings (opt-out) and drops expected network
        //    operating states (#267)
        //  - FOSS_BUILD → no Sentry for F-Droid (no tracking, no telemetry)
        //  - Session replay as error replay (retro buffer ~30 s before an error):
        //    onErrorSampleRate 1.0, sessionSampleRate 0.0 - no continuous recording;
        //    the opt-out steers it on two levels (rates + buffering), because
        //    beforeSend does not filter replay envelopes (see SentryReplayPolicy).
        if (!BuildConfig.FOSS_BUILD && !safeMode) {
            SentryAndroid.init(this) { options ->
                // JavaBean accessor: isSendDefaultPii (no IP / device name collection)
                options.isSendDefaultPii = false
                // App context as Sentry tags (flavor + exact version): every
                // report is attributable to its build (watchdog + triage, see
                // SentryAppTags). Keys app.flavor/app.version_code/app.version_name.
                val appTags = SentryAppTags(
                    flavor = BuildConfig.FLAVOR,
                    versionCode = BuildConfig.VERSION_CODE,
                    versionName = BuildConfig.VERSION_NAME,
                ).toSentryTags()
                appTags.forEach { (tag, value) -> options.setTag(tag, value) }
                // beforeSend → two filters in one chain: (1) the user's opt-out,
                // (2) expected network operating states (SentryTransportFilter).
                // Point (2) prevents the GitHub issue watchdog from creating an
                // issue for every unreachable OBS/WHIP host (Sentry VIVID-3P
                // #267 and its predecessors #217/#219/#220/#224/#244/#248). Real
                // FATAL crashes always get through.
                options.beforeSend = sentryBeforeSendWithTransportFilter { sentryEnabled }
                // Error replay configuration (opt-out capable, see SentryReplayPolicy):
                val replayPlan = SentryReplayPolicy.plan(sentryEnabled)
                options.sessionReplay.onErrorSampleRate = replayPlan.errorSampleRate
                options.sessionReplay.sessionSampleRate = replayPlan.sessionSampleRate
                // Replays are their own envelopes - the opt-out filters them separately:
                options.beforeSendReplay = sentryBeforeSendReplayCallback { sentryEnabled }
            }
            // Track the opt-out state live (for beforeSend/beforeSendReplay)
            // and also steer error replay buffering (start/stop only on an
            // actual change - no flicker on settings re-collection).
            applicationScope.launch {
                var previousEnabled: Boolean? = null
                settingsRepository.appSettingsFlow.collect { settings ->
                    sentryEnabled = settings.sentryEnabled
                    when (SentryReplayPolicy.runtimeAction(settings.sentryEnabled, previousEnabled)) {
                        SentryReplayPolicy.RuntimeAction.REPLAY_START ->
                            runCatching { io.sentry.Sentry.replay().startBuffering() }
                                .onFailure { Timber.w(it, "Replay buffering could not be started") }
                        SentryReplayPolicy.RuntimeAction.REPLAY_STOP ->
                            runCatching { io.sentry.Sentry.replay().stop() }
                                .onFailure { Timber.w(it, "Replay buffering could not be stopped") }
                        SentryReplayPolicy.RuntimeAction.NONE -> Unit
                    }
                    previousEnabled = settings.sentryEnabled
                }
            }
        } else {
            // FOSS build: Sentry disabled for F-Droid compliance
            Timber.i("FOSS build - Sentry disabled for F-Droid compliance")
        }
        // Start web remote control over LAN - fault-tolerant and only when
        // the setting is on (kill switch against the EADDRINUSE startup crash,
        // candidate REMOTE-EADDRINUSE-STARTUP). Skipped in safe mode
        // (diagnostics do not need a server).
        if (!safeMode) {
            applicationScope.launch {
                val remoteEnabled = runCatching {
                    settingsRepository.appSettingsFlow.first().remoteControlEnabled
                }.getOrDefault(true)
                if (!remoteEnabled) {
                    Timber.i("Web remote control disabled in settings - no server bind")
                    return@launch
                }
                runCatching { remoteControlServer.start() }
                    .onFailure { Timber.e(it, "Web remote control server could not be started") }
            }
        }
    }
}
