package com.vivid.feature.streaming

import android.hardware.camera2.CameraMetadata
import android.util.Range

/** Shared command selection and applied-state checks for idle and encoding cameras. */
abstract class CameraControlPolicy(
    private val enableOptical: () -> Boolean,
    private val enableDigital: () -> Boolean,
    private val disableDigital: () -> Unit,
    private val disableOptical: () -> Unit,
    private val enableLantern: () -> Unit,
    private val disableLantern: () -> Unit,
    private val enableExposure: () -> Boolean,
    private val disableExposure: () -> Unit,
    private val enableWhiteBalance: (Int) -> Boolean,
    private val disableWhiteBalance: () -> Unit,
    private val zoomRangeProvider: () -> Range<Float>?,
) : CameraControls {
    final override fun getZoomRange(): ZoomRange? =
        zoomRangeProvider()?.let { ZoomRange(it.lower, it.upper) }

    final override fun enableStabilization(): Boolean =
        if (hasOpticalStabilization()) enableOptical() else enableDigital()

    final override fun disableStabilization(): Boolean = applyAndVerify(
        // Clear request defaults even when Camera2's cached flags are inactive.
        action = {
            disableDigital()
            disableOptical()
        },
        applied = { !isStabilizationEnabled() },
    )

    final override fun enableTorch(): Boolean = setTorch(true)

    final override fun disableTorch(): Boolean = setTorch(false)

    private fun setTorch(enabled: Boolean): Boolean = applyAndVerify(
        action = if (enabled) enableLantern else disableLantern,
        applied = { isTorchEnabled() == enabled },
    )

    final override fun hasManualFocus(): Boolean = true

    final override fun getFocusDistance(): Float = 0.0f

    final override fun enableAutoExposure(): Boolean =
        runCatching { enableExposure() }.getOrDefault(false)

    final override fun disableAutoExposure(): Boolean = applyAndVerify(
        action = disableExposure,
        applied = { !isAutoExposureEnabled() },
    )

    final override fun hasWhiteBalanceControl(): Boolean =
        CameraMetadata.CONTROL_AWB_MODE_AUTO in getWhiteBalanceModesAvailable()

    final override fun enableAutoWhiteBalance(): Boolean = runCatching {
        if (!hasWhiteBalanceControl()) return false
        enableWhiteBalance(CameraMetadata.CONTROL_AWB_MODE_AUTO)
    }.getOrDefault(false)

    final override fun disableAutoWhiteBalance(): Boolean = applyAndVerify(
        action = disableWhiteBalance,
        applied = { !isAutoWhiteBalanceEnabled() },
    )

    private fun applyAndVerify(action: () -> Unit, applied: () -> Boolean): Boolean =
        runCatching {
            action()
            applied()
        }.getOrDefault(false)
}
