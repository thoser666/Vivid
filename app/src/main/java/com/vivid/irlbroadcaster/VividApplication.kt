package com.vivid.irlbroadcaster

import android.app.Application
import com.vivid.core.remote.RemoteControlServer
import dagger.hilt.android.HiltAndroidApp
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

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        // Web-Remote-Control über LAN starten — fehlertolerant, damit ein
        // Port-Konflikt die App nicht crashen lässt.
        applicationScope.launch {
            runCatching { remoteControlServer.start() }
                .onFailure { Timber.e(it, "Web-Remote-Control-Server konnte nicht gestartet werden") }
        }
    }
}
