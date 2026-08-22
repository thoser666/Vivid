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
}
