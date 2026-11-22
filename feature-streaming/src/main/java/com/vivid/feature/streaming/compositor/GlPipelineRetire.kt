package com.vivid.feature.streaming.compositor

import com.pedro.library.view.GlStreamInterface
import java.util.concurrent.RejectedExecutionException

/**
 * Tear-down guards for RootEncoder's `GlStreamInterface` executor.
 *
 * RootEncoder 2.7.5 creates a single-thread executor in `start()` and calls
 * `shutdownNow()` in `stop()` before it clears the field. Only
 * `add/removeMediaCodecSurface` (and the record variants) submit to that pool —
 * `stop()` and `deAttachPreview()` themselves do not.
 *
 * Where the rejection actually surfaces (latest event of Sentry `7856326808`,
 * pulled with the CI event token): `GlStreamInterface.start$lambda$0$1` →
 * `ThreadPoolExecutor.execute` → `rejectedExecution`, resumed from
 * `ForceRenderer.invokeSuspend` on `Dispatchers.Default`/`LimitedDispatcher`.
 * The submit therefore runs on RootEncoder's own render coroutine, not inside
 * our teardown call — a guard wrapped around `stop()`/`deAttachPreview()`
 * cannot catch that instance. It only covers the synchronous window in which
 * the guarded call itself submits. The deferred path stays with RootEncoder
 * (or with stopping the render loop before `stop()` shuts the pool down);
 * these helpers remain for the synchronous window and for `close()`, where
 * the encoder release runs while the pipeline is still attached.
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
