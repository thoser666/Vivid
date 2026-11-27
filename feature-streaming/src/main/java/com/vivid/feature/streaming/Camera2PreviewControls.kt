package com.vivid.feature.streaming

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.view.MotionEvent
import android.view.View
import com.pedro.encoder.input.video.Camera2ApiManager

/** Controls for the Camera2 manager feeding the idle GL preview. */
internal class Camera2PreviewControls(
    private val cameraManager: Camera2ApiManager,
) : CameraControlPolicy(
    cameraManager::enableOpticalVideoStabilization,
    cameraManager::enableVideoStabilization,
    cameraManager::disableVideoStabilization,
    cameraManager::disableOpticalVideoStabilization,
    cameraManager::enableLantern,
    cameraManager::disableLantern,
    cameraManager::enableAutoExposure,
    cameraManager::disableAutoExposure,
    cameraManager::enableAutoWhiteBalance,
    cameraManager::disableAutoWhiteBalance,
    { cameraManager.zoomRange },
) {

    override fun getZoom(): Float = cameraManager.zoom

    override fun setZoom(value: Float) { cameraManager.zoom = value }

    override fun tapToFocus(view: View, event: MotionEvent) {
        cameraManager.tapToFocus(view, event)
    }

    override fun hasOpticalStabilization(): Boolean =
        runCatching {
            cameraManager.cameraCharacteristics?.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
                ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true
        }.getOrDefault(false)

    override fun isStabilizationEnabled(): Boolean =
        cameraManager.isVideoStabilizationEnabled || cameraManager.isOpticalStabilizationEnabled

    override fun hasTorch(): Boolean = cameraManager.isLanternSupported

    override fun isTorchEnabled(): Boolean = cameraManager.isLanternEnabled

    // --- Manuelle Kamera-Steuerung ---

    override fun setFocusDistance(distance: Float) {
        cameraManager.setFocusDistance(distance)
    }

    override fun getAvailableCameraIds(): List<String> = runCatching {
        cameraManager.camerasAvailable.toList()
    }.getOrDefault(emptyList())

    override fun getCurrentCameraId(): String = runCatching {
        cameraManager.getCurrentCameraId()
    }.getOrDefault("unknown")

    override fun selectCamera(cameraId: String): Boolean = runCatching {
        cameraManager.reOpenCamera(cameraId)
        true
    }.getOrDefault(false)

    // --- Belichtung und Weißabgleich ---

    override fun hasExposureControl(): Boolean = runCatching {
        cameraManager.minExposure < cameraManager.maxExposure
    }.getOrDefault(false)

    override fun getExposure(): Int = runCatching { cameraManager.exposure }.getOrDefault(0)

    override fun getExposureRange(): IntRange? = runCatching {
        val min = cameraManager.minExposure
        val max = cameraManager.maxExposure
        if (min <= max) min..max else null
    }.getOrNull()

    override fun setExposure(value: Int): Boolean = runCatching {
        val range = getExposureRange() ?: return false
        if (value !in range) return false
        cameraManager.exposure = value
        cameraManager.exposure == value
    }.getOrDefault(false)

    override fun isAutoExposureEnabled(): Boolean = runCatching {
        cameraManager.isAutoExposureEnabled
    }.getOrDefault(true)

    override fun isAutoWhiteBalanceEnabled(): Boolean = runCatching {
        cameraManager.isAutoWhiteBalanceEnabled
    }.getOrDefault(true)

    override fun getWhiteBalanceModesAvailable(): List<Int> = runCatching {
        cameraManager.getAutoWhiteBalanceModesAvailable()
    }.getOrDefault(emptyList())
}
