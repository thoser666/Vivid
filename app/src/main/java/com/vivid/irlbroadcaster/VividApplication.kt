package com.vivid.irlbroadcaster

import android.app.Application
import com.vivid.core.data.SettingsRepository
import com.vivid.core.remote.RemoteControlServer
import dagger.hilt.android.HiltAndroidApp
import io.sentry.SentryOptions
import io.sentry.android.core.SentryAndroid
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class VividApplication : Application() {

    @Inject
    lateinit var remoteControlServer: RemoteControlServer

    @Inject
    lateinit var settingsRepository: SettingsRepository

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * Aktueller Sentry-Opt-out-Stand für [SentryOptions.BeforeSendCallback]
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
            options.beforeSend = SentryOptions.BeforeSendCallback { event, _ ->
                if (sentryEnabled) event else null
            }
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
