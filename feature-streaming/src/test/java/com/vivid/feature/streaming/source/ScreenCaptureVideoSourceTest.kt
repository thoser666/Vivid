package com.vivid.feature.streaming.source

import android.app.Activity
import android.content.Intent
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

class ScreenCaptureVideoSourceTest {

    private lateinit var display: MultiDisplay
    private lateinit var source: ScreenCaptureVideoSource

    @BeforeEach
    fun setUp() {
        display = mockk(relaxed = true)
        source = ScreenCaptureVideoSource(display)
    }

    @Test
    fun `kind is SCREEN_CAPTURE`() {
        assertEquals(VideoSourceKind.SCREEN_CAPTURE, source.kind)
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
    fun `onConsentResult with RESULT_OK grants consent and passes the data through`() {
        val data: Intent = mockk()

        val granted = source.onConsentResult(Activity.RESULT_OK, data)

        assertTrue(granted)
        assertTrue(source.isConsentGranted)
        verify { display.setIntentResult(Activity.RESULT_OK, data) }
    }

    @Test
    fun `onConsentResult with canceled result does not grant consent`() {
        val granted = source.onConsentResult(Activity.RESULT_CANCELED, mockk())

        assertFalse(granted)
        assertFalse(source.isConsentGranted)
    }

    @Test
    fun `onConsentResult with missing data does not grant consent`() {
        val granted = source.onConsentResult(Activity.RESULT_OK, null)

        assertFalse(granted)
        assertFalse(source.isConsentGranted)
    }

    @Test
    fun `start returns false without consent`() {
        assertFalse(source.start())
        verify(exactly = 0) { display.prepareAudio() }
        verify(exactly = 0) { display.prepareVideo() }
    }

    @Test
    fun `start prepares audio and video after consent`() {
        source.onConsentResult(Activity.RESULT_OK, mockk())
        every { display.prepareAudio() } returns true
        every { display.prepareVideo() } returns true

        assertTrue(source.start())
        verify { display.prepareAudio() }
        verify { display.prepareVideo() }
    }

    @Test
    fun `start returns false when video preparation fails`() {
        source.onConsentResult(Activity.RESULT_OK, mockk())
        every { display.prepareAudio() } returns true
        every { display.prepareVideo() } returns false

        assertFalse(source.start())
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
