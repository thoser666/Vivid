package com.vivid.feature.widget

import com.vivid.core.data.AppSettings
import com.vivid.core.data.SettingsRepository
import com.vivid.core.data.SpeechToTextEngine
import com.vivid.core.data.SubtitleError
import com.vivid.core.data.SubtitleState
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * ViewModel-Tests für das Untertitel-Overlay: Settings-Toggle → Maschine →
 * Engine-Aufrufe; Engine-Callbacks → sichtbarer State. Die Engine ist ein Mock,
 * Fehlercodes sind die SpeechRecognizer-Literale.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class SubtitleWidgetViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = MutableStateFlow(AppSettings())
    private val repository: SettingsRepository = mockk {
        every { appSettingsFlow } returns settings
    }

    private lateinit var engineListener: SpeechToTextEngine.Listener
    private var engineAvailable = true
    private var startedCount = 0
    private var stoppedCount = 0

    private val engine: SpeechToTextEngine = mockk {
        every { isAvailable } answers { engineAvailable }
        every { setListener(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            engineListener = firstArg()
        }
        every { start() } answers { startedCount++ }
        every { stop() } answers { stoppedCount++ }
    }

    private val capturedListener = AtomicReference<SpeechToTextEngine.Listener>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        startedCount = 0
        stoppedCount = 0
        engineAvailable = true
        // setListener fängt den Listener in der AtomicReference ein.
        every { engine.setListener(any()) } answers {
            @Suppress("UNCHECKED_CAST")
            capturedListener.set(firstArg())
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): SubtitleWidgetViewModel {
        val vm = SubtitleWidgetViewModel(repository, engine)
        engineListener = capturedListener.get()!!
        return vm
    }

    @Test
    fun `enable in settings starts the engine`() = runTest(dispatcher.scheduler) {
        val vm = createViewModel()
        runCurrent()
        startedCount = 0

        settings.value = settings.value.copy(subtitlesEnabled = true)
        runCurrent()

        assertTrue(vm.state.value.enabled)
        assertEquals(1, startedCount)
        vm.onClearedForTest()
    }

    @Test
    fun `disable in settings resets the state and stops the engine`() = runTest(dispatcher.scheduler) {
        settings.value = settings.value.copy(subtitlesEnabled = true)
        val vm = createViewModel()
        runCurrent()
        assertTrue(vm.state.value.enabled)

        settings.value = settings.value.copy(subtitlesEnabled = false)
        runCurrent()

        assertFalse(vm.state.value.enabled)
        assertEquals(SubtitleState(), vm.state.value)
        assertTrue(stoppedCount >= 1)
        vm.onClearedForTest()
    }

    @Test
    fun `unavailable engine shows hint and never starts`() = runTest(dispatcher.scheduler) {
        engineAvailable = false
        val vm = createViewModel()
        settings.value = settings.value.copy(subtitlesEnabled = true)
        runCurrent()

        assertTrue(vm.state.value.enabled)
        assertFalse(vm.state.value.available)
        assertEquals(0, startedCount)
        vm.onClearedForTest()
    }

    @Test
    fun `partial and final update the visible lines`() = runTest(dispatcher.scheduler) {
        settings.value = settings.value.copy(subtitlesEnabled = true)
        val vm = createViewModel()
        runCurrent()

        engineListener.onListening()
        engineListener.onPartial("hallo ")
        runCurrent()
        assertEquals(listOf("hallo "), vm.state.value.displayLines)

        engineListener.onFinal("hallo welt")
        runCurrent()
        assertEquals(listOf("hallo welt"), vm.state.value.lines)
        assertEquals("", vm.state.value.partial)
        // Final startet die Erkennung sofort neu.
        assertTrue(startedCount >= 2)
        vm.onClearedForTest()
    }

    @Test
    fun `transient error schedules restart via delay`() = runTest(dispatcher.scheduler) {
        settings.value = settings.value.copy(subtitlesEnabled = true)
        val vm = createViewModel()
        runCurrent()

        engineListener.onError(8) // ERROR_RECOGNIZER_BUSY
        runCurrent()
        assertEquals(SubtitleError.BUSY, vm.state.value.error)

        // Nach Ablauf des Backoffs (2 s) startet die Engine neu.
        dispatcher.scheduler.advanceTimeBy(2_000)
        runCurrent()
        assertTrue(startedCount >= 2)
        vm.onClearedForTest()
    }

    @Test
    fun `permanent error stops the engine without restart`() = runTest(dispatcher.scheduler) {
        settings.value = settings.value.copy(subtitlesEnabled = true)
        val vm = createViewModel()
        runCurrent()
        startedCount = 0

        engineListener.onError(9) // ERROR_INSUFFICIENT_PERMISSIONS
        runCurrent()

        assertEquals(0, startedCount)
        assertTrue(stoppedCount >= 1)
        vm.onClearedForTest()
    }

    @Test
    fun `cleared viewmodel releases the engine listener`() = runTest(dispatcher.scheduler) {
        val vm = createViewModel()
        vm.onClearedForTest()
        verify { engine.setListener(null) }
    }
}
