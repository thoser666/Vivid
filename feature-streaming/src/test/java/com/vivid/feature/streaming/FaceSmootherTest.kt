package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P2 — Hysterese und Zuordnung der Gesichts-Slots (Skizze §5).
 *
 * Getaktet über die Aufrufe selbst (Frames statt Uhr), damit die Tests
 * deterministisch sind. Die hier festgehaltenen Schwellen — 2 Frames zum
 * Erscheinen, 3 Frames Haltezeit, IoU 0.25, Glättung 0.5 — sind die
 * sichtbare Antwort auf „Flackern vs. Nachlauf“ und gehören in die Doku.
 */
class FaceSmootherTest {

    private fun e(centerX: Float, centerY: Float, radiusX: Float = 0.1f, radiusY: Float = 0.1f) =
        PrivacyEllipse(centerX, centerY, radiusX, radiusY)

    private fun assertEllipse(
        expected: PrivacyEllipse,
        actual: PrivacyEllipse,
        delta: Float = 1e-6f,
        label: String = "ellipse",
    ) {
        assertEquals(expected.centerX, actual.centerX, delta, "$label centerX")
        assertEquals(expected.centerY, actual.centerY, delta, "$label centerY")
        assertEquals(expected.radiusX, actual.radiusX, delta, "$label radiusX")
        assertEquals(expected.radiusY, actual.radiusY, delta, "$label radiusY")
    }

    @Test
    fun `a new face needs two frames before it is output`() {
        val smoother = FaceSmoother()
        val detection = e(0.25f, 0.25f)

        assertEquals(0, smoother.update(listOf(detection)).size)
        assertEquals(0, smoother.current().size, "ein einzelner Frame zählt nicht")

        val output = smoother.update(listOf(detection))
        assertEquals(1, output.size)
        assertEllipse(detection, output.single(), label = "entered")
    }

    @Test
    fun `the slot blends halfway toward its detection each frame`() {
        val smoother = FaceSmoother()
        val start = e(0.25f, 0.25f)
        val moved = e(0.3f, 0.3f)

        smoother.update(listOf(start))
        smoother.update(listOf(start))

        // Erster Folge-Frame: 0.25 -> 0.275 (Glättung 0.5 Richtung 0.30).
        assertEllipse(e(0.275f, 0.275f), smoother.update(listOf(moved)).single(), label = "first follow")
        // Zweiter Folge-Frame: 0.275 -> 0.2875.
        assertEllipse(e(0.2875f, 0.2875f), smoother.update(listOf(moved)).single(), label = "second follow")
    }

    @Test
    fun `a missing face is held for three frames and then released`() {
        val smoother = FaceSmoother()
        val detection = e(0.5f, 0.5f)

        smoother.update(listOf(detection))
        smoother.update(listOf(detection))
        assertEquals(1, smoother.current().size)

        // Verdeckung 1..3: der Slot bleibt (kein Flackern).
        repeat(3) {
            assertEquals(1, smoother.update(emptyList()).size, "Haltezeit nach ${it + 1} Leer-Frames")
        }
        // Der vierte Leer-Frame überschreitet die Haltezeit.
        assertTrue(smoother.update(emptyList()).isEmpty())
    }

    @Test
    fun `a new position opens its own slot instead of hijacking the first`() {
        val smoother = FaceSmoother()
        val first = e(0.2f, 0.2f)
        val farAway = e(0.9f, 0.9f)

        smoother.update(listOf(first))
        smoother.update(listOf(first))

        // Keine Überlappung → eigener Slot; der erste hält seine Position.
        val afterSwitch = smoother.update(listOf(farAway))
        assertEquals(1, afterSwitch.size)
        assertEllipse(first, afterSwitch.single(), label = "held")

        // Nach der Haltezeit bleibt nur die neue Position — unverfälscht,
        // weil der neue Slot bei der Erkennung entsteht.
        repeat(3) { smoother.update(listOf(farAway)) }
        val output = smoother.update(listOf(farAway))
        assertEquals(1, output.size)
        assertEllipse(farAway, output.single(), label = "survivor")
    }

    @Test
    fun `the slot cap keeps only the first maxFaces detections`() {
        val smoother = FaceSmoother()
        val detections = listOf(
            e(0.1f, 0.2f),
            e(0.3f, 0.2f),
            e(0.5f, 0.2f),
            e(0.7f, 0.2f),
            e(0.9f, 0.2f),
        )

        smoother.update(detections)
        val output = smoother.update(detections)

        assertEquals(FaceEllipseMapper.MAX_FACE_ELLIPSES, output.size)
        detections.take(FaceEllipseMapper.MAX_FACE_ELLIPSES).forEachIndexed { index, detection ->
            assertEllipse(detection, output[index], label = "slot $index")
        }
        assertTrue(output.none { it == detections.last() })
    }

    @Test
    fun `two simultaneous faces keep two slots`() {
        val smoother = FaceSmoother()
        val left = e(0.25f, 0.4f)
        val right = e(0.75f, 0.4f)

        smoother.update(listOf(left, right))
        val output = smoother.update(listOf(right, left))

        assertEquals(2, output.size)
        assertEllipse(left, output[0], label = "left")
        assertEllipse(right, output[1], label = "right")
    }

    @Test
    fun `reset forgets the slots and forces a fresh entry`() {
        val smoother = FaceSmoother()
        val detection = e(0.4f, 0.4f)

        smoother.update(listOf(detection))
        smoother.update(listOf(detection))
        assertEquals(1, smoother.current().size)

        smoother.update(emptyList())
        smoother.reset()
        assertTrue(smoother.current().isEmpty())
        assertEquals(0, smoother.update(listOf(detection)).size, "Wiedereintritt braucht erneut zwei Frames")
    }
}
