package com.vivid.feature.streaming.source

import android.view.Surface
import com.pedro.library.multiple.MultiStream
import com.pedro.library.multiple.MultiType
import com.vivid.feature.streaming.source.uvc.UvcCaptureMode
import com.vivid.feature.streaming.source.uvc.UvcConnectionManager
import com.vivid.feature.streaming.source.uvc.UvcConnectionState
import com.vivid.feature.streaming.source.uvc.UvcDeviceDescriptor
import com.vivid.feature.streaming.source.uvc.UvcPixelFormat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UsbVideoSourceTest {

    private lateinit var multi: MultiStream
    private lateinit var connectionManager: UvcConnectionManager
    private lateinit var connectionState: MutableStateFlow<UvcConnectionState>
    private lateinit var source: UsbVideoSource

    private val device = UvcDeviceDescriptor(
        stableSessionId = "046d:0825#0",
        vendorId = 0x046d,
        productId = 0x0825,
        productLabel = "TestCam",
    )
    private val mode = UvcCaptureMode(1280, 720, 30, UvcPixelFormat.MJPEG)

    @BeforeEach
    fun setUp() {
        multi = mockk(relaxed = true)
        connectionManager = mockk(relaxed = true)
        connectionState =
            MutableStateFlow<UvcConnectionState>(UvcConnectionState.Disconnected)
        every { connectionManager.connectionState } returns connectionState
        source = UsbVideoSource(multi, connectionManager)
    }

    @Test
    fun `kind is USB_UVC`() {
        assertEquals(VideoSourceKind.USB_UVC, source.kind)
    }

    @Test
    fun `start without ready device prepares nothing`() {
        assertFalse(source.start())

        verify(exactly = 0) { multi.prepareAudio(any(), any(), any()) }
        verify(exactly = 0) { multi.prepareVideo(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `start prepares audio and video with capture mode size`() {
        connectionState.value = UvcConnectionState.Ready(device, mode)
        every { multi.prepareAudio(any(), any(), any()) } returns true
        every { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        assertTrue(source.start())

        verify(exactly = 1) {
            multi.prepareAudio(
                UsbVideoSource.DEFAULT_AUDIO_SAMPLE_RATE,
                UsbVideoSource.DEFAULT_AUDIO_STEREO,
                UsbVideoSource.DEFAULT_AUDIO_BITRATE,
            )
        }
        verify(exactly = 1) {
            multi.prepareVideo(
                1280,
                720,
                UsbVideoSource.DEFAULT_VIDEO_BITRATE,
                30,
                UsbVideoSource.KEYFRAME_INTERVAL_SECONDS,
                0,
            )
        }
    }

    @Test
    fun `start stops idle preview before preparing`() {
        connectionState.value = UvcConnectionState.Ready(device, mode)
        every { multi.isOnPreview } returns true
        every { multi.prepareAudio(any(), any(), any()) } returns true
        every { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        source.start()

        verify(exactly = 1) { multi.stopPreview() }
    }

    @Test
    fun `stream routing uses indexed RTMP on the shared encoder`() {
        source.startStream(0, "rtmp://a/live")
        source.startStream(1, "rtmp://b/live")

        verify(exactly = 1) { multi.startStream(MultiType.RTMP, 0, "rtmp://a/live") }
        verify(exactly = 1) { multi.startStream(MultiType.RTMP, 1, "rtmp://b/live") }

        source.stopStream(0)

        verify(exactly = 1) { multi.stopStream(MultiType.RTMP, 0) }
        verify(exactly = 0) { multi.stopStream(MultiType.RTMP, 1) }
    }

    @Test
    fun `idle preview uses encoder surface pipeline without a second stream`() {
        connectionState.value = UvcConnectionState.Ready(device, mode)
        val surface = mockk<Surface>()
        every { multi.isOnPreview } returns false
        every { multi.prepareVideo(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) } returns true

        assertTrue(source.startIdlePreview(surface, 1280, 720))

        verify(exactly = 1) { multi.startPreview(surface, 1280, 720) }
        verify(exactly = 0) { multi.startStream(any(), any(), any()) }
    }

    @Test
    fun `idle preview without ready device fails closed`() {
        assertFalse(source.startIdlePreview(mockk(), 1280, 720))

        verify(exactly = 0) { multi.startPreview(any<Surface>(), any(), any()) }
    }

    @Test
    fun `isActive reflects streaming or recording`() {
        every { multi.isStreaming } returns true
        assertTrue(source.isActive)

        every { multi.isStreaming } returns false
        every { multi.isRecording } returns true
        assertTrue(source.isActive)

        every { multi.isRecording } returns false
        assertFalse(source.isActive)
    }
}
