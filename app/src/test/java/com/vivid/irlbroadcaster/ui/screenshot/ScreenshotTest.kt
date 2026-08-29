package com.vivid.irlbroadcaster.ui.screenshot

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.GraphicsMode

/**
 * Roborazzi Screenshot-Tests für Theme-Varianten.
 *
 * ⚠️ INAKTIV — Roborazzi-Konfiguration vorhanden, aber Tests können
 * aktuell nicht ausgeführt werden wegen Inkompatibilität:
 *   - Roborazzi 1.72.0 + Robolectric 4.14.1 + AGP 9.3.2 + JDK 25
 *   - ComposeView kann unter diesen Bedingungen kein WindowRecomposer finden
 *   - `captureRoboImage()` auf Views schlägt mit "not attached to a window" fehl
 *
 * Status: github.com/takahirom/roborazzi/issues/830 (AGP 9 + Gradle 9)
 *
 * Workaround: Visuelle Regression-Tests laufen über die bestehende
 * Screengrab-Lane (`capture_play_screenshots`) im Emulator (androidTest).
 *
 * Sobald Roborazzi AGP 9.x vollständig unterstützt, können diese Tests
 * reaktiviert werden. Siehe dann auch:
 *   ./gradlew :app:recordRoborazziStandardDebug
 *   ./gradlew :app:verifyRoborazziStandardDebug
 *   ./gradlew :app:compareRoborazziStandardDebug
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotTest {

    @Test
    fun placeholderTest() {
        // Platzhalter — echte Tests kommen sobald Roborazzi AGP 9.x
        // vollständig unterstützt.
        assert(true)
    }
}
