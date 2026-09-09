package com.vivid.feature.streaming.source

import android.content.Context
import com.pedro.library.multiple.MultiFromFile
import com.pedro.library.multiple.MultiType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Deckt die [ReplayVideoSource] ab: Loop-Modus-Aktivierung, prepare-Erfolg
 * und -Fehler (Video/Audio/Exception), Start-Guard ohne Datei und die
 * Stream-Delegation an den RootEncoder-Player.
 */
class ReplayVideoSourceTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var context: Context
    private lateinit var player: MultiFromFile
    private lateinit var source: ReplayVideoSource

    @BeforeEach
    fun setUp() {
        context = mockk(relaxed = true)
        player = mockk(relaxed = true)
        source = ReplayVideoSource(context, player)
    }

    private fun replayFile(): File = File(tempDir, "replay-test.mp4").apply { writeText("x") }

    @Test
    fun `kind is REPLAY`() {
        assertEquals(VideoSourceKind.REPLAY, source.kind)
    }

    @Test
    fun `isActive reflects the player streaming state`() {
        every { player.isStreaming } returns true

        assertTrue(source.isActive)
    }

    @Test
    fun `isVideoSet is false initially`() {
        assertFalse(source.isVideoSet)
        assertNull(source.replayFile)
    }

    @Test
    fun `setReplay enables loop mode and prepares video and audio from the file path`() {
        val file = replayFile()
        every { player.prepareVideo(file.absolutePath) } returns true
        every { player.prepareAudio(file.absolutePath) } returns true

        val ok = source.setReplay(file)

        assertTrue(ok)
        assertTrue(source.isVideoSet)
        assertEquals(file, source.replayFile)
        verify(exactly = 1) { player.setLoopMode(true) }
        verify(exactly = 1) { player.prepareVideo(file.absolutePath) }
        verify(exactly = 1) { player.prepareAudio(file.absolutePath) }
    }

    @Test
    fun `setReplay returns false when video preparation fails`() {
        val file = replayFile()
        every { player.prepareVideo(file.absolutePath) } returns false

        val ok = source.setReplay(file)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
        assertNull(source.replayFile)
    }

    @Test
    fun `setReplay returns false when audio preparation fails`() {
        val file = replayFile()
        every { player.prepareVideo(file.absolutePath) } returns true
        every { player.prepareAudio(file.absolutePath) } returns false

        val ok = source.setReplay(file)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
    }

    @Test
    fun `setReplay returns false and does not throw when prepare throws`() {
        val file = replayFile()
        every { player.prepareVideo(file.absolutePath) } throws RuntimeException("corrupt file")

        val ok = source.setReplay(file)

        assertFalse(ok)
        assertFalse(source.isVideoSet)
    }

    @Test
    fun `start returns false without a set replay`() {
        assertFalse(source.start())
    }

    @Test
    fun `start returns true after a replay is set`() {
        val file = replayFile()
        every { player.prepareVideo(file.absolutePath) } returns true
        every { player.prepareAudio(file.absolutePath) } returns true
        source.setReplay(file)

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
