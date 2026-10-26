package com.vivid.feature.streaming

import android.os.Build
import android.hardware.camera2.CameraManager
import com.vivid.core.data.CameraMode
import com.vivid.core.data.EncoderPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import android.Manifest
import android.content.Context
import com.vivid.core.data.SceneComposition
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.SceneProducerState
import com.vivid.feature.streaming.compositor.SceneRenderState
import com.vivid.feature.streaming.compositor.capture.SceneBitmapRegistry
import com.vivid.feature.streaming.compositor.capture.SceneProjectionRegistry
import com.vivid.feature.streaming.compositor.gl.AndroidSceneTextureProvider
import com.vivid.feature.streaming.compositor.gl.SceneCompositorFilterRender
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import com.vivid.feature.streaming.compositor.matchesSceneIdentity
import android.net.Uri
import androidx.core.net.toUri
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.WindowManager
import java.io.File
import com.pedro.common.ConnectChecker
import com.pedro.library.base.Camera2Base
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraHelper
import com.pedro.encoder.input.video.facedetector.Face
import com.pedro.encoder.input.video.facedetector.FaceDetectorCallback
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
import androidx.annotation.StringRes
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
import com.vivid.feature.streaming.source.UvcVideoSource

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
    internal var sceneRenderClock = com.vivid.feature.streaming.compositor.SceneRenderClock()
    private var sceneSession: com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession? = null
    internal var sceneSessionFactory: (List<ConnectChecker>) -> com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession = {
        com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession.create(context, it)
    }
    internal var dispatchSceneTask: (() -> Unit) -> Unit = { action -> android.os.Handler(context.mainLooper).post(action) }
    private var sceneSessionGeneration = 0L
    private var sceneReplayController: ReplayController? = null
    private var sceneReplayAudio: Boolean? = null
    private var sceneSelectedCameraId: String? = null
    private var sceneVideoUri: Uri? = null
    private var sceneReplayFile: File? = null
    @Volatile private var legacyFileGeneration = 0L
    private var requestedSceneProjectionId: String? = null
    private var pendingSceneProjection: Pair<String, Intent>? = null
    private var requestedCameraMode = CameraMode.STANDARD
    private val _cameraModeStatus = MutableStateFlow(CameraModeStatus())
    val cameraModeStatus: StateFlow<CameraModeStatus> = _cameraModeStatus.asStateFlow()
    private val _cameraSelectionVersion = MutableStateFlow(0)
    val cameraSelectionVersion: StateFlow<Int> = _cameraSelectionVersion.asStateFlow()
    private val captureScope = CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))
    private val captureGeneration = AtomicInteger()
    private var preparationGeneration = 0
    internal var launchCameraPreparation: ((() -> Unit) -> Unit) = { work -> captureScope.launch { work() } }
    private var experimentalCapture: ExperimentalCapture? = null
    private var externalCapture: RootEncoderExternalCapture? = null
    internal var experimentalProfile: (ResolvedEncoderConfig, android.hardware.camera2.CameraCharacteristics?) -> ExperimentalCameraProfile? =
        ::resolveExperimentalCameraProfile
    internal var externalCaptureFactory: (Camera2Base) -> RootEncoderExternalCapture = ::RootEncoderExternalCapture
    internal var experimentalCaptureFactory: (String, ExperimentalCameraProfile, Surface, (Throwable) -> Unit) -> ExperimentalCapture =
        { id, profile, surface, failure ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val texture = requireNotNull(camera?.glInterface?.surfaceTexture)
                HighSpeedGlBridge(context.getSystemService(CameraManager::class.java), id, profile, surface, failure,
                    consumedTimestampNs = { texture.timestamp })
            } else error("Experimental capture requires Android 10")
        }

    fun canChangeCameraMode(): Boolean = !_cameraModeStatus.value.preparing &&
        _streamingState.value !is StreamingState.Preparing && _streamingState.value !is StreamingState.Streaming &&
        camera?.isStreaming != true && camera?.isRecording != true && _replayState.value !is ReplayState.Recording

    fun configureCameraMode(mode: CameraMode): Boolean {
        if (!canChangeCameraMode()) return false
        requestedCameraMode = mode
        privacyComposer.preserveExternalFilters = mode == CameraMode.EXPERIMENTAL_60_FPS
        return true
    }

    fun supportsExperimentalCamera(preset: EncoderPreset, codec: VideoCodecPreference): Boolean = runCatching {
        val info = idlePreviewCamera?.cameraCharacteristics ?: camera?.cameraCharacteristics
        experimentalProfile(ResolvedEncoderConfig(codec, preset, false), info) != null
    }.getOrDefault(false)

    private fun beginCameraPreparation(work: () -> Unit) {
        _cameraModeStatus.value = CameraModeStatus(preparing = true)
        preparationGeneration = captureGeneration.incrementAndGet()
        launchCameraPreparation {
            try {
                if (preparationGeneration == captureGeneration.get()) work()
            } catch (error: Exception) {
                Timber.e(error, "Camera preparation/start failed")
                runCatching { closeExperimentalCapture() }
                runCatching { camera?.stopRecord() }
                runCatching { camera?.stopStream() }
                failStream(ENCODER_PREPARATION_ERROR)
                _cameraModeStatus.value = _cameraModeStatus.value.copy(notice = CameraModeNotice.CAPTURE_FAILED)
            } finally {
                if (preparationGeneration != captureGeneration.get()) {
                    runCatching { closeExperimentalCapture() }
                    runCatching { camera?.stopRecord() }
                    runCatching { camera?.stopStream() }
                    _replayState.value = ReplayState.Idle
                    _streamingState.value = StreamingState.Idle
                }
                _cameraModeStatus.value = _cameraModeStatus.value.copy(preparing = false)
                if (camera?.isStreaming != true && camera?.isRecording != true) startIdlePreviewIfReady()
            }
        }
    }

    private fun closeExperimentalCapture() {
        val capture = experimentalCapture
        experimentalCapture = null
        try { capture?.close() } finally {
            externalCapture?.close()
            externalCapture = null
        }
    }

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
    private val _bitrateDiagnostics = MutableStateFlow<EncoderBitrateDiagnostics?>(null)
    val bitrateDiagnostics: StateFlow<EncoderBitrateDiagnostics?> = _bitrateDiagnostics.asStateFlow()
    internal var readBitrateDiagnostics: (Camera2Base) -> EncoderBitrateDiagnostics = RootEncoderBitrateDiagnostics::read
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
    private var stoppingStreams = false
    private var lastAdaptiveSampleMs = 0L

    /** Zeitquelle (injektierbar fuer Tests; Realzeit im Betrieb). */
    internal var timeSource: () -> Long = { SystemClock.elapsedRealtime() }

    /** S2: Screen-Capture-Quelle (MediaProjection), lazy erzeugt (wie die Kamera). */
    private var screenCaptureSource: ScreenCaptureVideoSource? = null

    /** S3: Video-Datei-Quelle (MultiFromFile), lazy erzeugt (wie die Kamera). */
    private var videoPlayerSource: VideoPlayerVideoSource? = null

    /** Replay-als-Quelle (MultiFromFile, Loop-Modus), lazy erzeugt (wie die Kamera). */
    private var replaySource: ReplayVideoSource? = null
    private var uvcVideoSource: UvcVideoSource? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    /** Monotonic start of the current live session, owned here to survive UI recreation. */
    private val _streamStartedAtMs = MutableStateFlow<Long?>(null)
    val streamStartedAtMs: StateFlow<Long?> = _streamStartedAtMs.asStateFlow()

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
    private val _sceneComposition = MutableStateFlow<SceneComposition?>(null)
    val sceneComposition: StateFlow<SceneComposition?> = _sceneComposition.asStateFlow()
    private val _sceneProducerStates = MutableStateFlow<Map<String, SceneProducerState>>(emptyMap())
    val sceneProducerStates: StateFlow<Map<String, SceneProducerState>> = _sceneProducerStates.asStateFlow()
    private val sceneProjections by lazy { SceneProjectionRegistry() }
    val sceneProjectionActive: StateFlow<Boolean> get() = sceneProjections.active
    fun stopSceneProjections() { sceneProjections.close() }
    private val sceneBitmaps = SceneBitmapRegistry()
    private val sceneWidgets by lazy {
        com.vivid.feature.streaming.compositor.capture.SceneWidgetFeed(::publishSceneWidget)
    }
    private val sceneCameraAvailable = java.util.concurrent.atomic.AtomicBoolean(true)
    private var desiredSceneRender: SceneRenderState? = null
    private var sceneRender: SceneCompositorFilterRender? = null
    private var sceneTextures: AndroidSceneTextureProvider? = null
    private var sceneRenderGl: GlStreamInterface? = null

    private fun createSceneRender(): com.pedro.encoder.input.gl.render.filters.BaseFilterRender? {
        val desired = desiredSceneRender ?: run {
            sceneRender = null
            sceneTextures = null
            sceneRenderGl = null
            return null
        }
        val renderOwner = activeGlInterface()
        sceneRender?.takeIf { sceneRenderGl === renderOwner && !it.isReleased }?.let { return it }
        val textures = AndroidSceneTextureProvider(
            projections = sceneProjections,
            bitmaps = sceneBitmaps,
            legacyCameraId = {
                if (sceneSession == null && activeSourceKind.value == VideoSourceKind.CAMERA)
                    idlePreviewCamera?.getCurrentCameraId() ?: camera?.currentCameraId else null
            },
            privacyEnabled = { privacyComposer.privacyEnabled.value },
            publishStates = {
                _sceneProducerStates.value = it
                dispatchSceneTask { syncSceneFileAudio() }
            },
            sourceReady = ::sceneSourceReady,
            videoLooped = ::sceneVideoLooped,
            videoLooping = ::sceneVideoLooping,
            captureFrameRate = {
                val streaming = _streamingState.value
                if (streaming is StreamingState.Preparing || streaming is StreamingState.Streaming ||
                    _replayState.value is ReplayState.Recording) _activeEncoder.value?.preset?.fps ?: 30 else 30
            },
        )
        sceneTextures = textures
        sceneRenderGl = renderOwner
        return SceneCompositorFilterRender(textures, frameRendered = {
            renderOwner?.let(sceneRenderClock::frameRendered)
        }, inputAvailable = {
            activeSourceKind.value != VideoSourceKind.CAMERA || sceneCameraAvailable.get()
        }).also {
            it.setScene(desired, animate = false)
            sceneRender = it
        }
    }

    /** Scene edits update desired GPU state; no source encoder is created here. */
    fun setSceneComposition(requested: SceneComposition?, animate: Boolean = true) {
        val previousCameraId = primarySceneCamera()?.id
        val composition = (if (requested == null && sceneEncoding()) fallbackSceneGraph() else requested)
            ?.let(::resolveSceneVideoPlayback)
        if (composition == null && sceneSession != null) closeSceneSession()
        alignLegacySceneCamera(composition)
        val inputId = legacySceneInputId(composition)
        val previous = desiredSceneRender
        val next = composition?.let { SceneRenderState(it, if (sceneSession != null) null else inputId) }
        desiredSceneRender = next
        _sceneComposition.value = composition
        scenePrimarySourceId = composition?.layers?.firstOrNull { it.followsPrimaryInput }?.sourceId
        syncSceneFileAudio()
        refreshSceneCameraBinding(previousCameraId)
        sceneWidgets.update(composition?.sources.orEmpty())
        sceneBitmaps.retainSources(composition?.sources?.filter { it.type == SceneSourceType.WIDGET }
            ?.map { it.id }?.toSet().orEmpty())
        sceneProjections.retainSources(composition?.sources?.filter { it.type == SceneSourceType.SCREEN_CAPTURE }
            ?.map { it.id }?.toSet().orEmpty())
        applySceneCompositionRender(previous, next, animate)
    }

    private fun alignLegacySceneCamera(composition: SceneComposition?) {
        if (sceneSession == null && activeSourceKind.value == VideoSourceKind.CAMERA) {
            sceneCameraSource(composition)
                ?.takeIf { it.type == SceneSourceType.CAMERA }
                ?.locator
                ?.takeIf { it.isNotBlank() }
                ?.let {
                    val current = idlePreviewCamera?.getCurrentCameraId() ?: camera?.currentCameraId
                    if (it != current) {
                        val previousAvailable = sceneCameraAvailable.getAndSet(false)
                        if (!selectLens(it)) sceneCameraAvailable.set(previousAvailable)
                    }
                }
        }
    }

    private fun legacySceneInputId(composition: SceneComposition?): String? {
        val inputType =
            when (activeSourceKind.value) {
                VideoSourceKind.CAMERA -> SceneSourceType.CAMERA
                VideoSourceKind.SCREEN_CAPTURE -> SceneSourceType.SCREEN_CAPTURE
                VideoSourceKind.VIDEO_PLAYER, VideoSourceKind.REPLAY -> SceneSourceType.VIDEO
                // The old UVC adapter is MediaProjection, not an external-camera producer.
                VideoSourceKind.UVC -> null
            }
        val cameraId = idlePreviewCamera?.getCurrentCameraId() ?: camera?.currentCameraId
        return composition
            ?.sources
            ?.firstOrNull {
                it.type == inputType && (
                    it.type != SceneSourceType.CAMERA ||
                        it.locator.isBlank() || it.locator == cameraId
                )
            }?.id
    }

    private fun refreshSceneCameraBinding(previousCameraId: String?) {
        val selectedCameraId = primarySceneCamera()?.id
        if (previousCameraId != selectedCameraId && sceneSession != null) {
            if (selectedCameraId == null) {
                faceProducer.detach()
            } else if (sceneTextures?.captureReady(selectedCameraId) == true) {
                sceneSourceReady(selectedCameraId)
            }
        }
    }

    private fun applySceneCompositionRender(
        previous: SceneRenderState?,
        next: SceneRenderState?,
        animate: Boolean,
    ) {
        val gl = activeGlInterface()
        updateSceneRenderClock()
        if (startRequestedScenePreview(animate)) return
        if (restartLegacyScenePreview(previous, next)) return
        val render = sceneRender
        if (render != null && previous != null && next != null && sceneRenderGl === gl) {
            render.setScene(next, animate)
        } else {
            render?.setScene(null, animate = false)
            privacyComposer.requestRebuild { gl }
        }
    }

    private fun startRequestedScenePreview(animate: Boolean): Boolean {
        if (!sceneRouteRequested() || previewRequest == null || sceneEncoding()) return false
        startIdlePreviewIfReady()
        if (sceneSession == null) return false
        desiredSceneRender = _sceneComposition.value?.let { SceneRenderState(it) }
        sceneRender?.setScene(desiredSceneRender, animate)
        return true
    }

    private fun restartLegacyScenePreview(previous: SceneRenderState?, next: SceneRenderState?): Boolean {
        if (previous != null || next == null || idlePreviewCamera == null ||
            camera?.isStreaming == true || camera?.isRecording == true) return false
        stopIdlePreview()
        startIdlePreviewIfReady()
        return true
    }

    val currentSceneCameraId: String?
        get() = if (sceneSession != null) activeSceneCamera()?.getCurrentCameraId() ?: sceneSelectedCameraId
            else idlePreviewCamera?.getCurrentCameraId() ?: camera?.currentCameraId

    /** Physical camera IDs are stable source locators; labels are resolved by the UI. */
    fun sceneCameraOptions(): List<Pair<String, Int>> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as? android.hardware.camera2.CameraManager
            ?: return emptyList()
        return runCatching {
            manager.cameraIdList.map { id ->
                id to (manager.getCameraCharacteristics(id)
                    .get(android.hardware.camera2.CameraCharacteristics.LENS_FACING) ?: -1)
            }
        }.getOrDefault(emptyList())
    }

    fun scenePreviewSize(): Pair<Int, Int> {
        val size = activeGlInterface()?.encoderSize
        return if (size != null && size.x > 0 && size.y > 0) size.x to size.y else
            (_sceneComposition.value?.canvasWidth ?: 720) to (_sceneComposition.value?.canvasHeight ?: 1280)
    }

    fun retrySceneSource(sourceId: String) { sceneTextures?.retry(sourceId) }

    /** Permission handles are runtime-only and supplied after the normal Android consent flow. */
    fun grantSceneProjection(sourceId: String, projection: android.media.projection.MediaProjection): Boolean {
        if (_sceneComposition.value?.sources?.none {
            it.id == sourceId && it.type == SceneSourceType.SCREEN_CAPTURE
        } != false) {
            projection.stop()
            return false
        }
        sceneProjections.grant(sourceId, projection)
        sceneTextures?.restart(sourceId)
        return true
    }

    fun publishSceneWidget(sourceId: String, bitmap: android.graphics.Bitmap): Boolean {
        if (_sceneComposition.value?.sources?.none { it.id == sourceId && it.type == SceneSourceType.WIDGET } != false) {
            return false
        }
        return try { sceneBitmaps.publish(sourceId, bitmap); true } catch (_: IllegalArgumentException) { false }
    }

    private val privacyComposer = PrivacyComposer(
        filterController = filterController,
        lowLightBoostController = lowLightBoostController,
        lutController = lutController,
        lutSize = LUT_SIZE,
        sceneRenderFactory = ::createSceneRender,
        sceneIsInput = { sceneSession != null },
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

    private fun activeGlInterface(): GlStreamInterface? = sceneSession?.gl ?: when (activeSourceKind.value) {
        VideoSourceKind.CAMERA -> camera?.glInterface as? GlStreamInterface
        VideoSourceKind.SCREEN_CAPTURE -> screenCaptureSource?.glInterface
        VideoSourceKind.VIDEO_PLAYER -> videoPlayerSource?.glInterface
        VideoSourceKind.REPLAY -> replaySource?.glInterface
        VideoSourceKind.UVC -> uvcVideoSource?.glInterface
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
            privacyComposer.requestRebuild { gl }
        }
    }

    /** Wechselt zum nächsten Filter in der Liste (zirkulär). */
    fun nextVideoFilter(): VideoFilter {
        val gl = activeGlInterface() ?: return filterController.activeFilter.value
        return filterController.nextFilter { _ ->
            privacyComposer.requestRebuild { gl }
        }
    }

    /** Setzt den Filter-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetVideoFilter() {
        filterController.resetFilterState()
        privacyComposer.requestRebuild(::activeGlInterface)
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
            privacyComposer.requestRebuild { gl }
        }
    }

    /** Setzt den Low-Light-Boost-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetLowLightBoost() {
        lowLightBoostController.resetState()
        privacyComposer.requestRebuild(::activeGlInterface)
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
            privacyComposer.requestRebuild { gl }
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
            privacyComposer.requestRebuild { gl }
        }
    }

    /** Setzt den LUT-Zustand zurück (z.B. beim Stoppen des Streams). */
    fun resetLut() {
        lutController.resetState()
        privacyComposer.requestRebuild(::activeGlInterface)
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
        val changed = privacyComposer.setPrivacyEnabled(enabled, gl)
        if (changed) {
            // Der Master-Toggle bestimmt mit, ob überhaupt geblurrt wird:
            // ohne ihn läuft die Gesichtserkennung nicht (P2-Ziel ist die
            // sichtbare Unkenntlichmachung, nicht das Erkennen selbst).
            syncFaceDetectionDesire()
            publishPrivacyEllipses()
        }
        return changed
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
        applyPrivacyZones()
    }

    /**
     * Mappt den Soll-Zustand auf die Privacy-Ellipsen (Textur-Koordinaten,
     * Y-Flip) und übergibt sie dem Composer. Ohne GL-Interface ein No-Op —
     * der Aufruf ist dann beim Stream-Start/Quellwechsel nachgeholt.
     */
    private fun applyPrivacyZones() {
        val gl = activeGlInterface() ?: return
        gl.setForceRender(desiredSceneRender != null && sceneSession != null, _activeEncoder.value?.preset?.fps ?: 30)
        publishPrivacyEllipses()
        // Der Toggle-Zustand liegt im Composer; ein Rebuild stellt sicher,
        // dass die Kette den Soll-Zustand (inkl. neuer Zonen) trägt.
        privacyComposer.requestRebuild { gl }
    }

    // --- P2: Automatische Gesichter (Skizze §5, Route A: Hardware) ----------

    /**
     * Soll-Zustand des P2-Toggles „Gesichter unkenntlich machen“ aus der
     * Persistenz (`ZoneRepository.faceBlurEnabledFlow`). Wirkt nur **zusammen**
     * mit dem Master-Toggle — [faceDetectionDesired] zieht beide.
     */
    private val _faceBlurEnabled = MutableStateFlow(false)

    /** P2-Toggle (Default aus; die Erkennung startet beim nächsten attach). */
    val faceBlurEnabled: StateFlow<Boolean> = _faceBlurEnabled.asStateFlow()

    /**
     * Capability der Hardware-Gesichtserkennung: `null` = noch nie versucht,
     * `true` = Gerät liefert Face-Modus, `false` = kein Face-Modus (Emulator
     * oder Kamera ohne `STATISTICS_FACE_DETECT_MODE`). Die manuellen Zonen
     * hängen nicht daran — sie bleiben die Garantie (Skizze §8).
     */
    private val _faceDetectionAvailable = MutableStateFlow<Boolean?>(null)
    val faceDetectionAvailable: StateFlow<Boolean?> = _faceDetectionAvailable.asStateFlow()

    /** Geglättete Gesichts-Ellipsen (leer, solange P2 nicht greift). */
    private var faceEllipses: List<PrivacyEllipse> = emptyList()

    private val faceSmoother = FaceSmoother()

    /**
     * Lebenszyklus der RootEncoder-Erkennung. `lazy`, weil die Defaults über
     * einen Haupt-Handler laufen, der erst beim ersten Versuch existiert —
     * die JVM-Engine-Tests bleiben dadurch ohne Looper lauffähig.
     */
    private val faceProducer by lazy {
        FaceDetectionProducer(
            onAvailableChanged = { available -> _faceDetectionAvailable.value = available },
        )
    }

    /** Drehung der Anzeige in Grad (0/90/180/270) — injizierbar für Tests. */
    internal var displayRotationDegrees: () -> Int = ::readDisplayRotationDegrees

    /** Adapter auf die Face-API des Stream-Encoders (Muster `idlePreviewFactory`). */
    internal var cameraFaceDetectableFactory: (MultiCamera2) -> FaceDetectable = { cam ->
        faceDetectable(
            facing = { cam.cameraFacing },
            enable = { callback -> cam.enableFaceDetection(callback) },
            disable = { cam.disableFaceDetection() },
        )
    }

    /** Adapter auf die Face-API der Idle-Preview-Kamera. */
    internal var idleFaceDetectableFactory: (Camera2ApiManager) -> FaceDetectable = { preview ->
        faceDetectable(
            facing = { preview.cameraFacing },
            enable = { callback -> preview.enableFaceDetection(callback) },
            disable = { preview.disableFaceDetection() },
        )
    }

    /** true, wenn die Erkennung laufen soll: Master- **und** P2-Toggle an. */
    private val faceDetectionDesired: Boolean
        get() = _faceBlurEnabled.value && privacyComposer.privacyEnabled.value

    /**
     * Schaltet P2 „Gesichter unkenntlich machen“ um (Persistenz-Ziel der
     * ViewModel-Schicht; die Engine hält den Soll-Zustand hier im Speicher).
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setFaceBlurEnabled(enabled: Boolean): Boolean {
        if (_faceBlurEnabled.value == enabled) return false
        _faceBlurEnabled.value = enabled
        if (!enabled) {
            faceSmoother.reset()
            faceEllipses = emptyList()
        }
        syncFaceDetectionDesire()
        publishPrivacyEllipses()
        return true
    }

    /**
     * P2b-Soll-Zustand „Ganzbild verpixeln“: das **gesamte** Bild wird mit
     * der groben Mosaik-Stufe unkenntlich gemacht, damit Architektur und
     * Umgebung des Streamers den Standort nicht verraten (Skizze §5, P2b).
     * Wirkt nur zusammen mit dem Master-Toggle — die Privacy-Render-Instanz
     * hängt nur dann in der Kette (Garantie-Postulat, §4).
     */
    private val _fullFramePrivacy = MutableStateFlow(false)

    /** P2b-Toggle (Default aus; reiner Render-Zustand, kein Ketten-Rebuild). */
    val fullFramePrivacy: StateFlow<Boolean> = _fullFramePrivacy.asStateFlow()

    /**
     * Schaltet P2b „Ganzbild verpixeln“ um (Persistenz-Ziel der ViewModel-
     * Schicht). Der Voll-Frame-Modus ist — genau wie die Ellipsen — reiner
     * Uniform-Zustand des Privacy-Renders: kein Ketten-Rebuild, kein Slot-
     * Verbrauch, wirksam ab dem nächsten Frame.
     *
     * @return true, wenn sich der Zustand geändert hat.
     */
    fun setFullFramePrivacy(enabled: Boolean): Boolean {
        if (_fullFramePrivacy.value == enabled) return false
        _fullFramePrivacy.value = enabled
        privacyComposer.setFullFrame(enabled)
        return true
    }

    /** Hält den Erkennungswunsch mit den beiden Toggles synchron. */
    private fun syncFaceDetectionDesire() {
        faceProducer.setDesired(faceDetectionDesired)
    }

    /**
     * Hängt die Erkennung an eine laufende Kamera-Instanz an (Idle-Preview,
     * Stream, Standalone-Aufnahme). Ein nicht gewünschter Wunsch wird beim
     * attach nur gemerkt — der Toggle kann ihn danach ohne Re-Attach an.
     */
    private fun attachFaceDetection(target: FaceDetectable) {
        faceProducer.setDesired(faceDetectionDesired)
        faceProducer.attach(target) { frame -> onFaceFrame(frame) }
    }

    /** Adapter über die beiden RootEncoder-Ziele (Stream-Encoder, Idle-Kamera). */
    private fun faceDetectable(
        facing: () -> CameraHelper.Facing,
        enable: (FaceDetectorCallback) -> Boolean,
        disable: () -> Unit,
    ): FaceDetectable = object : FaceDetectable {
        override fun enableFaceDetection(onFaces: (FaceFrame) -> Unit): Boolean =
            enable(faceCallback(onFaces, facing))

        override fun disableFaceDetection() = disable()
    }

    /** Baut pro Capture-Frame aus RootEncoder-Typen einen [FaceFrame]. */
    private fun faceCallback(
        onFaces: (FaceFrame) -> Unit,
        facing: () -> CameraHelper.Facing,
    ): FaceDetectorCallback = object : FaceDetectorCallback {
        override fun onGetFaces(faces: Array<Face>, scaleSensor: Rect?, sensorOrientation: Int) {
            onFaces(
                FaceFrame(
                    faces = faces.map { face ->
                        val rect = face.rect
                        DetectedFace(
                            left = rect.left,
                            top = rect.top,
                            right = rect.right,
                            bottom = rect.bottom,
                            score = face.score,
                        )
                    },
                    meta = FaceFrameMeta(
                        // Ohne Skalierungs-Rechteck ist keine Normalisierung
                        // möglich — der Mapper verwirft den Frame dann (0×0).
                        activeArrayWidth = scaleSensor?.width() ?: 0,
                        activeArrayHeight = scaleSensor?.height() ?: 0,
                        sensorOrientationDegrees = sensorOrientation,
                        displayRotationDegrees = displayRotationDegrees(),
                        frontFacing = facing() == CameraHelper.Facing.FRONT,
                    ),
                ),
            )
        }
    }

    /**
     * Verarbeitet ein Erkennungs-Frame: mappen (Sensor → Textur), glätten,
     * Slot-Budget rechnen, Composer aktualisieren — **ohne** Ketten-Rebuild,
     * weil Gesichts-Boxen nur Uniforms ändern (Skizze §5).
     */
    private fun onFaceFrame(frame: FaceFrame) {
        if (!faceDetectionDesired) return
        faceEllipses = faceSmoother.update(FaceEllipseMapper.map(frame))
        publishPrivacyEllipses()
    }

    /**
     * Der zuletzt an den Composer übergebene Ellipsen-Satz (Zonen zuerst,
     * dann Gesichter bis zum Slot-Budget). Reine Lesegröße — sie macht die
     * Merge-Regel beobachtbar, ohne an den Render-Zustand zu müssen.
     */
    private val _publishedPrivacyEllipses = MutableStateFlow<List<PrivacyEllipse>>(emptyList())
    internal val publishedPrivacyEllipses: StateFlow<List<PrivacyEllipse>> =
        _publishedPrivacyEllipses.asStateFlow()

    /**
     * Vereinigt Zonen- und Gesichts-Ellipsen auf die 8 Uniform-Slots des
     * Privacy-Shaders: Zonen sind die Garantie und belegen ihre Slots zuerst,
     * Gesichter füllen nur den Rest. Ohne GL ein No-Op.
     */
    private fun publishPrivacyEllipses() {
        val gl = activeGlInterface() ?: return
        val zones = desiredPrivacyZones.value.map { it.toPrivacyEllipse() }
        val budget = (PrivacyBlurFilterRender.MAX_ELLIPSES - zones.size).coerceAtLeast(0)
        val faces = if (faceDetectionDesired) faceEllipses.take(budget) else emptyList()
        _publishedPrivacyEllipses.value = zones + faces
        privacyComposer.setEllipses(_publishedPrivacyEllipses.value)
    }

    /** Drehung der Anzeige relativ zur nativen Orientierung (Risiko #2, §8). */
    private fun readDisplayRotationDegrees(): Int = runCatching {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        when (windowManager.defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
    }.getOrDefault(0)

    companion object {
        /**
         * Fehlerursachen sind String-Ressourcen (i18n), keine Literale: sie
         * erscheinen im Banner, in der Notification und im Chat-Status.
         * Rohmeldungen von RTMP/Encoder laufen weiter über
         * [StreamingState.Failed.reason].
         */
        @StringRes
        private val ENCODER_PREPARATION_ERROR = R.string.streaming_error_encoder_preparation

        /** Startfehler außerhalb der Engine (Foreground-Service, #327). */
        @StringRes
        private val SERVICE_START_ERROR = R.string.streaming_error_service_start

        /** Screen-Capture ohne gültigen MediaProjection-Consent (#326). */
        @StringRes
        private val SCREEN_CAPTURE_CONSENT_REQUIRED = R.string.streaming_error_screen_capture_consent

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
    private var glPreviewOwner: GlStreamInterface? = null
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
            failTarget(index, reason)
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
            if (stoppingStreams) return
            val target = _targetStates.value.getOrNull(index) ?: return
            // Disconnect is also emitted by failed-client cleanup. Keep the cause
            // visible and do not let cleanup callbacks revive/clear an old target.
            if (target.status == StreamTargetStatus.FAILED || target.status == StreamTargetStatus.IDLE) return
            updateTarget(index) { it.copy(status = StreamTargetStatus.IDLE, failureReason = null, bitrateKbps = null) }
            finishEncodersIfNoActiveTargets()
        }

        override fun onAuthError() {
            failTarget(index, "RTMP Auth Error")
        }

        override fun onAuthSuccess() {
            // Optional: Handle auth success
        }
    }

    private fun createLegacyFileTargetChecker(index: Int): ConnectChecker {
        val generation = legacyFileGeneration
        val target = createTargetChecker(index)
        fun deliver(action: () -> Unit) { if (legacyFileGeneration == generation) action() }
        return object : ConnectChecker {
            override fun onConnectionStarted(url: String) = deliver { target.onConnectionStarted(url) }
            override fun onConnectionSuccess() = deliver { target.onConnectionSuccess() }
            override fun onConnectionFailed(reason: String) = deliver { target.onConnectionFailed(reason) }
            override fun onNewBitrate(bitrate: Long) = deliver { target.onNewBitrate(bitrate) }
            override fun onDisconnect() = deliver { target.onDisconnect() }
            override fun onAuthError() = deliver { target.onAuthError() }
            override fun onAuthSuccess() = deliver { target.onAuthSuccess() }
        }
    }

    private fun failTarget(index: Int, reason: String) {
        if (stoppingStreams) return
        val target = _targetStates.value.getOrNull(index) ?: return
        if (target.status == StreamTargetStatus.IDLE || target.status == StreamTargetStatus.FAILED) return
        updateTarget(index) { it.copy(status = StreamTargetStatus.FAILED, failureReason = reason, bitrateKbps = null) }
        stopTargetStream(index)
        finishEncodersIfNoActiveTargets()
    }

    private fun stopTargetStream(index: Int) {
        sceneSession?.let { it.stopTarget(index); return }
        when (activeSourceKind.value) {
            VideoSourceKind.CAMERA -> camera?.stopStream(MultiType.RTMP, index)
            VideoSourceKind.SCREEN_CAPTURE -> screenCaptureSource?.stopStream(index)
            VideoSourceKind.VIDEO_PLAYER -> videoPlayerSource?.stopStream(index)
            VideoSourceKind.REPLAY -> replaySource?.stopStream(index)
            VideoSourceKind.UVC -> uvcVideoSource?.stopStream(index)
        }
    }

    private fun finishEncodersIfNoActiveTargets() {
        if (_targetStates.value.any { it.status == StreamTargetStatus.STREAMING || it.status == StreamTargetStatus.PREPARING }) return
        stopSharedStreamEncoder()
        adaptiveController = null
        startIdlePreviewIfReady()
    }

    /** RootEncoder 2.7.5's indexed stop tests clients BEFORE disconnecting them.
     * Explicitly finish the shared encoder after disconnecting the last target.
     * Camera2Base preserves an ongoing local recording in this overload.
     */
    private fun stopSharedStreamEncoder() {
        sceneSession?.let { session ->
            _targetStates.value.indices.forEach { runCatching { session.stopTarget(it) } }
            return
        }
        when (activeSourceKind.value) {
            VideoSourceKind.CAMERA -> camera?.let { cam ->
                if (cam.isStreaming) {
                    if (!cam.isRecording) {
                        // Stop both capture paths and face detection when the final consumer leaves.
                        faceProducer.detach()
                        closeExperimentalCapture()
                        detachGlPreview()
                    }
                    Timber.i("Stopping shared camera encoder after last RTMP target")
                    cam.stopStream()
                }
            }
            VideoSourceKind.SCREEN_CAPTURE -> screenCaptureSource?.stop()
            VideoSourceKind.VIDEO_PLAYER -> videoPlayerSource?.stop()
            VideoSourceKind.REPLAY -> replaySource?.stop()
            VideoSourceKind.UVC -> uvcVideoSource?.stop()
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
        val live = states.any { it.status == StreamTargetStatus.STREAMING }
        if (live) {
            if (_streamStartedAtMs.value == null) _streamStartedAtMs.value = timeSource()
        } else {
            _streamStartedAtMs.value = null
        }
        _streamingState.value = when {
            states.any { it.status == StreamTargetStatus.STREAMING } -> StreamingState.Streaming
            states.any { it.status == StreamTargetStatus.PREPARING } -> StreamingState.Preparing
            else -> {
                val failed = states.firstOrNull { it.status == StreamTargetStatus.FAILED }
                if (failed != null) {
                    // Rohmeldung der Library, sonst lokalisierter Fallback.
                    val rawReason = failed.failureReason
                    StreamingState.Failed(
                        reason = rawReason,
                        reasonRes = if (rawReason == null) R.string.streaming_error_unknown else null,
                    )
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
        if (_cameraModeStatus.value.preparing) return false
        if (kind != activeSourceKind.value && legacyEncoding() && camera?.isStreaming == true) return false
        if (sceneSession != null || (_sceneComposition.value != null && !legacyEncoding())) {
            return switchSceneBaseSource(kind)
        }
        val switched = switchLegacySource(kind)
        if (switched && kind != VideoSourceKind.CAMERA) {
            if (_replayState.value is ReplayState.Recording) stopReplay()
            stopIdlePreview()
        }
        // P1: Quellwechsel — die Zonen sind quellrelativ; der Soll-Zustand
        // wird auf die neue Quelle angewendet (relative Bildmitte bleibt
        // erhalten; Persistenz je Quelle prüft P3, Skizze §9).
        if (switched && _sceneComposition.value != null) {
            updateSceneBaseSource(kind)
            setSceneComposition(_sceneComposition.value, animate = false)
        }
        applyPrivacyZones()
        return switched
    }

    private fun switchLegacySource(kind: VideoSourceKind): Boolean {
        if (kind == VideoSourceKind.CAMERA) return videoSourceRegistry.switchTo(kind)
        val source =
            when (kind) {
                VideoSourceKind.SCREEN_CAPTURE -> ensureScreenCaptureSource()
                VideoSourceKind.VIDEO_PLAYER -> ensureVideoPlayerSource()
                VideoSourceKind.REPLAY -> ensureReplaySource()
                VideoSourceKind.UVC -> videoSourceRegistry.activeSource.value?.takeIf { it.kind == kind }
                VideoSourceKind.CAMERA -> null
            } ?: return false
        videoSourceRegistry.registerFactory(kind) { requested -> source.takeIf { requested == kind } }
        return videoSourceRegistry.switchTo(kind)
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
        val player = playerFactory.create(List(MAX_STREAM_TARGETS) { createLegacyFileTargetChecker(it) })
        return VideoPlayerVideoSource(context, player).also { videoPlayerSource = it }
    }

    /**
     * Erzeugt die Replay-Quelle einmalig (wie [ensureVideoPlayerSource] für den
     * Video-Player) — ein [com.pedro.library.multiple.MultiFromFile] mit einem
     * ConnectChecker pro Stream-Ziel, der die Ziel-Status der Engine aktualisiert.
     */
    private fun ensureReplaySource(): ReplayVideoSource? {
        if (replaySource != null) return replaySource
        val player = playerFactory.create(List(MAX_STREAM_TARGETS) { createLegacyFileTargetChecker(it) })
        return ReplayVideoSource(context, player).also { replaySource = it }
    }

    internal var sceneFileReadable: (String) -> Boolean = {
        com.vivid.feature.streaming.compositor.capture.sceneVideoFileReadable(context, it)
    }

    /**
     * S3: setzt die abzuspielende Video-Datei (Content-Uri aus dem SAF-Picker)
     * für die Video-Player-Quelle.
     *
     * @return true, wenn die Datei gesetzt und die Encoder vorbereitet werden konnten.
     */
    fun setVideoPlayerUri(uri: Uri): Boolean {
        if (sceneRouteRequested()) {
            if (uri.scheme !in listOf(null, "content", "file") || !sceneFileReadable(uri.toString())) return false
            val previous = sceneVideoUri
            sceneVideoUri = uri
            if (activeSourceKind.value == VideoSourceKind.VIDEO_PLAYER && !updateSceneBaseSource(VideoSourceKind.VIDEO_PLAYER)) {
                sceneVideoUri = previous
                return false
            }
            return true
        }
        val ready = ensureVideoPlayerSource()?.setVideo(uri) ?: false
        if (ready) {
            sceneVideoUri = uri
            if (_sceneComposition.value != null) updateSceneBaseSource(VideoSourceKind.VIDEO_PLAYER)
        }
        return ready
    }

    /**
     * Setzt die abzuspielende Replay-Datei für die Replay-Quelle und wechselt auf
     * sie. Schlägt die Vorbereitung fehl (korrupte/fehlende Datei), bleibt die
     * aktive Quelle unverändert.
     *
     * @return true, wenn die Datei gesetzt wurde und die Quelle aktiv ist.
     */
    fun useReplayAsSource(file: File): Boolean {
        if (!file.isFile || !file.canRead()) return false
        if (sceneRouteRequested()) {
            if (!sceneFileReadable(file.absolutePath)) return false
            val previous = sceneReplayFile
            sceneReplayFile = file
            if (switchSource(VideoSourceKind.REPLAY)) return true
            sceneReplayFile = previous
            return false
        }
        val source = ensureReplaySource() ?: return false
        if (!source.setReplay(file)) return false
        sceneReplayFile = file
        return switchSource(VideoSourceKind.REPLAY)
    }

    /** Die Replay-Datei der aktiven Replay-Quelle (null, wenn keine aktiv/geladen). */
    val activeReplayFile: File?
        get() {
            val selected = sceneReplayFile
            if (selected != null) return selected
            val source = replaySource ?: return null
            return source.replayFile
        }

    /**
     * S2: liefert den MediaProjection-Consent-Intent der Screen-Capture-Quelle
     * (System-Dialog „Bildschirm übertragen“). null, solange die Quelle nicht
     * initialisiert ist (vorher [switchSource](SCREEN_CAPTURE) aufrufen).
     */
    fun createScreenCaptureConsentIntent(): Intent? {
        if (_sceneComposition.value != null && !legacyEncoding()) {
            requestedSceneProjectionId = primarySceneScreenId() ?: return null
            return (context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager)
                .createScreenCaptureIntent()
        }
        return screenCaptureSource?.createConsentIntent()
    }

    /**
     * S2: übergibt das Ergebnis des MediaProjection-Consent-Dialogs an die
     * Screen-Capture-Quelle.
     *
     * @return true, wenn der Nutzer zugestimmt hat (RESULT_OK + Daten vorhanden).
     */
    fun onScreenCaptureConsentResult(resultCode: Int, data: Intent?): Boolean {
        val sourceId = requestedSceneProjectionId
        requestedSceneProjectionId = null
        if (sourceId != null) {
            if (resultCode != android.app.Activity.RESULT_OK || data == null ||
                _sceneComposition.value?.sources?.none { it.id == sourceId && it.type == SceneSourceType.SCREEN_CAPTURE } != false) return false
            pendingSceneProjection = sourceId to data
            return true
        }
        return screenCaptureSource?.onConsentResult(resultCode, data) == true
    }

    /**
     * true, wenn der nächste Start der Screen-Capture-Quelle einen neuen
     * MediaProjection-Consent braucht (#326).
     *
     * Der Consent ist ab Android 14 ein einmaliges Token: nach einem Start oder
     * Stop ist er verbraucht. UI und Service starten deshalb erst nach einem
     * frischen Dialog; ohne ihn würde die Projektion sofort sterben (#327).
     * Für alle anderen Quellen immer false.
     */
    fun needsScreenCaptureConsent(): Boolean {
        if (activeSourceKind.value != VideoSourceKind.SCREEN_CAPTURE) return false
        if (_sceneComposition.value != null && !legacyEncoding()) {
            val id = primarySceneScreenId() ?: return false
            return !sceneProjections.hasGrant(id) && pendingSceneProjection?.first != id
        }
        return screenCaptureSource?.requiresFreshConsent() ?: true
    }

    /**
     * Erstellt die Kamera einmalig, view-unabhängig.
     *
     * Wird beim Anzeigen des Streaming-Screens aufgerufen; da die Engine ein
     * Singleton ist, wird die bestehende Instanz bei einem Activity-Recreate
     * (Rotation, Recents) nicht ersetzt — der laufende Stream bleibt erhalten.
     */
    private fun autofocusOperation(
        operation: (Camera2ApiManager) -> Boolean,
        fallback: () -> Boolean,
    ): Boolean {
        val sceneCamera = activeSceneCamera()
        if (sceneCamera != null) return operation(sceneCamera)
        if (sceneSession != null) return false
        return idlePreviewCamera?.let(operation) ?: fallback()
    }

    private fun initializeCameraControls() {
        val encoderControls = RootEncoderCameraControls(camera!!)
        cameraControls =
            ActiveCameraControls {
                if (sceneSession != null) {
                    activeSceneCamera()?.let(::Camera2PreviewControls)
                } else {
                    idlePreviewCamera?.let(::Camera2PreviewControls) ?: encoderControls
                }
            }
        focusController =
            CameraFocusController(
                object : FocusableCamera {
                    override fun enableAutoFocus() = autofocusOperation({ it.enableAutoFocus() }) { camera!!.enableAutoFocus() }

                    override fun disableAutoFocus() = autofocusOperation({ it.disableAutoFocus() }) { camera!!.disableAutoFocus() }

                    override fun isAutoFocusEnabled() = autofocusOperation({ it.isAutoFocusEnabled }) { camera!!.isAutoFocusEnabled }

                    override fun setFocusDistance(distance: Float) {
                        cameraControls!!.setFocusDistance(distance)
                    }
                },
            )
        stabilizationController =
            CameraStabilizationController(cameraControls!!).also {
                _stabilizationEnabled.value = it.isEnabled
            }
        _torchEnabled.value = cameraControls!!.isTorchEnabled()

        // Manuelle Kamera-Steuerung initialisieren
        val lensController = CameraLensController(cameraControls!!)
        manualCameraController =
            ManualCameraController(cameraControls!!, lensController).also {
                it.syncState()
            }
        refreshCameraControls(opened = false)
    }

    fun initializeCamera() {
        if (camera == null) {
            camera = cameraFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
            camera!!.setCameraCallbacks(object : com.pedro.encoder.input.video.CameraCallbacks {
                override fun onCameraOpened() { sceneCameraAvailable.set(true) }
                override fun onCameraChanged(facing: CameraHelper.Facing) = Unit
                override fun onCameraError(error: String) { sceneCameraAvailable.set(false) }
                override fun onCameraDisconnected() { sceneCameraAvailable.set(false) }
            })
            camera!!.setRecordController(monitorKeyframes(AndroidMuxerRecordController()))
            camera!!.setFpsListener { fps ->
                if (_activeEncoder.value != null) _measuredEncoderFps.value = fps
            }
            initializeCameraControls()

        }
    }

    /**
     * Schaltet zwischen Autofokus und Fokus-Lock (Unendlich) um (Moblin #377).
     *
     * @return true, wenn der neue Modus von der Kamera übernommen wurde.
     */
    fun toggleFocusLock(): Boolean {
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return false
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
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return
        val controls = cameraControls ?: return
        val range = controls.getZoomRange() ?: return
        controls.setZoom(ZoomCalculator.zoomForScale(controls.getZoom(), scaleFactor, range))
    }

    /** Setzt den Zoom auf 1.0 zurück (z. B. per Doppeltipp). */
    fun resetZoom() {
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return
        val controls = cameraControls ?: return
        val range = controls.getZoomRange() ?: return
        controls.setZoom(ZoomCalculator.clamp(ZoomCalculator.MIN_ZOOM, range))
    }

    /** Tap-to-Focus auf die getippte Stelle der Kamera-Vorschau. */
    fun tapToFocus(view: View, event: MotionEvent) {
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return
        cameraControls?.tapToFocus(view, event)
    }

    /**
     * Schaltet die Video-Stabilisierung (OIS bevorzugt, sonst EIS) um.
     *
     * @return true, wenn die Kamera den neuen Zustand übernommen hat.
     */
    fun toggleStabilization(): Boolean {
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return false
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
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return false
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
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) return
        manualCameraController?.setFocusDistance(distance)
    }

    /** Wechselt auf die Linse mit der angegebenen ID. */
    fun selectLens(lensId: String): Boolean {
        if (_cameraModeStatus.value.preparing || experimentalCapture != null) return false
        if (sceneSession != null) {
            val primary = primarySceneCamera() ?: return false
            val facing = sceneCameraOptions().firstOrNull { it.first == lensId }?.second ?: return false
            val graph = requireNotNull(_sceneComposition.value)
            val type = if (facing == android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL)
                SceneSourceType.UVC else SceneSourceType.CAMERA
            val replacement = com.vivid.feature.streaming.compositor.rebindSceneSourceLayers(
                graph, primary.id, type, lensId, ::sceneCameraIdentity) ?: return false
            sceneSelectedCameraId = lensId
            setSceneComposition(replacement.graph, animate = false)
            _cameraSelectionVersion.value++
            return true
        }
        return (manualCameraController?.selectLens(lensId) ?: false).also {
            if (it) _cameraSelectionVersion.value++
        }
    }

    /** Verfügbare Linsen. */
    fun getAvailableLenses(): List<LensInfo> = manualCameraController?.getAvailableLenses() ?: emptyList()

    /**
     * Startet eine lokale MP4-Aufnahme mit oder ohne aktiven Stream.
     *
     * @param includeAudio true = Bild + Ton (Standard), false = nur Bild
     *   (ReplayAudioMode.VIDEO_ONLY — der Muxer schreibt keine Audiospur).
     */
    fun canRecordReplay(): Boolean = sceneRouteRequested() || activeSourceKind.value == VideoSourceKind.CAMERA

    fun startReplay(nowMillis: Long = System.currentTimeMillis(), includeAudio: Boolean = true): Boolean {
        if (_cameraModeStatus.value.preparing) return false
        if (sceneRouteRequested()) return startSceneRecording(nowMillis, includeAudio)
        if (requestedCameraMode == CameraMode.EXPERIMENTAL_60_FPS && camera?.isStreaming != true) {
            if (camera == null || activeSourceKind.value != VideoSourceKind.CAMERA || camera?.isRecording == true) return false
            beginCameraPreparation {
                if (!startReplayNow(nowMillis, includeAudio)) {
                    _cameraModeStatus.value = _cameraModeStatus.value.copy(notice = CameraModeNotice.RECORDING_FAILED)
                }
            }
            return true // Accepted asynchronous preparation; failure is exposed through cameraModeStatus.
        }
        return startReplayNow(nowMillis, includeAudio)
    }

    private fun startReplayNow(nowMillis: Long, includeAudio: Boolean): Boolean {
        val cam = camera ?: return false
        if (activeSourceKind.value != VideoSourceKind.CAMERA || _replayState.value is ReplayState.Recording) return false
        val standalone = !cam.isStreaming
        try {
            if (standalone && !prepareStandaloneRecording(cam, includeAudio)) return false
            if (_cameraModeStatus.value.preparing && preparationGeneration != captureGeneration.get()) {
                closeExperimentalCapture()
                cam.stopStream()
                return false
            }
            val controller = recordingController(cam, includeAudio)
            if (!controller.start(nowMillis)) {
                restorePreviewAfterRecordingFailure(cam, standalone)
                return false
            }
            // RootEncoder prepares GL again when its encoders start, restoring Camera2's rotation.
            externalCapture?.restoreInputOrientation()
            applyPrivacyZones()
            // Standalone recording owns the face-detection lifecycle when no stream is running.
            if (standalone) attachFaceDetection(cameraFaceDetectableFactory(cam))
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
        _bitrateDiagnostics.value = null
        resetKeyframeMonitoring()
        syncSelectedCamera(cam)
        stopIdlePreview()
        val audioReady = prepareStandaloneRecordingAudio(cam, includeAudio)
        encoderRotationDegrees = if (_encoderConfig.value == null) CameraHelper.getCameraOrientation(context) else 0
        val videoReady = prepareCameraVideo(cam)
        if (audioReady && videoReady && updateBitrateDiagnostics(cam)) return true
        closeExperimentalCapture()
        (cam.glInterface as? GlStreamInterface)?.stop()
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
        closeExperimentalCapture()
        runCatching { cam.stopRecord() }
        _replayState.value = ReplayState.Idle
        startIdlePreviewIfReady()
    }

    /** Stoppt die lokale Replay-Aufnahme und gibt die Datei zurück. */
    fun stopReplay(): java.io.File? {
        if (sceneSession?.isRecording == true) {
            val file = sceneReplayController?.stop()
            startIdlePreviewIfReady()
            return file
        }
        if (_cameraModeStatus.value.preparing) {
            captureGeneration.incrementAndGet()
            return null
        }
        if (camera?.isStreaming != true) closeExperimentalCapture()
        if (camera?.isStreaming != true) detachGlPreview()
        val file = replayController?.stop()
        startIdlePreviewIfReady()
        return file
    }

    /** Entfernt alte Replay-Dateien gemäß der Aufbewahrungsgrenze. */
    fun pruneReplays() {
        sceneReplayController?.prune() ?: replayController?.prune()
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
        if (experimentalCapture != null) return
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
        if (experimentalCapture != null) return
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
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) false else (manualCameraController?.setAutoExposure(enabled) ?: false).also { refreshCameraControls() }

    fun setExposure(value: Int): Boolean =
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) false else (manualCameraController?.setExposure(value) ?: false).also { refreshCameraControls() }

    fun setAutoWhiteBalance(enabled: Boolean): Boolean =
        if (experimentalCapture != null || _cameraModeStatus.value.preparing) false else (manualCameraController?.setAutoWhiteBalance(enabled) ?: false).also { refreshCameraControls() }

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
        if (sceneRouteRequested()) {
            if (sceneEncoding()) attachPreviewIfRunning() else startIdlePreviewIfReady()
        } else if (activeSourceKind.value == VideoSourceKind.CAMERA && camera?.isStreaming != true && camera?.isRecording != true) {
            startIdlePreviewIfReady()
        } else {
            attachPreviewIfRunning()
        }
    }

    /** Open Camera2 into the same GL pipeline used by recording, without starting encoders. */
    private fun legacyIdlePreviewAllowed() = activeSourceKind.value == VideoSourceKind.CAMERA &&
        camera?.isStreaming != true && camera?.isRecording != true &&
        context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    private fun idlePreviewCallbacks(preview: Camera2ApiManager): com.pedro.encoder.input.video.CameraCallbacks =
        object : com.pedro.encoder.input.video.CameraCallbacks {
            override fun onCameraOpened() {
                if (idlePreviewCamera === preview) {
                    sceneCameraAvailable.set(true)
                    restoreCameraControls()
                    _cameraSelectionVersion.value++
                }
            }

            override fun onCameraChanged(facing: CameraHelper.Facing) = Unit

            override fun onCameraError(error: String) {
                sceneCameraAvailable.set(false)
                Timber.e("Idle camera: %s", error)
            }

            override fun onCameraDisconnected() {
                sceneCameraAvailable.set(false)
            }
        }

    fun startIdlePreviewIfReady() {
        updateSceneRenderClock()
        val request = previewRequest ?: return
        if (_cameraModeStatus.value.preparing) return
        if (request.width <= 0 || request.height <= 0) return
        if (sceneRouteRequested()) { startScenePreview(request); return }
        if (!legacyIdlePreviewAllowed()) return
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
            gl.setForceRender(false, 30)
            gl.start()
            val preview = idlePreviewFactory(context)
            idlePreviewCamera = preview
            preview.setCameraCallbacks(idlePreviewCallbacks(preview))
            preview.prepareCamera(gl.surfaceTexture, 1280, 720, 30)
            val cameraId = camera?.currentCameraId
            if (cameraId.isNullOrBlank()) preview.openCameraBack() else preview.openCameraId(cameraId)
            idlePreviewSurface = request.surface
            idlePreviewSize = size
            attachPreviewIfRunning()
            // Kamera wird gerade geöffnet: der Producer versucht das Einschalten
            // der Erkennung nach, bis das Input-Surface steht (Risiko #1).
            attachFaceDetection(idleFaceDetectableFactory(preview))
            privacyComposer.requestRebuild { gl }
            restoreCameraControls()
        } catch (error: Exception) {
            stopIdlePreview()
            Timber.e(error, "Could not open idle camera preview")
        }
    }

    private fun stopIdlePreview() {
        if (sceneSession != null && !sceneEncoding()) sceneSession?.stopPreview()
        idlePreviewSurface = null
        idlePreviewSize = null
        if (idlePreviewCamera != null) {
            // Erkennung zuerst abschalten, solange die Kamera noch offen ist
            // (Callbacks hängen am Gerät) — laufende Nachversuche fallen weg.
            faceProducer.detach()
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
        sceneSession?.stopPreview()
        (glPreviewOwner ?: (camera?.glInterface as? GlStreamInterface))?.deAttachPreview()
        glPreviewOwner = null
        glPreviewSurface = null
        updateSceneRenderClock()
    }

    private fun detachGlPreview() {
        if (glPreviewSurface == null) return
        sceneSession?.stopPreview()
        (glPreviewOwner ?: (camera?.glInterface as? GlStreamInterface))?.deAttachPreview()
        glPreviewOwner = null
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
        if (activeUrls.isEmpty() || _cameraModeStatus.value.preparing) return

        if (_streamingState.value is StreamingState.Streaming ||
            _streamingState.value is StreamingState.Preparing
        ) {
            return
        }

        if (sceneRouteRequested()) { startSceneStream(activeUrls); return }
        when (activeSourceKind.value) {
            VideoSourceKind.SCREEN_CAPTURE -> {
                val source = screenCaptureSource ?: return
                // #326: Das MediaProjection-Token ist einmalig verwendbar — ohne
                // frischen Consent würde die Projektion (und mit ihr der Service)
                // beim Start sterben (#327). Klar melden statt blind zu starten.
                if (source.requiresFreshConsent()) {
                    failStream(SCREEN_CAPTURE_CONSENT_REQUIRED)
                    return
                }
                startSourceStream(source, activeUrls, source::startStream)
            }
            VideoSourceKind.VIDEO_PLAYER -> startLegacyVideoStream(activeUrls)
            VideoSourceKind.REPLAY -> startLegacyReplayStream(activeUrls)
            VideoSourceKind.UVC -> uvcVideoSource?.let { source ->
                startSourceStream(source, activeUrls, source::startStream)
            }
            VideoSourceKind.CAMERA -> {
                if (requestedCameraMode == CameraMode.EXPERIMENTAL_60_FPS && camera?.isRecording != true) {
                    if (camera == null) return
                    _streamingState.value = StreamingState.Preparing
                    beginCameraPreparation { startCameraStream(activeUrls) }
                } else startCameraStream(activeUrls)
            }
        }
    }

    private fun selectLegacyFileSource(source: com.vivid.feature.streaming.source.VideoSource) {
        videoSourceRegistry.registerFactory(source.kind) { requested -> if (requested == source.kind) source else null }
        videoSourceRegistry.switchTo(source.kind)
    }

    private fun failLegacyFilePreparation(urls: List<String>) {
        _targetStates.value = urls.map { StreamTargetState(it, StreamTargetStatus.FAILED) }
        failStream(ENCODER_PREPARATION_ERROR)
    }

    private fun startLegacyVideoStream(urls: List<String>) {
        val source = ensureVideoPlayerSource() ?: return
        if (!source.isVideoSet) {
            val uri = sceneVideoUri
            if (uri == null || !source.setVideo(uri)) { failLegacyFilePreparation(urls); return }
        }
        selectLegacyFileSource(source)
        startSourceStream(source, urls, source::startStream)
    }

    private fun startLegacyReplayStream(urls: List<String>) {
        val source = ensureReplaySource() ?: return
        if (!source.isVideoSet) {
            val file = sceneReplayFile
            if (file == null || !source.setReplay(file)) { failLegacyFilePreparation(urls); return }
        }
        selectLegacyFileSource(source)
        startSourceStream(source, urls, source::startStream)
    }

    private fun startSourceStream(
        source: com.vivid.feature.streaming.source.VideoSource?,
        urls: List<String>,
        startTarget: (Int, String) -> Unit,
    ) {
        if (source == null || source.isActive) return
        _targetStates.value = urls.map { StreamTargetState(it, status = StreamTargetStatus.PREPARING) }
        _streamingState.value = StreamingState.Preparing
        if (!source.start()) {
            failStream(ENCODER_PREPARATION_ERROR)
            return
        }
        applyPrivacyZones()
        urls.forEachIndexed(startTarget)
    }

    private fun startCameraStream(activeUrls: List<String>) {
        // Recording and RTMP share the already prepared camera/GL/encoder path.
        val cam = camera ?: return
        if (cam.isStreaming) return
        if (!cam.isRecording) resetKeyframeMonitoring()
        syncSelectedCamera(cam)
        stopIdlePreview()

        _targetStates.value = activeUrls.map { StreamTargetState(it, status = StreamTargetStatus.PREPARING) }
        _streamingState.value = StreamingState.Preparing

        if (prepareStreamEncoders(cam)) {
            if (_cameraModeStatus.value.preparing && preparationGeneration != captureGeneration.get()) {
                closeExperimentalCapture()
                cam.stopStream()
                _streamingState.value = StreamingState.Idle
                return
            }
            resetAdaptiveBitrate()
            // P1: Anonymisierung — persistierter Soll-Zustand ab dem ersten
            // Frame (Composer-Zustand überlebt stopStream bewusst).
            applyPrivacyZones()
            activeUrls.forEachIndexed { index, url ->
                cam.startStream(MultiType.RTMP, index, url)
            }
            externalCapture?.restoreInputOrientation()
            // RootEncoder opens Camera2 during startStream; attach after that transition.
            attachPreviewIfRunning()
            attachFaceDetection(cameraFaceDetectableFactory(cam))
            restoreCameraControls()
        } else {
            val hadExperimentalCapture = experimentalCapture != null
            closeExperimentalCapture()
            if (hadExperimentalCapture) (cam.glInterface as? GlStreamInterface)?.stop()
            if (_cameraModeStatus.value.preparing && preparationGeneration != captureGeneration.get()) {
                _streamingState.value = StreamingState.Idle
                return
            }
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

    private fun prepareCameraVideo(cam: MultiCamera2): Boolean {
        _measuredEncoderFps.value = null
        _activeEncoder.value = null
        val generation = if (_cameraModeStatus.value.preparing) preparationGeneration else captureGeneration.get()
        if (generation != captureGeneration.get()) return false
        _cameraModeStatus.value = _cameraModeStatus.value.copy(applied = CameraMode.STANDARD, notice = null)
        if (requestedCameraMode == CameraMode.EXPERIMENTAL_60_FPS) {
            privacyComposer.preserveExternalFilters = true
            val requested = _encoderConfig.value ?: ResolvedEncoderConfig(VideoCodecPreference.AUTO, EncoderPreset.FHD30, false)
            val profile = runCatching { experimentalProfile(requested, cam.cameraCharacteristics) }.getOrNull()
            if (profile != null) {
                val prepared = runCatching {
                    check(prepareResolvedVideo(cam, profile.output))
                    val adapter = externalCaptureFactory(cam).also { externalCapture = it }
                    val gl = adapter.prepare(profile.output.preset.width, profile.output.preset.height)
                    privacyComposer.requestRebuild(gl)
                    val capture = experimentalCaptureFactory(cam.currentCameraId, profile, gl.surface) { error ->
                        Timber.e(error, "Experimental camera capture failed")
                        captureScope.launch {
                            if (experimentalCapture != null && generation == captureGeneration.get() &&
                                _cameraModeStatus.value.applied == CameraMode.EXPERIMENTAL_60_FPS && !_cameraModeStatus.value.preparing
                            ) {
                                stopReplay()
                                stopStream()
                                _cameraModeStatus.value = CameraModeStatus(notice = CameraModeNotice.CAPTURE_FAILED)
                            }
                        }
                    }.also { experimentalCapture = it }
                    check(capture.prepare()) { "High-speed bridge produced no decoded frames" }
                    check(generation == captureGeneration.get()) { "Camera preparation cancelled" }
                    adapter.claimCamera()
                    _cameraModeStatus.value = _cameraModeStatus.value.copy(applied = CameraMode.EXPERIMENTAL_60_FPS)
                    true
                }.getOrElse {
                    Timber.w(it, "Experimental camera preparation failed; returning to standard capture")
                    closeExperimentalCapture()
                    (cam.glInterface as? GlStreamInterface)?.stop()
                    false
                }
                if (prepared) {
                    encoderRotationDegrees = 0 // The resolved bridge output always uses native landscape axes.
                    return true
                }
            }
            if (generation != captureGeneration.get()) return false
            // A capture fallback must retain the requested mode's GL overlay preservation.
            _cameraModeStatus.value = _cameraModeStatus.value.copy(
                applied = CameraMode.STANDARD,
                notice = if (profile == null) CameraModeNotice.UNSUPPORTED else CameraModeNotice.PREPARATION_FAILED,
            )
            // Capture fallback is explicit, independent of the requested strict encoder setting.
            val requestedStandard = _encoderConfig.value
            if (requestedStandard != null) {
                val fallback = resolveCameraStreamProfile(requestedStandard, true, captureCapabilities(cam), encoderCapabilities)
                    ?: return false
                return prepareResolvedVideo(cam, fallback)
            }
        }
        return if (_encoderConfig.value == null) cam.prepareVideo() else applyEncoderPreset(cam)
    }

    private fun prepareStreamEncoders(cam: MultiCamera2): Boolean {
        // An ongoing local recording already owns the running encoders.
        val recording = cam.isRecording
        if (!recording) _bitrateDiagnostics.value = null
        if (!recording) {
            encoderRotationDegrees = if (_encoderConfig.value == null) CameraHelper.getCameraOrientation(context) else 0
        }
        val audioReady = recording || cam.prepareAudio() == true
        val videoReady = if (recording) {
            true
        } else {
            prepareCameraVideo(cam)
        }
        return audioReady && videoReady && (recording || updateBitrateDiagnostics(cam))
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
        return prepareResolvedVideo(cam, resolved, codec)
    }

    private fun prepareResolvedVideo(cam: Camera2Base, resolved: ResolvedEncoderConfig,
        codec: VideoCodec = when (resolved.codec) {
            VideoCodecPreference.H265 -> VideoCodec.H265
            VideoCodecPreference.AV1 -> VideoCodec.AV1
            else -> VideoCodec.H264
        },
    ): Boolean {
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

    private fun updateBitrateDiagnostics(cam: Camera2Base): Boolean {
        _bitrateDiagnostics.value = null
        return runCatching {
            val diagnostics = readBitrateDiagnostics(cam)
            _bitrateDiagnostics.value = diagnostics
            Timber.i("Video encoder=%s mode=%s CBR supported=%s target=%d kbps",
                diagnostics.encoderName, diagnostics.mode, diagnostics.cbrSupported, diagnostics.targetKbps)
            if (diagnostics.mode != EncoderBitrateMode.CBR) {
                Timber.w("CBR unavailable: encoder=%s uses %s", diagnostics.encoderName, diagnostics.mode)
            }
            true
        }.getOrElse {
            Timber.e(it, "Cannot verify video encoder bitrate mode")
            false
        }
    }

    private fun resetKeyframeMonitoring() {
        keyframeController.reset()
        _measuredEncoderFps.value = null
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
        if (!canChangeCameraMode()) return
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

    /** Publish a localized failure and reset the live session clock. */
    private fun failStream(@StringRes reasonRes: Int) {
        _streamStartedAtMs.value = null
        _streamingState.value = StreamingState.Failed(reasonRes = reasonRes)
        _targetStates.value = _targetStates.value.map {
            it.copy(status = StreamTargetStatus.FAILED)
        }
    }

    /**
     * Meldet einen Startfehler von außerhalb der Engine (Service, #327).
     *
     * Der Foreground-Service fängt Exceptions aus `onStartCommand` ab, damit der
     * Prozess nicht als „Unable to start service“ stirbt. Damit die UI trotzdem
     * einen Failed-Status statt eines stillen Idle-Zustands sieht, wird die
     * lokalisierte Ursache hier in den Zustand geschrieben.
     */
    fun reportStreamStartFailure() {
        failStream(SERVICE_START_ERROR)
    }

    /** Einfacher Einstieg für genau ein Ziel. */
    fun startStream(url: String) {
        startStream(listOf(url))
    }

    /** Hängt die gemerkte Preview-Surface an, sobald die GL-Pipeline läuft. */
    private fun attachPreviewIfRunning() {
        updateSceneRenderClock()
        val request = previewRequest ?: return
        val composed = _sceneComposition.value != null
        val gl = if (composed) activeGlInterface() else camera?.glInterface as? GlStreamInterface
        if (gl?.isRunning == true) {
            if (glPreviewSurface !== request.surface || glPreviewOwner !== gl) {
                glPreviewOwner?.deAttachPreview()
                if (sceneSession != null) sceneSession?.startPreview(request.surface, request.width, request.height)
                else gl.attachPreview(request.surface)
                glPreviewSurface = request.surface
                glPreviewOwner = gl
            }
            gl.setPreviewResolution(request.width, request.height)
            if (composed) {
                // Editor geometry refers to the complete FBO, including its canvas letterbox.
                gl.setPreviewIsPortrait(false)
                gl.setPreviewRotation(0)
                gl.setAspectRatioMode(AspectRatioMode.Adjust)
            } else {
                configureLegacyPreviewOrientation(gl, request)
            }
        }
    }

    private fun configureLegacyPreviewOrientation(gl: GlStreamInterface, request: PreviewRequest) {
        // Fixed encoder dimensions still need a half-turn for the opposite device orientation.
        // Apply it to the source before filters; rotating the final output would invert overlays.
        val inputHalfTurn = if (request.rotationDegrees >= 180) 180 else 0
        val external = externalCapture
        if (external != null) external.setInputOrientation(inputHalfTurn) else {
            val inputRotation = if (encoderRotationDegrees == 0) 270 else encoderRotationDegrees - 90
            gl.setCameraOrientation((inputRotation + inputHalfTurn) % 360)
        }
        gl.setPreviewIsPortrait(request.rotationDegrees % 180 == 0)
        gl.setPreviewRotation((90 - request.rotationDegrees - encoderRotationDegrees - inputHalfTurn + 720) % 360)
        gl.setAspectRatioMode(AspectRatioMode.Fill)
    }

    /** Stoppt alle laufenden/startenden Ziele und setzt den Zustand auf Idle. */
    fun stopStream() {
        _streamStartedAtMs.value = null
        if (_cameraModeStatus.value.preparing) {
            captureGeneration.incrementAndGet()
            _streamingState.value = StreamingState.Idle
            return
        }
        if (_streamingState.value is StreamingState.Idle && camera?.isStreaming != true && sceneSession?.isStreaming != true) return
        stoppingStreams = true
        try {
            _targetStates.value.indices.forEach(::stopTargetStream)
            stopSharedStreamEncoder()
            _targetStates.value = _targetStates.value.map {
                it.copy(status = StreamTargetStatus.IDLE, failureReason = null, bitrateKbps = null)
            }
            _streamingState.value = StreamingState.Idle
            adaptiveController = null
        } finally {
            stoppingStreams = false
        }
        startIdlePreviewIfReady()
    }

    /**
     * Wertet eine gemessene Sendebitrate (kbps) aus und passt die
     * Encoder-Zielbitrate on-the-fly an (AIMD-aehnlich). Rate-limited auf
     * ein Sample je [ADAPTIVE_SAMPLE_INTERVAL_MS]; die Streak-Logik liegt
     * im [AdaptiveBitrateController].
     */
    private fun sampleAdaptiveBitrate(measuredKbps: Long) {
        if (!_adaptiveBitrateEnabled.value) return
        val cam = camera
        if (cam == null && sceneSession == null) return
        val controller = adaptiveController ?: return
        val now = timeSource()
        if (now - lastAdaptiveSampleMs < ADAPTIVE_SAMPLE_INTERVAL_MS) return
        lastAdaptiveSampleMs = now
        val nextKbps = controller.onSample(measuredKbps) ?: return
        sceneSession?.setBitrateKbps(nextKbps) ?: cam?.setVideoBitrateOnFly(nextKbps * 1_000)
        _bitrateDiagnostics.value = _bitrateDiagnostics.value?.copy(targetKbps = nextKbps)
        Timber.i("Adaptive bitrate: measured=%d kbps target=%d kbps mode=%s",
            measuredKbps, nextKbps, _bitrateDiagnostics.value?.mode)
    }
    private fun updateSceneRenderClock() {
        val gl = activeGlInterface()
        val legacyOutput = sceneSession == null && desiredSceneRender != null && gl?.isRunning == true
        sceneRenderClock.update(if (legacyOutput) gl else null, _activeEncoder.value?.preset?.fps ?: 30)
    }

    private fun sceneEncoding(): Boolean = sceneSession?.let { it.isStreaming || it.isRecording } == true
    private fun legacyEncoding(): Boolean = camera?.isStreaming == true || camera?.isRecording == true ||
        screenCaptureSource?.isActive == true || videoPlayerSource?.isActive == true ||
        replaySource?.isActive == true || uvcVideoSource?.isActive == true

    private fun sceneRouteRequested(): Boolean = sceneSession != null || (_sceneComposition.value != null && !legacyEncoding())

    /** Missing camera permission affects its layer, rather than unrelated image/widget output. */
    fun sceneNeedsCameraPermission(): Boolean = _sceneComposition.value?.let { graph ->
        val visible = graph.layers.filter { it.visible && it.opacity > 0f }.map { it.sourceId }.toSet()
        graph.sources.any { it.id in visible && it.type in listOf(SceneSourceType.CAMERA, SceneSourceType.UVC) }
    } ?: (activeSourceKind.value in listOf(VideoSourceKind.CAMERA, VideoSourceKind.UVC))

    private fun primarySceneCamera(): com.vivid.core.data.SceneSource? = sceneCameraSource(_sceneComposition.value)

    private fun sceneCameraSource(graph: SceneComposition?): com.vivid.core.data.SceneSource? {
        graph ?: return null
        val visible = graph.layers.filter { it.visible && it.opacity > 0f }.map { it.sourceId }.toSet()
        val cameras = graph.sources.filter { it.id in visible && it.type in listOf(SceneSourceType.CAMERA, SceneSourceType.UVC) }
        val primaryId = graph.layers.firstOrNull { it.followsPrimaryInput }?.sourceId
        return cameras.firstOrNull { it.id == primaryId } ?: cameras.firstOrNull()
    }

    private fun activeSceneCamera(): Camera2ApiManager? = if (sceneSession == null) null else
        primarySceneCamera()?.let { sceneTextures?.camera(it.id) }

    private data class SceneFileAudioKey(val sourceId: String?, val locator: String, val capture: Any?, val loop: Boolean)
    private var sceneFileAudioKey: SceneFileAudioKey? = null
    private var sceneFileAudioCycle = 0L

    private fun resolveSceneVideoPlayback(graph: SceneComposition): SceneComposition {
        val primaryId = graph.layers.firstOrNull { it.followsPrimaryInput }?.sourceId
        val movieId = primaryId ?: if (activeSourceKind.value == VideoSourceKind.VIDEO_PLAYER)
            graph.sources.firstOrNull { it.type == SceneSourceType.VIDEO && it.locator == sceneVideoUri?.toString() }?.id else null
        return graph.copy(sources = graph.sources.map {
            if (it.type == SceneSourceType.VIDEO && it.videoLoop == null) it.copy(videoLoop = it.id != movieId) else it
        })
    }

    private fun sceneVideoLooping(source: com.vivid.core.data.SceneSource): Boolean {
        source.videoLoop?.let { return it }
        if (activeSourceKind.value != VideoSourceKind.VIDEO_PLAYER) return true
        val locator = sceneVideoUri?.toString() ?: return true
        val primary = _sceneComposition.value?.sources?.firstOrNull { it.id == scenePrimarySourceId }
            ?: _sceneComposition.value?.sources?.firstOrNull { it.type == SceneSourceType.VIDEO && it.locator == locator }
        return source.id != primary?.id
    }

    private fun sceneVideoLooped(sourceId: String, playback: com.vivid.feature.streaming.compositor.capture.SceneVideoPlayback) {
        dispatchSceneTask {
            syncSceneFileAudio()
            val key = sceneFileAudioKey
            val current = sceneTextures?.videoPlayback(sourceId)
            if (key?.sourceId == sourceId && key.capture === playback.identity &&
                current?.identity === playback.identity && playback.cycle > sceneFileAudioCycle) {
                sceneFileAudioCycle = maxOf(playback.cycle, current.cycle)
                sceneSession?.seekFileAudio(current.positionSeconds)
            }
        }
    }

    /** A visible file waits for video; a hidden primary file can continue as audio only. */
    private fun sceneFileAudioSource(
        locator: String,
        kind: VideoSourceKind,
    ): com.vivid.core.data.SceneSource? {
        val graph = _sceneComposition.value
        return graph?.sources?.firstOrNull { it.id == scenePrimarySourceId && it.type == SceneSourceType.VIDEO && it.locator == locator }
            ?: graph?.sources?.firstOrNull {
                it.matchesSceneIdentity(
                    SceneSourceType.VIDEO,
                    locator,
                    ::sceneCameraIdentity,
                    kind == VideoSourceKind.REPLAY,
                )
            }
    }

    private fun sceneAudioPosition(
        source: com.vivid.core.data.SceneSource?,
        visible: Boolean,
        playback: com.vivid.feature.streaming.compositor.capture.SceneVideoPlayback?,
    ): Double =
        if (visible) {
            val current = sceneTextures?.videoPlayback(requireNotNull(source).id)
            check(
                current != null && current.identity === playback?.identity,
            ) { "Scene video capture changed before audio start" }
            current.positionSeconds
        } else {
            0.0
        }

    private fun createSceneFileAudioInput(
        source: com.vivid.core.data.SceneSource?,
        locator: String,
        loops: Boolean,
        visible: Boolean,
        playback: com.vivid.feature.streaming.compositor.capture.SceneVideoPlayback?,
        key: SceneFileAudioKey,
    ): com.vivid.feature.streaming.compositor.audio.SceneAudioInput =
        if (visible && playback == null) {
            com.vivid.feature.streaming.compositor.audio
                .SceneAudioInput(key) {
                    com.pedro.encoder.input.sources.audio
                        .SilenceAudioSource()
                }
        } else {
            com.vivid.feature.streaming.compositor.audio.SceneAudioInput(key) {
                com.vivid.feature.streaming.compositor.audio.SceneFileAudioSource({ listener ->
                    com.vivid.feature.streaming.compositor.audio.AndroidSceneFileAudioDriver(
                        context,
                        locator.toUri(),
                        !visible && loops,
                        listener,
                    )
                }, positionSeconds = { sceneAudioPosition(source, visible, playback) })
            }
        }

    private fun syncSceneFileAudio() {
        val session = sceneSession ?: return
        val kind = activeSourceKind.value
        val locator = if (kind in listOf(VideoSourceKind.VIDEO_PLAYER, VideoSourceKind.REPLAY)) sceneSourceLocator(kind) else ""
        if (locator.isBlank()) {
            session.setAudioInput(null)
            sceneFileAudioKey = null
            return
        }
        val source = sceneFileAudioSource(locator, kind)
        val graph = _sceneComposition.value
        val loops = source?.videoLoop ?: (kind == VideoSourceKind.REPLAY)
        val visible = source != null && graph?.layers?.any { it.sourceId == source.id && it.visible && it.opacity > 0f } == true
        val playback = if (visible) sceneTextures?.videoPlayback(requireNotNull(source).id) else null
        val key = SceneFileAudioKey(source?.id, locator, if (visible) playback?.identity ?: "pending" else "audio-only", loops)
        val input = createSceneFileAudioInput(source, locator, loops, visible, playback, key)
        try {
            session.setAudioInput(input)
            if (sceneFileAudioKey != key) sceneFileAudioCycle = playback?.cycle ?: 0L
            sceneFileAudioKey = key
        } catch (error: Exception) { Timber.w(error, "Could not switch scene file audio") }
    }

    private fun sceneSourceReady(sourceId: String) {
        if (_sceneComposition.value?.sources?.any { it.id == sourceId && it.type == SceneSourceType.VIDEO } == true) {
            dispatchSceneTask { syncSceneFileAudio() }
        }
        if (sceneSession == null || primarySceneCamera()?.id != sourceId) return
        val manager = sceneTextures?.camera(sourceId) ?: return
        dispatchSceneTask {
            if (sceneSession != null && primarySceneCamera()?.id == sourceId && sceneTextures?.camera(sourceId) === manager) {
                sceneSelectedCameraId = manager.getCurrentCameraId()
                restoreCameraControls()
                attachFaceDetection(idleFaceDetectableFactory(manager))
            }
        }
    }

    private fun ensureSceneSession(): com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession {
        sceneSession?.let { return it }
        sceneSelectedCameraId = currentSceneCameraId ?: sceneSelectedCameraId
        check(!legacyEncoding()) { "Cannot replace an active legacy transport" }
        stopIdlePreview()
        detachGlPreview()
        retireLegacyFileSources()
        val generation = ++sceneSessionGeneration
        val checkers = List(MAX_STREAM_TARGETS) { index ->
            val target = createTargetChecker(index)
            fun deliver(action: () -> Unit) = dispatchSceneTask {
                if (sceneSessionGeneration == generation && sceneSession != null) action()
            }
            object : ConnectChecker {
                override fun onConnectionStarted(url: String) = deliver { target.onConnectionStarted(url) }
                override fun onConnectionSuccess() = deliver { target.onConnectionSuccess() }
                override fun onConnectionFailed(reason: String) = deliver { target.onConnectionFailed(reason) }
                override fun onNewBitrate(bitrate: Long) = deliver { target.onNewBitrate(bitrate) }
                override fun onDisconnect() = deliver { target.onDisconnect() }
                override fun onAuthError() = deliver { target.onAuthError() }
                override fun onAuthSuccess() = deliver { target.onAuthSuccess() }
            }
        }
        return sceneSessionFactory(checkers).also { session ->
            sceneSession = session
            session.setRecordController(monitorKeyframes(AndroidMuxerRecordController()))
            session.setFpsListener { fps -> dispatchSceneTask {
                if (sceneSession === session) _measuredEncoderFps.value = fps
            } }
            val graph = _sceneComposition.value ?: return@also
            val resolved = graph.copy(sources = graph.sources.map { source ->
                if (source.type == SceneSourceType.CAMERA && source.locator.isBlank())
                    source.copy(locator = sceneSelectedCameraId.orEmpty()) else source
            })
            _sceneComposition.value = resolved
            desiredSceneRender = SceneRenderState(resolved)
        }
    }

    private fun retireLegacyFileSources() {
        legacyFileGeneration++
        videoPlayerSource?.let { source ->
            if (sceneVideoUri == null) sceneVideoUri = source.videoUri
            source.stop()
            videoPlayerSource = null
        }
        replaySource?.let { source ->
            if (sceneReplayFile == null) sceneReplayFile = source.replayFile
            source.stop()
            replaySource = null
        }
        registerSceneSource(VideoSourceKind.VIDEO_PLAYER)
        registerSceneSource(VideoSourceKind.REPLAY)
        if (activeSourceKind.value in listOf(VideoSourceKind.VIDEO_PLAYER, VideoSourceKind.REPLAY)) {
            videoSourceRegistry.switchTo(activeSourceKind.value)
        }
    }

    private fun closeSceneSession() {
        val session = sceneSession ?: return
        check(!sceneEncoding())
        detachGlPreview()
        faceProducer.detach()
        sceneSession = null
        sceneFileAudioKey = null
        sceneSessionGeneration++
        sceneReplayController = null
        sceneReplayAudio = null
        runCatching { session.close() }.onFailure { Timber.w(it, "Could not release scene output") }
    }

    private fun startScenePreview(request: PreviewRequest) {
        val session = ensureSceneSession()
        if (session.isStreaming || session.isRecording) { attachPreviewIfRunning(); return }
        if (glPreviewOwner === session.gl && glPreviewSurface === request.surface && session.gl.isRunning) {
            session.gl.setForceRender(true, 30)
            attachPreviewIfRunning()
            return
        }
        val graph = requireNotNull(_sceneComposition.value)
        session.gl.setEncoderSize(graph.canvasWidth, graph.canvasHeight)
        session.gl.setIsPortrait(false)
        session.gl.setRotation(0)
        session.gl.setForceRender(true, 30)
        session.startPreview(request.surface, request.width, request.height)
        glPreviewOwner = session.gl
        glPreviewSurface = request.surface
        privacyComposer.requestRebuild(session.gl)
        attachPreviewIfRunning()
    }

    private fun prepareSceneEncoding(session: com.vivid.feature.streaming.compositor.encoding.SceneEncoderSession): Boolean {
        syncSceneFileAudio()
        if (session.isRecording || session.isStreaming) return true
        detachGlPreview()
        session.stopPreview()
        faceProducer.detach()
        resetKeyframeMonitoring()
        _bitrateDiagnostics.value = null
        val requested = _encoderConfig.value ?: ResolvedEncoderConfig(VideoCodecPreference.H264,
            com.vivid.core.data.EncoderPreset.HD30, false)
        val profile = if (_encoderAutoFallback.value) com.vivid.core.data.resolveEncoderConfig(
            requested.codec, requested.preset, encoderCapabilities) else requested
        _activeEncoder.value = null
        if (!session.prepare(profile)) return false
        _activeEncoder.value = profile
        encoderRotationDegrees = 0
        return true
    }

    private fun startSceneStream(urls: List<String>) {
        val session = ensureSceneSession()
        if (session.isStreaming) return
        _targetStates.value = urls.map { StreamTargetState(it, StreamTargetStatus.PREPARING) }
        _streamingState.value = StreamingState.Preparing
        try {
            consumeSceneProjectionConsent()
            if (!prepareSceneEncoding(session)) {
                failStream(ENCODER_PREPARATION_ERROR)
                startIdlePreviewIfReady()
                return
            }
            resetAdaptiveBitrate()
            applyPrivacyZones()
            urls.forEachIndexed(session::startTarget)
            attachPreviewIfRunning()
        } catch (failure: Exception) {
            Timber.w(failure, "Could not start scene output")
            urls.indices.forEach { runCatching { session.stopTarget(it) } }
            failStream(ENCODER_PREPARATION_ERROR)
        }
        if (!session.isStreaming && !session.isRecording) startIdlePreviewIfReady()
    }

    private fun startSceneRecording(nowMillis: Long, includeAudio: Boolean): Boolean {
        if (_replayState.value is ReplayState.Recording) return false
        val session = ensureSceneSession()
        return try {
            if (!prepareSceneEncoding(session)) {
                startIdlePreviewIfReady()
                return false
            }
            val controller = sceneReplayController?.takeIf { sceneReplayAudio == includeAudio }
                ?: ReplayController(replayStorage(context), object : ReplayRecorder {
                    override fun start(file: File) = session.startRecording(file, includeAudio)
                    override fun stop() = session.stopRecording()
                }, _replayState).also { sceneReplayController = it; sceneReplayAudio = includeAudio }
            applyPrivacyZones()
            controller.start(nowMillis).also { started ->
                if (started) attachPreviewIfRunning() else startIdlePreviewIfReady()
            }
        } catch (failure: Exception) {
            Timber.w(failure, "Could not record scene output")
            startIdlePreviewIfReady()
            false
        }
    }

    private fun fallbackSceneGraph(): SceneComposition {
        val old = _sceneComposition.value
        val type = sceneSourceType(activeSourceKind.value)
        val locator = sceneSourceLocator(activeSourceKind.value)
        val inputId = scenePrimarySourceId ?: sceneFileAudioKey?.sourceId
        val source = old?.sources?.firstOrNull { it.id == inputId && it.matchesSceneIdentity(type, locator, ::sceneCameraIdentity) }
            ?: old?.sources?.firstOrNull { it.matchesSceneIdentity(type, locator, ::sceneCameraIdentity,
                videoLoop = if (type == SceneSourceType.VIDEO) activeSourceKind.value == VideoSourceKind.REPLAY else null) }
            ?: com.vivid.core.data.SceneSource("scene-default:${activeSourceKind.value}", type, locator)
        return SceneComposition(canvasWidth = old?.canvasWidth ?: 1280, canvasHeight = old?.canvasHeight ?: 720,
            sources = listOf(source), layers = listOf(com.vivid.core.data.SceneLayer("scene-default-layer", source.id, followsPrimaryInput = true)),
            primaryInputConfigured = true)
    }

    private fun sceneSourceType(kind: VideoSourceKind): SceneSourceType = when (kind) {
        VideoSourceKind.CAMERA -> SceneSourceType.CAMERA
        VideoSourceKind.SCREEN_CAPTURE -> SceneSourceType.SCREEN_CAPTURE
        VideoSourceKind.UVC -> SceneSourceType.UVC
        else -> SceneSourceType.VIDEO
    }

    private fun sceneSourceLocator(kind: VideoSourceKind): String = when (kind) {
        VideoSourceKind.CAMERA -> sceneSelectedCameraId?.takeIf { it.isNotBlank() }
            ?: camera?.currentCameraId?.takeIf { it.isNotBlank() }
            ?: sceneCameraSource(_sceneComposition.value)?.takeIf { it.type == SceneSourceType.CAMERA }?.let(::sceneCameraIdentity)
            ?: _sceneComposition.value?.sources?.firstOrNull { it.type == SceneSourceType.CAMERA }?.let(::sceneCameraIdentity).orEmpty()
        VideoSourceKind.UVC -> sceneUvcLocator()
        VideoSourceKind.VIDEO_PLAYER -> sceneVideoUri?.toString().orEmpty()
        VideoSourceKind.REPLAY -> sceneReplayFile?.absolutePath.orEmpty()
        else -> ""
    }

    private fun sceneUvcLocator(): String {
        val graph = _sceneComposition.value
        val primaryId = graph?.layers?.firstOrNull { it.followsPrimaryInput }?.sourceId
        val visible = graph?.layers?.filter { it.visible && it.opacity > 0f }?.map { it.sourceId }.orEmpty().toSet()
        val sources = graph?.sources?.filter { it.type == SceneSourceType.UVC }.orEmpty()
        val source = sources.firstOrNull { it.id == primaryId } ?: sources.firstOrNull { it.id in visible } ?: sources.firstOrNull()
        return source?.let(::sceneCameraIdentity)?.takeIf { it.isNotBlank() }
            ?: sceneCameraOptions().firstOrNull { it.second == android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL }?.first.orEmpty()
    }

    internal fun sceneCameraIdentity(source: com.vivid.core.data.SceneSource): String {
        if (source.locator.isNotBlank()) return source.locator
        sceneTextures?.camera(source.id)?.getCurrentCameraId()?.takeIf { it.isNotBlank() }?.let { return it }
        val options = sceneCameraOptions()
        val facing = if (source.type == SceneSourceType.UVC)
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_EXTERNAL else
            android.hardware.camera2.CameraCharacteristics.LENS_FACING_BACK
        return options.firstOrNull { it.second == facing }?.first
            ?: if (source.type == SceneSourceType.CAMERA) options.firstOrNull()?.first.orEmpty() else ""
    }

    private var scenePrimarySourceId: String? = null

    private fun registerSceneSource(kind: VideoSourceKind) {
        videoSourceRegistry.registerFactory(kind) { requested -> if (requested != kind) null else
            object : com.vivid.feature.streaming.source.VideoSource {
                override val kind = requested
                override val isActive get() = sceneSession?.let { it.isStreaming || it.isRecording } == true
                override fun start() = true
                override fun stop() = true
            }
        }
    }

    private fun switchSceneBaseSource(kind: VideoSourceKind): Boolean {
        val oldKind = activeSourceKind.value
        if (kind in listOf(VideoSourceKind.VIDEO_PLAYER, VideoSourceKind.REPLAY) && sceneSourceLocator(kind).isBlank()) return false
        val graph = _sceneComposition.value ?: return false
        val replacement = com.vivid.feature.streaming.compositor.replaceScenePrimarySource(graph, scenePrimarySourceId ?: desiredSceneRender?.inputSourceId,
            sceneSourceType(oldKind), sceneSourceType(kind), sceneSourceLocator(kind), ::sceneCameraIdentity,
            videoLoop = when (kind) { VideoSourceKind.VIDEO_PLAYER -> false; VideoSourceKind.REPLAY -> true; else -> null }) ?: return false
        if (kind == VideoSourceKind.CAMERA) { if (!videoSourceRegistry.switchTo(kind)) return false }
        else {
            registerSceneSource(kind)
            if (!videoSourceRegistry.switchTo(kind)) return false
        }
        scenePrimarySourceId = replacement.sourceId
        setSceneComposition(replacement.graph, animate = false)
        return true
    }

    private fun updateSceneBaseSource(kind: VideoSourceKind, oldKind: VideoSourceKind = kind): Boolean {
        val graph = _sceneComposition.value ?: return false
        val replacement = com.vivid.feature.streaming.compositor.replaceScenePrimarySource(graph, scenePrimarySourceId ?: desiredSceneRender?.inputSourceId,
            sceneSourceType(oldKind), sceneSourceType(kind), sceneSourceLocator(kind), ::sceneCameraIdentity,
            videoLoop = when (kind) { VideoSourceKind.VIDEO_PLAYER -> false; VideoSourceKind.REPLAY -> true; else -> null }) ?: return false
        scenePrimarySourceId = replacement.sourceId
        setSceneComposition(replacement.graph, animate = false)
        return true
    }

    private fun primarySceneScreenId(): String? {
        val graph = _sceneComposition.value ?: return null
        val primaryId = graph.layers.firstOrNull { it.followsPrimaryInput }?.sourceId
        val visible = graph.layers.filter { it.visible && it.opacity > 0f }.map { it.sourceId }.toSet()
        val screens = graph.sources.filter { it.type == SceneSourceType.SCREEN_CAPTURE }
        return screens.firstOrNull { it.id == primaryId }?.id
            ?: screens.firstOrNull { it.id in visible }?.id ?: screens.firstOrNull()?.id
    }

    fun sceneProjectionForegroundRequired(): Boolean = pendingSceneProjection != null ||
        sceneProjectionActive.value || (activeSourceKind.value == VideoSourceKind.SCREEN_CAPTURE && screenCaptureSource?.isActive == true)

    private fun consumeSceneProjectionConsent() {
        val pending = pendingSceneProjection ?: return
        pendingSceneProjection = null
        if (_sceneComposition.value?.sources?.none { it.id == pending.first && it.type == SceneSourceType.SCREEN_CAPTURE } != false) return
        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val projection = manager.getMediaProjection(android.app.Activity.RESULT_OK, pending.second)
            ?: error("Scene screen consent did not provide a projection")
        grantSceneProjection(pending.first, projection)
    }

}
