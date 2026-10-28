package com.vivid.irlbroadcaster

import com.vivid.R
import com.vivid.feature.streaming.StreamingState
import com.vivid.feature.streaming.source.VideoSourceKind
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingServiceSupportTest {

    @Test
    fun `layered screen capture requires projection service regardless of primary source`() {
        VideoSourceKind.entries.forEach { kind ->
            assertTrue(StreamingServiceSupport.requiresMediaProjectionFgs(kind, sceneProjectionActive = true))
        }
    }

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

    @Test
    fun `failureText prefers the localized resource over the raw library reason`() {
        val state = StreamingState.Failed(reason = "RTMP Auth Error", reasonRes = R.string.notif_text_failed)
        var resolved: Int? = null

        val text = StreamingServiceSupport.failureText(state) { resolved = it; "lokalisierter Grund" }

        assertEquals(R.string.notif_text_failed, resolved)
        assertEquals("lokalisierter Grund", text)
    }

    @Test
    fun `failureText falls back to the raw reason of the library`() {
        val text = StreamingServiceSupport.failureText(StreamingState.Failed("RTMP Auth Error")) {
            error("Ressource darf hier nicht aufgelöst werden")
        }

        assertEquals("RTMP Auth Error", text)
    }

    @Test
    fun `failureText is empty without failure state or cause`() {
        assertEquals("", StreamingServiceSupport.failureText(StreamingState.Streaming) { "x" })
        assertEquals("", StreamingServiceSupport.failureText(StreamingState.Failed()) { "x" })
    }

    @Test
    fun `requiresMediaProjectionFgs is true only for screen capture`() {
        assertTrue(StreamingServiceSupport.requiresMediaProjectionFgs(VideoSourceKind.SCREEN_CAPTURE))
        assertFalse(StreamingServiceSupport.requiresMediaProjectionFgs(VideoSourceKind.CAMERA))
        assertFalse(StreamingServiceSupport.requiresMediaProjectionFgs(VideoSourceKind.VIDEO_PLAYER))
        assertFalse(StreamingServiceSupport.requiresMediaProjectionFgs(VideoSourceKind.REPLAY))
    }
}
