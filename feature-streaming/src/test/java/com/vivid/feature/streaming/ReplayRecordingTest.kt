package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ReplayRecordingTest {

    @TempDir
    lateinit var tempDir: File

    private fun recorderThatAccepts(): ReplayRecorder =
        mockk {
            every { start(any()) } returns true
            every { stop() } returns Unit
        }

    // --- ReplayStorage.nextFile ---

    @Test
    fun `nextFile creates unique files for the same timestamp`() {
        val storage = ReplayStorage(tempDir, maxFiles = 3)
        val now = 1_700_000_000_000L

        val first = storage.nextFile(nowMillis = now)

        assertTrue(first.name.startsWith("replay-"))
        assertEquals("mp4", first.extension)

        first.createNewFile()
        val second = storage.nextFile(nowMillis = now)

        assertEquals(first.nameWithoutExtension + "-1", second.nameWithoutExtension)
    }

    @Test
    fun `nextFile creates the directory when missing`() {
        val nested = File(tempDir, "replays/nested")
        val storage = ReplayStorage(nested, maxFiles = 1)

        val file = storage.nextFile(nowMillis = 0)

        assertTrue(nested.isDirectory)
        assertEquals(nested, file.parentFile)
    }

    // --- ReplayStorage.prune / list ---

    @Test
    fun `prune keeps only the newest maxFiles mp4 recordings`() {
        val storage = ReplayStorage(tempDir, maxFiles = 2)
        val old = File(tempDir, "replay-old.mp4").apply { createNewFile(); setLastModified(1_000) }
        val mid = File(tempDir, "replay-mid.mp4").apply { createNewFile(); setLastModified(2_000) }
        val new = File(tempDir, "replay-new.mp4").apply { createNewFile(); setLastModified(3_000) }
        File(tempDir, "notes.txt").apply { createNewFile() }

        storage.prune()

        assertFalse(old.exists())
        assertTrue(mid.exists())
        assertTrue(new.exists())
        assertTrue(File(tempDir, "notes.txt").exists())
    }

    @Test
    fun `list returns only mp4 files sorted newest first`() {
        val storage = ReplayStorage(tempDir, maxFiles = 5)
        val a = File(tempDir, "replay-a.mp4").apply { createNewFile(); setLastModified(1_000) }
        val b = File(tempDir, "replay-b.mp4").apply { createNewFile(); setLastModified(2_000) }
        File(tempDir, "ignored.txt").apply { createNewFile() }

        assertEquals(listOf(b, a), storage.list())
    }

    // --- ReplayController.start/stop lifecycle ---

    @Test
    fun `start transitions from idle to recording`() = runTest {
        val storage = ReplayStorage(tempDir, maxFiles = 2)
        val recorder = recorderThatAccepts()
        val controller = ReplayController(storage, recorder)

        assertTrue(controller.state.first() is ReplayState.Idle)
        assertTrue(controller.start(nowMillis = 1_700_000_000_000))

        val recording = controller.state.first() as ReplayState.Recording
        verify { recorder.start(recording.file) }
    }

    @Test
    fun `start returns false when already recording`() {
        val storage = ReplayStorage(tempDir, maxFiles = 2)
        val controller = ReplayController(storage, recorderThatAccepts())

        assertTrue(controller.start(nowMillis = 1_700_000_000_000))
        assertFalse(controller.start(nowMillis = 1_700_000_001_000))
    }

    @Test
    fun `start returns false and keeps no file when the recorder fails`() {
        val storage = ReplayStorage(tempDir, maxFiles = 2)
        val recorder = mockk<ReplayRecorder> {
            every { start(any()) } returns false
        }
        val controller = ReplayController(storage, recorder)

        assertFalse(controller.start(nowMillis = 1_700_000_000_000))

        assertTrue(controller.state.value is ReplayState.Idle)
        assertEquals(0, storage.list().size)
    }

    @Test
    fun `stop returns the recording file and prunes old recordings`() {
        val storage = ReplayStorage(tempDir, maxFiles = 1)
        val recorder = recorderThatAccepts()
        val controller = ReplayController(storage, recorder)

        controller.start(nowMillis = 1_700_000_000_000)
        // Simulate that the muxer wrote the file.
        val recording = controller.state.value as ReplayState.Recording
        recording.file.createNewFile()
        // An older file that must be pruned on stop.
        File(tempDir, "replay-older.mp4").apply { createNewFile(); setLastModified(1) }

        val stopped = controller.stop()

        assertEquals(recording.file, stopped)
        assertFalse(File(tempDir, "replay-older.mp4").exists())
        assertEquals(ReplayState.Idle, controller.state.value)
        verify { recorder.stop() }
    }

    @Test
    fun `stop returns null when not recording`() {
        val storage = ReplayStorage(tempDir, maxFiles = 1)
        val recorder = recorderThatAccepts()
        val controller = ReplayController(storage, recorder)

        assertNull(controller.stop())
    }

    @Test
    fun `stop returns null when the muxer never wrote the file`() {
        val storage = ReplayStorage(tempDir, maxFiles = 1)
        val controller = ReplayController(storage, recorderThatAccepts())

        controller.start(nowMillis = 1_700_000_000_000)

        assertNull(controller.stop())
    }
}
