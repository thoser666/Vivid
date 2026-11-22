package com.vivid.feature.streaming.compositor.capture

import android.util.Range
import com.vivid.feature.streaming.CameraCaptureCapabilities

/** Regular sessions only. Unknown timing metadata retains the bounded legacy capture default. */
internal fun resolveSceneCaptureFrameRate(
    width: Int,
    height: Int,
    requested: Int,
    concurrent: Boolean,
    capabilities: CameraCaptureCapabilities,
): Int {
    require(requested in 1..120)
    val ceiling = if (concurrent) minOf(requested, 30) else requested
    val candidates = listOf(ceiling, 60, 30, 24, 15).distinct().filter { it <= ceiling }
    return candidates.firstOrNull { capabilities.supports(width, height, it) } ?: minOf(ceiling, 30)
}

/** Match RootEncoder's regular-session range policy, preferring a narrower supported range. */
internal fun selectSceneCaptureFpsRange(ranges: Array<out Range<Int>>, requested: Int): Range<Int>? =
    ranges.minWithOrNull(compareBy<Range<Int>>(
        { if (it.contains(requested)) 0 else 1 },
        { kotlin.math.abs(it.lower - requested) + kotlin.math.abs(it.upper - requested) },
        { it.upper - it.lower },
    ))
