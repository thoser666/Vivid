package com.vivid.feature.streaming.scene

import com.vivid.core.data.SceneRepository
import com.vivid.core.data.StreamScene
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoSceneSwitcherTest {

    private fun scene(id: String) = StreamScene(id = id, name = "Szene $id")

    private class Harness(
        val switcher: AutoSceneSwitcher,
        val controller: SceneController,
    )

    private fun createSwitcher(
        scope: CoroutineScope,
        scenes: MutableStateFlow<List<StreamScene>> = MutableStateFlow(
            listOf(scene("a"), scene("b"), scene("c")),
        ),
        activeSceneId: MutableStateFlow<String?> = MutableStateFlow("a"),
    ): Harness {
        val sceneRepository = mockk<SceneRepository> {
            every { scenesFlow } returns scenes
            every { activeSceneIdFlow } returns activeSceneId
        }
        val controller = mockk<SceneController> {
            coEvery { applyScene(any()) } just Runs
        }
        return Harness(
            switcher = AutoSceneSwitcher(scope, sceneRepository, controller),
            controller = controller,
        )
    }

    // --- Pure Index-Logik (ohne Zeit/Loop) ---

    @Test
    fun `nextSceneIndex returns the scene after the active one`() {
        val scenes = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals(1, AutoSceneSwitcher.nextSceneIndex(scenes, "a"))
        assertEquals(2, AutoSceneSwitcher.nextSceneIndex(scenes, "b"))
    }

    @Test
    fun `nextSceneIndex wraps around from the last scene`() {
        val scenes = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals(0, AutoSceneSwitcher.nextSceneIndex(scenes, "c"))
    }

    @Test
    fun `nextSceneIndex starts at the first scene when none is active`() {
        val scenes = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals(0, AutoSceneSwitcher.nextSceneIndex(scenes, null))
    }

    @Test
    fun `nextSceneIndex starts at the first scene when the active one is gone`() {
        val scenes = listOf(scene("a"), scene("b"), scene("c"))
        assertEquals(0, AutoSceneSwitcher.nextSceneIndex(scenes, "unbekannt"))
    }

    @Test
    fun `nextSceneIndex with fewer than two scenes is a no-op`() {
        assertEquals(0, AutoSceneSwitcher.nextSceneIndex(listOf(scene("a")), "a"))
        assertEquals(0, AutoSceneSwitcher.nextSceneIndex(emptyList(), null))
    }

    // --- Loop mit virtueller Zeit ---

    @Test
    fun `enabling starts the loop and applies the next scene after each interval`() = runTest {
        val harness = createSwitcher(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        harness.switcher.setIntervalSeconds(10)
        harness.switcher.setEnabled(true)

        // Intervall 1: nach Szene "a" kommt "b".
        // advanceTimeBy läuft Tasks bei exakt der Zielzeit NICHT aus (strikt
        // kleiner), daher runCurrent() für den Tick bei genau 10_000 ms.
        advanceTimeBy(10_000)
        runCurrent()
        assertTrue(harness.switcher.enabled.value)
        coVerify(exactly = 1) { harness.controller.applyScene(match { it.id == "b" }) }

        // Intervall 2: der Loop liest die aktive ID erneut (weiterhin "a",
        // da der Mock-Controller nichts schreibt) — zirkuliert also wieder zu "b".
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 2) { harness.controller.applyScene(match { it.id == "b" }) }

        // Sauber beenden, damit kein verzögerter Task im Test-Scheduler zurückbleibt.
        harness.switcher.setEnabled(false)
    }

    @Test
    fun `the loop follows the active scene id and switches to the next one`() = runTest {
        val activeSceneId = MutableStateFlow<String?>("a")
        val harness = createSwitcher(
            scope = CoroutineScope(UnconfinedTestDispatcher(testScheduler)),
            activeSceneId = activeSceneId,
        )
        harness.switcher.setIntervalSeconds(10)
        harness.switcher.setEnabled(true)

        // Der echte Ablauf: der Controller markiert die neue Szene als aktiv,
        // die Schleife liest die aktive ID beim nächsten Intervall neu.
        harness.controller.applyScene(
            com.vivid.core.data.StreamScene(id = "b", name = "Szene b"),
        )
        activeSceneId.value = "b"
        advanceTimeBy(10_000)
        runCurrent()
        coVerify(exactly = 1) { harness.controller.applyScene(match { it.id == "c" }) }

        harness.switcher.setEnabled(false)
    }

    @Test
    fun `disabling cancels the loop and stops further switches`() = runTest {
        val harness = createSwitcher(CoroutineScope(UnconfinedTestDispatcher(testScheduler)))
        harness.switcher.setIntervalSeconds(10)
        harness.switcher.setEnabled(true)

        advanceTimeBy(10_000)
        runCurrent()
        harness.switcher.setEnabled(false)
        assertFalse(harness.switcher.enabled.value)

        advanceTimeBy(60_000)
        runCurrent()
        coVerify(exactly = 1) { harness.controller.applyScene(any()) }
    }

    @Test
    fun `setIntervalSeconds clamps to the minimum`() {
        val harness = createSwitcher(CoroutineScope(UnconfinedTestDispatcher()))
        harness.switcher.setIntervalSeconds(1)
        assertEquals(
            AutoSceneSwitcher.MIN_INTERVAL_SECONDS,
            harness.switcher.intervalSeconds.value,
        )
    }
}