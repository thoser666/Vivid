package com.vivid.feature.streaming.compositor.capture

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import androidx.core.net.toUri
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import com.vivid.feature.streaming.compositor.SceneProducer
import java.io.File
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicBoolean

/** Identity belongs to one capture lifetime, not a persisted document or layer. */
data class SceneVideoPlayback(val identity: Any, val positionSeconds: Double, val cycle: Long)

internal interface SceneVideoPlayer {
    fun prepare(prepared: () -> Unit, completed: () -> Unit, failed: () -> Unit)
    fun start()
    fun rewind(completed: () -> Unit)
    fun positionSeconds(): Double
    fun release()
}

/** Calls and MediaPlayer callbacks share one Looper; GL never owns a player callback. */
internal interface SceneVideoWorker {
    fun <T> call(action: () -> T): T
    fun close()
}

internal class AndroidSceneVideoWorker : SceneVideoWorker {
    private val thread = HandlerThread("VividSceneVideo").apply { start() }
    private val handler = Handler(thread.looper)
    override fun <T> call(action: () -> T): T {
        if (Looper.myLooper() === thread.looper) return action()
        val task = FutureTask(action)
        check(handler.post(task)) { "Scene video worker has stopped" }
        var interrupted = false
        try {
            while (true) {
                try { return task.get() }
                catch (_: InterruptedException) { interrupted = true }
                catch (failure: java.util.concurrent.ExecutionException) { throw requireNotNull(failure.cause) }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
    override fun close() { thread.quitSafely() }
}

/** Encoder-free video with an observable playhead and explicitly coordinated loops. */
internal class SceneVideoProducer(
    private val playerFactory: () -> SceneVideoPlayer,
    private val failed: () -> Unit,
    private val looped: (SceneVideoPlayback) -> Unit,
    private val worker: SceneVideoWorker = AndroidSceneVideoWorker(),
    private val loop: Boolean = true,
) : SceneProducer {
    private val access = Any()
    private val announced = AtomicBoolean(false)
    private val closed = AtomicBoolean(false)
    private val retainFrame = AtomicBoolean(false)
    private val identity = Any()
    private var player: SceneVideoPlayer? = null
    private var prepared = false
    private var usable = false
    private var cycle = 0L
    private var rewinding = false
    private var failedOnce = false

    override fun start() = synchronized(access) {
        check(!closed.get())
        worker.call {
            check(player == null)
            val candidate = playerFactory().also { player = it }
            try {
                candidate.prepare({
                    if (current(candidate)) {
                        try { prepared = true; candidate.start() } catch (_: Exception) { reportFailure() }
                    }
                }, {
                    if (current(candidate) && prepared && !rewinding && loop) {
                        try {
                            rewinding = true
                            candidate.rewind {
                                if (current(candidate) && rewinding) {
                                    rewinding = false
                                    cycle++
                                    looped(SceneVideoPlayback(identity, 0.0, cycle))
                                }
                            }
                        } catch (_: Exception) { reportFailure() }
                    }
                }, { if (current(candidate)) reportFailure() })
            } catch (error: Exception) {
                player = null
                try { candidate.release() } catch (cleanup: Exception) { error.addSuppressed(cleanup) }
                throw error
            }
        }
    }

    private fun current(candidate: SceneVideoPlayer) = !closed.get() && !failedOnce && player === candidate
    private fun reportFailure() {
        if (!failedOnce && !closed.get()) { failedOnce = true; usable = false; retainFrame.set(false); failed() }
    }

    fun frameReady() {
        if (announced.get()) return
        synchronized(access) {
            if (closed.get() || announced.get()) return
            worker.call {
                if (prepared && !failedOnce) { usable = true; announced.set(true); retainFrame.set(true) }
            }
        }
    }

    fun retainsLastFrame(): Boolean = retainFrame.get()

    fun playback(): SceneVideoPlayback? = synchronized(access) {
        if (closed.get()) return null
        worker.call {
            if (!prepared || !usable || failedOnce || rewinding) null else try {
                val position = requireNotNull(player).positionSeconds()
                if (position.isFinite() && position >= 0) SceneVideoPlayback(identity, position, cycle) else null
            } catch (_: Exception) { reportFailure(); null }
        }
    }

    override fun stop(): Unit = synchronized(access) {
        if (!closed.compareAndSet(false, true)) return
        retainFrame.set(false)
        // Complete release before the GL owner destroys the borrowed Surface.
        try { worker.call<Unit> { val old = player; player = null; prepared = false; usable = false; old?.release() } }
        finally { worker.close() }
    }
    override fun release() = stop()
}

internal fun androidSceneVideoPlayer(context: Context, locator: String, target: SceneCaptureTarget): SceneVideoPlayer {
    val parsed = locator.toUri()
    require(parsed.scheme == null || parsed.scheme in listOf("content", "file")) {
        "Video sources require a local document"
    }
    val uri = if (parsed.scheme == null) Uri.fromFile(File(locator)) else parsed
    return object : SceneVideoPlayer {
        private val player = MediaPlayer()
        private var released = false
        private var rewindComplete: (() -> Unit)? = null
        override fun prepare(prepared: () -> Unit, completed: () -> Unit, failed: () -> Unit) {
            player.setSurface(target.surface)
            player.isLooping = false
            player.setVolume(0f, 0f)
            player.setOnPreparedListener { prepared() }
            player.setOnCompletionListener { completed() }
            player.setOnSeekCompleteListener { if (!released) {
                try {
                    player.start()
                    val complete = rewindComplete; rewindComplete = null
                    complete?.invoke()
                } catch (_: Exception) { failed() }
            } }
            player.setOnVideoSizeChangedListener { _, width, height ->
                if (!released && width > 0 && height > 0) target.texture.setDefaultBufferSize(width, height)
            }
            player.setOnErrorListener { _, _, _ -> failed(); true }
            player.setDataSource(context, uri)
            player.prepareAsync()
        }
        override fun start() = player.start()
        override fun rewind(completed: () -> Unit) { rewindComplete = completed; player.seekTo(0) }
        override fun positionSeconds() = player.currentPosition / 1000.0
        override fun release() { if (!released) { released = true; player.release() } }
    }
}


/** Probe track readability before a global switch; this allocates no codec/encoder. */
internal fun sceneVideoFileReadable(context: Context, locator: String): Boolean = try {
    val parsed = locator.toUri()
    require(parsed.scheme == null || parsed.scheme in listOf("content", "file"))
    val uri = if (parsed.scheme == null) Uri.fromFile(File(locator)) else parsed
    val extractor = android.media.MediaExtractor()
    try {
        extractor.setDataSource(context, uri, null)
        (0 until extractor.trackCount).any {
            extractor.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
        }
    } finally { extractor.release() }
} catch (_: Exception) { false }
