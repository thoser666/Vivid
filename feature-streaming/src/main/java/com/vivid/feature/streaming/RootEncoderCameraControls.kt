package com.vivid.feature.streaming

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
) : CameraControls {

    override fun getZoom(): Float = camera.zoom

    override fun getZoomRange(): ZoomRange? =
        camera.zoomRange?.let { ZoomRange(it.lower, it.upper) }

    override fun setZoom(value: Float) = camera.setZoom(value)

    override fun tapToFocus(view: View, event: MotionEvent) {
        camera.tapToFocus(view, event)
    }

    override fun hasOpticalStabilization(): Boolean =
        (camera.opticalZooms as? Array<Float>)?.isNotEmpty() == true

    override fun isStabilizationEnabled(): Boolean =
        camera.isVideoStabilizationEnabled || camera.isOpticalVideoStabilizationEnabled

    override fun enableStabilization(): Boolean =
        if (hasOpticalStabilization()) {
            camera.enableOpticalVideoStabilization()
        } else {
            camera.enableVideoStabilization()
        }

    override fun disableStabilization(): Boolean {
        if (camera.isVideoStabilizationEnabled) {
            camera.disableVideoStabilization()
        }
        if (camera.isOpticalVideoStabilizationEnabled) {
            camera.disableOpticalVideoStabilization()
        }
        return true
    }

    override fun hasTorch(): Boolean = camera.isLanternSupported

    override fun isTorchEnabled(): Boolean = camera.isLanternEnabled

    override fun enableTorch(): Boolean = runCatching {
        camera.enableLantern()
        true
    }.getOrDefault(false)

    override fun disableTorch(): Boolean = runCatching {
        camera.disableLantern()
        true
    }.getOrDefault(false)

    // --- Manuelle Kamera-Steuerung ---

    override fun hasManualFocus(): Boolean = true // Camera2API supports focus distance

    override fun getFocusDistance(): Float = 0.0f // Default: infinity

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
        true
    }.getOrDefault(false)

    override fun isAutoExposureEnabled(): Boolean = runCatching {
        camera.isAutoExposureEnabled
    }.getOrDefault(true)

    override fun enableAutoExposure(): Boolean = runCatching {
        camera.enableAutoExposure()
    }.getOrDefault(false)

    override fun disableAutoExposure(): Boolean = runCatching {
        camera.disableAutoExposure()
        true
    }.getOrDefault(false)

    override fun hasWhiteBalanceControl(): Boolean = runCatching {
        camera.getAutoWhiteBalanceModesAvailable().isNotEmpty()
    }.getOrDefault(false)

    override fun isAutoWhiteBalanceEnabled(): Boolean = runCatching {
        camera.isAutoWhiteBalanceEnabled
    }.getOrDefault(true)

    override fun enableAutoWhiteBalance(): Boolean = runCatching {
        val mode = camera.getAutoWhiteBalanceModesAvailable().firstOrNull() ?: return false
        camera.enableAutoWhiteBalance(mode)
    }.getOrDefault(false)

    override fun disableAutoWhiteBalance(): Boolean = runCatching {
        camera.disableAutoWhiteBalance()
        true
    }.getOrDefault(false)

    override fun getWhiteBalanceModesAvailable(): List<Int> = runCatching {
        camera.getAutoWhiteBalanceModesAvailable()
    }.getOrDefault(emptyList())
}
