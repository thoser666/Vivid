package com.vivid.feature.streaming

import com.pedro.library.base.Camera2Base
import com.pedro.library.multiple.MultiCamera2
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class StandaloneRecordingAudioTest {
    @Test fun `video only after audio recording does not restart a released AAC encoder`() {
        val camera = mockk<MultiCamera2>(relaxed = true)
        val field = Camera2Base::class.java.getDeclaredField("audioInitialized").apply { isAccessible = true }
        field.setBoolean(camera, true)
        assertTrue(prepareStandaloneRecordingAudio(camera, false))
        assertFalse(field.getBoolean(camera))
        verify(exactly = 0) { camera.prepareAudio() }
    }
    @Test fun `a new recording with audio prepares AAC again`() {
        val camera = mockk<MultiCamera2>(relaxed = true)
        every { camera.prepareAudio() } returns true
        assertTrue(prepareStandaloneRecordingAudio(camera, true))
        verify(exactly = 1) { camera.prepareAudio() }
    }
}
