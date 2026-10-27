package com.vivid.feature.streaming

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Regressionstest Session-Closed-Race im Kamerapfad (#324 / VIVID-3W).
 *
 * Der Fatal entsteht, wenn ein Control-Dispatch (UI-Thread oder
 * `onCameraOpened`-Framework-Thread) auf die gerade geschlossene
 * Capture-Session trifft: `CameraCaptureSessionImpl.checkNotClosed` wirft
 * `IllegalStateException("Session has been closed; further changes are illegal.")`
 * uncaught. Der Guard an der Fassade verhindert beides: dasdispatchen während
 * des Schließens und das Weiterreichen dieser IllegalStateException.
 *
 * Mutationsnachweis (Hausregel): Ohne Guard-Injection bzw. ohne Catch bzw.
 * ohne Sperre fällt jeweils genau ein Test hier rot aus — die Testfälle sind
 * so geschnitten, dass sie einzeln gegen die drei Baustellen stehen.
 */
class CameraSessionRaceTest {

    /** Exakte Botschaft von `CameraCaptureSessionImpl.checkNotClosed` (#324). */
    private val closedSessionMessage = "Session has been closed; further changes are illegal."

    @Test
    fun `closed session race is suppressed at the dispatch seam`() {
        val guard = CameraSessionGuard()
        val camera = mockk<CameraControls>()
        // Genau der Aufruf, der im Sentry-Stack uncaught durchschlägt:
        // enableStabilization läuft in der Policy ohne runCatching auf die Session.
        every { camera.enableStabilization() } throws IllegalStateException(closedSessionMessage)
        val controls = ActiveCameraControls(guard) { camera }

        assertFalse(controls.enableStabilization())
        verify(exactly = 1) { camera.enableStabilization() }
    }

    @Test
    fun `dispatch does not touch the camera while it is closing`() {
        val guard = CameraSessionGuard()
        val camera = mockk<CameraControls>(relaxed = true)
        val controls = ActiveCameraControls(guard) { camera }

        guard.beginClose()
        assertFalse(controls.enableTorch())
        verify(exactly = 0) { camera.enableTorch() }

        every { camera.enableTorch() } returns true
        guard.endClose()
        assertTrue(controls.enableTorch())
        verify(exactly = 1) { camera.enableTorch() }
    }

    @Test
    fun `closing waits for an in-flight dispatch`() {
        val guard = CameraSessionGuard()
        val camera = mockk<CameraControls>()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        every { camera.enableTorch() } answers {
            entered.countDown()
            release.await(5, TimeUnit.SECONDS)
            true
        }
        val controls = ActiveCameraControls(guard) { camera }

        val dispatchThread = Thread({ controls.enableTorch() }, "test-dispatch").apply { start() }
        assertTrue(entered.await(5, TimeUnit.SECONDS), "Dispatch muss die Kamera erreichen")

        val closeFinished = CountDownLatch(1)
        val closeThread = Thread({
            guard.beginClose()
            closeFinished.countDown()
        }, "test-close").apply { start() }
        try {
            // Ohne Sperre würde beginClose die Flagge sofort setzen — der Zeitraum
            // ist der Beweis: solange der Dispatch in der Kamera hängt, bleibt er draußen.
            val completedEarly = closeFinished.await(500, TimeUnit.MILLISECONDS)
            assertFalse(completedEarly, "beginClose darf einen laufenden Dispatch nicht überholen")
        } finally {
            release.countDown()
        }

        dispatchThread.join(5_000)
        closeThread.join(5_000)
        assertTrue(closeFinished.await(5, TimeUnit.SECONDS), "beginClose muss nach dem Dispatch-Ende durchlaufen")
        verify(exactly = 1) { camera.enableTorch() }
    }

    @Test
    fun `unrelated illegal state still fails loudly`() {
        val guard = CameraSessionGuard()
        val camera = mockk<CameraControls>()
        every { camera.enableStabilization() } throws IllegalStateException("Camera not started yet")
        val controls = ActiveCameraControls(guard) { camera }

        val thrown = assertThrows(IllegalStateException::class.java) {
            controls.enableStabilization()
        }
        assertTrue(thrown.message?.contains("Camera not started") == true)
    }
}
