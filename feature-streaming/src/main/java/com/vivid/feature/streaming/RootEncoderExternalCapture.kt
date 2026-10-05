package com.vivid.feature.streaming

import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.library.base.Camera2Base
import com.pedro.library.view.GlStreamInterface

/**
 * RootEncoder 2.7.5 has no external-camera hook in Camera2Base. Isolate its private
 * contract here: configure the SAME GL renderer, then suppress openLastCamera while
 * the bridge owns Camera2. Never replace the renderer or bypass its filter chain.
 * Consumer keep rules preserve these names. A changed contract throws before start.
 */
internal class RootEncoderExternalCapture(private val camera: Camera2Base) : AutoCloseable {
    private val manager = Camera2Base::class.java.getDeclaredField("cameraManager").apply { isAccessible = true }
        .get(camera) as Camera2ApiManager
    private val running = Camera2ApiManager::class.java.getDeclaredField("isRunning").apply { isAccessible = true }
    private val prepareGl = Camera2Base::class.java.getDeclaredMethod("prepareGlView",
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }

    fun prepare(width: Int, height: Int): GlStreamInterface {
        check(!camera.isStreaming && !camera.isRecording && !manager.isRunning)
        prepareGl.invoke(camera, width, height, width, height, 0)
        val gl = camera.glInterface as GlStreamInterface
        check(gl.isRunning)
        restoreInputOrientation()
        return gl
    }

    /** Decoder output already uses native landscape pixel axes, unlike Camera2's texture transform. */
    fun restoreInputOrientation() {
        (camera.glInterface as GlStreamInterface).setCameraOrientation(0)
    }

    fun claimCamera() {
        check(!manager.isRunning)
        running.setBoolean(manager, true)
    }

    override fun close() {
        // No CameraDevice is assigned to this manager; the bridge owns and closes it.
        running.setBoolean(manager, false)
    }
}
