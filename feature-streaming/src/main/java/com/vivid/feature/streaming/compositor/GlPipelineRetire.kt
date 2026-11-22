package com.vivid.feature.streaming.compositor

import com.pedro.library.view.GlStreamInterface
import java.util.concurrent.RejectedExecutionException

/**
 * Tear-down guards for RootEncoder's `GlStreamInterface` executor.
 *
 * RootEncoder 2.7.5 creates a single-thread executor in `start()` and calls
 * `shutdownNow()` in `stop()` before it clears the field. Only
 * `add/removeMediaCodecSurface` (and the record variants) submit to that pool.
 * When the encoder side tears down while we retire the pipeline, it can read
 * the old reference inside that window and submit into a stopped pool — the
 * `ThreadPoolExecutor` then reports `[Shutting down, …]` and throws
 * `RejectedExecutionException: Task … rejected` (Sentry VIVID-5S/#453,
 * VIVID-4Z/#445, fatal). The GL pipeline is stopping in that moment anyway and
 * the queued work has no surface left to touch, so dropping the call is the
 * correct reaction — the same contract `SceneRenderClock` uses for
 * `onFrameAvailable`.
 *
 * Only `RejectedExecutionException` is swallowed; every other failure still
 * surfaces.
 */
internal inline fun <T> onRetiredGlPipeline(block: () -> T): T? =
    try {
        block()
    } catch (_: RejectedExecutionException) {
        null
    }

/** [GlStreamInterface.stop] that tolerates a teardown racing its executor shutdown. */
internal fun GlStreamInterface.stopQuietly() {
    onRetiredGlPipeline { stop() }
}

/** [GlStreamInterface.deAttachPreview] that tolerates a torn-down preview pipeline. */
internal fun GlStreamInterface.deAttachPreviewQuietly() {
    onRetiredGlPipeline { deAttachPreview() }
}
