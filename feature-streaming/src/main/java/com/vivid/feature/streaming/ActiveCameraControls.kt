package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View

/**
 * Resolve the open camera for each operation across preview/encoder handoffs.
 *
 * Jeder Dispatch läuft über den [guard] — die Fassade ist der einzige Punkt,
 * an dem Control-Requests aus UI- und Framework-Threads auf eine schließende
 * Session treffen können (#324). Während des Idle-Preview-Closes liefert sie
 * neutrale Werte, statt in `CameraCaptureSessionImpl.checkNotClosed` zu krachen;
 * ein `IllegalStateException` anderer Ursache wird bewusst weitergereicht.
 */
internal class ActiveCameraControls(
    private val guard: CameraSessionGuard,
    private val current: () -> CameraControls,
) : CameraControls {
    override fun getZoom(): Float = guard.dispatch(ZOOM_UNZOOMED) { current().getZoom() }
    override fun getZoomRange(): ZoomRange? = guard.dispatch<ZoomRange?>(null) { current().getZoomRange() }
    override fun setZoom(value: Float) = guard.dispatch(Unit) { current().setZoom(value) }
    override fun tapToFocus(view: View, event: MotionEvent) = guard.dispatch(Unit) { current().tapToFocus(view, event) }
    override fun hasOpticalStabilization(): Boolean = guard.dispatch(false) { current().hasOpticalStabilization() }
    override fun isStabilizationEnabled(): Boolean = guard.dispatch(false) { current().isStabilizationEnabled() }
    override fun enableStabilization(): Boolean = guard.dispatch(false) { current().enableStabilization() }
    override fun disableStabilization(): Boolean = guard.dispatch(false) { current().disableStabilization() }
    override fun hasTorch(): Boolean = guard.dispatch(false) { current().hasTorch() }
    override fun isTorchEnabled(): Boolean = guard.dispatch(false) { current().isTorchEnabled() }
    override fun enableTorch(): Boolean = guard.dispatch(false) { current().enableTorch() }
    override fun disableTorch(): Boolean = guard.dispatch(false) { current().disableTorch() }
    override fun hasManualFocus(): Boolean = guard.dispatch(false) { current().hasManualFocus() }
    override fun getFocusDistance(): Float = guard.dispatch(0f) { current().getFocusDistance() }
    override fun setFocusDistance(distance: Float) = guard.dispatch(Unit) { current().setFocusDistance(distance) }
    override fun getAvailableCameraIds(): List<String> = guard.dispatch(emptyList()) { current().getAvailableCameraIds() }
    override fun getCurrentCameraId(): String = guard.dispatch(CAMERA_ID_UNKNOWN) { current().getCurrentCameraId() }
    override fun selectCamera(cameraId: String): Boolean = guard.dispatch(false) { current().selectCamera(cameraId) }
    override fun hasExposureControl(): Boolean = guard.dispatch(false) { current().hasExposureControl() }
    override fun getExposure(): Int = guard.dispatch(0) { current().getExposure() }
    override fun getExposureRange(): IntRange? = guard.dispatch<IntRange?>(null) { current().getExposureRange() }
    override fun setExposure(value: Int): Boolean = guard.dispatch(false) { current().setExposure(value) }

    // Reads neutral during close: both adapters default to `true` (Camera2PreviewControls).
    override fun isAutoExposureEnabled(): Boolean = guard.dispatch(true) { current().isAutoExposureEnabled() }
    override fun enableAutoExposure(): Boolean = guard.dispatch(false) { current().enableAutoExposure() }
    override fun disableAutoExposure(): Boolean = guard.dispatch(false) { current().disableAutoExposure() }
    override fun hasWhiteBalanceControl(): Boolean = guard.dispatch(false) { current().hasWhiteBalanceControl() }
    override fun isAutoWhiteBalanceEnabled(): Boolean = guard.dispatch(true) { current().isAutoWhiteBalanceEnabled() }
    override fun enableAutoWhiteBalance(): Boolean = guard.dispatch(false) { current().enableAutoWhiteBalance() }
    override fun disableAutoWhiteBalance(): Boolean = guard.dispatch(false) { current().disableAutoWhiteBalance() }
    override fun getWhiteBalanceModesAvailable(): List<Int> = guard.dispatch(emptyList()) { current().getWhiteBalanceModesAvailable() }

    private companion object {
        const val ZOOM_UNZOOMED = 1f
        const val CAMERA_ID_UNKNOWN = "unknown"
    }
}
