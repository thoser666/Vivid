package com.vivid.irlbroadcaster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vivid.R
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

/**
 * Hält den Livestream am Leben, wenn die App im Hintergrund ist:
 *
 * - **Foreground-Service** → der Prozess wird nicht als Kandidat für den
 *   Low-Memory-Killer eingestuft, solange der Stream läuft.
 * - **PARTIAL_WAKE_LOCK** → CPU bleibt auch bei ausgeschaltetem Display aktiv.
 * - **Persistente Notification** mit Stop-Aktion, aktualisiert auf den
 *   Engine-Status (Preparing → Streaming/Failed).
 *
 * Der Service ist bewusst dünn: Die URL wird vom [StreamingViewModel] gebaut und
 * per Intent-Extra übergeben, der eigentliche Stream läuft über die Singleton-
 * [StreamingEngine] (gleiche Instanz wie im UI).
 *
 * Einschränkung: Die Kamera-Preview wird von der Activity erzeugt. Wenn die
 * Activity zerstört wird (z. B. Wischen aus den Recents), stirbt auch die
 * Preview-Surface und damit der Stream — im reinen Hintergrund-Fall
 * (Home-Taste, Bildschirm aus) bleibt er aktiv.
 */
@AndroidEntryPoint
class StreamingService : Service() {

    @Inject
    lateinit var streamingEngine: StreamingEngine

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wakeLock: PowerManager.WakeLock? = null
    private var stopped = false
    private var stateJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            StreamingServiceSupport.ACTION_START_STREAM -> {
                val url = intent.getStringExtra(StreamingServiceSupport.EXTRA_STREAM_URL)
                if (url.isNullOrBlank()) {
                    Timber.w("StreamingService: START ohne URL — stoppe")
                    stopSelf()
                    return START_NOT_STICKY
                }
                startAsForeground()
                streamingEngine.startStream(url)
                observeStreamState()
                scheduleStartupWatchdog()
            }

            StreamingServiceSupport.ACTION_STOP_STREAM -> stopStreaming()

            // Prozess-Neustart ohne Intent: nur weiterlaufen, wenn wirklich gestreamt wird.
            else -> {
                if (streamingEngine.streamingState.value !is StreamingState.Streaming) {
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Startet den Service als Foreground mit persistenter Notification + WakeLock. */
    private fun startAsForeground() {
        createNotificationChannel()
        val notification = buildNotification(streamingEngine.streamingState.value)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(StreamingServiceSupport.NOTIFICATION_ID, notification, foregroundServiceType())
        } else {
            startForeground(StreamingServiceSupport.NOTIFICATION_ID, notification)
        }
        acquireWakeLock()
    }

    /**
     * FGS-Typ für Kamera + Mikrofon. Die Typen sind bitmaskiert; ältere Plattformen
     * ignorieren unbekannte Bits, daher reicht die API-Level-Abfrage.
     */
    private fun foregroundServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        return type
    }

    /** Aktualisiert die Notification, sobald sich der Engine-Status ändert. */
    private fun observeStreamState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            var previous: StreamingState = StreamingState.Idle
            streamingEngine.streamingState.collect { state ->
                // Remote-Controlled-Stopp (z. B. Web-Remote-Control / OBS) beendet den Service.
                if (state is StreamingState.Idle && previous !is StreamingState.Idle && !stopped) {
                    stopService()
                } else {
                    updateNotification(state)
                }
                previous = state
            }
        }
    }

    /** Aktualisiert die bereits gezeigte Notification mit dem neuen Status. */
    private fun updateNotification(state: StreamingState) {
        NotificationManagerCompat.from(this)
            .notify(StreamingServiceSupport.NOTIFICATION_ID, buildNotification(state))
    }

    /**
     * Falls der Start nicht gegriffen hat (z. B. Kamera nicht initialisiert), soll
     * der Service nicht endlos mit „Vorbereitung"-Notification hängen bleiben.
     */
    private fun scheduleStartupWatchdog() {
        serviceScope.launch {
            delay(15_000)
            if (streamingEngine.streamingState.value is StreamingState.Idle && !stopped) {
                Timber.w("StreamingService: nach 15s kein Stream-Start — stoppe")
                stopService()
            }
        }
    }

    private fun stopStreaming() {
        stopped = true
        streamingEngine.stopStream()
        teardown()
    }

    /** Beendet den Foreground-Zustand, gibt WakeLock frei und stoppt den Service. */
    private fun stopService() {
        stopped = true
        teardown()
    }

    private fun teardown() {
        stateJob?.cancel()
        stateJob = null
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vivid:Streaming",
        ).apply {
            setReferenceCounted(false)
            acquire(6 * 60 * 60 * 1000L) // max. 6h, danach muss neu gestartet werden
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            StreamingServiceSupport.CHANNEL_ID,
            StreamingServiceSupport.CHANNEL_NAME,
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Zeigt den Live-Stream-Status, solange Vivid sendet."
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(state: StreamingState): Notification {
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StreamingService::class.java)
                .setAction(StreamingServiceSupport.ACTION_STOP_STREAM),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val contentIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, StreamingServiceSupport.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(StreamingServiceSupport.notificationTitle(state))
            .setContentText(StreamingServiceSupport.notificationText(state))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (StreamingServiceSupport.showStopAction(state)) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            android.R.drawable.ic_media_pause,
                            "Stream beenden",
                            stopIntent,
                        ).build(),
                    )
                }
            }
            .build()
    }

    companion object {
        /** Startet den Service und streamt [url] (aus dem Vordergrund aufrufen). */
        fun start(context: Context, url: String) {
            val intent = Intent(context, StreamingService::class.java)
                .setAction(StreamingServiceSupport.ACTION_START_STREAM)
                .putExtra(StreamingServiceSupport.EXTRA_STREAM_URL, url)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Stoppt den Service (und damit den Stream). */
        fun stop(context: Context) {
            val intent = Intent(context, StreamingService::class.java)
                .setAction(StreamingServiceSupport.ACTION_STOP_STREAM)
            context.startService(intent)
        }
    }
}
