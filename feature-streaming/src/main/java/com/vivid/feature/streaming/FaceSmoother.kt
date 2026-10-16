package com.vivid.feature.streaming

/**
 * P2 — stabilisiert Gesichts-Boxen über mehrere Frames (Skizze §5).
 *
 * Die Hardware-Erkennung meldet pro Frame eine rohe Menge ohne stabile IDs
 * über die Framegrenzen hinweg; der Smoother ordnet die Detektionen über
 * **IoU-Matching** (Bounding-Box-Näherung der Ellipsen) bestehenden Slotos zu
 * und glättet die Position, damit die Blur-Ellipsen nicht flackern.
 *
 * Bewusst reine Funktion ohne Android-/RootEncoder-Bezug — die Zeit (Frames)
 * kommt über die Aufrufe selbst, nicht über eine Uhr, was die Tests
 * deterministisch macht. Hysterese:
 *
 * - ein neues Gesicht muss [enterFrames] Frames lang auftreten, bevor es
 *   ausgegeben wird (einzelne Fehlmeldung erzeugt keinen Blur),
 * - ein verschwundenes Gesicht bleibt bis zu [maxMissedFrames] Frames auf
 *   seiner letzten Position (kurze Verdeckungen flackern nicht weg),
 * - höchstens [maxFaces] Slots — 4 Zonen + 4 Gesichter füllen die
 *   8 Uniform-Slots des Privacy-Shaders.
 *
 * Reihenfolge der Ausgabe ist stabil (Anlage-Reihenfolge der Slots), damit
 * die Uniform-Slots nicht pro Frame springen.
 */
internal class FaceSmoother(
    private val maxFaces: Int = FaceEllipseMapper.MAX_FACE_ELLIPSES,
    private val enterFrames: Int = ENTER_FRAMES,
    private val maxMissedFrames: Int = MAX_MISSED_FRAMES,
    private val matchThreshold: Float = MATCH_IOU_THRESHOLD,
    private val smoothing: Float = SMOOTHING,
) {

    private class Slot(
        var ellipse: PrivacyEllipse,
        var seen: Int = 1,
        var missed: Int = 0,
    ) {
        var claimed: Boolean = false
    }

    private val slots = mutableListOf<Slot>()

    /**
     * Verarbeitet einen Frame und liefert die aktuell gültigen Ellipsen
     * (Anlage-Reihenfolge, nur Slots mit `seen >= enterFrames`).
     */
    fun update(detections: List<PrivacyEllipse>): List<PrivacyEllipse> {
        slots.forEach { it.claimed = false }
        detections.forEach { detection ->
            val slot = slots
                .filterNot { it.claimed }
                .maxByOrNull { iou(it.ellipse, detection) }
                ?.takeIf { iou(it.ellipse, detection) >= matchThreshold }
            when {
                slot != null -> {
                    slot.claimed = true
                    slot.ellipse = blend(slot.ellipse, detection, smoothing)
                    slot.seen += 1
                    slot.missed = 0
                }
                slots.size < maxFaces -> slots.add(Slot(detection))
                // Kein freier Slot: die schwächste neue Box wird verworfen.
            }
        }
        slots.forEach { slot ->
            if (!slot.claimed) slot.missed += 1
        }
        slots.removeAll { it.missed > maxMissedFrames }
        return current()
    }

    /** Letzte bekannte Position ohne Frame-Update (z. B. nach Toggle-Wechseln). */
    fun current(): List<PrivacyEllipse> =
        slots.filter { it.seen >= enterFrames }.map { it.ellipse }

    /** Vergisst alle Slots (z. B. Quellwechsel, Kamera-Stopp). */
    fun reset() {
        slots.clear()
    }

    private companion object {
        /** Frames, die ein Gesicht durchgehend auftreten muss, bevor es zählt. */
        const val ENTER_FRAMES = 2

        /** Frames, die ein vermisstes Gesicht auf seiner Position gehalten wird. */
        const val MAX_MISSED_FRAMES = 3

        /** Mindest-IoU (auf Ellipsen-Bounding-Boxen), um zuzuordnen. */
        const val MATCH_IOU_THRESHOLD = 0.25f

        /** Anteil der Neubewegung pro Frame (0 = stehenbleiben, 1 = springen). */
        const val SMOOTHING = 0.5f

        /**
         * IoU zweier Ellipsen, genähert über die umschließenden Rechtecke
         * (Mittelpunkt ± Radius) — deterministisch und ohne Kreisgeometrie.
         */
        fun iou(a: PrivacyEllipse, b: PrivacyEllipse): Float {
            val aWidth = a.radiusX * 2f
            val aHeight = a.radiusY * 2f
            val bWidth = b.radiusX * 2f
            val bHeight = b.radiusY * 2f
            val interWidth = (
                minOf(a.centerX + a.radiusX, b.centerX + b.radiusX) -
                    maxOf(a.centerX - a.radiusX, b.centerX - b.radiusX)
                ).coerceAtLeast(0f)
            val interHeight = (
                minOf(a.centerY + a.radiusY, b.centerY + b.radiusY) -
                    maxOf(a.centerY - a.radiusY, b.centerY - b.radiusY)
                ).coerceAtLeast(0f)
            val inter = interWidth * interHeight
            val union = aWidth * aHeight + bWidth * bHeight - inter
            return if (union <= 0f) 0f else inter / union
        }

        fun blend(from: PrivacyEllipse, to: PrivacyEllipse, factor: Float): PrivacyEllipse = PrivacyEllipse(
            centerX = from.centerX + (to.centerX - from.centerX) * factor,
            centerY = from.centerY + (to.centerY - from.centerY) * factor,
            radiusX = from.radiusX + (to.radiusX - from.radiusX) * factor,
            radiusY = from.radiusY + (to.radiusY - from.radiusY) * factor,
        )
    }
}
