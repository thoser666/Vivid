package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View
import com.vivid.core.data.StabilizationMode

/**
 * Resolve the open camera for each operation across preview/encoder handoffs.
 *
 * Jeder Dispatch läuft über den [guard] — die Fassade ist der einzige Punkt,
 * an dem Control-Requests aus UI- und Framework-Threads auf eine schließende
 * Session treffen können (#324). Während des Idle-Preview-Closes liefert sie
 * neutrale Werte, statt in `CameraCaptureSessionImpl.checkNotClosed` zu krachen;
 * ein `IllegalStateException` anderer Ursache wird bewusst weitergereicht.
 *
 * [current] darf `null` liefern: eine aktive Szenen-Session kann ohne eigene
 * Kamera laufen (#330). Dann greifen dieselben neutralen Werte wie im
 * Schließfenster — die Fassade bleibt damit für beide Fälle eine Antwort
 * schuldig, ohne die Session zu berühren.
 */
internal class ActiveCameraControls(
    private val guard: CameraSessionGuard,
    private val current: () -> CameraControls?,
) : CameraControls {
    override fun getZoom(): Float = guard.dispatch(ZOOM_UNZOOMED) { current()?.getZoom() ?: ZOOM_UNZOOMED }
    override fun getZoomRange(): ZoomRange? = guard.dispatch<ZoomRange?>(null) { current()?.getZoomRange() }
    override fun setZoom(value: Float) = guard.dispatch(Unit) { current()?.setZoom(value) ?: Unit }
    override fun tapToFocus(view: View, event: MotionEvent) = guard.dispatch(Unit) { current()?.tapToFocus(view, event) ?: Unit }
    override fun hasOpticalStabilization(): Boolean = guard.dispatch(false) { current()?.hasOpticalStabilization() ?: false }
    override fun isStabilizationEnabled(): Boolean = guard.dispatch(false) { current()?.isStabilizationEnabled() ?: false }
    override fun enableStabilization(): Boolean = guard.dispatch(false) { current()?.enableStabilization() ?: false }
    override fun supportedStabilizationModes(): Set<StabilizationMode> =
        guard.dispatch(setOf(StabilizationMode.OFF)) { current()?.supportedStabilizationModes() ?: setOf(StabilizationMode.OFF) }
    override fun setStabilizationMode(mode: StabilizationMode): Boolean =
        guard.dispatch(false) { current()?.setStabilizationMode(mode) ?: false }
    override fun disableStabilization(): Boolean = guard.dispatch(false) { current()?.disableStabilization() ?: false }
    override fun hasTorch(): Boolean = guard.dispatch(false) { current()?.hasTorch() ?: false }
    override fun isTorchEnabled(): Boolean = guard.dispatch(false) { current()?.isTorchEnabled() ?: false }
    override fun enableTorch(): Boolean = guard.dispatch(false) { current()?.enableTorch() ?: false }
    override fun disableTorch(): Boolean = guard.dispatch(false) { current()?.disableTorch() ?: false }
    override fun hasManualFocus(): Boolean = guard.dispatch(false) { current()?.hasManualFocus() ?: false }
    override fun getFocusDistance(): Float = guard.dispatch(0f) { current()?.getFocusDistance() ?: 0f }
    override fun setFocusDistance(distance: Float) = guard.dispatch(Unit) { current()?.setFocusDistance(distance) ?: Unit }
    override fun getAvailableCameraIds(): List<String> = guard.dispatch(emptyList()) { current()?.getAvailableCameraIds() ?: emptyList() }
    override fun getCurrentCameraId(): String = guard.dispatch(CAMERA_ID_UNKNOWN) { current()?.getCurrentCameraId() ?: CAMERA_ID_UNKNOWN }
    override fun selectCamera(cameraId: String): Boolean = guard.dispatch(false) { current()?.selectCamera(cameraId) ?: false }
    override fun hasExposureControl(): Boolean = guard.dispatch(false) { current()?.hasExposureControl() ?: false }
    override fun getExposure(): Int = guard.dispatch(0) { current()?.getExposure() ?: 0 }
    override fun getExposureRange(): IntRange? = guard.dispatch<IntRange?>(null) { current()?.getExposureRange() }
    override fun setExposure(value: Int): Boolean = guard.dispatch(false) { current()?.setExposure(value) ?: false }

    // Reads neutral during close and while a scene runs without a camera: both
    // adapters default to `true` (Camera2PreviewControls).
    override fun isAutoExposureEnabled(): Boolean = guard.dispatch(true) { current()?.isAutoExposureEnabled() ?: true }
    override fun enableAutoExposure(): Boolean = guard.dispatch(false) { current()?.enableAutoExposure() ?: false }
    override fun disableAutoExposure(): Boolean = guard.dispatch(false) { current()?.disableAutoExposure() ?: false }
    override fun hasWhiteBalanceControl(): Boolean = guard.dispatch(false) { current()?.hasWhiteBalanceControl() ?: false }
    override fun isAutoWhiteBalanceEnabled(): Boolean = guard.dispatch(true) { current()?.isAutoWhiteBalanceEnabled() ?: true }
    override fun enableAutoWhiteBalance(): Boolean = guard.dispatch(false) { current()?.enableAutoWhiteBalance() ?: false }
    override fun disableAutoWhiteBalance(): Boolean = guard.dispatch(false) { current()?.disableAutoWhiteBalance() ?: false }
    override fun getWhiteBalanceModesAvailable(): List<Int> = guard.dispatch(emptyList()) { current()?.getWhiteBalanceModesAvailable() ?: emptyList() }

    private companion object {
        const val ZOOM_UNZOOMED = 1f
        const val CAMERA_ID_UNKNOWN = "unknown"
    }
}
