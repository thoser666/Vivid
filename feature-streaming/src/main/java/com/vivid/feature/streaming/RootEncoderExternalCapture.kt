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
    @Volatile private var inputRotationDegrees = 0
    private val manager = Camera2Base::class.java.getDeclaredField("cameraManager").apply { isAccessible = true }
        .get(camera) as Camera2ApiManager
    private val running = Camera2ApiManager::class.java.getDeclaredField("isRunning").apply { isAccessible = true }
    private val prepareGl = Camera2Base::class.java.getDeclaredMethod("prepareGlView",
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
        Int::class.javaPrimitiveType, Int::class.javaPrimitiveType).apply { isAccessible = true }

    fun prepare(width: Int, height: Int, rotationDegrees: Int = 0): GlStreamInterface {
        check(!camera.isStreaming && !camera.isRecording && !manager.isRunning)
        require(rotationDegrees == 0 || rotationDegrees == 90)
        inputRotationDegrees = rotationDegrees
        prepareGl.invoke(camera, width, height, width, height, rotationDegrees)
        val gl = camera.glInterface as GlStreamInterface
        check(gl.isRunning)
        restoreInputOrientation()
        return gl
    }

    /** Decoder output uses native axes; portrait rotation is applied before scene composition. */
    fun restoreInputOrientation() {
        (camera.glInterface as GlStreamInterface).setCameraOrientation(inputRotationDegrees)
    }

    /** Rotate the camera source before GL filters, keeping overlay text upright in the output. */
    fun setInputOrientation(rotationDegrees: Int) {
        require(rotationDegrees in setOf(0, 90, 180, 270))
        inputRotationDegrees = rotationDegrees
        restoreInputOrientation()
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
