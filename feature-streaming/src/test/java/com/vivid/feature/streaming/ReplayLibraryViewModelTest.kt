package com.vivid.feature.streaming

import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

@OptIn(ExperimentalCoroutinesApi::class)
class ReplayLibraryViewModelTest {

    @TempDir
    lateinit var tempDir: File

    private lateinit var storage: ReplayStorage
    private lateinit var library: ReplayLibrary
    private lateinit var viewModel: ReplayLibraryViewModel

    @BeforeEach
    fun setUp() {
        Dispatchers.setMain(StandardTestDispatcher())
        storage = ReplayStorage(tempDir, maxFiles = 3)
        library = ReplayLibrary(storage)
        viewModel = ReplayLibraryViewModel(
            library = library,
            appContext = mockk {
                every { packageName } returns "com.vivid.test"
            },
        )
    }

    @AfterEach
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun newFile(name: String, size: Long = 2048): File =
        File(tempDir, name).apply {
            writeText("x".repeat(size.toInt()))
        }

    @Test
    fun `refresh loads items newest first and clears loading`() = runTest {
        newFile("replay-a.mp4").setLastModified(1_000)
        newFile("replay-b.mp4").setLastModified(2_000)

        viewModel.refresh()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.loading)
        assertEquals(2, state.items.size)
        assertEquals("replay-b.mp4", state.items.first().name)
        assertTrue(state.items.first().sizeBytes > 0)
    }

    @Test
    fun `open and close manage the playing item`() = runTest {
        val file = newFile("replay-x.mp4")
        viewModel.refresh()
        advanceUntilIdle()
        val item = viewModel.uiState.value.items.first()

        viewModel.open(item)
        assertEquals(item, viewModel.uiState.value.playing)

        viewModel.close()
        assertNull(viewModel.uiState.value.playing)
        assertNotNull(file)
    }

    @Test
    fun `confirmDelete removes only the candidate file`() = runTest {
        val keep = newFile("replay-keep.mp4")
        newFile("replay-drop.mp4")
        viewModel.refresh()
        advanceUntilIdle()
        val drop = viewModel.uiState.value.items.first { it.name == "replay-drop.mp4" }

        viewModel.requestDelete(drop)
        assertEquals(drop, viewModel.uiState.value.deleteCandidate)

        viewModel.confirmDelete()
        advanceUntilIdle()

        assertFalse(File(tempDir, "replay-drop.mp4").exists())
        assertTrue(keep.exists())
        assertNull(viewModel.uiState.value.deleteCandidate)
        assertEquals(1, viewModel.uiState.value.items.size)
    }

    @Test
    fun `dismissDelete clears the candidate without deleting the file`() = runTest {
        val file = newFile("replay-dismiss.mp4")
        viewModel.refresh()
        advanceUntilIdle()
        val item = viewModel.uiState.value.items.first { it.name == "replay-dismiss.mp4" }

        viewModel.requestDelete(item)
        assertEquals(item, viewModel.uiState.value.deleteCandidate)

        viewModel.dismissDelete()
        assertTrue(file.exists())
        assertNull(viewModel.uiState.value.deleteCandidate)
    }

    @Test
    fun `confirmDelete without candidate is a no-op`() = runTest {
        viewModel.confirmDelete()
        advanceUntilIdle()
        assertEquals(emptyList<ReplayItem>(), viewModel.uiState.value.items)
    }

    @Test
    fun `confirmDeleteAll clears the library`() = runTest {
        newFile("replay-1.mp4")
        newFile("replay-2.mp4")
        newFile("keep.txt")
        viewModel.refresh()
        advanceUntilIdle()

        viewModel.confirmDeleteAll()
        advanceUntilIdle()

        assertEquals(0, viewModel.uiState.value.items.size)
        assertTrue(File(tempDir, "keep.txt").exists())
    }

    @Test
    fun `shareIntent returns null for a missing file`() = runTest {
        val item = ReplayItem(file = File(tempDir, "gone.mp4"), name = "gone.mp4", sizeBytes = 1, lastModified = 0)

        assertNull(viewModel.shareIntent(item))
    }

    // Hinweis: Der shareIntent-Happy-Path (FileProvider-Uri + ACTION_SEND-Intent)
    // braucht android.content.Intent/Uri — android.jar-Systemklassen kann MockK
    // im JVM-Unit-Test nicht instrumentieren. Der Pfad ist Robolectric-
    // instrumentiertem Test vorbehalten (siehe PARITY-Log Coverage-Ausbau).

    @Test
    fun `delete rejects files outside the replay directory`() {
        val replayDir = File(tempDir, "replays").apply { mkdirs() }
        val library = ReplayLibrary(ReplayStorage(replayDir))
        val outsideDir = File(tempDir, "outside").apply { mkdirs() }
        val outsideFile = File(outsideDir, "evil.mp4").apply { writeText("x") }

        val result = library.delete(outsideFile)

        assertFalse(result)
        assertTrue(outsideFile.exists())
    }

    @Test
    fun `delete accepts a file inside the replay directory`() {
        val replayDir = File(tempDir, "replays").apply { mkdirs() }
        val library = ReplayLibrary(ReplayStorage(replayDir))
        val file = File(replayDir, "ok.mp4").apply { writeText("x") }

        assertTrue(library.delete(file))
        assertFalse(file.exists())
    }

    @Test
    fun `library items are sorted newest first`() {
        val a = newFile("a.mp4").apply { setLastModified(1_000) }
        val b = newFile("b.mp4").apply { setLastModified(2_000) }

        assertEquals(listOf(b, a), library.items())
    }

    @Test
    fun `deleteAll returns the number of deleted files`() {
        newFile("a.mp4")
        newFile("b.mp4")
        newFile("c.mp4")

        assertEquals(3, library.deleteAll())
        assertEquals(0, library.items().size)
    }
}
