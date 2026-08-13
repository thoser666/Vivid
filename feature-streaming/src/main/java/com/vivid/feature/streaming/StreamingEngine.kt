package com.vivid.feature.streaming

import android.content.Context
import android.view.Surface
import com.pedro.common.ConnectChecker
import com.pedro.library.rtmp.RtmpCamera2
import com.pedro.library.view.GlStreamInterface
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

// Ein Interface, das es uns erlaubt, die Kameraerstellung zu mocken
interface CameraFactory {
    fun create(connectChecker: ConnectChecker): RtmpCamera2
}

// Die echte Implementierung für die App
class RtmpCamera2Factory @Inject constructor(
    @ApplicationContext private val context: Context,
) : CameraFactory {
    override fun create(connectChecker: ConnectChecker): RtmpCamera2 {
        // Context-Konstruktor statt View-Konstruktor: RootEncoder baut dann eine
        // eigene GL-Pipeline (GlStreamInterface) ohne Activity-View auf. Die
        // Kamera-Vorschau wird separat über attachPreview(Surface) angehängt —
        // so überlebt der Stream die Zerstörung der Activity (Recents-Wischen),
        // weil der Encoder nicht an der Preview-Surface hängt.
        // (Verifiziert an RootEncoder 2.6.4 per Bytecode + Maintainer-Doku.)
        return RtmpCamera2(context, connectChecker)
    }
}

@Singleton // Die Engine sollte ein Singleton sein, da sie die Kamera steuert
class StreamingEngine @Inject constructor(
    private val cameraFactory: CameraFactory, // <-- WIR INJIZIEREN EINE FACTORY
) {
    private var rtmpCamera: RtmpCamera2? = null

    private val _streamingState = MutableStateFlow<StreamingState>(StreamingState.Idle)
    val streamingState: StateFlow<StreamingState> = _streamingState.asStateFlow()

    private val _focusMode = MutableStateFlow(FocusMode.AUTO)
    val focusMode: StateFlow<FocusMode> = _focusMode.asStateFlow()

    private var focusController: CameraFocusController? = null

    /** Preview-Surface der Activity, die an die interne GL-Pipeline angehängt wird. */
    private data class PreviewRequest(val surface: Surface, val width: Int, val height: Int)

    // Die zuletzt gemeldete Preview-Surface. Wird beim Start (nach prepareVideo)
    // angehängt bzw. sofort, wenn die GL-Pipeline bereits läuft (Rotation/Recreate).
    private var previewRequest: PreviewRequest? = null

    private val connectChecker = object : ConnectChecker {
        override fun onConnectionStarted(url: String) {
            _streamingState.value = StreamingState.Preparing
        }

        override fun onConnectionSuccess() {
            _streamingState.value = StreamingState.Streaming
        }

        override fun onConnectionFailed(reason: String) {
            _streamingState.value = StreamingState.Failed(reason)
            rtmpCamera?.stopStream()
        }

        override fun onNewBitrate(bitrate: Long) {
            // Optional: Handle bitrate changes
        }

        override fun onDisconnect() {
            _streamingState.value = StreamingState.Idle
        }

        override fun onAuthError() {
            _streamingState.value = StreamingState.Failed("RTMP Auth Error")
        }

        override fun onAuthSuccess() {
            // Optional: Handle auth success
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
        if (rtmpCamera == null) {
            rtmpCamera = cameraFactory.create(connectChecker)
            focusController = CameraFocusController(FocusableRtmpCamera(rtmpCamera!!))
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
     * Adapter, der nur die Fokus-Steuerung des [RtmpCamera2] exponiert.
     * Voraussetzung: Die Kamera wurde vorher über [initializeCamera] erstellt.
     */
    private class FocusableRtmpCamera(
        private val camera: RtmpCamera2,
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
        (rtmpCamera?.glInterface as? GlStreamInterface)?.deAttachPreview()
    }

    fun startStream(url: String) {
        if (url.isBlank()) return

        if (rtmpCamera?.isStreaming == false) {
            _streamingState.value = StreamingState.Preparing
            if (rtmpCamera?.prepareAudio() == true && rtmpCamera?.prepareVideo() == true) {
                // GL-Pipeline läuft jetzt — gemerkte Preview-Surface anhängen.
                attachPreviewIfRunning()
                rtmpCamera?.startStream(url)
            } else {
                _streamingState.value = StreamingState.Failed("Failed to prepare audio/video")
            }
        }
    }

    /** Hängt die gemerkte Preview-Surface an, sobald die GL-Pipeline läuft. */
    private fun attachPreviewIfRunning() {
        val request = previewRequest ?: return
        val gl = rtmpCamera?.glInterface as? GlStreamInterface ?: return
        if (gl.isRunning) {
            gl.attachPreview(request.surface)
            gl.setPreviewResolution(request.width, request.height)
        }
    }

    fun stopStream() {
        if (rtmpCamera?.isStreaming == true) {
            rtmpCamera?.stopStream()
            _streamingState.value = StreamingState.Idle
        }
    }
}
