package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * P2 — Goldenwerte der sensoraktiven Abbildung (Skizze §5).
 *
 * Die hier festgehaltenen Zahlen sind die **Konventions-Entscheidung** aus
 * `FaceEllipseMapper` (Risk #2): spiegelungsfreier Sensorpuffer, Front =
 * `(sensor + display) % 360`, Back = `(sensor - display + 360) % 360`, dann
 * Rotation im y-down-Raum und Y-Flip in den GL-Textur-Raum. Weicht ein
 * Gerätetest davon ab, werden genau diese Erwartungen (und die Konventions-
 * KDoc im Mapper) gegenzedreht — nicht der Mapper „im Nebenbei“.
 */
class FaceEllipseMapperTest {

    private fun face(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        score: Int = 80,
    ) = DetectedFace(left, top, right, bottom, score)

    private fun frame(
        vararg faces: DetectedFace,
        width: Int = 4000,
        height: Int = 3000,
        sensor: Int = 90,
        display: Int = 0,
        front: Boolean = false,
    ) = FaceFrame(
        faces = faces.toList(),
        meta = FaceFrameMeta(width, height, sensor, display, front),
    )

    /** Mappt genau eine Box allein — die Referenz für „ist das dieselbe?“. */
    private fun single(face: DetectedFace, template: FaceFrame = frame()) =
        FaceEllipseMapper.map(template.copy(faces = listOf(face))).single()

    private fun assertEllipse(
        expected: PrivacyEllipse,
        actual: PrivacyEllipse,
        delta: Float = 1e-4f,
        label: String = "ellipse",
    ) {
        assertEquals(expected.centerX, actual.centerX, delta, "$label centerX")
        assertEquals(expected.centerY, actual.centerY, delta, "$label centerY")
        assertEquals(expected.radiusX, actual.radiusX, delta, "$label radiusX")
        assertEquals(expected.radiusY, actual.radiusY, delta, "$label radiusY")
    }

    // ------------------------------------------------------------------
    // Konvention: Display-Rotation relativ zur nativen Orientierung.
    // ------------------------------------------------------------------

    @Test
    fun `rotationDegreesFor normalises both directions`() {
        // Hinten: Rotation wird abgezogen.
        assertEquals(90, FaceEllipseMapper.rotationDegreesFor(90, 0, frontFacing = false))
        assertEquals(0, FaceEllipseMapper.rotationDegreesFor(90, 90, frontFacing = false))
        assertEquals(270, FaceEllipseMapper.rotationDegreesFor(90, 180, frontFacing = false))
        assertEquals(90, FaceEllipseMapper.rotationDegreesFor(0, 270, frontFacing = false))
        // Vorne: Rotation wird addiert.
        assertEquals(270, FaceEllipseMapper.rotationDegreesFor(270, 0, frontFacing = true))
        assertEquals(0, FaceEllipseMapper.rotationDegreesFor(270, 90, frontFacing = true))
        assertEquals(90, FaceEllipseMapper.rotationDegreesFor(270, 180, frontFacing = true))
        assertEquals(90, FaceEllipseMapper.rotationDegreesFor(0, 90, frontFacing = true))
    }

    // ------------------------------------------------------------------
    // Goldenwerte (4000×3000 aktiv, Box 1000/500 → 2000/1500, Score 80).
    // ------------------------------------------------------------------

    @Test
    fun `back portrait rotates the box into texture space`() {
        val mapped = FaceEllipseMapper.map(frame(face(1000, 500, 2000, 1500), sensor = 90, display = 0))

        assertEquals(1, mapped.size)
        // cx 0.375 / cy 0.333333 → 90° → (0.666667, 0.375) → Y-Flip → 0.625,
        // Radien 0.125 × 0.166667 werden bei 90° getauscht.
        assertEllipse(
            PrivacyEllipse(0.666667f, 0.625f, 0.166667f, 0.125f),
            mapped.single(),
            label = "back portrait",
        )
    }

    @Test
    fun `front portrait rotates the other way`() {
        val mapped = FaceEllipseMapper.map(
            frame(face(1000, 500, 2000, 1500), sensor = 270, display = 0, front = true),
        )

        assertEquals(1, mapped.size)
        // 270° → (0.333333, 0.625) → Y-Flip → 0.375.
        assertEllipse(
            PrivacyEllipse(0.333333f, 0.375f, 0.166667f, 0.125f),
            mapped.single(),
            label = "front portrait",
        )
    }

    @Test
    fun `display rotation cancels the sensor offset in landscape`() {
        val mapped = FaceEllipseMapper.map(frame(face(1000, 500, 2000, 1500), sensor = 90, display = 90))

        assertEquals(1, mapped.size)
        // Rotation 0 → nur der Y-Flip der Zonen-Konvention (toPrivacyEllipse).
        assertEllipse(
            PrivacyEllipse(0.375f, 0.666667f, 0.125f, 0.166667f),
            mapped.single(),
            label = "landscape",
        )
    }

    @Test
    fun `rotation 180 keeps the radii and flips both axes`() {
        // 180 = sensorOrientation 180 ohne Display-Drehung (Roll um 180°).
        val mapped = FaceEllipseMapper.map(frame(face(1000, 500, 2000, 1500), sensor = 180, display = 0))

        assertEllipse(
            PrivacyEllipse(0.625f, 0.333333f, 0.125f, 0.166667f),
            mapped.single(),
            label = "sensor 180",
        )
    }

    // ------------------------------------------------------------------
    // Filter, Cap, Clamp.
    // ------------------------------------------------------------------

    @Test
    fun `scores below the minimum are ignored`() {
        val mapped = FaceEllipseMapper.map(
            frame(face(1000, 500, 2000, 1500, score = 39), face(2500, 500, 3500, 1500, score = 40)),
        )

        assertEquals(1, mapped.size)
        // Genau der Mindestscore zählt noch.
        assertEllipse(
            single(face(2500, 500, 3500, 1500, score = 40)),
            mapped.single(),
            label = "score boundary",
        )
    }

    @Test
    fun `the strongest faces survive the cap in descending order`() {
        val weakest = face(100, 100, 500, 700, score = 50)
        val strongest = face(600, 100, 1000, 700, score = 90)
        val third = face(1100, 100, 1500, 700, score = 70)
        val fourth = face(1600, 100, 2000, 700, score = 60)
        val second = face(2100, 100, 2500, 700, score = 80)

        val mapped = FaceEllipseMapper.map(frame(weakest, strongest, third, fourth, second))

        assertEquals(FaceEllipseMapper.MAX_FACE_ELLIPSES, mapped.size)
        assertEllipse(single(strongest), mapped[0], label = "rank 1")
        assertEllipse(single(second), mapped[1], label = "rank 2")
        assertEllipse(single(third), mapped[2], label = "rank 3")
        assertEllipse(single(fourth), mapped[3], label = "rank 4")
        // Der schwächste gehört nicht zum Ergebnis (weder über den Cap noch
        // über die Reihenfolge).
        assertTrue(mapped.none { it == single(weakest) })
    }

    @Test
    fun `boxes outside the active array are clamped before normalising`() {
        val mapped = FaceEllipseMapper.map(frame(face(-1000, -500, 1000, 500), sensor = 90))

        assertEllipse(
            PrivacyEllipse(0.916667f, 0.875f, 0.083333f, 0.125f),
            mapped.single(),
            label = "clamped",
        )
    }

    @Test
    fun `degenerate boxes are dropped`() {
        assertTrue(FaceEllipseMapper.map(frame(face(500, 100, 500, 700))).isEmpty())
        // Vollständig außerhalb des aktiven Bereichs → nach dem Clamp 0 breit.
        assertTrue(FaceEllipseMapper.map(frame(face(5000, 100, 6000, 700))).isEmpty())
    }

    @Test
    fun `an unusable active array yields no ellipses`() {
        assertTrue(FaceEllipseMapper.map(frame(face(1000, 500, 2000, 1500), width = 0)).isEmpty())
        assertTrue(FaceEllipseMapper.map(frame(face(1000, 500, 2000, 1500), height = -1)).isEmpty())
        // Frame ohne jede Detektion bleibt leer.
        assertTrue(FaceEllipseMapper.map(frame()).isEmpty())
    }

    @Test
    fun `an explicit face budget is honoured`() {
        val faces = arrayOf(
            face(100, 100, 500, 700, score = 90),
            face(600, 100, 1000, 700, score = 80),
            face(1100, 100, 1500, 700, score = 70),
        )

        assertEquals(1, FaceEllipseMapper.map(frame(*faces), maxFaces = 1).size)
        assertTrue(FaceEllipseMapper.map(frame(*faces), maxFaces = 0).isEmpty())
    }
}
