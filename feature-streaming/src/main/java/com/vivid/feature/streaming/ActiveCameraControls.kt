package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View

/** Resolve the open camera for each operation across preview/encoder handoffs. */
internal class ActiveCameraControls(private val current: () -> CameraControls) : CameraControls {
    override fun getZoom(): Float = current().getZoom()
    override fun getZoomRange(): ZoomRange? = current().getZoomRange()
    override fun setZoom(value: Float) = current().setZoom(value)
    override fun tapToFocus(view: View, event: MotionEvent) = current().tapToFocus(view, event)
    override fun hasOpticalStabilization(): Boolean = current().hasOpticalStabilization()
    override fun isStabilizationEnabled(): Boolean = current().isStabilizationEnabled()
    override fun enableStabilization(): Boolean = current().enableStabilization()
    override fun disableStabilization(): Boolean = current().disableStabilization()
    override fun hasTorch(): Boolean = current().hasTorch()
    override fun isTorchEnabled(): Boolean = current().isTorchEnabled()
    override fun enableTorch(): Boolean = current().enableTorch()
    override fun disableTorch(): Boolean = current().disableTorch()
    override fun hasManualFocus(): Boolean = current().hasManualFocus()
    override fun getFocusDistance(): Float = current().getFocusDistance()
    override fun setFocusDistance(distance: Float) = current().setFocusDistance(distance)
    override fun getAvailableCameraIds(): List<String> = current().getAvailableCameraIds()
    override fun getCurrentCameraId(): String = current().getCurrentCameraId()
    override fun selectCamera(cameraId: String): Boolean = current().selectCamera(cameraId)
    override fun hasExposureControl(): Boolean = current().hasExposureControl()
    override fun getExposure(): Int = current().getExposure()
    override fun getExposureRange(): IntRange? = current().getExposureRange()
    override fun setExposure(value: Int): Boolean = current().setExposure(value)
    override fun isAutoExposureEnabled(): Boolean = current().isAutoExposureEnabled()
    override fun enableAutoExposure(): Boolean = current().enableAutoExposure()
    override fun disableAutoExposure(): Boolean = current().disableAutoExposure()
    override fun hasWhiteBalanceControl(): Boolean = current().hasWhiteBalanceControl()
    override fun isAutoWhiteBalanceEnabled(): Boolean = current().isAutoWhiteBalanceEnabled()
    override fun enableAutoWhiteBalance(): Boolean = current().enableAutoWhiteBalance()
    override fun disableAutoWhiteBalance(): Boolean = current().disableAutoWhiteBalance()
    override fun getWhiteBalanceModesAvailable(): List<Int> = current().getWhiteBalanceModesAvailable()
}
