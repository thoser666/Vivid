package com.vivid.irlbroadcaster

import com.vivid.R
import com.vivid.feature.streaming.StreamingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingServiceSupportTest {

    @Test
    fun `notificationTitle shows live state while streaming`() {
        assertEquals(R.string.notif_title_live, StreamingServiceSupport.notificationTitleRes(StreamingState.Streaming))
    }

    @Test
    fun `notificationTitle shows failure state`() {
        assertEquals(
            R.string.notif_title_failed,
            StreamingServiceSupport.notificationTitleRes(StreamingState.Failed("RTMP Auth Error")),
        )
    }

    @Test
    fun `notificationTitle shows preparing state otherwise`() {
        assertEquals(R.string.notif_title_preparing, StreamingServiceSupport.notificationTitleRes(StreamingState.Idle))
        assertEquals(R.string.notif_title_preparing, StreamingServiceSupport.notificationTitleRes(StreamingState.Preparing))
    }

    @Test
    fun `notificationText mentions background streaming while streaming`() {
        assertEquals(R.string.notif_text_streaming, StreamingServiceSupport.notificationTextRes(StreamingState.Streaming))
    }

    @Test
    fun `notificationText includes the failure reason`() {
        assertEquals(
            R.string.notif_text_failed,
            StreamingServiceSupport.notificationTextRes(StreamingState.Failed("RTMP Auth Error")),
        )
    }

    @Test
    fun `notification channel name is a resource`() {
        assertEquals(R.string.notif_channel_name, StreamingServiceSupport.CHANNEL_NAME_RES)
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
        assertEquals("com.vivid.extra.STREAM_URLS", StreamingServiceSupport.EXTRA_STREAM_URLS)
    }
}
