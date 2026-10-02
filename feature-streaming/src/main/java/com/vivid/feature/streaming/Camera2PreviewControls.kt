package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View
import com.pedro.encoder.input.video.Camera2ApiManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata

/** Controls for the Camera2 manager feeding the idle GL preview. */
internal class Camera2PreviewControls(
    private val camera: Camera2ApiManager,
) : CameraControlPolicy(
    camera::enableOpticalVideoStabilization,
    camera::enableVideoStabilization,
    camera::disableVideoStabilization,
    camera::disableOpticalVideoStabilization,
    camera::enableLantern,
    camera::disableLantern,
    camera::enableAutoExposure,
    camera::disableAutoExposure,
    camera::enableAutoWhiteBalance,
    camera::disableAutoWhiteBalance,
    { camera.zoomRange },
) {

    override fun getZoom(): Float = camera.zoom

    override fun setZoom(value: Float) { camera.zoom = value }

    override fun tapToFocus(view: View, event: MotionEvent) {
        camera.tapToFocus(view, event)
    }

    override fun hasOpticalStabilization(): Boolean =
        runCatching { camera.cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true }.getOrDefault(false)

    override fun isStabilizationEnabled(): Boolean =
        camera.isVideoStabilizationEnabled || camera.isOpticalStabilizationEnabled

    override fun hasTorch(): Boolean = camera.isLanternSupported

    override fun isTorchEnabled(): Boolean = camera.isLanternEnabled

    // --- Manuelle Kamera-Steuerung ---

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

    override fun isAutoWhiteBalanceEnabled(): Boolean = runCatching {
        camera.isAutoWhiteBalanceEnabled
    }.getOrDefault(true)

    override fun getWhiteBalanceModesAvailable(): List<Int> = runCatching {
        camera.getAutoWhiteBalanceModesAvailable()
    }.getOrDefault(emptyList())
}
