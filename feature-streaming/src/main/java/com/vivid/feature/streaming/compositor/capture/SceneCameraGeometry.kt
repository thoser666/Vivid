package com.vivid.feature.streaming.compositor.capture

/** Camera2 buffers already carry sensor rotation/mirroring in SurfaceTexture's matrix. */
internal object SceneCameraGeometry {
    /** Select buffer dimensions before the platform's sensor-to-display transform. */
    fun swapsBufferAxes(sensorDegrees: Int, displayDegrees: Int): Boolean {
        require(sensorDegrees in 0..359 && sensorDegrees % 90 == 0)
        require(displayDegrees in 0..359 && displayDegrees % 90 == 0)
        return (sensorDegrees - displayDegrees + 360) % 180 != 0
    }

    /** Additional image rotation from natural display orientation to the current display. */
    fun displayCorrection(displayDegrees: Int): Int {
        require(displayDegrees in 0..359 && displayDegrees % 90 == 0)
        return (360 - displayDegrees) % 360
    }

    /** Destination top-left UV -> unrotated source GL UV, before SurfaceTexture's matrix. */
    fun samplingMatrix(clockwiseDegrees: Int): FloatArray {
        require(clockwiseDegrees in 0..359 && clockwiseDegrees % 90 == 0)
        val out = FloatArray(16).apply { this[10] = 1f; this[15] = 1f }
        when (clockwiseDegrees) {
            0 -> { out[0] = 1f; out[5] = -1f; out[13] = 1f } // x, 1-y
            90 -> { out[4] = 1f; out[1] = 1f } // y, x
            180 -> { out[0] = -1f; out[5] = 1f; out[12] = 1f } // 1-x, y
            270 -> { out[4] = -1f; out[1] = -1f; out[12] = 1f; out[13] = 1f } // 1-y, 1-x
        }
        return out
    }
}

/** Wait for a confirmed raw request; AUTO buffers may already have an additional pixel rotation. */
internal class SceneCameraSampling(
    private val displayDegrees: () -> Int,
    private val needsCaptureMetadata: Boolean,
) {
    private var rawConfirmedFrom: Long? = null
    private var newestMetadata = Long.MIN_VALUE

    @Synchronized
    fun record(timestamp: Long, appliedClockwiseDegrees: Int, rawRequestConfirmed: Boolean) {
        if (timestamp <= 0L || timestamp < newestMetadata) return
        newestMetadata = timestamp
        if (appliedClockwiseDegrees != 0 || !rawRequestConfirmed) rawConfirmedFrom = null
        else if (rawConfirmedFrom == null) rawConfirmedFrom = timestamp
    }

    @Synchronized
    fun rotation(timestamp: Long): Int? {
        if (needsCaptureMetadata && rawConfirmedFrom?.let { timestamp >= it } != true) return null
        return SceneCameraGeometry.displayCorrection(displayDegrees())
    }
}
