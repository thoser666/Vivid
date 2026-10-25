package com.vivid.feature.streaming.compositor.capture

import kotlin.math.abs

internal enum class SceneUsbFormat { MJPEG, YUYV }
internal data class SceneUsbMode(val width: Int, val height: Int, val format: SceneUsbFormat, val rates: Set<Int>)
internal data class SceneUsbConfiguration(val mode: SceneUsbMode, val frameRate: Int)

/** Regular UVC modes only, bounded like Camera2 capture; preference is resolution, rate, then MJPEG. */
internal fun sceneUsbConfigurations(modes: List<SceneUsbMode>, width: Int, height: Int,
    requestedRate: Int, concurrent: Boolean): List<SceneUsbConfiguration> {
    require(width > 0 && height > 0 && requestedRate in 1..120)
    val ceiling = if (concurrent) minOf(requestedRate, 30) else requestedRate
    val valid = modes.filter { mode -> mode.rates.any { it in 1..120 && (!concurrent || it <= 30) } }
        .filter { it.width > 0 && it.height > 0 && maxOf(it.width, it.height) <= 4096 &&
        it.width.toLong() * it.height <= 3840L * 2160 }
    val budget = if (concurrent) 1280L * 720 else minOf(width.toLong() * height, 3840L * 2160)
    val bounded = valid.filter { it.width.toLong() * it.height <= budget }
    val sizes = bounded.ifEmpty {
        val smallest = valid.minOfOrNull { it.width.toLong() * it.height }
        valid.filter { it.width.toLong() * it.height == smallest }
    }
    val candidates = sizes.flatMap { mode ->
        val rates = mode.rates.filter { it in 1..120 && it <= ceiling }.sortedDescending()
        // A fixed-rate device may exceed output demand, but concurrent capture
        // never exceeds 30 fps. The compositor samples at the output's own rate.
        val usable = rates.ifEmpty { listOfNotNull(mode.rates
            .filter { it in 1..120 && (!concurrent || it <= 30) }.minOrNull()) }
        usable.map { SceneUsbConfiguration(mode, it) }
    }
    val ratio = width.toDouble() / height
    return candidates.distinct().sortedWith(compareBy<SceneUsbConfiguration>(
        { abs(it.mode.width.toDouble() / it.mode.height - ratio) * 1000 +
            abs(it.mode.width.toLong() * it.mode.height - budget) / 1_000_000.0 },
        { abs(it.frameRate - ceiling) },
        { if (it.mode.format == SceneUsbFormat.MJPEG) 0 else 1 },
    ))
}
