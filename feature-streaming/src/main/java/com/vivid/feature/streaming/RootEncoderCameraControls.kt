package com.vivid.feature.streaming

import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraMetadata
import android.view.MotionEvent
import android.view.View
import com.pedro.library.base.Camera2Base

/**
 * [CameraControls]-Implementierung über RootEncoders [Camera2Base]
 * (bzw. [com.pedro.library.multiple.MultiCamera2]).
 *
 * Wandelt die Android-Framework-Typen der Kamera (z. B. `android.util.Range`)
 * in die reinen Typen des [CameraControls]-Vertrags um.
 */
class RootEncoderCameraControls(
    private val camera: Camera2Base,
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

    override fun setZoom(value: Float) = camera.setZoom(value)

    override fun tapToFocus(view: View, event: MotionEvent) {
        camera.tapToFocus(view, event)
    }

    override fun hasOpticalStabilization(): Boolean =
        runCatching { camera.cameraCharacteristics.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
            ?.contains(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON) == true }.getOrDefault(false)

    override fun isStabilizationEnabled(): Boolean =
        camera.isVideoStabilizationEnabled || camera.isOpticalVideoStabilizationEnabled

    override fun hasTorch(): Boolean = camera.isLanternSupported

    override fun isTorchEnabled(): Boolean = camera.isLanternEnabled

    // --- Manuelle Kamera-Steuerung ---

    override fun setFocusDistance(distance: Float) {
        camera.setFocusDistance(distance)
    }

    override fun getAvailableCameraIds(): List<String> = runCatching {
        // Camera2Base doesn't directly expose available camera IDs
        // We return the current camera ID as a single-element list
        listOf(camera.currentCameraId)
    }.getOrDefault(emptyList())

    override fun getCurrentCameraId(): String = runCatching {
        camera.currentCameraId
    }.getOrDefault("unknown")

    override fun selectCamera(cameraId: String): Boolean = runCatching {
        camera.switchCamera(cameraId)
        true
    }.getOrDefault(false)

    // --- Belichtung und Weißabgleich ---

    override fun hasExposureControl(): Boolean = runCatching {
        camera.getMinExposure() < camera.getMaxExposure()
    }.getOrDefault(false)

    override fun getExposure(): Int = runCatching { camera.getExposure() }.getOrDefault(0)

    override fun getExposureRange(): IntRange? = runCatching {
        val min = camera.getMinExposure()
        val max = camera.getMaxExposure()
        if (min <= max) min..max else null
    }.getOrNull()

    override fun setExposure(value: Int): Boolean = runCatching {
        val range = getExposureRange() ?: return false
        if (value !in range) return false
        camera.setExposure(value)
        camera.getExposure() == value
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
