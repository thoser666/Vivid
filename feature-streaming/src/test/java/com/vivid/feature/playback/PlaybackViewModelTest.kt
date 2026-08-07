package com.vivid.feature.playback

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PlaybackViewModelTest {

    @Test
    fun `starts without a stream url and not playing`() {
        val viewModel = PlaybackViewModel()

        assertNull(viewModel.currentStreamUrl.value)
        assertFalse(viewModel.isPlaying.value)
    }

    @Test
    fun `setStreamUrl updates the current url`() {
        val viewModel = PlaybackViewModel()

        viewModel.setStreamUrl("rtmp://stream.example/live")

        assertEquals("rtmp://stream.example/live", viewModel.currentStreamUrl.value)
    }

    @Test
    fun `togglePlayback flips the playing state`() {
        val viewModel = PlaybackViewModel()

        viewModel.togglePlayback()
        assertTrue(viewModel.isPlaying.value)

        viewModel.togglePlayback()
        assertFalse(viewModel.isPlaying.value)
    }
}
