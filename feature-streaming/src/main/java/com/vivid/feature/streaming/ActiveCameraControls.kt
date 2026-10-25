package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View

/** Resolve the open camera for each operation across preview/encoder handoffs. */
internal class ActiveCameraControls(private val current: () -> CameraControls?) : CameraControls {
    override fun getZoom(): Float = current()?.getZoom() ?: 1f
    override fun getZoomRange(): ZoomRange? = current()?.getZoomRange()
    override fun setZoom(value: Float) = current()?.setZoom(value) ?: Unit
    override fun tapToFocus(view: View, event: MotionEvent) = current()?.tapToFocus(view, event) ?: Unit
    override fun hasOpticalStabilization(): Boolean = current()?.hasOpticalStabilization() ?: false
    override fun isStabilizationEnabled(): Boolean = current()?.isStabilizationEnabled() ?: false
    override fun enableStabilization(): Boolean = current()?.enableStabilization() ?: false
    override fun disableStabilization(): Boolean = current()?.disableStabilization() ?: false
    override fun hasTorch(): Boolean = current()?.hasTorch() ?: false
    override fun isTorchEnabled(): Boolean = current()?.isTorchEnabled() ?: false
    override fun enableTorch(): Boolean = current()?.enableTorch() ?: false
    override fun disableTorch(): Boolean = current()?.disableTorch() ?: false
    override fun hasManualFocus(): Boolean = current()?.hasManualFocus() ?: false
    override fun getFocusDistance(): Float = current()?.getFocusDistance() ?: 0f
    override fun setFocusDistance(distance: Float) = current()?.setFocusDistance(distance) ?: Unit
    override fun getAvailableCameraIds(): List<String> = current()?.getAvailableCameraIds() ?: emptyList()
    override fun getCurrentCameraId(): String = current()?.getCurrentCameraId() ?: ""
    override fun selectCamera(cameraId: String): Boolean = current()?.selectCamera(cameraId) ?: false
    override fun hasExposureControl(): Boolean = current()?.hasExposureControl() ?: false
    override fun getExposure(): Int = current()?.getExposure() ?: 0
    override fun getExposureRange(): IntRange? = current()?.getExposureRange()
    override fun setExposure(value: Int): Boolean = current()?.setExposure(value) ?: false
    override fun isAutoExposureEnabled(): Boolean = current()?.isAutoExposureEnabled() ?: true
    override fun enableAutoExposure(): Boolean = current()?.enableAutoExposure() ?: false
    override fun disableAutoExposure(): Boolean = current()?.disableAutoExposure() ?: false
    override fun hasWhiteBalanceControl(): Boolean = current()?.hasWhiteBalanceControl() ?: false
    override fun isAutoWhiteBalanceEnabled(): Boolean = current()?.isAutoWhiteBalanceEnabled() ?: true
    override fun enableAutoWhiteBalance(): Boolean = current()?.enableAutoWhiteBalance() ?: false
    override fun disableAutoWhiteBalance(): Boolean = current()?.disableAutoWhiteBalance() ?: false
    override fun getWhiteBalanceModesAvailable(): List<Int> = current()?.getWhiteBalanceModesAvailable() ?: emptyList()
}
