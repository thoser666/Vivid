package com.vivid.feature.streaming.compositor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.display.DisplayManager
import android.view.Display
import androidx.test.core.app.ApplicationProvider
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers
import org.robolectric.util.ReflectionHelpers.ClassParameter
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
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
        every { get(CameraCharacteristics.SENSOR_ORIENTATION) } returns 0
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
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
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
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
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
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { constructed++; camera }
        backend.configure(listOf(source))
        try { backend.create(source, target) { }; fail("Permission denial ignored") }
        catch (_: SceneProducerPermissionException) { }
        assertEquals(0, constructed)
        assertNull(backend.camera(source.id))
        backend.release()
    }
    @Test fun `single camera can match 1080p while concurrent capture retains its 720p budget`() {
        val sizes = arrayOf(Size(640, 480), Size(1280, 720), Size(1920, 1080), Size(3840, 2160))
        assertEquals(Size(1920, 1080), AndroidSceneCaptureBackend.selectSize(sizes, 1920, 1080, concurrent = false))
        assertEquals(Size(1280, 720), AndroidSceneCaptureBackend.selectSize(sizes, 1920, 1080, concurrent = true))
        assertEquals(Size(3840, 2160), AndroidSceneCaptureBackend.selectSize(sizes, 3840, 2160, concurrent = false))
        val largeOnly = arrayOf(Size(3840, 2160), Size(1920, 1080))
        assertEquals(Size(1920, 1080), AndroidSceneCaptureBackend.selectSize(largeOnly, 640, 480))
        try {
            AndroidSceneCaptureBackend.selectSize(arrayOf(Size(7680, 4320)), 640, 480)
            fail("Capture memory limit ignored")
        } catch (_: IllegalStateException) { }
    }

    @Test fun `backend release closes a still live capture once and late producer release stays harmless`() {
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { fail("Released capture callback") }
        producer.start()
        backend.release()
        producer.release()
        assertNull(backend.camera(source.id))
        verify(exactly = 1) { camera.closeCamera(false) }
    }

    @Test fun `sampling correction follows display changes without applying sensor orientation twice`() {
        every { characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) } returns 90
        var display = 0
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { display }) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { }
        producer.start()
        assertEquals(0, backend.samplingRotationDegrees(source.id, 100L))
        display = 180
        assertEquals(180, backend.samplingRotationDegrees(source.id, 100L))
        verify(exactly = 1) { camera.openCameraId("rear") }
        producer.release()
        backend.release()
    }

    @Test @Config(sdk = [34])
    fun `automatic HAL rotation is matched by timestamp until a raw request is confirmed`() {
        every { characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) } returns 90
        every { characteristics.get(CameraCharacteristics.SCALER_AVAILABLE_ROTATE_AND_CROP_MODES) } returns
            intArrayOf(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE, CaptureRequest.SCALER_ROTATE_AND_CROP_90,
                CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO)
        val callback = slot<(CameraCaptureSession, CaptureRequest, TotalCaptureResult) -> Unit>()
        val opened = slot<CameraCallbacks>()
        every { camera.setCustomOnCaptureCompletedCallback(capture(callback)) } just Runs
        every { camera.setCameraCallbacks(capture(opened)) } just Runs
        val device = Shadow.newInstanceOf(Class.forName("android.hardware.camera2.impl.CameraDeviceImpl")) as CameraDevice
        ReflectionHelpers.setField(device, "mCameraId", "rear")
        val builder = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD)
        builder.set(CaptureRequest.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_AUTO)
        val initialRequest = builder.build()
        val session = Shadow.newInstanceOf(Class.forName("android.hardware.camera2.impl.CameraCaptureSessionImpl")) as CameraCaptureSession
        val metadata = ReflectionHelpers.callConstructor<Any>(Class.forName("android.hardware.camera2.impl.CameraMetadataNative"))
        val result = Shadow.newInstanceOf(TotalCaptureResult::class.java)
        ReflectionHelpers.setField(result, "mResults", metadata)
        fun <T> setResult(key: CaptureResult.Key<T>, value: T) {
            ReflectionHelpers.callInstanceMethod<Any?>(metadata, "set",
                ClassParameter.from(CaptureResult.Key::class.java, key), ClassParameter.from(Any::class.java, value))
        }
        var configured = false
        every { camera.setCustomRequest(any()) } answers {
            firstArg<(CaptureRequest.Builder) -> Unit>().invoke(builder)
            configured
        }
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { }
        producer.start()
        opened.captured.onCameraOpened()
        assertNull(backend.samplingRotationDegrees(source.id, 100L))
        setResult(CaptureResult.SENSOR_TIMESTAMP, 100L)
        setResult(CaptureResult.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_90)
        configured = true
        callback.captured.invoke(session, initialRequest, result)
        assertNull(backend.samplingRotationDegrees(source.id, 100L))
        assertNull(backend.samplingRotationDegrees(source.id, 101L))
        assertEquals(CaptureRequest.SCALER_ROTATE_AND_CROP_NONE, builder.get(CaptureRequest.SCALER_ROTATE_AND_CROP))
        val rawRequest = builder.build()
        setResult(CaptureResult.SCALER_ROTATE_AND_CROP, CaptureRequest.SCALER_ROTATE_AND_CROP_NONE)
        setResult(CaptureResult.SENSOR_TIMESTAMP, 101L)
        callback.captured.invoke(session, rawRequest, result)
        assertEquals(0, backend.samplingRotationDegrees(source.id, 102L))
        producer.release()
        callback.captured.invoke(session, rawRequest, result)
        verify(exactly = 2) { camera.setCustomRequest(any()) }
        backend.release()
    }

    @Test fun `display observer caches changes and unregisters exactly once`() {
        var rotation = Surface.ROTATION_0
        val display = mockk<Display> { every { getRotation() } answers { rotation } }
        val listener = slot<DisplayManager.DisplayListener>()
        val displays = mockk<DisplayManager>(relaxed = true) {
            every { getDisplay(Display.DEFAULT_DISPLAY) } returns display
            every { registerDisplayListener(capture(listener), any()) } just Runs
        }
        every { context.getSystemService(Context.DISPLAY_SERVICE) } returns displays
        every { context.mainLooper } returns ApplicationProvider.getApplicationContext<Context>().mainLooper
        val observer = SceneDisplayRotation(context)
        assertEquals(0, observer.degrees)
        rotation = Surface.ROTATION_90
        listener.captured.onDisplayChanged(Display.DEFAULT_DISPLAY)
        repeat(30) { assertEquals(90, observer.degrees) }
        rotation = Surface.ROTATION_180
        listener.captured.onDisplayChanged(42)
        assertEquals(90, observer.degrees)
        observer.close()
        observer.close()
        listener.captured.onDisplayChanged(Display.DEFAULT_DISPLAY)
        assertEquals(90, observer.degrees)
        verify(exactly = 2) { displays.getDisplay(Display.DEFAULT_DISPLAY) }
        verify(exactly = 1) { displays.unregisterDisplayListener(any()) }
    }

    @Test fun `single camera configures the output sized buffer before opening the borrowed surface`() {
        every { characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) } returns mockk<StreamConfigurationMap> {
            every { getOutputSizes(SurfaceTexture::class.java) } returns arrayOf(Size(1280, 720), Size(1920, 1080))
        }
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
        backend.configure(listOf(source))
        val output = target.copy(width = 1920, height = 1080)
        val producer = backend.create(source, output) { }
        producer.start()
        verify { target.texture.setDefaultBufferSize(1920, 1080) }
        verify { camera.prepareCamera(output.surface, 30); camera.openCameraId("rear") }
        producer.release()
        backend.release()
    }

    @Test fun `portrait output chooses buffer dimensions before the embedded sensor transform`() {
        every { characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) } returns 90
        every { characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) } returns mockk<StreamConfigurationMap> {
            every { getOutputSizes(SurfaceTexture::class.java) } returns arrayOf(Size(640, 480), Size(1920, 1080))
        }
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = { 0 }) { camera }
        backend.configure(listOf(source))
        val producer = backend.create(source, target.copy(width = 1080, height = 1920)) { }
        verify { target.texture.setDefaultBufferSize(1920, 1080) }
        producer.release()
        backend.release()
    }

    @Test fun `external camera orientation stays independent of phone rotation without a display observer`() {
        every { characteristics.get(CameraCharacteristics.LENS_FACING) } returns CameraCharacteristics.LENS_FACING_EXTERNAL
        val external = source.copy(type = SceneSourceType.UVC)
        var rotationQueries = 0
        val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(), deviceRotationDegrees = {
            rotationQueries++
            90
        }) { camera }
        backend.configure(listOf(external))
        val producer = backend.create(external, target) { }
        producer.start()
        assertEquals(0, backend.samplingRotationDegrees(external.id, 100L))
        assertEquals(0, rotationQueries)
        producer.release()
        backend.release()
    }

}
