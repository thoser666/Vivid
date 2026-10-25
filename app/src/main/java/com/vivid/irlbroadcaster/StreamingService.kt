package com.vivid.irlbroadcaster

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vivid.R
import com.vivid.feature.chat.bot.ChatBotController
import com.vivid.feature.streaming.StreamingEngine
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.source.VideoSourceKind
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import com.vivid.feature.streaming.ReplayState
import com.vivid.core.data.SceneSourceType
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
 * Der Service ist bewusst dünn: Die gemeinsame Startvorbereitung baut die URLs und
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

    @Inject
    lateinit var chatBotController: ChatBotController

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var wakeLock: PowerManager.WakeLock? = null
    private var stopped = false
    private var stateJob: Job? = null
    private var projectionJob: Job? = null
    private var pendingProjection = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            StreamingServiceSupport.ACTION_START_STREAM -> {
                val urls = intent
                    .getStringArrayListExtra(StreamingServiceSupport.EXTRA_STREAM_URLS)
                    ?.orEmpty()
                    ?.map { it.trim() }
                    ?.filter { it.isNotEmpty() }
                    .orEmpty()
                if (urls.isEmpty()) {
                    Timber.w("StreamingService: START without stream target - stopping")
                    stopSelf()
                    return START_NOT_STICKY
                }
                if (!startAsForeground()) {
                    // Ohne laufenden Foreground-Service startet kein Stream: der
                    // mediaProjection-Typ fehlt (VIVID-39/#221) und jede
                    // Projektion danach stirbt — früher als AMS-"Unable to
                    // start service" sichtbar (#327).
                    streamingEngine.reportStreamStartFailure()
                    return START_NOT_STICKY
                }
                try {
                    if (streamingEngine.activeSourceKind.value == VideoSourceKind.CAMERA) {
                        streamingEngine.initializeCamera()
                    }
                    streamingEngine.startStream(urls)
                } catch (e: Exception) {
                    // VIVID-39 (#221) + #327: Hier landen sowohl die
                    // SecurityException (MediaProjection ohne FGS-Typ/ohne
                    // gültiges Token) als auch RuntimeException aus der
                    // RootEncoder-Vorbereitung. Ungetragen killt sie den Prozess
                    // und AMS meldet "Unable to start service"; deshalb wird der
                    // Fehler in den Engine-Zustand geschrieben (UI sieht Failed)
                    // und der Service räumt sauber auf.
                    Timber.e(e, "StreamingService: failed to start stream")
                    streamingEngine.reportStreamStartFailure()
                    teardown()
                    return START_NOT_STICKY
                }
                chatBotController.onStreamStarted()
                observeStreamState()
                scheduleStartupWatchdog()
            }

            StreamingServiceSupport.ACTION_GRANT_SCENE_PROJECTION -> acceptSceneProjection(intent)

            StreamingServiceSupport.ACTION_STOP_STREAM -> stopStreaming()

            // Prozess-Neustart ohne Intent: nur weiterlaufen, wenn wirklich gestreamt wird.
            else -> {
                if (streamingEngine.streamingState.value !is StreamingState.Streaming && !streamingEngine.sceneProjectionActive.value) {
                    chatBotController.onStreamStopped()
                    stopSelf()
                }
            }
        }
        return START_NOT_STICKY
    }

    /** Consent is consumed only after the mediaProjection foreground type is active. */
    private fun acceptSceneProjection(intent: Intent) {
        val sourceId = intent.getStringExtra(StreamingServiceSupport.EXTRA_SCENE_SOURCE_ID)
        val result = intent.getIntExtra(StreamingServiceSupport.EXTRA_PROJECTION_RESULT, android.app.Activity.RESULT_CANCELED)
        val data = androidx.core.content.IntentCompat.getParcelableExtra(intent,
            StreamingServiceSupport.EXTRA_PROJECTION_DATA, Intent::class.java)
        if (sourceId == null || data == null || result != android.app.Activity.RESULT_OK ||
            streamingEngine.sceneComposition.value?.sources?.none {
                it.id == sourceId && it.type == SceneSourceType.SCREEN_CAPTURE
            } != false) {
            if (!streamingEngine.sceneProjectionActive.value && streamingEngine.streamingState.value is StreamingState.Idle) stopService()
            return
        }
        pendingProjection = true
        try {
            if (!startAsForeground(stopOnFailure = wakeLock?.isHeld != true)) return
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
            val projection = manager.getMediaProjection(result, data)
                ?: error("MediaProjection consent produced no grant")
            streamingEngine.grantSceneProjection(sourceId, projection)
        } catch (failure: RuntimeException) {
            Timber.w(failure, "StreamingService: scene capture authorization failed")
        } finally {
            pendingProjection = false
            observeSceneProjection()
        }
    }

    private fun observeSceneProjection() {
        projectionJob?.cancel()
        projectionJob = serviceScope.launch {
            combine(streamingEngine.sceneProjectionActive, streamingEngine.streamingState,
                streamingEngine.replayState) { projection, stream, replay -> Triple(projection, stream, replay) }
                .collect { (projection, stream, replay) ->
                    if (!projection && stream is StreamingState.Idle && replay !is ReplayState.Recording) {
                        stopService()
                    } else updateNotification(stream)
                }
        }
    }

    /**
     * Startet den Service als Foreground mit persistenter Notification + WakeLock.
     *
     * Die FGS-Typen microphone/camera erfordern ab Android 14/15 die jeweilige
     * Runtime-Permission (RECORD_AUDIO/CAMERA) — ohne sie crasht startForeground()
     * mit SecurityException. Der Go-Live-Flow fragt die Berechtigungen zwar vorher
     * an, aber als Defense-in-Depth (z. B. Revoke in den Einstellungen während die
     * App im Hintergrund ist) werden die Typen nur mit erteilter Berechtigung gesetzt
     * und ein fehlgeschlagener Start beendet den Service sauber statt zu crashen.
     *
     * @return true, wenn der Foreground-Zustand steht. false heißt: kein Stream
     *   starten (das Flag/der Typ ist nicht durchsetzbar, jede Projektion würde
     *   ohnehin scheitern) — der Aufrufer meldet den Fehler und beendet sich.
     */
    private fun startAsForeground(stopOnFailure: Boolean = true): Boolean {
        createNotificationChannel()
        val type = foregroundServiceType()
        // API 34+: Ist der Manifest-Typ-FGS deklariert, darf startForeground() nicht
        // mit Type 0 aufgerufen werden (MissingForegroundServiceTypeException).
        // Ohne jede erteilte FGS-Berechtigung gar nicht erst starten.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && type == 0) {
            Timber.w("StreamingService: FGS type permissions missing - stream not started")
            if (stopOnFailure) teardown()
            return false
        }
        val notification = buildNotification(streamingEngine.streamingState.value)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(StreamingServiceSupport.NOTIFICATION_ID, notification, type)
            } else {
                startForeground(StreamingServiceSupport.NOTIFICATION_ID, notification)
            }
            stopped = false
            acquireWakeLock()
            true
        } catch (e: SecurityException) {
            // z. B. RECORD_AUDIO/CAMERA zwischen Go-Live und Start entzogen.
            Timber.e(e, "StreamingService: startForeground denied (FGS type permission?)")
            if (stopOnFailure) teardown()
            false
        }
    }

    /**
     * FGS-Typ für Kamera + Mikrofon, abhängig von der erteilten Runtime-Permission:
     * - microphone: Android 14+ verlangt RECORD_AUDIO (vorher bedingungslos)
     * - camera: Android 14+ verlangt CAMERA (vorher bedingungslos)
     * - mediaProjection: nur bei aktiver Screen-Capture-Quelle (VIVID-39, #221);
     *   die Permission ist normal (Installzeit), die Entscheidung hängt an der
     *   aktiven Videoquelle (siehe [StreamingServiceSupport.requiresMediaProjectionFgs]).
     * Auf älteren Plattformen sind die Bits unkritisch und werden immer gesetzt.
     */
    private fun foregroundServiceType(): Int {
        var type = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11–13: Typ unkritisch (keine Runtime-Permission-Pflicht).
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (hasPermission(Manifest.permission.CAMERA)) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        // VIVID-39 (#221): Die Screen-Capture-Quelle streamt über
        // MediaProjection.createVirtualDisplay() — Android 14+ verlangt dafür
        // einen laufenden FGS vom Typ mediaProjection (sonst SecurityException,
        // die den Service als "Unable to start service" crasht).
        // FOREGROUND_SERVICE_MEDIA_PROJECTION ist eine normale Permission
        // (Installzeit erteilt) — kein Runtime-Check wie bei camera/microphone.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            StreamingServiceSupport.requiresMediaProjectionFgs(
                if (streamingEngine.sceneComposition.value == null) streamingEngine.activeSourceKind.value else VideoSourceKind.CAMERA,
                pendingProjection || streamingEngine.sceneProjectionForegroundRequired())
        ) {
            type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        }
        return type
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    /** Aktualisiert die Notification, sobald sich der Engine-Status ändert. */
    private fun observeStreamState() {
        stateJob?.cancel()
        stateJob = serviceScope.launch {
            var previous: StreamingState = StreamingState.Idle
            streamingEngine.streamingState.collect { state ->
                // Remote-Controlled-Stopp (z. B. Web-Remote-Control / OBS) beendet den Service.
                if (state is StreamingState.Idle && previous !is StreamingState.Idle && !stopped) {
                    // Preview capture may keep the service alive after a remote stream stop.
                    // The chat bot still follows the stream, rather than the capture grant.
                    chatBotController.onStreamStopped()
                    if (streamingEngine.sceneProjectionActive.value) updateNotification(state)
                    else stopService()
                } else {
                    updateNotification(state)
                }
                previous = state
            }
        }
    }

    /** Aktualisiert die bereits gezeigte Notification mit dem neuen Status. */
    private fun updateNotification(state: StreamingState) {
        // Auf Android 13+ braucht notify() die POST_NOTIFICATIONS-Berechtigung
        // (auf Android ≤12 existiert sie nicht — der Guard greift dort gar nicht).
        // startForeground() selbst ist davon ausgenommen (FGS-Notification erscheint
        // auch ohne die Berechtigung); nur dieses Update hier wird übersprungen,
        // falls der Nutzer die Berechtigung verweigert hat.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
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
            if (streamingEngine.streamingState.value is StreamingState.Idle && !stopped && !streamingEngine.sceneProjectionActive.value) {
                Timber.w("StreamingService: no stream start after 15s - stopping")
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
        projectionJob?.cancel()
        projectionJob = null
        streamingEngine.stopSceneProjections()
        stateJob?.cancel()
        stateJob = null
        chatBotController.onStreamStopped()
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        streamingEngine.stopSceneProjections()
        serviceScope.cancel()
        chatBotController.onStreamStopped()
        releaseWakeLock()
        super.onDestroy()
    }

    // Bewusst KEIN Timeout: Die Freigabe ist an den Stream-Lifecycle gebunden
    // (teardown()/onDestroy()); ein künstliches Timeout würde bei Langzeit-Streams
    // mitten im Stream verfallen → CPU-Schlaf → Aussetzer. Das OS gibt den
    // WakeLock bei Prozess-Tod automatisch frei.
    @SuppressLint("WakelockTimeout")
    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "Vivid:Streaming",
        ).apply {
            // Kein künstliches Timeout: Die Freigabe ist an den Stream-Lifecycle
            // gebunden (teardown()/onDestroy()), und das OS gibt den WakeLock
            // automatisch frei, wenn der Prozess stirbt. Ein Timeout würde bei
            // Streams über 6h mitten im Stream verfallen → CPU-Schlaf → Aussetzer.
            setReferenceCounted(false)
            acquire()
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
            getString(StreamingServiceSupport.CHANNEL_NAME_RES),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.notif_channel_desc)
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
        // notif_text_failed enthält einen %1$s-Platzhalter für die technische
        // Fehlerursache; bei anderen Stati werden die überzähligen Argumente
        // von String.format ignoriert.
        val failureReason = StreamingServiceSupport.failureText(state, ::getString)
        val previewCapture = state is StreamingState.Idle && (pendingProjection || streamingEngine.sceneProjectionActive.value)
        return NotificationCompat.Builder(this, StreamingServiceSupport.CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(if (previewCapture) R.string.notif_scene_capture_title else StreamingServiceSupport.notificationTitleRes(state)))
            .setContentText(getString(if (previewCapture) R.string.notif_scene_capture_text else StreamingServiceSupport.notificationTextRes(state), failureReason))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .apply {
                if (StreamingServiceSupport.showStopAction(state) || pendingProjection || streamingEngine.sceneProjectionActive.value) {
                    addAction(
                        NotificationCompat.Action.Builder(
                            android.R.drawable.ic_media_pause,
                            getString(R.string.notif_stop_action),
                            stopIntent,
                        ).build(),
                    )
                }
            }
            .build()
    }

    companion object {
        fun grantSceneProjection(context: Context, sourceId: String, resultCode: Int, data: Intent) {
            val intent = Intent(context, StreamingService::class.java)
                .setAction(StreamingServiceSupport.ACTION_GRANT_SCENE_PROJECTION)
                .putExtra(StreamingServiceSupport.EXTRA_SCENE_SOURCE_ID, sourceId)
                .putExtra(StreamingServiceSupport.EXTRA_PROJECTION_RESULT, resultCode)
                .putExtra(StreamingServiceSupport.EXTRA_PROJECTION_DATA, data)
            ContextCompat.startForegroundService(context, intent)
        }

        /** Startet den Service und streamt die [urls] (aus dem Vordergrund aufrufen). */
        fun start(context: Context, urls: List<String>) {
            val intent = Intent(context, StreamingService::class.java)
                .setAction(StreamingServiceSupport.ACTION_START_STREAM)
                .putStringArrayListExtra(StreamingServiceSupport.EXTRA_STREAM_URLS, ArrayList(urls))
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
