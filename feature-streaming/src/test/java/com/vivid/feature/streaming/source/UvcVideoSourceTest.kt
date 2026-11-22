package com.vivid.feature.streaming.source

import android.app.Activity
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import com.pedro.library.multiple.MultiDisplay
import com.pedro.library.multiple.MultiType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UvcVideoSourceTest {

    private lateinit var display: MultiDisplay
    private lateinit var source: UvcVideoSource

    @BeforeEach
    fun setUp() {
        display = mockk(relaxed = true)
        source = UvcVideoSource(display, mockk(), mockk())
    }

    @Test
    fun `kind is UVC`() {
        assertEquals(VideoSourceKind.UVC, source.kind)
    }

    @Test
    fun `isActive reflects the display streaming state`() {
        every { display.isStreaming } returns true

        assertTrue(source.isActive)
    }

    @Test
    fun `createConsentIntent delegates to the display`() {
        val intent: Intent = mockk()
        every { display.sendIntent() } returns intent

        assertEquals(intent, source.createConsentIntent())
    }

    @Test
    fun `onDeviceResult with RESULT_OK grants consent and passes the data through`() {
        val data: Intent = mockk()

        val granted = source.onDeviceResult(Activity.RESULT_OK, data)

        assertTrue(granted)
        assertTrue(source.isDeviceOpen)
        verify { display.setIntentResult(Activity.RESULT_OK, data) }
    }

    @Test
    fun `onDeviceResult with canceled result does not grant consent`() {
        val granted = source.onDeviceResult(Activity.RESULT_CANCELED, mockk())

        assertFalse(granted)
        assertFalse(source.isDeviceOpen)
    }

    @Test
    fun `onDeviceResult with missing data does not grant consent`() {
        val granted = source.onDeviceResult(Activity.RESULT_OK, null)

        assertFalse(granted)
        assertFalse(source.isDeviceOpen)
    }

    @Test
    fun `start returns false without device open`() {
        assertFalse(source.start())
        verify(exactly = 0) { display.prepareAudio() }
        verify(exactly = 0) { display.prepareVideo() }
    }

    @Test
    fun `start prepares audio and video after device open`() {
        source.onDeviceResult(Activity.RESULT_OK, mockk())
        every { display.prepareAudio() } returns true
        every { display.prepareVideo() } returns true

        assertTrue(source.start())
        verify { display.prepareAudio() }
        verify { display.prepareVideo() }
    }

    @Test
    fun `start returns false when video preparation fails`() {
        source.onDeviceResult(Activity.RESULT_OK, mockk())
        every { display.prepareAudio() } returns true
        every { display.prepareVideo() } returns false

        assertFalse(source.start())
    }

    @Test
    fun `start returns false instead of crashing when prepare throws SecurityException`() {
        // VIVID-39 (#221): fehlender FGS-Typ mediaProjection wirft auf Android 14+
        // die SecurityException schon beim Vorbereiten — die Quelle meldet false
        // (Engine routet in den Failed-Zustand) statt den Prozess zu crashen.
        source.onDeviceResult(Activity.RESULT_OK, mockk())
        every { display.prepareAudio() } throws SecurityException("Media projections")

        assertFalse(source.start())
        verify(exactly = 0) { display.prepareVideo() }
    }

    @Test
    fun `stop stops the display stream`() {
        source.stop()

        verify { display.stopStream() }
    }

    @Test
    fun `startStream delegates to the display`() {
        source.startStream(1, "rtmp://test.com/app")

        verify { display.startStream(MultiType.RTMP, 1, "rtmp://test.com/app") }
    }

    @Test
    fun `stopStream delegates to the display`() {
        source.stopStream(0)

        verify { display.stopStream(MultiType.RTMP, 0) }
    }
}