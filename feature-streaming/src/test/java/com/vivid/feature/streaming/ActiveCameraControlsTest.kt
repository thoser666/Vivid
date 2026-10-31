package com.vivid.feature.streaming

import android.view.MotionEvent
import android.view.View
import com.vivid.core.data.StabilizationMode
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Regressionstest: Szenen-Session ohne eigene Kamera (#330, Merge-Auflösung).
 *
 * Der Szenen-Compositor rendert Szenen, in denen keine Kamera-Quelle sichtbar
 * ist — `sceneSession != null`, aber `activeSceneCamera() == null`. Die Fassade
 * [ActiveCameraControls] muss dann dieselben neutralen Werte liefern wie im
 * Schließfenster (#324) und darf insbesondere nicht auf die Encoder-Kamera
 * ausweichen, die in dieser Szene gar nicht im Bild ist.
 *
 * Vor der Merge-Auflösung war der Provider nicht-nullbar: der Fall ließ sich
 * weder ausdrücken noch testen, und die naheliegende Alternative wäre ein `!!`
 * im Provider gewesen — NPE beim ersten UI-Gestenzug auf eine kamerafreie
 * Szene. Die Fälle unten stehen deshalb gegen zwei Baustellen: das fehlende
 * `null`-Verständnis der Fassade und ein Zurückfallen auf einen fremden
 * Controller.
 *
 * Mutationsnachweis (Hausregel): `?: true` bei [ActiveCameraControls.isAutoExposureEnabled]
 * oder [ActiveCameraControls.isAutoWhiteBalanceEnabled] auf `false` gedreht,
 * `CAMERA_ID_UNKNOWN` ersetzt oder ein `?:`-Zweig gestrichen ⇒ der jeweilige
 * Fall wird rot, [CameraSessionRaceTest] bleibt grün (Negativkontrolle).
 */
class ActiveCameraControlsTest {

    private val guard = CameraSessionGuard()

    @Test
    fun `camera free scene keeps saved stabilization controls neutral`() {
        val controls = ActiveCameraControls(guard) { null }
        assertEquals(setOf(StabilizationMode.OFF), controls.supportedStabilizationModes())
        assertFalse(controls.setStabilizationMode(StabilizationMode.CINEMATIC))
    }

    @Test
    fun `scene without a camera answers the documented neutral values`() {
        var resolves = 0
        val noCamera: () -> CameraControls? = { resolves++; null }
        val controls = ActiveCameraControls(guard, noCamera)
        val view: View = mockk()
        val event: MotionEvent = mockk()

        // Lesen: neutral, aber typrichtig — inklusive der Auto-Modi, die „aktiv"
        // melden, weil die Kamera in dieser Szene fehlt und nicht abgeschaltet ist
        // (Camera2PreviewControls defaultet genauso).
        assertEquals(1f, controls.getZoom())
        assertNull(controls.getZoomRange())
        assertEquals("unknown", controls.getCurrentCameraId())
        assertTrue(controls.getAvailableCameraIds().isEmpty())
        assertEquals(0f, controls.getFocusDistance())
        assertEquals(0, controls.getExposure())
        assertNull(controls.getExposureRange())
        assertTrue(controls.getWhiteBalanceModesAvailable().isEmpty())
        assertTrue(controls.isAutoExposureEnabled())
        assertTrue(controls.isAutoWhiteBalanceEnabled())
        assertFalse(controls.isTorchEnabled())
        assertFalse(controls.isStabilizationEnabled())

        // Schreiben: no-op ohne Ausnahme, Erfolg wird nicht behauptet.
        controls.setZoom(2f)
        controls.tapToFocus(view, event)
        controls.setFocusDistance(0.5f)
        assertFalse(controls.enableTorch())
        assertFalse(controls.disableTorch())
        assertFalse(controls.enableStabilization())
        assertFalse(controls.disableStabilization())
        assertFalse(controls.selectCamera("0"))
        assertFalse(controls.setExposure(1))
        assertFalse(controls.enableAutoExposure())
        assertFalse(controls.disableAutoExposure())
        assertFalse(controls.enableAutoWhiteBalance())
        assertFalse(controls.disableAutoWhiteBalance())

        // Fähigkeiten: nichts wird zugesagt, was nicht da ist.
        assertFalse(controls.hasTorch())
        assertFalse(controls.hasOpticalStabilization())
        assertFalse(controls.hasManualFocus())
        assertFalse(controls.hasExposureControl())
        assertFalse(controls.hasWhiteBalanceControl())

        // 12 Lesezugriffe + 13 Mutationen + 5 Fähigkeiten: jede Anfrage löst den
        // Provider genau einmal auf — kein Zwischenspeichern, keine Doppelauflösung.
        assertEquals(30, resolves)
    }

    @Test
    fun `camera-free scene picks the camera up as soon as the scene has one`() {
        var camera: CameraControls? = null
        val controls = ActiveCameraControls(guard) { camera }
        val sceneCamera = mockk<CameraControls>(relaxed = true)
        every { sceneCamera.getZoom() } returns 2.5f

        // Solange die Szene keine Kamera hat, bleibt es neutral.
        assertEquals(1f, controls.getZoom())

        camera = sceneCamera
        assertEquals(2.5f, controls.getZoom())
        verify(exactly = 1) { sceneCamera.getZoom() }
    }

    @Test
    fun `missing camera and close window do not latch the facade`() {
        var camera: CameraControls? = null
        val controls = ActiveCameraControls(guard) { camera }
        val sceneCamera = mockk<CameraControls>(relaxed = true)
        every { sceneCamera.enableTorch() } returns true

        guard.beginClose()
        assertFalse(controls.enableTorch())
        guard.endClose()

        // Schließfenster zu, Kamera fehlt weiterhin: weiterhin neutral.
        assertFalse(controls.enableTorch())

        camera = sceneCamera
        assertTrue(controls.enableTorch())
        verify(exactly = 1) { sceneCamera.enableTorch() }
    }
}
