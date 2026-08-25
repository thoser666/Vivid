package com.vivid.feature.streaming.source

import android.content.Context
import android.net.Uri
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class VideoPlayerVideoSourceTest {

    private lateinit var context: Context
    private lateinit var player: MultiFromFile
    private lateinit var source: VideoPlayerVideoSource

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        player = mockk(relaxed = true)
        source = VideoPlayerVideoSource(context, player)
    }

    @Test
    fun `kind is VIDEO_PLAYER`() {
        assertEquals(VideoSourceKind.VIDEO_PLAYER, source.kind)
    }

    @Test
    fun `isActive reflects the player streaming state`() {
        every { player.isStreaming } returns true

        assertTrue(source.isActive)
    }

    @Test
    fun `isVideoSet is false initially`() {
        assertFalse(source.isVideoSet)
        assertNull(source.videoUri)
    }

    @Test
    fun `setVideo prepares the player and marks the source ready`() {
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns true

        val ok = source.setVideo(uri)

        assertTrue(ok)
        assertTrue(source.isVideoSet)
        assertEquals(uri, source.videoUri)
        verify { player.prepareVideo(context, uri) }
        verify { player.prepareAudio(context, uri) }
    }

    @Test
    fun `setVideo returns false when video preparation fails`() {
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns false

        val ok = source.setVideo(uri)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
        assertNull(source.videoUri)
    }

    @Test
    fun `setVideo returns false when audio preparation fails`() {
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns false

        val ok = source.setVideo(uri)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
    }

    @Test
    fun `setVideo returns false and does not throw when prepare throws`() {
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } throws RuntimeException("bad file")

        val ok = source.setVideo(uri)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
    }

    @Test
    fun `start returns false without a set video`() {
        assertFalse(source.start())
    }

    @Test
    fun `start returns true after a video is set`() {
        val uri: Uri = mockk<Uri>()
        every { player.prepareVideo(any<Context>(), any<Uri>()) } returns true
        every { player.prepareAudio(any<Context>(), any<Uri>()) } returns true
        source.setVideo(uri)

        assertTrue(source.start())
    }

    @Test
    fun `stop stops the player stream`() {
        source.stop()

        verify { player.stopStream() }
    }

    @Test
    fun `startStream delegates to the player`() {
        source.startStream(1, "rtmp://test.com/app")

        verify { player.startStream(MultiType.RTMP, 1, "rtmp://test.com/app") }
    }

    @Test
    fun `stopStream delegates to the player`() {
        source.stopStream(0)

        verify { player.stopStream(MultiType.RTMP, 0) }
    }
}
