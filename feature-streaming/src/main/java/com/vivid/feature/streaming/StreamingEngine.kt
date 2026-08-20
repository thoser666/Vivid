package com.vivid.feature.streaming

import android.content.Context
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import com.pedro.common.ConnectChecker
import com.pedro.library.base.Camera2Base
import com.pedro.library.multiple.MultiCamera2
import com.pedro.library.multiple.MultiType
import com.pedro.library.view.GlStreamInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

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
    private val cameraFactory: CameraFactory, // <-- WIR INJIZIEREN EINE FACTORY
) {
    private var camera: MultiCamera2? = null

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

    private var cameraControls: CameraControls? = null
    private var stabilizationController: CameraStabilizationController? = null

    /** Preview-Surface der Activity, die an die interne GL-Pipeline angehängt wird. */
    private data class PreviewRequest(val surface: Surface, val width: Int, val height: Int)

    // Die zuletzt gemeldete Preview-Surface. Wird beim Start (nach prepareVideo)
    // angehängt bzw. sofort, wenn die GL-Pipeline bereits läuft (Rotation/Recreate).
    private var previewRequest: PreviewRequest? = null

    companion object {
        /**
         * Maximale Anzahl paralleler Stream-Ziele (MVP: primär + 1 sekundär).
         *
         * RootEncoder legt pro Ziel einen RTMP-Client an — eine Erweiterung auf
         * mehr Ziele erfordert eine Neuerstellung der [MultiCamera2] mit mehr
         * ConnectCheckern (Kamera darf dabei nicht neu gestartet werden).
         */
        const val MAX_STREAM_TARGETS = 2
    }

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
            // Optional: Handle bitrate changes
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
     * Erstellt die Kamera einmalig, view-unabhängig.
     *
     * Wird beim Anzeigen des Streaming-Screens aufgerufen; da die Engine ein
     * Singleton ist, wird die bestehende Instanz bei einem Activity-Recreate
     * (Rotation, Recents) nicht ersetzt — der laufende Stream bleibt erhalten.
     */
    fun initializeCamera() {
        if (camera == null) {
            camera = cameraFactory.create(List(MAX_STREAM_TARGETS) { createTargetChecker(it) })
            cameraControls = RootEncoderCameraControls(camera!!)
            focusController = CameraFocusController(FocusableCamera2(camera!!))
            stabilizationController = CameraStabilizationController(cameraControls!!).also {
                _stabilizationEnabled.value = it.isEnabled
            }
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
     * Adapter, der nur die Fokus-Steuerung der [Camera2Base] (MultiCamera2)
     * exponiert. Voraussetzung: Die Kamera wurde vorher über [initializeCamera]
     * erstellt.
     */
    private class FocusableCamera2(
        private val camera: Camera2Base,
    ) : FocusableCamera {
        override fun enableAutoFocus(): Boolean = camera.enableAutoFocus()

        override fun disableAutoFocus(): Boolean = camera.disableAutoFocus()

        override fun isAutoFocusEnabled(): Boolean = camera.isAutoFocusEnabled()

        override fun setFocusDistance(distance: Float) = camera.setFocusDistance(distance)
    }

    /**
     * Hängt die Preview-Surface der Activity an die interne GL-Pipeline an.
     *
     * Die Surface darf jederzeit gewechselt werden (Rotation, Activity-Recreate)
     * — der Stream selbst hängt nicht an ihr. Läuft die GL-Pipeline noch nicht
     * (Stream noch nicht gestartet), wird die Surface gemerkt und beim
     * Stream-Start angehängt.
     */
    fun attachPreview(surface: Surface, width: Int, height: Int) {
        previewRequest = PreviewRequest(surface, width, height)
        attachPreviewIfRunning()
    }

    /** Löst die Preview-Surface (Activity zerstört/verdeckt). Der Stream läuft weiter. */
    fun detachPreview() {
        previewRequest = null
        (camera?.glInterface as? GlStreamInterface)?.deAttachPreview()
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

        val cam = camera ?: return
        if (cam.isStreaming) return

        _targetStates.value = activeUrls.map { StreamTargetState(it) }
        _streamingState.value = StreamingState.Preparing

        if (cam.prepareAudio() == true && cam.prepareVideo() == true) {
            // GL-Pipeline läuft jetzt — gemerkte Preview-Surface anhängen.
            attachPreviewIfRunning()
            activeUrls.forEachIndexed { index, url ->
                cam.startStream(MultiType.RTMP, index, url)
            }
        } else {
            val reason = "Failed to prepare audio/video"
            _streamingState.value = StreamingState.Failed(reason)
            _targetStates.value = _targetStates.value.map {
                it.copy(status = StreamTargetStatus.FAILED, failureReason = reason)
            }
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
            gl.attachPreview(request.surface)
            gl.setPreviewResolution(request.width, request.height)
        }
    }

    /** Stoppt alle laufenden/startenden Ziele und setzt den Zustand auf Idle. */
    fun stopStream() {
        if (_streamingState.value !is StreamingState.Streaming &&
            _streamingState.value !is StreamingState.Preparing
        ) {
            return
        }
        val cam = camera ?: return
        _targetStates.value.forEachIndexed { index, _ ->
            cam.stopStream(MultiType.RTMP, index)
        }
        _targetStates.value = _targetStates.value.map {
            it.copy(status = StreamTargetStatus.IDLE, failureReason = null)
        }
        _streamingState.value = StreamingState.Idle
    }
}
