package com.vivid.feature.streaming.compositor

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.SurfaceTexture
import android.hardware.camera2.*
import android.hardware.camera2.params.StreamConfigurationMap
import android.util.Range
import android.util.Size
import android.view.Surface
import com.pedro.encoder.input.video.Camera2ApiManager
import com.vivid.core.data.SceneSource
import com.vivid.core.data.SceneSourceType
import com.vivid.feature.streaming.compositor.capture.*
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadow.api.Shadow
import org.robolectric.util.ReflectionHelpers

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@ConscryptMode(ConscryptMode.Mode.OFF)
@GraphicsMode(GraphicsMode.Mode.LEGACY)
class SceneCameraFrameRateRobolectricTest {
    private var durationNs = 16_666_667L
    private val ranges = arrayOf(Range(30, 30), Range(30, 60), Range(60, 60))
    private val metadata = mockk<CameraCharacteristics> {
        every { get(CameraCharacteristics.SENSOR_ORIENTATION) } returns 0
        every { get(CameraCharacteristics.LENS_FACING) } returns CameraCharacteristics.LENS_FACING_BACK
        every { get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) } returns ranges
        every { get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) } returns mockk<StreamConfigurationMap> {
            every { getOutputSizes(SurfaceTexture::class.java) } returns arrayOf(Size(1280, 720))
            every { getOutputMinFrameDuration(SurfaceTexture::class.java, any()) } answers { durationNs }
        }
    }
    private val manager = mockk<CameraManager> {
        every { cameraIdList } returns arrayOf("rear")
        every { getCameraCharacteristics("rear") } returns metadata
    }
    private val context = mockk<Context> {
        every { getSystemService(Context.CAMERA_SERVICE) } returns manager
        every { checkSelfPermission(Manifest.permission.CAMERA) } returns PackageManager.PERMISSION_GRANTED
    }
    private val camera = mockk<Camera2ApiManager>(relaxed = true)
    private val source = SceneSource("source", SceneSourceType.CAMERA, "rear")
    private val target = SceneCaptureTarget(mockk(relaxed = true), mockk<Surface>(relaxed = true), 1280, 720)
    private val tasks = mutableListOf<() -> Unit>()
    private val backend = AndroidSceneCaptureBackend(context, SceneProjectionRegistry(),
        deviceRotationDegrees = { 0 }, dispatchCameraTask = { tasks.add(it) }, cameraFactory = { camera })

    @After fun release() { backend.release() }
    private fun start(fps: Int) = backend.run {
        configure(listOf(source))
        create(source, target.copy(frameRate = fps)) { fail("Unexpected camera failure") }.also {
            it.start()
            frameReady(source.id)
        }
    }
    private fun requestBuilder(): CaptureRequest.Builder {
        val device = Shadow.newInstanceOf(Class.forName("android.hardware.camera2.impl.CameraDeviceImpl")) as CameraDevice
        ReflectionHelpers.setField(device, "mCameraId", "rear")
        return device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range(30, 30))
            set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 1)
        }
    }
    private fun flush() { while (tasks.isNotEmpty()) tasks.removeAt(0).invoke() }

    @Test fun `initial capture requests high FPS only when size timing and AE ranges support it`() {
        val producer = start(60)
        verify { camera.prepareCamera(target.surface, 60) }
        producer.release()
        clearMocks(camera, answers = false, recordedCalls = true, childMocks = false)
        durationNs = 33_333_333L
        start(60)
        verify(exactly = 1) { camera.prepareCamera(target.surface, 30) }
        verify(exactly = 0) { camera.prepareCamera(target.surface, 60) }
    }

    @Test fun `preview demand lowers FPS through the existing session without reopening or resetting exposure`() {
        val builder = requestBuilder()
        every { camera.setCustomRequest(any()) } answers {
            firstArg<(CaptureRequest.Builder) -> Unit>().invoke(builder); true
        }
        start(60)
        repeat(5) { backend.setTargetFrameRate(30) }
        assertEquals(1, tasks.size)
        flush()
        assertEquals(Range(30, 30), builder.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE))
        assertEquals(1, builder.get(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION))
        verify(exactly = 1) { camera.prepareCamera(target.surface, 60); camera.prepareCamera(target.surface, 30) }
        verify(exactly = 1) { camera.openCameraId("rear") }
        verify(exactly = 0) { camera.closeCamera(any()) }
    }

    @Test fun `rapid demand changes coalesce before touching the camera`() {
        start(60)
        backend.setTargetFrameRate(30)
        backend.setTargetFrameRate(60)
        assertEquals(1, tasks.size)
        flush()
        verify(exactly = 0) { camera.setCustomRequest(any()) }
        verify(exactly = 1) { camera.prepareCamera(target.surface, 60) }
    }

    @Test fun `retired capture cannot apply a queued rate change to a replacement`() {
        val producer = start(60)
        backend.setTargetFrameRate(30)
        assertEquals(1, tasks.size)
        producer.release()
        flush()
        verify(exactly = 0) { camera.setCustomRequest(any()); camera.prepareCamera(target.surface, 30) }
    }

    @Test fun `failed rate requests restore the builder and stop retrying after a bounded failure count`() {
        val builder = requestBuilder()
        every { camera.setCustomRequest(any()) } answers {
            firstArg<(CaptureRequest.Builder) -> Unit>().invoke(builder); false
        }
        start(30)
        repeat(3) {
            backend.setTargetFrameRate(60)
            flush()
            assertEquals(Range(30, 30), builder.get(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE))
        }
        backend.setTargetFrameRate(60)
        assertTrue(tasks.isEmpty())
        verify(exactly = 6) { camera.setCustomRequest(any()) } // Request + builder restoration per attempt.
        verify(exactly = 1) { camera.prepareCamera(target.surface, 30); camera.openCameraId("rear") }
    }

    @Test fun `pending changes wait for the first usable buffer before configuring the session`() {
        backend.configure(listOf(source))
        val producer = backend.create(source, target) { }
        producer.start()
        backend.setTargetFrameRate(60)
        assertTrue(tasks.isEmpty())
        backend.frameReady(source.id)
        assertEquals(1, tasks.size)
        producer.release()
        flush()
    }

    @Test fun `range selection prefers an exact regular rate over a wider variable range`() {
        assertEquals(Range(60, 60), selectSceneCaptureFpsRange(ranges, 60))
        assertEquals(Range(30, 30), selectSceneCaptureFpsRange(ranges, 30))
        assertNull(selectSceneCaptureFpsRange(emptyArray(), 30))
    }
}
