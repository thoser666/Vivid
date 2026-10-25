package com.vivid.feature.streaming.compositor.gl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.EGL14
import android.opengl.EGLContext
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Surface
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.SceneFramePlan
import com.vivid.feature.streaming.compositor.SceneProducer
import com.vivid.feature.streaming.compositor.SceneProducerManager
import com.vivid.feature.streaming.compositor.SceneProducerState
import com.vivid.feature.streaming.compositor.capture.AndroidSceneCaptureBackend
import com.vivid.feature.streaming.compositor.capture.SceneBitmapRegistry
import com.vivid.feature.streaming.compositor.capture.SceneCaptureBackend
import com.vivid.feature.streaming.compositor.capture.SceneCaptureTarget
import com.vivid.feature.streaming.compositor.capture.SceneProjectionRegistry
import java.io.File
import java.io.InputStream
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Bounded latest-frame texture slots. GL upload/update happens only on the render thread. */
class AndroidSceneTextureProvider(
    private val projections: SceneProjectionRegistry,
    private val bitmaps: SceneBitmapRegistry,
    private val clockMillis: () -> Long = SystemClock::elapsedRealtime,
    private val legacyCameraId: () -> String? = { null },
    private val privacyEnabled: () -> Boolean = { false },
    private val publishStates: (Map<String, SceneProducerState>) -> Unit = {},
    private val backendFactory: (Context) -> SceneCaptureBackend = { AndroidSceneCaptureBackend(it, projections) },
) : SceneTextureProvider {
    private class Slot(val source: SceneSource, val id: Int, val external: Boolean,
        val texture: SurfaceTexture?, val surface: Surface?, val createdAt: Long) {
        val pendingFrame = AtomicBoolean(false)
        val lastArrival = AtomicLong(createdAt)
        val closed = AtomicBoolean(false)
        var frame = SceneTextureFrame(id, external)
        var ready = false
        var bitmapRevision = 0L
    }
    private data class Failure(val slot: Slot, val generation: Long, val permission: Boolean)
    private data class Image(val slot: Slot, val bitmap: Bitmap)
    private data class Retry(val sourceId: String, val restart: Boolean)
    private val slots = linkedMapOf<String, Slot>()
    private val failures = ConcurrentLinkedQueue<Failure>()
    private val images = ConcurrentLinkedQueue<Image>()
    private val retries = ConcurrentLinkedQueue<Retry>()
    private var context: Context? = null
    private var attachedContext: EGLContext? = null
    private var backend: SceneCaptureBackend? = null
    private var manager: SceneProducerManager? = null
    private var workers: ExecutorService? = null
    private var frameThread: HandlerThread? = null
    private var frameHandler: Handler? = null
    private var width = 0
    private var height = 0
    private var lastPublished: Map<String, SceneProducerState> = emptyMap()
    private val sourceMatrix = identityTextureMatrix()
    private val topLeftToGl = identityTextureMatrix().apply { this[5] = -1f; this[13] = 1f }

    @Synchronized
    override fun attach(context: Context, width: Int, height: Int) {
        detach()
        this.context = context
        this.width = width
        this.height = height
        attachedContext = EGL14.eglGetCurrentContext()
        workers = Executors.newFixedThreadPool(2)
        frameThread = HandlerThread("VividSceneFrames").apply { start() }
        frameHandler = Handler(requireNotNull(frameThread).looper)
        backend = backendFactory(context)
        manager = SceneProducerManager { createProducer(it) }
    }

    @Synchronized
    override fun update(plan: SceneFramePlan, nowMillis: Long) {
        val lifecycle = manager ?: return
        val states = listOfNotNull(plan.outgoing, plan.current)
        val inputIds = states.mapNotNull { it.inputSourceId }.toSet()
        val legacyCamera = legacyCameraId()
        val requested = states.flatMap { state ->
            val ids = state.composition.layers.filter { it.visible && it.opacity > 0f }.map { it.sourceId }.toSet() +
                listOfNotNull(state.inputSourceId)
            state.composition.sources.filter { it.id in ids }
        }.distinctBy { it.id }.map { source ->
            if (source.id in inputIds && source.type == SceneSourceType.CAMERA && legacyCamera != null) {
                source.copy(locator = legacyCamera)
            } else source
        }.toMutableList()
        // Even an invisible legacy input still owns its camera until the pipeline is stopped.
        // Include it in capability checks so an extra source cannot displace that session.
        if (legacyCamera != null && requested.none {
            it.type == SceneSourceType.CAMERA && it.locator == legacyCamera
        }) requested.add(SceneSource("pipeline-camera:$legacyCamera", SceneSourceType.CAMERA, legacyCamera))
        backend?.configure(requested)
        val captureScenes = states.map { state -> state.composition.copy(
            sources = state.composition.sources.filter { it.id != state.inputSourceId },
            layers = state.composition.layers.filter { it.sourceId != state.inputSourceId },
        ) }
        while (true) {
            val failure = failures.poll() ?: break
            if (slots[failure.slot.source.id] === failure.slot && !failure.slot.closed.get()) {
                lifecycle.disconnected(failure.slot.source.id, nowMillis, failure.generation, failure.permission)
            }
        }
        while (true) {
            val retry = retries.poll() ?: break
            if (retry.restart) lifecycle.states[retry.sourceId]?.let {
                lifecycle.disconnected(retry.sourceId, nowMillis, it.generation)
            }
            lifecycle.retry(retry.sourceId)
        }
        lifecycle.reconcile(captureScenes, nowMillis)
        while (true) {
            val pending = images.poll() ?: break
            try {
                if (!pending.slot.closed.get() && slots[pending.slot.source.id] === pending.slot) {
                    upload(pending.slot, pending.bitmap)
                }
            } finally { pending.bitmap.recycle() }
        }
        for (slot in slots.values.toList()) {
            if (slot.external) {
                try {
                    if (slot.pendingFrame.getAndSet(false)) {
                        requireNotNull(slot.texture).updateTexImage()
                        slot.texture.getTransformMatrix(sourceMatrix)
                        Matrix.multiplyMM(slot.frame.transform, 0, sourceMatrix, 0, topLeftToGl, 0)
                        if (!slot.ready) lifecycle.states[slot.source.id]?.let {
                            lifecycle.markHealthy(slot.source.id, it.generation)
                        }
                        slot.ready = true
                    }
                    if (nowMillis - slot.lastArrival.get() > FRAME_TIMEOUT_MILLIS) {
                        val generation = lifecycle.states[slot.source.id]?.generation ?: continue
                        lifecycle.disconnected(slot.source.id, nowMillis, generation)
                    }
                } catch (_: RuntimeException) {
                    val generation = lifecycle.states[slot.source.id]?.generation ?: continue
                    lifecycle.disconnected(slot.source.id, nowMillis, generation)
                }
            } else if (slot.source.type == SceneSourceType.WIDGET) {
                bitmaps.consumeChanged(slot.source.id, slot.bitmapRevision) { revision, bitmap ->
                    upload(slot, bitmap)
                    slot.bitmapRevision = revision
                }
            }
        }
        val snapshot = lifecycle.states
        if (snapshot != lastPublished) { lastPublished = snapshot; publishStates(snapshot) }
    }

    @Synchronized
    override fun texture(sourceId: String): SceneTextureFrame? {
        val slot = slots[sourceId]?.takeIf { it.ready } ?: return null
        val protect = slot.external && privacyEnabled()
        if (slot.frame.fullFramePrivacy != protect) slot.frame = slot.frame.copy(fullFramePrivacy = protect)
        return slot.frame
    }

    fun retry(sourceId: String) { retries.add(Retry(sourceId, false)) }

    /** A newly granted projection replaces any old session on the render thread. */
    fun restart(sourceId: String) { retries.add(Retry(sourceId, true)) }

    private fun createProducer(source: SceneSource): SceneProducer {
        val external = source.type !in listOf(SceneSourceType.IMAGE, SceneSourceType.WIDGET)
        val slot = createSlot(source, external)
        slots[source.id] = slot
        try {
            val generation = requireNotNull(manager).states.getValue(source.id).generation
            val delegate = when (source.type) {
                SceneSourceType.IMAGE -> imageProducer(slot)
                SceneSourceType.WIDGET -> object : SceneProducer {
                    override fun start() = Unit
                    override fun stop() = Unit
                    override fun release() = Unit
                }
                else -> requireNotNull(backend).create(source,
                    SceneCaptureTarget(requireNotNull(slot.texture), requireNotNull(slot.surface), width, height)) {
                    permission -> failures.add(Failure(slot, generation, permission))
                }
            }
            return object : SceneProducer {
                override fun start() = delegate.start()
                override fun stop() = delegate.stop()
                override fun release() {
                    try { delegate.release() } finally { releaseSlot(slot) }
                }
            }
        } catch (failure: Exception) {
            releaseSlot(slot)
            throw failure
        }
    }

    private fun createSlot(source: SceneSource, external: Boolean): Slot {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        check(ids[0] > 0) { "Unable to allocate source texture" }
        val target = if (external) GLES11Ext.GL_TEXTURE_EXTERNAL_OES else GLES20.GL_TEXTURE_2D
        GLES20.glBindTexture(target, ids[0])
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(target, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        var texture: SurfaceTexture? = null
        var surface: Surface? = null
        try {
            if (external) {
                texture = SurfaceTexture(ids[0])
                texture.setDefaultBufferSize(width, height)
                surface = Surface(texture)
            }
            val slot = Slot(source, ids[0], external, texture, surface, clockMillis())
            texture?.setOnFrameAvailableListener({
                if (!slot.closed.get()) {
                    slot.lastArrival.set(clockMillis())
                    slot.pendingFrame.set(true)
                }
            }, frameHandler)
            return slot
        } catch (failure: Exception) {
            surface?.release()
            texture?.release()
            GLES20.glDeleteTextures(1, ids, 0)
            throw failure
        }
    }

    private fun imageProducer(slot: Slot): SceneProducer = object : SceneProducer {
        private var task: Future<*>? = null
        override fun start() {
            val generation = requireNotNull(manager).states.getValue(slot.source.id).generation
            task = requireNotNull(workers).submit {
                try {
                    val bitmap = decodeImage(requireNotNull(context), slot.source.locator)
                    synchronized(this@AndroidSceneTextureProvider) {
                        if (slot.closed.get()) bitmap.recycle() else images.add(Image(slot, bitmap))
                    }
                } catch (_: SecurityException) {
                    failures.add(Failure(slot, generation, true))
                } catch (_: Exception) {
                    failures.add(Failure(slot, generation, false))
                }
            }
        }
        override fun stop() { task?.cancel(true); task = null }
        override fun release() = stop()
    }

    private fun upload(slot: Slot, bitmap: Bitmap) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, slot.id)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        if (!slot.ready) manager?.states?.get(slot.source.id)?.let {
            manager?.markHealthy(slot.source.id, it.generation)
        }
        slot.ready = true
    }

    private fun releaseSlot(slot: Slot) {
        if (!slot.closed.compareAndSet(false, true)) return
        if (slots[slot.source.id] === slot) slots.remove(slot.source.id)
        slot.texture?.setOnFrameAvailableListener(null)
        slot.surface?.release()
        slot.texture?.release()
        if (attachedContext != null && attachedContext == EGL14.eglGetCurrentContext()) {
            GLES20.glDeleteTextures(1, intArrayOf(slot.id), 0)
        }
    }

    @Synchronized
    override fun detach() {
        manager?.release()
        manager = null
        slots.values.toList().forEach(::releaseSlot)
        backend?.release()
        backend = null
        workers?.shutdownNow()
        workers = null
        frameThread?.quitSafely()
        frameThread = null
        frameHandler = null
        while (true) { val image = images.poll() ?: break; image.bitmap.recycle() }
        failures.clear()
        retries.clear()
        context = null
        attachedContext = null
        lastPublished = emptyMap()
        publishStates(emptyMap())
    }

    companion object {
        private const val FRAME_TIMEOUT_MILLIS = 5_000L
        private fun decodeImage(context: Context, locator: String): Bitmap {
            val uri = Uri.parse(locator)
            fun stream(): InputStream {
                require(uri.scheme in listOf("content", "file") || uri.scheme == null)
                return if (uri.scheme == null) File(locator).inputStream()
                    else requireNotNull(context.contentResolver.openInputStream(uri))
            }
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            stream().use { BitmapFactory.decodeStream(it, null, bounds) }
            require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Invalid image source" }
            val options = BitmapFactory.Options().apply { inSampleSize = 1 }
            while (bounds.outWidth / options.inSampleSize > SceneBitmapRegistry.MAX_BITMAP_SIZE ||
                bounds.outHeight / options.inSampleSize > SceneBitmapRegistry.MAX_BITMAP_SIZE) {
                options.inSampleSize *= 2
            }
            return stream().use { requireNotNull(BitmapFactory.decodeStream(it, null, options)) }
        }
    }
}
