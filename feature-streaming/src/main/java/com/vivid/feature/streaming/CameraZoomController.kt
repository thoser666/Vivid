package com.vivid.feature.streaming

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil

/** Absolute zoom API used by settings and the future preset widget. */
internal class CameraZoomController(private val scope: () -> CoroutineScope) {
    constructor(scope: CoroutineScope) : this({ scope })
    private var transition: Job? = null

    fun cancel() { transition?.cancel(); transition = null }

    fun move(camera: CameraControls, target: Float, speed: Float, stillActive: () -> Boolean = { true }): Boolean {
        val range = camera.getZoomRange() ?: return false
        if (!target.isFinite() || target !in range.min..range.max || !speed.isFinite() || speed <= 0) return false
        cancel()
        val start = ZoomCalculator.clamp(camera.getZoom(), range)
        val steps = ceil(abs(target - start) / (speed * 0.016f)).toInt().coerceAtLeast(1)
        transition = scope().launch {
            for (step in 1..steps) {
                if (!stillActive()) break
                camera.setZoom(start + (target - start) * step / steps)
                if (step < steps) delay(16)
            }
        }
        return true
    }
}
