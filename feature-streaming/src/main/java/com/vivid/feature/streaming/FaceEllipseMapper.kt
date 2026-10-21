package com.vivid.feature.streaming

/**
 * Ein von der Hardware-Erkennung gemeldetes Gesicht in **sensoraktiven
 * Koordinaten** (RootEncoder `FaceDetectorCallback`, `scaleSensor` =
 * `SENSOR_INFO_ACTIVE_ARRAY_SIZE`).
 *
 * Bewusst Android-frei, damit die gesamte P2-Kette ohne Robolectric auf der
 * JVM testbar ist — die Engine übersetzt `Face[]`/`Rect` hierher.
 */
internal data class DetectedFace(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
    /** 0..100 (Camera2 `Face.getScore()`). */
    val score: Int,
)

/**
 * Kontext des Frames, in dem die Boxen gemeldet wurden — die Umrechnung in
 * Textur-Koordinaten hängt an genau diesen vier Werten.
 */
internal data class FaceFrameMeta(
    val activeArrayWidth: Int,
    val activeArrayHeight: Int,
    /** `CameraCharacteristics.SENSOR_ORIENTATION` (0/90/180/270). */
    val sensorOrientationDegrees: Int,
    /** Drehung der Ausgabe relativ zur nativen Geräteorientierung (0/90/180/270). */
    val displayRotationDegrees: Int,
    val frontFacing: Boolean,
)

/** Ein Erkennungs-Frame aus der Kamera (Lebenszyklus: `FaceDetectionProducer`). */
internal data class FaceFrame(
    val faces: List<DetectedFace>,
    val meta: FaceFrameMeta,
)

/**
 * P2 — reine Abbildung sensoraktiver Gesichtsboxen auf [PrivacyEllipse]en
 * (Skizze §5, docs/architecture/privacy-anonymization.md).
 *
 * ## Konvention (Risk #2 der Skizze — **Gerätetest offen**)
 *
 * Der Sensorpuffer ist **gespiegelungsfrei** in die Pipeline eingebunden: es
 * wird nirgends ein Flip gesetzt (weder in Vivid noch in RootEncoder 2.7.5 —
 * verifiziert über `setIsStreamHorizontalFlip`/`setVerticalFlip` in der
 * Library, dort nirgends aufgerufen). Damit trägt die Box exakt die Drehung,
 * die den Sensorpuffer zum angezeigten Bild macht:
 *
 * - vorne:  `(sensorOrientation + displayRotation) % 360`
 * - hinten: `(sensorOrientation - displayRotation + 360) % 360`
 *
 * Das klassische Formelpaar mit `360 - x` für die Vorderkamera kompensiert
 * eine **gespiegelte** Vorschau (Camera1-/CameraX-Sicht) — hier bewusst nicht
 * angewandt. Rotation im normalisierten Raum mit Ursprung oben links
 * (y-down), danach der Y-Flip in den GL-Textur-Raum wie in
 * [toPrivacyEllipse] für die Zonen.
 *
 * Der Wert ist an einem Gerät gegen die sichtbare Vorschau zu prüfen; eine
 * Umkehrung betrifft ausschließlich [rotationDegreesFor] und die
 * Front-/Display-Zweige dieses Dokuments.
 */
internal object FaceEllipseMapper {

    /** Mindest-Score (0..100), darunter wird ein Gesicht ignoriert. */
    const val MIN_SCORE = 40

    /**
     * Reservierte Slots für Gesichter: 4 Zonen + 4 Gesichter = die 8
     * Uniform-Slots des Privacy-Shaders ([PrivacyBlurFilterRender.MAX_ELLIPSES]).
     */
    const val MAX_FACE_ELLIPSES = 4

    /** Sensor- → Ausgabedrehung (siehe Klassen-Doku). */
    fun rotationDegreesFor(
        sensorOrientationDegrees: Int,
        displayRotationDegrees: Int,
        frontFacing: Boolean,
    ): Int {
        val rotation = if (frontFacing) {
            sensorOrientationDegrees + displayRotationDegrees
        } else {
            sensorOrientationDegrees - displayRotationDegrees
        }
        return ((rotation % 360) + 360) % 360
    }

    /**
     * Mappt einen Frame auf die stärksten [maxFaces] Ellipsen (Textur-
     * Koordinaten, Y-Flip). Boxen außerhalb des aktiven Bereichs werden auf
     * diesen begrenzt, bevor normalisiert wird.
     */
    fun map(
        frame: FaceFrame,
        maxFaces: Int = MAX_FACE_ELLIPSES,
        minScore: Int = MIN_SCORE,
    ): List<PrivacyEllipse> {
        val meta = frame.meta
        if (meta.activeArrayWidth <= 0 || meta.activeArrayHeight <= 0 || maxFaces <= 0) return emptyList()
        val rotation = rotationDegreesFor(
            sensorOrientationDegrees = meta.sensorOrientationDegrees,
            displayRotationDegrees = meta.displayRotationDegrees,
            frontFacing = meta.frontFacing,
        )
        val activeWidth = meta.activeArrayWidth.toFloat()
        val activeHeight = meta.activeArrayHeight.toFloat()

        return frame.faces.asSequence()
            .filter { it.score >= minScore }
            // Stärkste Box zuerst: der Cap kappt die schwächsten.
            .sortedByDescending { it.score }
            .mapNotNull { face ->
                val left = face.left.toFloat().coerceIn(0f, activeWidth)
                val right = face.right.toFloat().coerceIn(0f, activeWidth)
                val top = face.top.toFloat().coerceIn(0f, activeHeight)
                val bottom = face.bottom.toFloat().coerceIn(0f, activeHeight)
                val radiusX = (right - left) / 2f / activeWidth
                val radiusY = (bottom - top) / 2f / activeHeight
                if (radiusX <= 0f || radiusY <= 0f) return@mapNotNull null

                val (centerX, centerY, swappedRadii) = rotate(
                    centerX = (left + right) / 2f / activeWidth,
                    centerY = (top + bottom) / 2f / activeHeight,
                    radiusX = radiusX,
                    radiusY = radiusY,
                    rotationDegrees = rotation,
                )
                // Rotation eines Punktes der Einheitsquadrate um (0.5, 0.5)
                // bleibt darin; der Clamp fängt nur Rundungsfehler ab.
                PrivacyEllipse(
                    centerX = centerX.coerceIn(0f, 1f),
                    // Y-Flip: UI-/Sensorraum (y down) → GL-Textur (y up).
                    centerY = (1f - centerY).coerceIn(0f, 1f),
                    radiusX = swappedRadii.first.coerceIn(0f, 1f),
                    radiusY = swappedRadii.second.coerceIn(0f, 1f),
                )
            }
            .take(maxFaces)
            .toList()
    }

    /** Rotation im normalisierten Raum (y-down, Uhrzeigersinn um (0.5,0.5)). */
    private fun rotate(
        centerX: Float,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
        rotationDegrees: Int,
    ): Triple<Float, Float, Pair<Float, Float>> = when (rotationDegrees) {
        90 -> Triple(1f - centerY, centerX, radiusY to radiusX)
        180 -> Triple(1f - centerX, 1f - centerY, radiusX to radiusY)
        270 -> Triple(centerY, 1f - centerX, radiusY to radiusX)
        else -> Triple(centerX, centerY, radiusX to radiusY)
    }
}
