package com.vivid.feature.streaming

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import java.io.File
import com.pedro.common.ConnectChecker
import com.pedro.library.base.Camera2Base
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.utils.gl.AspectRatioMode
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiType
import com.pedro.common.VideoCodec
import com.pedro.library.view.GlStreamInterface
import android.media.MediaCodecInfo
import android.media.MediaCodec
import com.pedro.library.base.recording.RecordController
import com.pedro.library.util.AndroidMuxerRecordController
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.SystemClock
import com.vivid.core.data.AdaptiveBitrateConfig
import com.vivid.core.data.AdaptiveBitrateController
import com.vivid.core.data.PrivacyZone
import com.vivid.core.data.ResolvedEncoderConfig
import com.vivid.core.data.AndroidEncoderCapabilities
import com.vivid.core.data.EncoderCapabilities
import com.vivid.core.data.VideoCodecPreference
import com.vivid.feature.streaming.source.DisplayFactory
import com.vivid.feature.streaming.source.PlayerFactory
import com.vivid.feature.streaming.source.ReplayVideoSource
import com.vivid.feature.streaming.source.ScreenCaptureVideoSource
import com.vivid.feature.streaming.source.VideoPlayerVideoSource
import com.vivid.feature.streaming.source.VideoSourceKind
import com.vivid.feature.streaming.source.VideoSourceRegistry
import timber.log.Timber
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Mappt eine quellrelative Zone (Normalisierung 0..1, (0,0) oben links — UI-
 * Konvention) auf die Textur-Koordinaten des Privacy-Shaders ((0,0) unten
 * links, GL-Konvention): nur Y wird gespiegelt, X und die Radien bleiben.
 */
internal fun PrivacyZone.toPrivacyEllipse(): PrivacyEllipse = PrivacyEllipse(
    centerX = centerX,
    centerY = 1f - centerY,
    radiusX = radiusX,
    radiusY = radiusY,
)

/** Status eines einzelnen Stream-Ziels (Multi-Streaming). */
enum class StreamTargetStatus {
    IDLE,
    PREPARING,
    STREAMING,
    FAILED,
}

/** Zustand eines einzelnen Stream-Ziels inkl. URL und ggf. Fehlerursache. */
data class StreamTargetState(
    val url: String,
    val status: StreamTargetStatus = StreamTargetStatus.IDLE,
    val failureReason: String? = null,
    /** Gemessene Sendebitrate dieser Verbindung (kbps), null = unbekannt. */
    val bitrateKbps: Int? = null,
)

// Ein Interface, das es uns erlaubt, die Kameraerstellung zu mocken.
// Pro Ziel wird ein ConnectChecker übergeben (Reihenfolge = Ziel-Index).
interface CameraFactory {
    fun create(connectCheckers: List<ConnectChecker>): MultiCamera2
}

// Die echte Implementierung für die App
class RtmpCamera2Factory @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraFactory {
    override fun create(connectCheckers: List<ConnectChecker>): MultiCamera2 {
        // Context-Konstruktor statt View-Konstruktor: RootEncoder baut dann eine
        // eigene GL-Pipeline (GlStreamInterface) ohne Activity-View auf. Die
        // Kamera-Vorschau wird separat über attachPreview(Surface) angehängt —
        // so überlebt der Stream die Zerstörung der Activity (Recents-Wischen),
        // weil der Encoder nicht an der Preview-Surface hängt.
        // MultiCamera2 verwaltet einen ConnectChecker pro Ziel (MVP: max. 2);
        // nicht genutzte Protokolle werden mit leeren Arrays deaktiviert.
        // (Verifiziert an RootEncoder 2.7.5 per Bytecode + Maintainer-Doku.)
        return MultiCamera2(
            context,
            connectCheckers.toTypedArray(), // rtmp
            emptyArray(), // rtsp
            emptyArray(), // srt
            emptyArray(), // udp
        )
    }
}

@Singleton // Die Engine sollte ein Singleton sein, da sie die Kamera steuert
class StreamingEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cameraFactory: CameraFactory, // <-- WIR INJIZIEREN EINE FACTORY
    private val displayFactory: DisplayFactory, // <-- S2: Screen-Capture-Encoder
    private val playerFactory: PlayerFactory, // <-- S3: Video-Datei-Encoder
    private val videoSourceRegistry: VideoSourceRegistry, // <-- S1: Source-Abstraktion
) {
    private var camera: MultiCamera2? = null
    private val keyframeController = KeyframeIntervalController()
    private val _measuredKeyframeIntervalMs = MutableStateFlow<Long?>(null)
    val measuredKeyframeIntervalMs: StateFlow<Long?> = _measuredKeyframeIntervalMs.asStateFlow()
    private var idlePreviewCamera: Camera2ApiManager? = null
    private var idlePreviewSurface: Surface? = null
    private var idlePreviewSize: Pair<Int, Int>? = null
    internal var idlePreviewFactory: (Context) -> Camera2ApiManager = ::Camera2ApiManager

    /** Gemerkte Encoder-Konfiguration (null = Legacy-Pfad, RootEncoder-Default). */
    private val _encoderConfig = MutableStateFlow<ResolvedEncoderConfig?>(null)
    private val _encoderAutoFallback = MutableStateFlow(true)
    private val _activeEncoder = MutableStateFlow<ResolvedEncoderConfig?>(null)
    private val _measuredEncoderFps = MutableStateFlow<Int?>(null)
    val measuredEncoderFps: StateFlow<Int?> = _measuredEncoderFps.asStateFlow()
    internal var encoderCapabilities: EncoderCapabilities = AndroidEncoderCapabilities()
    internal var captureCapabilities: (Camera2Base) -> CameraCaptureCapabilities = {
        AndroidCameraCaptureCapabilities(it.cameraCharacteristics)
    }

    /** Adaptive Bitrate (v0.6.0): Zielbitrate an gemessene Strecke anpassen. */
    private val _adaptiveBitrateEnabled = MutableStateFlow(false)
    private var adaptiveController: AdaptiveBitrateController? = null
    private var lastAdaptiveSampleMs = 0L

    /** Zeitquelle (injektierbar fuer Tests; Realzeit im Betrieb). */
    internal var timeSource: () -> Long = { SystemClock.elapsedRealtime() }

    /** S2: Screen-Capture-Quelle (MediaProjection), lazy erzeugt (wie die Kamera). */
    private var screenCaptureSource: ScreenCaptureVideoSource? = null

    /** S3: Video-Datei-Quelle (MultiFromFile), lazy erzeugt (wie die Kamera). */
    private var videoPlayerSource: VideoPlayerVideoSource? = null

    /** Replay-als-Quelle (MultiFromFile, Loop-Modus), lazy erzeugt (wie die Kamera). */
    private var replaySource: ReplayVideoSource? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _targetStates = MutableStateFlow<List<StreamTargetState>>(emptyList())

    /** Zustand jedes einzelnen Stream-Ziels (URL, Status, Fehlerursache). */
    val targetStates: StateFlow<List<StreamTargetState>> = _targetStates.asStateFlow()

    private val _focusMode = MutableStateFlow(FocusMode.AUTO)
    val focusMode: StateFlow<FocusMode> = _focusMode.asStateFlow()

    private var focusController: CameraFocusController? = null

    private val _stabilizationEnabled = MutableStateFlow(false)
    val stabilizationEnabled: StateFlow<Boolean> = _stabilizationEnabled.asStateFlow()

    private val _torchEnabled = MutableStateFlow(false)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    // Video-Effekte: OpenGL-Filter (Graustufen, Sepia, Rauschen, …)
    private val filterController = VideoFilterController()

    /** Der aktuell aktive Video-Filter. */
    val activeFilter: StateFlow<VideoFilter> = filterController.activeFilter

    // Low-Light-Boost: software-basierte Helligkeitsanhebung (1.5x Gain)
    private val lowLightBoostController = LowLightBoostController()

    /** Ob der Low-Light-Boost aktiv ist. */
    val lowLightBoostEnabled: StateFlow<Boolean> = lowLightBoostController.enabled

    // Color-Spaces + 3D-LUTs: Farbraum-Auswahl und LUT-Presets
    private val lutController = LutController()

    /**
     * Zentraler Filter-Ketten-Composer (P0 der Anonymisierungs-Skizze,
     * docs/architecture/privacy-anonymization.md §4): baut die GL-Filterkette
     * kanonisch aus dem Soll-Zustand der vier Komponenten wieder auf —
     * Anonymisierung an Position 0, kreative Filter kombinierbar (behebt die
     * Single-Slot-Exklusivität der drei Bestands-Controller). Die
     * Applier-Lambdas der Filter-APIs rufen nur noch den Rebuild.
     */
    private val privacyComposer = PrivacyComposer(
        filterController = filterController,
        lowLightBoostController = lowLightBoostController,
        lutController = lutController,
        lutSize = LUT_SIZE,
    )

    /** Der aktive LUT-Preset. */
    val activeLutPreset: StateFlow<LutPreset> = lutController.activePreset

    /** Der aktive Color-Space. */
    val activeColorSpace: StateFlow<ColorSpace> = lutController.activeColorSpace

    /**
     * S1 (Source-Abstraktion): die aktuell aktive Videoquelle der Engine.
     * Die Engine spricht nur noch die Registry an statt hart „die Kamera“ —
     * Screen-Capture/Video-Player-Quellen docken hier ohne Engine-Umbau an.
     */
    val activeSourceKind: StateFlow<VideoSourceKind> = videoSourceRegistry.activeKind

    private var cameraControls: CameraControls? = null
    private var stabilizationController: CameraStabilizationController? = null
    private var replayController: ReplayController? = null

    /** Audio-Modus, mit dem der aktuelle [replayController] erzeugt wurde. */
    private var lastReplayIncludeAudio: Boolean? = null
    private val _replayState = MutableStateFlow<ReplayState>(ReplayState.Idle)

    /** Zustand der lokalen MP4-Replay-Aufnahme. */
    val replayState: StateFlow<ReplayState> = _replayState.asStateFlow()

    // --- Manuelle Kamera-Steuerung ---
    private var manualCameraController: ManualCameraController? = null

    private fun activeGlInterface(): GlStreamInterface? = when (activeSourceKind.value) {
        VideoSourceKind.CAMERA -> camera?.glInterface as? GlStreamInterface
        VideoSourceKind.SCREEN_CAPTURE -> screenCaptureSource?.glInterface
        VideoSourceKind.VIDEO_PLAYER -> videoPlayerSource?.glInterface
        VideoSourceKind.REPLAY -> replaySource?.glInterface
    }

    /**
     * Wendet einen Video-Filter (OpenGL-Effekt) auf die GL-Pipeline an.
     * Der Effekt wirkt auf Vorschau + Encoder (gestreamtes Video).
     *
     * @return true, wenn der Filter erfolgreich gesetzt wurde.
     */
    fun setVideoFilter(filter: VideoFilter): Boolean {
        val gl = activeGlInterface() ?: return false
        return filterController.setFilter(filter) { _ ->
            privacyComposer.requestRebuild(gl)
        }
    }

    /** Wechselt zum nächsten Filter in der Liste (zirkulär). */
    fun nextVideoFilter(): VideoFilter {
        val gl = activeGlInterface() ?: return filterController.activeFilter.value
        return filterController.nextFilter { _ ->
            privacyComposer.requestRebuild(gl)
        }
    }

    /** Setzt den Filter-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetVideoFilter() {
        filterController.resetFilterState()
        privacyComposer.requestRebuild(activeGlInterface())
    }

    /**
     * Schaltet den Low-Light-Boost um (Helligkeitsanhebung via OpenGL-Brightness-Filter).
     * Der Boost funktioniert auf allen Videoquellen (Kamera, Screen-Capture, Video-Player).
     *
     * @return true, wenn der Boost jetzt aktiv ist.
     */
    fun toggleLowLightBoost(): Boolean {
        val gl = activeGlInterface() ?: return false
        return lowLightBoostController.toggle { _ ->
            privacyComposer.requestRebuild(gl)
        }
    }

    /** Setzt den Low-Light-Boost-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetLowLightBoost() {
        lowLightBoostController.resetState()
        privacyComposer.requestRebuild(activeGlInterface())
    }

    /**
     * Setzt den 3D-LUT-Preset (Color-Spaces + 3D-LUTs Roadmap-Bucket).
     * Die Filter wirken auf den Encoder-Pfad (Vorschau + gestreamtes Video).
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setLutPreset(preset: LutPreset): Boolean {
        val gl = activeGlInterface() ?: return false
        return lutController.setPreset(preset, LUT_SIZE) { _ ->
            privacyComposer.requestRebuild(gl)
        }
    }

    /**
     * Setzt den Color-Space (ändert die Gamma-Korrektur des LUT-Filters).
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setColorSpace(colorSpace: ColorSpace): Boolean {
        val gl = activeGlInterface() ?: return false
        return lutController.setColorSpace(colorSpace, LUT_SIZE) { _ ->
            privacyComposer.requestRebuild(gl)
        }
    }

    /** Setzt den LUT-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetLut() {
        lutController.resetState()
        privacyComposer.requestRebuild(activeGlInterface())
    }

    // --- Datenschutz-Anonymisierung (P0, Skizze §5) ---

    /** Ob die Anonymisierung aktiv ist (Position 0 in der Filterkette). */
    val privacyEnabled: StateFlow<Boolean> = privacyComposer.privacyEnabled

    /**
     * Schaltet die Anonymisierung um (P0: programmatisch — Zonen-UI folgt in
     * P1, BlazeFace-Producer in P2).
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setPrivacyEnabled(enabled: Boolean): Boolean {
        val gl = activeGlInterface() ?: return false
        return privacyComposer.setPrivacyEnabled(enabled, gl)
    }

    /**
     * Ersetzt die Ellipsen-Zonen (P0: programmatisch; framesynchron über
     * Uniform-Update — kein Ketten-Rebuild, solange der Privacy-Render in
     * der Kette ist).
     */
    fun setPrivacyEllipses(ellipses: List<PrivacyEllipse>) {
        privacyComposer.setEllipses(ellipses)
    }

    // --- P1: Manuelle Zonen (Soll-Zustand, Skizze §5) ----------------------

    /**
     * Soll-Zustand der manuellen Zonen aus der Persistenz (ZoneRepository).
     * Wird beim Stream-Start und bei jedem Quellwechsel auf die aktive Quelle
     * angewendet (mappen zu Textur-Koordinaten: quellrelativ (0,0) oben links,
     * Y-Flip zum Shader-Textur-Raum) — verzögert bis die GL-Pipeline läuft
     * (Idle-Pfade sind bewusst No-Ops). Der Composer-Controller bleibt
     * zustandsfrei — hier liegt die einzige Zonen-Quelle.
     */
    private val desiredPrivacyZones = MutableStateFlow<List<PrivacyZone>>(emptyList())

    /**
     * Ersetzt den Soll-Zustand der Zonen und wendet ihn sofort an (beim
     * Laufzeit-Edit des Zonen-Editors). Ohne Kamera/GL bleibt der Aufruf ein
     * No-Op (der Zustand wird beim Stream-Start nachgeholt).
     */
    fun setPrivacyZones(zones: List<PrivacyZone>) {
        desiredPrivacyZones.value = zones.take(PrivacyZone.MAX_ZONES)
        applyPrivacyZones(desiredPrivacyZones.value)
    }

    /**
     * Mappt den Soll-Zustand auf die Privacy-Ellipsen (Textur-Koordinaten,
     * Y-Flip) und übergibt sie dem Composer. Ohne GL-Interface ein No-Op —
     * der Aufruf ist dann beim Stream-Start/Quellwechsel nachgeholt.
     */
    private fun applyPrivacyZones(zones: List<PrivacyZone>) {
        val gl = activeGlInterface() ?: return
        privacyComposer.setEllipses(zones.map { it.toPrivacyEllipse() })
        // Der Toggle-Zustand liegt im Composer; ein Rebuild stellt sicher,
        // dass die Kette den Soll-Zustand (inkl. neuer Zonen) trägt.
        privacyComposer.requestRebuild(gl)
    }

    companion object {
        private const val ENCODER_PREPARATION_ERROR = "Failed to prepare audio/video"

        /**
         * Maximale Anzahl paralleler Stream-Ziele (MVP: primär + 1 sekundär).
         *
         * RootEncoder legt pro Ziel einen RTMP-Client an — eine Erweiterung auf
         * mehr Ziele erfordert eine Neuerstellung der [MultiCamera2] mit mehr
         * ConnectCheckern (Kamera darf dabei nicht neu gestartet werden).
         */
        const val MAX_STREAM_TARGETS = 2

        /** Mindest-Intervall zwischen adaptiven Bitraten-Samples. */
        private const val ADAPTIVE_SAMPLE_INTERVAL_MS = 2_000L

        /** Untergrenze der adaptiven Zielbitrate (kbps). */
        private const val ADAPTIVE_MIN_BITRATE_KBPS = 1_000

        /** Legacy-Fallback-Bitrate (kbps) ohne konfiguriertes Preset. */
        private const val DEFAULT_VIDEO_BITRATE_KBPS = 6_000

        /** LUT-Größe: 16×16×16 = 4096 Einträge (gute Balance aus Qualität und Performance). */
        const val LUT_SIZE = 16
    }

    /** Preview-Surface der Activity, verwendet von idle Camera2 oder der Streaming-GL-Pipeline. */
    private data class PreviewRequest(val surface: Surface, val width: Int, val height: Int, val rotationDegrees: Int)

    // Die zuletzt gemeldete Preview-Surface. Im Leerlauf direkt an Camera2,
    // beim Stream-Start oder nach Rotation an die laufende GL-Pipeline angebunden.
    private var previewRequest: PreviewRequest? = null
    private var glPreviewSurface: Surface? = null
    private var encoderRotationDegrees = 90


    /**
     * Erstellt einen ConnectChecker für ein Stream-Ziel. Jeder Checker kennt
     * seinen Ziel-Index und aktualisiert nur den eigenen Eintrag in
     * [targetStates] — der Gesamt-Status wird danach aggregiert.
     */
    private fun createTargetChecker(index: Int): ConnectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            updateTarget(index) { it.copy(status = StreamTargetStatus.PREPARING) }
        }

        override fun onConnectionSuccess() {
            updateTarget(index) {
                it.copy(status = StreamTargetStatus.STREAMING, failureReason = null)
            }
        }

        override fun onConnectionFailed(reason: String) {
            updateTarget(index) {
                it.copy(status = StreamTargetStatus.FAILED, failureReason = reason)
            }
            // Nur das fehlgeschlagene Ziel stoppen — andere Ziele streamen weiter.
            camera?.stopStream(MultiType.RTMP, index)
        }

        override fun onNewBitrate(bitrate: Long) {
            // RootEncoder reports bits/s; application state and adaptive control use kbps.
            val measuredKbps = (bitrate / 1_000L).coerceIn(0L, Int.MAX_VALUE.toLong()).toInt()
            updateTarget(index) { it.copy(bitrateKbps = measuredKbps) }
            // Adaptive Steuerung: nur vom ersten Ziel sampeln — der
            // Encoder ist geteilt, alle Ziele sehen dieselbe Bitrate.
            if (index == 0) sampleAdaptiveBitrate(measuredKbps.toLong())
        }

        override fun onDisconnect() {
            updateTarget(index) {
                it.copy(status = StreamTargetStatus.IDLE, failureReason = null)
            }
        }

        override fun onAuthError() {
            updateTarget(index) {
                it.copy(status = StreamTargetStatus.FAILED, failureReason = "RTMP Auth Error")
            }
        }

        override fun onAuthSuccess() {
            // Optional: Handle auth success
        }
    }

    /** Aktualisiert den Ziel-Eintrag [index] und aggregiert danach den Gesamt-Status. */
    private fun updateTarget(index: Int, transform: (StreamTargetState) -> StreamTargetState) {
        _targetStates.value = _targetStates.value.mapIndexed { i, state ->
            if (i == index) transform(state) else state
        }
        recomputeStreamingState()
    }

    /**
     * Aggregiert den Gesamt-Status aus den Ziel-Zuständen:
     * - Streaming, sobald irgendein Ziel streamt
     * - sonst Preparing, sobald sich irgendein Ziel vorbereitet
     * - sonst Failed (mit Ursache des ersten fehlgeschlagenen Ziels)
     * - sonst Idle
     */
    private fun recomputeStreamingState() {
        val states = _targetStates.value
        _streamingState.value = when {
            states.any { it.status == StreamTargetStatus.STREAMING } -> StreamingState.Streaming
            states.any { it.status == StreamTargetStatus.PREPARING } -> StreamingState.Preparing
            else -> {
                val failed = states.firstOrNull { it.status == StreamTargetStatus.FAILED }
                if (failed != null) {
                    StreamingState.Failed(failed.failureReason ?: "unknown error")
                } else {
                    StreamingState.Idle
                }
            }
        }
    }

    /**
     * S2: wechselt die aktive Videoquelle über die [VideoSourceRegistry].
     *
     * Die Kamera ist immer verfügbar. Screen-Capture (S2) wird beim ersten Wechsel
     * lazy initialisiert (MultiDisplay + Consent-Flow) und in der Registry
     * registriert; der Video-Player (S3) wird beim ersten Wechsel lazy
     * initialisiert (MultiFromFile, Datei wird über [setVideoPlayerUri] gesetzt).
     */
    fun switchSource(kind: VideoSourceKind): Boolean {
        val switched = when (kind) {
            VideoSourceKind.CAMERA -> videoSourceRegistry.switchTo(VideoSourceKind.CAMERA)

            VideoSourceKind.SCREEN_CAPTURE -> {
                val source = ensureScreenCaptureSource() ?: return false
                // Die Quelle ist bereits erzeugt (Engine besitzt Display + Checker) —
                // die Fabrik liefert genau diese Instanz für SCREEN_CAPTURE.
                videoSourceRegistry.registerFactory(VideoSourceKind.SCREEN_CAPTURE) { requested ->
                    if (requested == VideoSourceKind.SCREEN_CAPTURE) source else null
                }
                videoSourceRegistry.switchTo(VideoSourceKind.SCREEN_CAPTURE)
            }

            VideoSourceKind.VIDEO_PLAYER -> {
                val source = ensureVideoPlayerSource() ?: return false
                videoSourceRegistry.registerFactory(VideoSourceKind.VIDEO_PLAYER) { requested ->
                    if (requested == VideoSourceKind.VIDEO_PLAYER) source else null
                }
                videoSourceRegistry.switchTo(VideoSourceKind.VIDEO_PLAYER)
            }

            VideoSourceKind.REPLAY -> {
                val source = ensureReplaySource() ?: return false
                videoSourceRegistry.registerFactory(VideoSourceKind.REPLAY) { requested ->
                    if (requested == VideoSourceKind.REPLAY) source else null
                }
                videoSourceRegistry.switchTo(VideoSourceKind.REPLAY)
            }
        }
        if (switched && kind != VideoSourceKind.CAMERA) {
            if (_replayState.value is ReplayState.Recording) stopReplay()
            stopIdlePreview()
        }
        // P1: Quellwechsel — die Zonen sind quellrelativ; der Soll-Zustand
        // wird auf die neue Quelle angewendet (relative Bildmitte bleibt
        // erhalten; Persistenz je Quelle prüft P3, Skizze §9).
        applyPrivacyZones(desiredPrivacyZones.value)
        return switched
    }

    /**
     * S2: erzeugt die Screen-Capture-Quelle einmalig (wie [initializeCamera] für die
     * Kamera) — ein [MultiDisplay] mit einem ConnectChecker pro Stream-Ziel, der die
     * Ziel-Status der Engine aktualisiert.
     */
    private fun ensureScreenCaptureSource(): ScreenCaptureVideoSource? {
        if (screenCaptureSource != null) return screenCaptureSource
        val display = displayFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
        return ScreenCaptureVideoSource(display).also { screenCaptureSource = it }
    }

    /**
     * S3: erzeugt die Video-Datei-Quelle einmalig (wie [initializeCamera] für die
     * Kamera) — ein [com.pedro.library.multiple.MultiFromFile] mit einem
     * ConnectChecker pro Stream-Ziel, der die Ziel-Status der Engine aktualisiert.
     */
    private fun ensureVideoPlayerSource(): VideoPlayerVideoSource? {
        if (videoPlayerSource != null) return videoPlayerSource
        val player = playerFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
        return VideoPlayerVideoSource(context, player).also { videoPlayerSource = it }
    }

    /**
     * Erzeugt die Replay-Quelle einmalig (wie [ensureVideoPlayerSource] für den
     * Video-Player) — ein [com.pedro.library.multiple.MultiFromFile] mit einem
     * ConnectChecker pro Stream-Ziel, der die Ziel-Status der Engine aktualisiert.
     */
    private fun ensureReplaySource(): ReplayVideoSource? {
        if (replaySource != null) return replaySource
        val player = playerFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
        return ReplayVideoSource(context, player).also { replaySource = it }
    }

    /**
     * S3: setzt die abzuspielende Video-Datei (Content-Uri aus dem SAF-Picker)
     * für die Video-Player-Quelle.
     *
     * @return true, wenn die Datei gesetzt und die Encoder vorbereitet werden konnten.
     */
    fun setVideoPlayerUri(uri: Uri): Boolean =
        ensureVideoPlayerSource()?.setVideo(uri) ?: false

    /**
     * Setzt die abzuspielende Replay-Datei für die Replay-Quelle und wechselt auf
     * sie. Schlägt die Vorbereitung fehl (korrupte/fehlende Datei), bleibt die
     * aktive Quelle unverändert.
     *
     * @return true, wenn die Datei gesetzt wurde und die Quelle aktiv ist.
     */
    fun useReplayAsSource(file: File): Boolean {
        val source = ensureReplaySource() ?: return false
        if (!source.setReplay(file)) return false
        return switchSource(VideoSourceKind.REPLAY)
    }

    /** Die Replay-Datei der aktiven Replay-Quelle (null, wenn keine aktiv/geladen). */
    val activeReplayFile: File?
        get() = replaySource?.replayFile

    /**
     * S2: liefert den MediaProjection-Consent-Intent der Screen-Capture-Quelle
     * (System-Dialog „Bildschirm übertragen“). null, solange die Quelle nicht
     * initialisiert ist (vorher [switchSource](SCREEN_CAPTURE) aufrufen).
     */
    fun createScreenCaptureConsentIntent(): Intent? = screenCaptureSource?.createConsentIntent()

    /**
     * S2: übergibt das Ergebnis des MediaProjection-Consent-Dialogs an die
     * Screen-Capture-Quelle.
     *
     * @return true, wenn der Nutzer zugestimmt hat (RESULT_OK + Daten vorhanden).
     */
    fun onScreenCaptureConsentResult(resultCode: Int, data: Intent?): Boolean =
        screenCaptureSource?.onConsentResult(resultCode, data) == true

    /**
     * Erstellt die Kamera einmalig, view-unabhängig.
     *
     * Wird beim Anzeigen des Streaming-Screens aufgerufen; da die Engine ein
     * Singleton ist, wird die bestehende Instanz bei einem Activity-Recreate
     * (Rotation, Recents) nicht ersetzt — der laufende Stream bleibt erhalten.
     */
    fun initializeCamera() {
        if (camera == null) {
            camera = cameraFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
            camera!!.setRecordController(monitorKeyframes(AndroidMuxerRecordController()))
            camera!!.setFpsListener { fps ->
                if (_activeEncoder.value != null) _measuredEncoderFps.value = fps
            }
            val encoderControls = RootEncoderCameraControls(camera!!)
            cameraControls = ActiveCameraControls { idlePreviewCamera?.let(::Camera2PreviewControls) ?: encoderControls }
            focusController = CameraFocusController(object : FocusableCamera {
                override fun enableAutoFocus() = idlePreviewCamera?.enableAutoFocus() ?: camera!!.enableAutoFocus()
                override fun disableAutoFocus() = idlePreviewCamera?.disableAutoFocus() ?: camera!!.disableAutoFocus()
                override fun isAutoFocusEnabled() = idlePreviewCamera?.isAutoFocusEnabled ?: camera!!.isAutoFocusEnabled
                override fun setFocusDistance(distance: Float) { cameraControls!!.setFocusDistance(distance) }
            })
            stabilizationController = CameraStabilizationController(cameraControls!!).also {
                _stabilizationEnabled.value = it.isEnabled
            }
            _torchEnabled.value = cameraControls!!.isTorchEnabled()

            // Manuelle Kamera-Steuerung initialisieren
            val lensController = CameraLensController(cameraControls!!)
            manualCameraController = ManualCameraController(cameraControls!!, lensController).also {
                it.syncState()
            }
            refreshCameraControls(opened = false)
        }
    }

    /**
     * Schaltet zwischen Autofokus und Fokus-Lock (Unendlich) um (Moblin #377).
     *
     * @return true, wenn der neue Modus von der Kamera übernommen wurde.
     */
    fun toggleFocusLock(): Boolean {
        val controller = focusController ?: return false
        val changed = controller.toggleFocusLock()
        if (changed) {
            _focusMode.value = controller.mode
        }
        return changed
    }

    /**
     * Pinch-Zoom: multipliziert den aktuellen Zoom mit dem [scaleFactor] des
     * ScaleGestureDetectors und begrenzt auf den Kamera-Zoombereich.
     *
     * Kein-op, solange die Kamera nicht initialisiert ist.
     */
    fun zoomBy(scaleFactor: Float) {
        val controls = cameraControls ?: return
        val range = controls.getZoomRange() ?: return
        controls.setZoom(ZoomCalculator.zoomForScale(controls.getZoom(), scaleFactor, range))
    }

    /** Setzt den Zoom auf 1.0 zurück (z. B. per Doppeltipp). */
    fun resetZoom() {
        val controls = cameraControls ?: return
        val range = controls.getZoomRange() ?: return
        controls.setZoom(ZoomCalculator.clamp(ZoomCalculator.MIN_ZOOM, range))
    }

    /** Tap-to-Focus auf die getippte Stelle der Kamera-Vorschau. */
    fun tapToFocus(view: View, event: MotionEvent) {
        cameraControls?.tapToFocus(view, event)
    }

    /**
     * Schaltet die Video-Stabilisierung (OIS bevorzugt, sonst EIS) um.
     *
     * @return true, wenn die Kamera den neuen Zustand übernommen hat.
     */
    fun toggleStabilization(): Boolean {
        val controller = stabilizationController ?: return false
        val changed = controller.toggle()
        if (changed) {
            _stabilizationEnabled.value = controller.isEnabled
        }
        return changed
    }

    /**
     * Schaltet die Taschenlampe (Torch/Lantern) um — Moblin-Parität.
     *
     * @return true, wenn die Kamera den neuen Zustand übernommen hat.
     */
    fun toggleTorch(): Boolean {
        val controls = cameraControls ?: return false
        val changed = if (controls.isTorchEnabled()) {
            controls.disableTorch()
        } else {
            controls.enableTorch()
        }
        if (changed) {
            _torchEnabled.value = controls.isTorchEnabled()
        }
        return changed
    }

    // --- Manuelle Kamera-Steuerung ---

    /**
     * Setzt den manuellen Fokusabstand.
     * @param distance 0.0 = Unendlich, höhere Werte = näher.
     */
    fun setManualFocusDistance(distance: Float) {
        manualCameraController?.setFocusDistance(distance)
    }

    /** Wechselt auf die Linse mit der angegebenen ID. */
    fun selectLens(lensId: String): Boolean = manualCameraController?.selectLens(lensId) ?: false

    /** Verfügbare Linsen. */
    fun getAvailableLenses(): List<LensInfo> = manualCameraController?.getAvailableLenses() ?: emptyList()

    /**
     * Startet eine lokale MP4-Aufnahme mit oder ohne aktiven Stream.
     *
     * @param includeAudio true = Bild + Ton (Standard), false = nur Bild
     *   (ReplayAudioMode.VIDEO_ONLY — der Muxer schreibt keine Audiospur).
     */
    fun startReplay(nowMillis: Long = System.currentTimeMillis(), includeAudio: Boolean = true): Boolean {
        val cam = camera ?: return false
        if (activeSourceKind.value != VideoSourceKind.CAMERA || _replayState.value is ReplayState.Recording) return false
        val standalone = !cam.isStreaming
        try {
            if (standalone && !prepareStandaloneRecording(cam, includeAudio)) return false
            val controller = recordingController(cam, includeAudio)
            if (!controller.start(nowMillis)) {
                restorePreviewAfterRecordingFailure(cam, standalone)
                return false
            }
            applyPrivacyZones(desiredPrivacyZones.value)
            attachPreviewIfRunning()
            restoreCameraControls()
            return true
        } catch (error: Exception) {
            Timber.e(error, "Could not start local recording")
            restorePreviewAfterRecordingFailure(cam, standalone)
            return false
        }
    }

    private fun prepareStandaloneRecording(cam: MultiCamera2, includeAudio: Boolean): Boolean {
        resetKeyframeMonitoring()
        syncSelectedCamera(cam)
        stopIdlePreview()
        val audioReady = !includeAudio || cam.prepareAudio()
        encoderRotationDegrees = if (_encoderConfig.value == null) CameraHelper.getCameraOrientation(context) else 0
        val videoReady = if (_encoderConfig.value == null) cam.prepareVideo() else applyEncoderPreset(cam)
        if (audioReady && videoReady) return true
        startIdlePreviewIfReady()
        return false
    }

    private fun recordingController(cam: MultiCamera2, includeAudio: Boolean): ReplayController =
        replayController?.takeIf { lastReplayIncludeAudio == includeAudio }
            ?: ReplayController(
                storage = replayStorage(context),
                recorder = TrackControlledReplayRecorder(cam, includeAudio = includeAudio, wrapController = ::monitorKeyframes),
                _state = _replayState,
            ).also {
                replayController = it
                lastReplayIncludeAudio = includeAudio
            }

    private fun restorePreviewAfterRecordingFailure(cam: MultiCamera2, standalone: Boolean) {
        if (!standalone) return
        runCatching { cam.stopRecord() }
        _replayState.value = ReplayState.Idle
        startIdlePreviewIfReady()
    }

    /** Stoppt die lokale Replay-Aufnahme und gibt die Datei zurück. */
    fun stopReplay(): java.io.File? {
        if (camera?.isStreaming != true) detachGlPreview()
        val file = replayController?.stop()
        startIdlePreviewIfReady()
        return file
    }

    /** Entfernt alte Replay-Dateien gemäß der Aufbewahrungsgrenze. */
    fun pruneReplays() {
        replayController?.prune()
    }

    private val _exposure = MutableStateFlow(0)
    val exposure: StateFlow<Int> = _exposure.asStateFlow()
    private val _exposureRange = MutableStateFlow<IntRange?>(null)
    val exposureRange: StateFlow<IntRange?> = _exposureRange.asStateFlow()
    private val _autoExposureEnabled = MutableStateFlow(true)
    val autoExposureEnabled: StateFlow<Boolean> = _autoExposureEnabled.asStateFlow()
    private val _autoWhiteBalanceEnabled = MutableStateFlow(true)
    val autoWhiteBalanceEnabled: StateFlow<Boolean> = _autoWhiteBalanceEnabled.asStateFlow()

    private fun restoreCameraControls() {
        val controls = cameraControls ?: return
        if (_torchEnabled.value) controls.enableTorch()
        if (_stabilizationEnabled.value) controls.enableStabilization() else controls.disableStabilization()
        focusController?.apply(_focusMode.value)
        if (_autoExposureEnabled.value) controls.enableAutoExposure() else controls.disableAutoExposure()
        if (_autoExposureEnabled.value) controls.setExposure(_exposure.value)
        if (_autoWhiteBalanceEnabled.value) controls.enableAutoWhiteBalance() else controls.disableAutoWhiteBalance()
        refreshCameraControls()
    }

    private fun refreshCameraControls(opened: Boolean = true) {
        val controls = cameraControls ?: return
        manualCameraController?.syncState()
        _torchEnabled.value = controls.isTorchEnabled()
        _stabilizationEnabled.value = controls.isStabilizationEnabled()
        stabilizationController?.syncState()
        _exposureRange.value = controls.getExposureRange()?.takeIf { it.first < it.last }
        _exposure.value = controls.getExposure()
        if (opened) {
            _autoExposureEnabled.value = controls.isAutoExposureEnabled()
            _autoWhiteBalanceEnabled.value = controls.isAutoWhiteBalanceEnabled()
        }
    }

    fun setAutoExposure(enabled: Boolean): Boolean =
        (manualCameraController?.setAutoExposure(enabled) ?: false).also { refreshCameraControls() }

    fun setExposure(value: Int): Boolean =
        (manualCameraController?.setExposure(value) ?: false).also { refreshCameraControls() }

    fun setAutoWhiteBalance(enabled: Boolean): Boolean =
        (manualCameraController?.setAutoWhiteBalance(enabled) ?: false).also { refreshCameraControls() }

    fun hasWhiteBalanceControl(): Boolean = manualCameraController?.hasWhiteBalanceControl() ?: false

    /** true, wenn ISO auf RootEncoder 2.7.5 separat steuerbar ist (derzeit nein). */
    fun hasIsoControl(): Boolean = manualCameraController?.hasIsoControl() ?: false

    /** true, wenn der EV-Regler über den Camera2-Belichtungsbereich abgebildet werden kann. */
    fun hasEvControl(): Boolean = manualCameraController?.hasEvControl() ?: false

    /**
     * Verbindet die Preview-Surface der Activity mit dem aktiven Kamerapfad.
     *
     * Die Surface darf jederzeit gewechselt werden (Rotation, Activity-Recreate)
     * — der Stream selbst hängt nicht an ihr. Im Leerlauf öffnet Camera2 die
     * Surface direkt; beim Stream-Start wird sie an die GL-Pipeline gehängt.
     */
    fun attachPreview(
        surface: Surface,
        width: Int,
        height: Int,
        rotationDegrees: Int = if (height > width) 0 else 90,
    ) {
        previewRequest = PreviewRequest(surface, width, height, rotationDegrees)
        if (activeSourceKind.value == VideoSourceKind.CAMERA && camera?.isStreaming != true && camera?.isRecording != true) {
            startIdlePreviewIfReady()
        } else {
            attachPreviewIfRunning()
        }
    }

    /** Open Camera2 into the same GL pipeline used by recording, without starting encoders. */
    fun startIdlePreviewIfReady() {
        val request = previewRequest ?: return
        if (activeSourceKind.value != VideoSourceKind.CAMERA || camera?.isStreaming == true || camera?.isRecording == true ||
            request.width <= 0 || request.height <= 0 ||
            context.checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED
        ) return
        // Layout-Maße dienen der Reattach-Erkennung; der Camera2-Buffer kann
        // eine andere, unterstützte Größe haben (z. B. 1088×1088 im Portrait).
        val size = request.width to request.height
        if (idlePreviewSurface === request.surface && idlePreviewSize == size) {
            attachPreviewIfRunning()
            return
        }

        stopIdlePreview()
        // GlStreamInterface.stop() does not release its preview EGL surface.
        // Disconnect that producer before Camera2 reconnects to the same SurfaceView.
        detachGlPreview()
        try {
            val gl = camera?.glInterface as? GlStreamInterface ?: return
            encoderRotationDegrees = 90
            gl.setEncoderSize(720, 1280)
            gl.setIsPortrait(true)
            gl.setRotation(0)
            gl.start()
            val preview = idlePreviewFactory(context)
            idlePreviewCamera = preview
            preview.setCameraCallbacks(object : com.pedro.encoder.input.video.CameraCallbacks {
                override fun onCameraOpened() {
                    if (idlePreviewCamera === preview) restoreCameraControls()
                }
                override fun onCameraChanged(facing: CameraHelper.Facing) = Unit
                override fun onCameraError(error: String) { Timber.e("Idle camera: %s", error) }
                override fun onCameraDisconnected() = Unit
            })
            preview.prepareCamera(gl.surfaceTexture, 1280, 720, 30)
            val cameraId = camera?.currentCameraId
            if (cameraId.isNullOrBlank()) preview.openCameraBack() else preview.openCameraId(cameraId)
            idlePreviewSurface = request.surface
            idlePreviewSize = size
            attachPreviewIfRunning()
            privacyComposer.requestRebuild(gl)
            restoreCameraControls()
        } catch (error: Exception) {
            stopIdlePreview()
            Timber.e(error, "Could not open idle camera preview")
        }
    }

    private fun stopIdlePreview() {
        idlePreviewSurface = null
        idlePreviewSize = null
        if (idlePreviewCamera != null) {
            runCatching { idlePreviewCamera?.closeCamera() }
            idlePreviewCamera = null
            detachGlPreview()
            (camera?.glInterface as? GlStreamInterface)?.stop()
        }
    }

    /** Löst die Preview-Surface (Activity zerstört/verdeckt). Der Stream läuft weiter. */
    fun detachPreview(surface: Surface? = null) {
        if (surface != null && previewRequest?.surface !== surface) return
        previewRequest = null
        stopIdlePreview()
        (camera?.glInterface as? GlStreamInterface)?.deAttachPreview()
        glPreviewSurface = null
    }

    private fun detachGlPreview() {
        if (glPreviewSurface == null) return
        (camera?.glInterface as? GlStreamInterface)?.deAttachPreview()
        glPreviewSurface = null
    }

    /**
     * Startet den Stream auf alle angegebenen Ziele (Multi-Streaming).
     *
     * Leere Einträge werden ignoriert; es werden maximal [MAX_STREAM_TARGETS]
     * Ziele gestartet. Läuft bereits ein Stream, wird nichts gestartet.
     */
    fun startStream(urls: List<String>) {
        val activeUrls = urls
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(MAX_STREAM_TARGETS)
        if (activeUrls.isEmpty()) return

        if (_streamingState.value is StreamingState.Streaming ||
            _streamingState.value is StreamingState.Preparing
        ) {
            return
        }

        when (activeSourceKind.value) {
            VideoSourceKind.SCREEN_CAPTURE -> screenCaptureSource?.let { source ->
                startSourceStream(source, activeUrls, source::startStream)
            }
            VideoSourceKind.VIDEO_PLAYER -> videoPlayerSource?.let { source ->
                startSourceStream(source, activeUrls, source::startStream)
            }
            VideoSourceKind.REPLAY -> replaySource?.let { source ->
                startSourceStream(source, activeUrls, source::startStream)
            }
            VideoSourceKind.CAMERA -> startCameraStream(activeUrls)
        }
    }

    private fun startSourceStream(
        source: com.vivid.feature.streaming.source.VideoSource?,
        urls: List<String>,
        startTarget: (Int, String) -> Unit,
    ) {
        if (source == null || source.isActive) return
        _targetStates.value = urls.map { StreamTargetState(it) }
        _streamingState.value = StreamingState.Preparing
        if (!source.start()) {
            failStream(ENCODER_PREPARATION_ERROR)
            return
        }
        applyPrivacyZones(desiredPrivacyZones.value)
        urls.forEachIndexed(startTarget)
    }

    private fun startCameraStream(activeUrls: List<String>) {
        // Kamera-Pfad (unverändert).
        val cam = camera ?: return
        if (cam.isStreaming) return
        if (!cam.isRecording) resetKeyframeMonitoring()
        syncSelectedCamera(cam)
        stopIdlePreview()

        _targetStates.value = activeUrls.map { StreamTargetState(it) }
        _streamingState.value = StreamingState.Preparing

        if (prepareStreamEncoders(cam)) {
            resetAdaptiveBitrate()
            // P1: Anonymisierung — persistierter Soll-Zustand ab dem ersten
            // Frame (Composer-Zustand überlebt stopStream bewusst).
            applyPrivacyZones(desiredPrivacyZones.value)
            activeUrls.forEachIndexed { index, url ->
                cam.startStream(MultiType.RTMP, index, url)
            }
            // RootEncoder opens Camera2 during startStream; attach after that transition.
            attachPreviewIfRunning()
            restoreCameraControls()
        } else {
            failStream(ENCODER_PREPARATION_ERROR)
            // Best-effort Rückkehr zur Vorschau; bei Kamera-Konkurrenz kann auch
            // dieser Versuch scheitern und wird in startIdlePreviewIfReady geloggt.
            startIdlePreviewIfReady()
        }
    }

    private fun resetAdaptiveBitrate() {
        if (_adaptiveBitrateEnabled.value) {
            val presetKbps = _activeEncoder.value?.preset?.videoBitrateKbps
                ?: DEFAULT_VIDEO_BITRATE_KBPS
            adaptiveController = AdaptiveBitrateController(
                AdaptiveBitrateConfig(
                    minBitrateKbps = ADAPTIVE_MIN_BITRATE_KBPS,
                    maxBitrateKbps = presetKbps,
                ),
            ).also { it.reset(presetKbps) }
            lastAdaptiveSampleMs = 0L
        } else {
            adaptiveController = null
        }

    }

    private fun prepareStreamEncoders(cam: MultiCamera2): Boolean {
        // An ongoing local recording already owns the running encoders.
        val recording = cam.isRecording
        if (!recording) {
            encoderRotationDegrees = if (_encoderConfig.value == null) CameraHelper.getCameraOrientation(context) else 0
        }
        val audioReady = recording || cam.prepareAudio() == true
        val videoReady = if (recording) {
            true
        } else if (_encoderConfig.value == null) {
            // Legacy-Pfad: RootEncoder-Default (640×480@30), Verhalten unverändert.
            cam.prepareVideo() == true
        } else {
            // Preset-Pfad: applyEncoderPreset ruft prepareVideo(width, …) selbst.
            applyEncoderPreset(cam)
        }
        return audioReady && videoReady
    }

    /**
     * Wendet das Encoder-Preset (Auflösung/FPS/Codec) vor dem Streamstart an —
     * der v0.6.0-Bucket „4K/60fps + HEVC“ (Moblin-Parität).
     *
     * Kein Encoder konfiguriert (Standard): RootEncoder-Default unangetastet —
     * der Legacy-Pfad `prepareVideo()` bleibt unverändert bestehen. Mit
     * Encoder-Konfiguration läuft vor `prepareVideo()` die Fallback-Kette
     * ([resolveCameraStreamProfile]): HEVC nur, wenn die Hardware es in der
     * gewählten Auflösung kann, sonst H.264 — je nach Preset-Abstufung auch
     * mit herabgesetzter Auflösung.
     *
     * @return false, wenn Kamera und Encoder kein gemeinsames Profil unterstützen.
     */
    private fun applyEncoderPreset(cam: Camera2Base): Boolean {
        // Recheck against the selected camera before preparation; the ViewModel
        // only checks encoder capabilities and the lens may have changed since then.
        val requested = _encoderConfig.value ?: return true
        _activeEncoder.value = null
        _measuredEncoderFps.value = null
        val resolved = resolveCameraStreamProfile(
            requested, _encoderAutoFallback.value, captureCapabilities(cam), encoderCapabilities,
        ) ?: return false

        val codec = when (resolved.codec) {
            VideoCodecPreference.H265 -> VideoCodec.H265
            VideoCodecPreference.AV1 -> VideoCodec.AV1
            else -> VideoCodec.H264
        }
        cam.setVideoCodec(codec)
        val prepared = cam.prepareVideo(
            resolved.preset.width,
            resolved.preset.height,
            resolved.preset.fps,
            resolved.preset.videoBitrateKbps * 1_000, // RootEncoder expects bits/s.
            KeyframeIntervalController.INTERVAL_SECONDS, // Seconds, paired with the resolved FPS above.
            0, // rotation
        )
        if (prepared == true) _activeEncoder.value = resolved
        return prepared == true
    }

    /** Lens selection during idle preview must also reach the streaming camera. */
    private fun syncSelectedCamera(cam: MultiCamera2) {
        val selectedId = idlePreviewCamera?.getCurrentCameraId() ?: return
        if (selectedId != cam.currentCameraId) cam.switchCamera(selectedId)
    }

    private fun resetKeyframeMonitoring() {
        keyframeController.reset()
        _measuredKeyframeIntervalMs.value = null
    }

    private fun monitorKeyframes(inner: RecordController): RecordController =
        KeyframeMonitoringRecordController(inner) { info ->
            val cam = camera
            if (cam != null && (cam.isStreaming || cam.isRecording)) {
                val overdue = keyframeController.onFrame(
                    info.presentationTimeUs, info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0, timeSource(),
                )
                _measuredKeyframeIntervalMs.value = keyframeController.measuredIntervalMs
                if (overdue) {
                    Timber.w("Encoder missed the 2-second keyframe interval; requesting a sync frame")
                    cam.requestKeyFrame()
                }
            }
        }

    /**
     * Konfiguriert den Encoder für den nächsten Streamstart. Erwartet die
     * aufgelöste Konfiguration (UI/VoM resolven die Preset-Einstellung gegen
     * die Encoder-Fähigkeiten — die Engine prüft zusätzlich die gewählte Kamera).
     */
    fun configureEncoder(resolved: ResolvedEncoderConfig, autoFallback: Boolean) {
        _encoderConfig.value = resolved
        _encoderAutoFallback.value = autoFallback
    }

    /**
     * Adaptive Bitrate (v0.6.0) ein-/ausschalten. Die Aenderung greift
     * beim naechsten Streamstart (Controller-Reset auf die Preset-Bitrate).
     */
    fun configureAdaptiveBitrate(enabled: Boolean) {
        _adaptiveBitrateEnabled.value = enabled
        if (!enabled) adaptiveController = null
    }

    /** Successfully prepared camera/encoder profile, rather than the requested preset. */
    val activeEncoder: StateFlow<ResolvedEncoderConfig?> = _activeEncoder.asStateFlow()

    /** true = Strict-Modus (kein automatischer HEVC/Preset-Fallback). */
    val encoderAutoFallback: StateFlow<Boolean> = _encoderAutoFallback.asStateFlow()

    /** Setzt alle Ziele auf Failed und den Gesamt-Status auf Failed (mit Ursache). */
    private fun failStream(reason: String) {
        _streamingState.value = StreamingState.Failed(reason)
        _targetStates.value = _targetStates.value.map {
            it.copy(status = StreamTargetStatus.FAILED, failureReason = reason)
        }
    }

    /** Einfacher Einstieg für genau ein Ziel. */
    fun startStream(url: String) {
        startStream(listOf(url))
    }

    /** Hängt die gemerkte Preview-Surface an, sobald die GL-Pipeline läuft. */
    private fun attachPreviewIfRunning() {
        val request = previewRequest ?: return
        val gl = camera?.glInterface as? GlStreamInterface ?: return
        if (gl.isRunning) {
            if (glPreviewSurface !== request.surface) {
                gl.attachPreview(request.surface)
                glPreviewSurface = request.surface
            }
            gl.setPreviewResolution(request.width, request.height)
            gl.setPreviewIsPortrait(request.rotationDegrees % 180 == 0)
            gl.setPreviewRotation((90 - request.rotationDegrees - encoderRotationDegrees + 720) % 360)
            gl.setAspectRatioMode(AspectRatioMode.Fill)
        }
    }

    /** Stoppt alle laufenden/startenden Ziele und setzt den Zustand auf Idle. */
    fun stopStream() {
        if (_streamingState.value !is StreamingState.Streaming &&
            _streamingState.value !is StreamingState.Preparing
        ) {
            return
        }
        // S2/S3: Die aktive Quelle bestimmt, welcher Encoder gestoppt wird.
        if (activeSourceKind.value == VideoSourceKind.SCREEN_CAPTURE) {
            val source = screenCaptureSource ?: return
            _targetStates.value.forEachIndexed { index, _ ->
                source.stopStream(index)
            }
        } else if (activeSourceKind.value == VideoSourceKind.VIDEO_PLAYER) {
            val source = videoPlayerSource ?: return
            _targetStates.value.forEachIndexed { index, _ ->
                source.stopStream(index)
            }
        } else if (activeSourceKind.value == VideoSourceKind.REPLAY) {
            val source = replaySource ?: return
            _targetStates.value.forEachIndexed { index, _ ->
                source.stopStream(index)
            }
        } else {
            val cam = camera ?: return
            if (!cam.isRecording) detachGlPreview()
            _targetStates.value.forEachIndexed { index, _ ->
                cam.stopStream(MultiType.RTMP, index)
            }
            startIdlePreviewIfReady()
        }
        _targetStates.value = _targetStates.value.map {
            it.copy(status = StreamTargetStatus.IDLE, failureReason = null, bitrateKbps = null)
        }
        _streamingState.value = StreamingState.Idle
        adaptiveController = null
    }

    /**
     * Wertet eine gemessene Sendebitrate (kbps) aus und passt die
     * Encoder-Zielbitrate on-the-fly an (AIMD-aehnlich). Rate-limited auf
     * ein Sample je [ADAPTIVE_SAMPLE_INTERVAL_MS]; die Streak-Logik liegt
     * im [AdaptiveBitrateController].
     */
    private fun sampleAdaptiveBitrate(measuredKbps: Long) {
        if (!_adaptiveBitrateEnabled.value) return
        val cam = camera ?: return
        val controller = adaptiveController ?: return
        val now = timeSource()
        if (now - lastAdaptiveSampleMs < ADAPTIVE_SAMPLE_INTERVAL_MS) return
        lastAdaptiveSampleMs = now
        val nextKbps = controller.onSample(measuredKbps) ?: return
        cam.setVideoBitrateOnFly(nextKbps * 1_000) // RootEncoder expects bits/s.
    }
}
