package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View
import com.pedro.encoder.input.video.Camera2ApiManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata

/** Controls for the Camera2 manager feeding the idle GL preview. */
internal class Camera2PreviewControls(
    private val camera: Camera2ApiManager,
) : CameraControls {

    override fun getZoom(): Float = camera.zoom

    override fun getZoomRange(): ZoomRange? =
        camera.zoomRange?.let { ZoomRange(it.lower, it.upper) }

    override fun setZoom(value: Float) { camera.zoom = value }

    override fun tapToFocus(view: View, event: MotionEvent) {
        camera.tapToFocus(view, event)
    }

    override fun hasOpticalStabilization(): Boolean =
        runCatching { camera.cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true }.getOrDefault(false)

    override fun isStabilizationEnabled(): Boolean =
        camera.isVideoStabilizationEnabled || camera.isOpticalStabilizationEnabled

    override fun enableStabilization(): Boolean =
        if (hasOpticalStabilization()) {
            camera.enableOpticalVideoStabilization()
        } else {
            camera.enableVideoStabilization()
        }

    override fun disableStabilization(): Boolean = runCatching {
        // Camera2's initial request can enable OIS before its cached flag is updated.
        camera.disableVideoStabilization()
        camera.disableOpticalVideoStabilization()
        !isStabilizationEnabled()
    }.getOrDefault(false)

    override fun hasTorch(): Boolean = camera.isLanternSupported

    override fun isTorchEnabled(): Boolean = camera.isLanternEnabled

    override fun enableTorch(): Boolean = runCatching {
        camera.enableLantern()
        camera.isLanternEnabled
    }.getOrDefault(false)

    override fun disableTorch(): Boolean = runCatching {
        camera.disableLantern()
        !camera.isLanternEnabled
    }.getOrDefault(false)

    // --- Manuelle Kamera-Steuerung ---

    override fun hasManualFocus(): Boolean = true // Camera2API supports focus distance

    override fun getFocusDistance(): Float = 0.0f // Default: infinity

    override fun setFocusDistance(distance: Float) {
        camera.setFocusDistance(distance)
    }

    override fun getAvailableCameraIds(): List<String> = runCatching {
        camera.camerasAvailable.toList()
    }.getOrDefault(emptyList())

    override fun getCurrentCameraId(): String = runCatching {
        camera.getCurrentCameraId()
    }.getOrDefault("unknown")

    override fun selectCamera(cameraId: String): Boolean = runCatching {
        camera.reOpenCamera(cameraId)
        true
    }.getOrDefault(false)

    // --- Belichtung und Weißabgleich ---

    override fun hasExposureControl(): Boolean = runCatching {
        camera.minExposure < camera.maxExposure
    }.getOrDefault(false)

    override fun getExposure(): Int = runCatching { camera.exposure }.getOrDefault(0)

    override fun getExposureRange(): IntRange? = runCatching {
        val min = camera.minExposure
        val max = camera.maxExposure
        if (min <= max) min..max else null
    }.getOrNull()

    override fun setExposure(value: Int): Boolean = runCatching {
        val range = getExposureRange() ?: return false
        if (value !in range) return false
        camera.exposure = value
        camera.exposure == value
    }.getOrDefault(false)

    override fun isAutoExposureEnabled(): Boolean = runCatching {
        camera.isAutoExposureEnabled
    }.getOrDefault(true)

    override fun enableAutoExposure(): Boolean = runCatching {
        camera.enableAutoExposure()
    }.getOrDefault(false)

    override fun disableAutoExposure(): Boolean = runCatching {
        camera.disableAutoExposure()
        !camera.isAutoExposureEnabled
    }.getOrDefault(false)

    override fun hasWhiteBalanceControl(): Boolean = runCatching {
        camera.getAutoWhiteBalanceModesAvailable().contains(CameraMetadata.CONTROL_AWB_MODE_AUTO)
    }.getOrDefault(false)

    override fun isAutoWhiteBalanceEnabled(): Boolean = runCatching {
        camera.isAutoWhiteBalanceEnabled
    }.getOrDefault(true)

    override fun enableAutoWhiteBalance(): Boolean = runCatching {
        val mode = camera.getAutoWhiteBalanceModesAvailable().firstOrNull { it == CameraMetadata.CONTROL_AWB_MODE_AUTO } ?: return false
        camera.enableAutoWhiteBalance(mode)
    }.getOrDefault(false)

    override fun disableAutoWhiteBalance(): Boolean = runCatching {
        camera.disableAutoWhiteBalance()
        !camera.isAutoWhiteBalanceEnabled
    }.getOrDefault(false)

    override fun getWhiteBalanceModesAvailable(): List<Int> = runCatching {
        camera.getAutoWhiteBalanceModesAvailable()
    }.getOrDefault(emptyList())
}
