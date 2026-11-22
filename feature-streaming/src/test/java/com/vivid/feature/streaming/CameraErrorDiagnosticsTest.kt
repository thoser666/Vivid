package com.vivid.feature.streaming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Kontext-Zeile der Kamera-Fehlerdiagnose (`docs/sentry-issues.md` §5.12).
 *
 * Die Zeile landet als Sentry-Breadcrumb am Fehler-Event, nicht als eigenes
 * Event: Sentry gruppiert Log-Events nach der Nachricht
 * (`sentry-android-timber` 8.58, `minEventLevel = ERROR`), variable Werte
 * würden die Gruppenzahl treiben. Was hier geprüft wird, ist die Lesbarkeit und
 * die vollständige Aufnahme der drei Fragen — Pfad, betroffene Kamera,
 * gleichzeitige Kameras.
 */
class CameraErrorDiagnosticsTest {

    @Test
    fun `describes the path, the camera and the concurrent groups`() {
        val text = describeCameraError(
            path = CameraErrorPath.STREAM,
            cameraId = "5",
            isStreaming = true,
            isRecording = false,
            environment = CameraEnvironment(
                availableIds = listOf("0", "1", "2", "3", "4", "5"),
                concurrentGroups = listOf(listOf("0", "1"), listOf("0", "2")),
            ),
        )

        assertEquals(
            "path=stream cameraId=5 available=[0,1,2,3,4,5] " +
                "concurrent=[0,1;0,2] streaming=true recording=false",
            text,
        )
    }

    @Test
    fun `an empty environment stays readable instead of losing the fields`() {
        val text = describeCameraError(
            path = CameraErrorPath.IDLE_PREVIEW,
            cameraId = "0",
            isStreaming = false,
            isRecording = false,
            environment = CameraEnvironment(),
        )

        assertEquals(
            "path=idlePreview cameraId=0 available=[] concurrent=[] streaming=false recording=false",
            text,
        )
    }

    @Test
    fun `a blank camera id is reported as the default facing`() {
        val text = describeCameraError(
            path = CameraErrorPath.STREAM,
            cameraId = "",
            isStreaming = false,
            isRecording = false,
            environment = CameraEnvironment(availableIds = listOf("0")),
        )

        assertTrue(text.contains("cameraId=default"), "leer bedeutet Rückkamera, nicht 'unbekannt'")
        assertFalse(text.contains("cameraId= "))
    }

    @Test
    fun `each path keeps its stable label`() {
        assertEquals("stream", CameraErrorPath.STREAM.label)
        assertEquals("idlePreview", CameraErrorPath.IDLE_PREVIEW.label)
    }

    @Test
    fun `the requested parameters are appended when the path knows them`() {
        val text = describeCameraError(
            path = CameraErrorPath.IDLE_PREVIEW,
            cameraId = "0",
            isStreaming = false,
            isRecording = false,
            environment = CameraEnvironment(),
            request = "1280x720@30fps",
        )

        assertEquals(
            "path=idlePreview cameraId=0 available=[] concurrent=[] streaming=false recording=false" +
                " request=1280x720@30fps",
            text,
        )
    }
}
