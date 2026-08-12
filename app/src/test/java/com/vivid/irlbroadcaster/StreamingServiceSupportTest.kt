package com.vivid.irlbroadcaster

import com.vivid.feature.streaming.StreamingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingServiceSupportTest {

    @Test
    fun `notificationTitle shows live state while streaming`() {
        assertEquals("Vivid sendet live", StreamingServiceSupport.notificationTitle(StreamingState.Streaming))
    }

    @Test
    fun `notificationTitle shows failure state`() {
        assertEquals(
            "Stream fehlgeschlagen",
            StreamingServiceSupport.notificationTitle(StreamingState.Failed("RTMP Auth Error")),
        )
    }

    @Test
    fun `notificationTitle shows preparing state otherwise`() {
        assertEquals("Stream wird vorbereitet …", StreamingServiceSupport.notificationTitle(StreamingState.Idle))
        assertEquals("Stream wird vorbereitet …", StreamingServiceSupport.notificationTitle(StreamingState.Preparing))
    }

    @Test
    fun `notificationText mentions background streaming while streaming`() {
        val text = StreamingServiceSupport.notificationText(StreamingState.Streaming)
        assertTrue(text.contains("Hintergrund"))
        assertTrue(text.contains("Beenden"))
    }

    @Test
    fun `notificationText includes the failure reason`() {
        val text = StreamingServiceSupport.notificationText(StreamingState.Failed("RTMP Auth Error"))
        assertTrue(text.contains("RTMP Auth Error"))
    }

    @Test
    fun `showStopAction is true for streaming and failed states`() {
        assertTrue(StreamingServiceSupport.showStopAction(StreamingState.Streaming))
        assertTrue(StreamingServiceSupport.showStopAction(StreamingState.Failed("boom")))
    }

    @Test
    fun `showStopAction is false for idle and preparing states`() {
        assertFalse(StreamingServiceSupport.showStopAction(StreamingState.Idle))
        assertFalse(StreamingServiceSupport.showStopAction(StreamingState.Preparing))
    }

    @Test
    fun `action and extra constants are stable`() {
        assertEquals("com.vivid.action.START_STREAM", StreamingServiceSupport.ACTION_START_STREAM)
        assertEquals("com.vivid.action.STOP_STREAM", StreamingServiceSupport.ACTION_STOP_STREAM)
        assertEquals("com.vivid.extra.STREAM_URL", StreamingServiceSupport.EXTRA_STREAM_URL)
    }
}
