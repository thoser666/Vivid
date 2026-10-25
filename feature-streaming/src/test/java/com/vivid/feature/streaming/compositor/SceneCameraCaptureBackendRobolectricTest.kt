package com.vivid.feature.streaming.compositor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Size
import android.view.Surface
import com.pedro.encoder.input.video.Camera2ApiManager
import com.pedro.encoder.input.video.CameraCallbacks
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.capture.*
import io.mockk.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneCameraCaptureBackendRobolectricTest {
    private val characteristics = mockk<CameraCharacteristics> {
        every { get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) } returns mockk<StreamConfigurationMap> {
            every { getOutputSizes(SurfaceTexture::class.java) } returns arrayOf(Size(640, 480))
        }
        every { get(CameraCharacteristics.LENS_FACING) } returns CameraCharacteristics.LENS_FACING_BACK
    }
    private val manager = mockk<CameraManager> {
        every { cameraIdList } returns arrayOf("rear")
        every { getCameraCharacteristics("rear") } returns characteristics
    }
    private val context = mockk<Context> {
        every { getSystemService(Context.CAMERA_SERVICE) } returns manager
        every { checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
    }
    private val camera = mockk<Camera2ApiManager>(relaxed = true)
    private val source = SceneSource("source", SceneSourceType.CAMERA, "rear")
    private val target = SceneCaptureTarget(mockk(relaxed = true), mockk<Surface>(relaxed = true), 640, 480)

    @Test fun `capture exposes its existing controls and releases once without owning the target`() {
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry()) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { fail("Unexpected disconnect") }
        producer.start()
        assertSame(camera, backend.camera(source.id))
        verify { camera.prepareCamera(target.surface, 30); camera.openCameraId("rear") }
        producer.stop()
        producer.release()
        assertNull(backend.camera(source.id))
        verify(exactly = 1) { camera.closeCamera(false) }
        verify(exactly = 0) { target.surface.release(); target.texture.release() }
        backend.release()
        verify(exactly = 1) { camera.closeCamera(false) }
    }

    @Test fun `revoked permission is classified and callbacks after release cannot affect a replacement`() {
        val callbacks = slot<CameraCallbacks>()
        every { camera.setCameraCallbacks(capture(callbacks)) } just Runs
        val failures = mutableListOf<Boolean>()
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry()) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { failures += it }
        producer.start()
        val oldCallbacks = callbacks.captured
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_DENIED
        oldCallbacks.onCameraDisconnected()
        assertEquals(listOf(true), failures)
        producer.release()
        oldCallbacks.onCameraError("Late error")
        assertEquals(listOf(true), failures)
        backend.release()
    }

    @Test fun `permission denial never constructs or exposes a camera`() {
        every { context.checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_DENIED
        var constructed = 0
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry()) { constructed++; camera }
        backend.configure(listOf(source))
        try { backend.create(source, target) { }; fail("Permission denial ignored") }
        catch (_: SceneProducerPermissionException) { }
        assertEquals(0, constructed)
        assertNull(backend.camera(source.id))
        backend.release()
    }
}
