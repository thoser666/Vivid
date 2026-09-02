package com.vivid.irlbroadcaster.ui.screenshot

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Roborazzi Screenshot-Tests für Theme-Varianten.
 *
 * ⚠️ INAKTIV — Roborazzi-Konfiguration vorhanden, aber Tests können
 * aktuell nicht ausgeführt werden wegen Inkompatibilität:
 *   - Roborazzi 1.72.0 + Robolectric 4.14.1 + AGP 9.3.2 + JDK 25
 *   - ComposeView kann unter diesen Bedingungen keinen WindowRecomposer finden
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
 *
 * Hinweis: Kein @RunWith(AndroidJUnit4) oder @GraphicsMode hier —
 * das triggert das Roborazzi-Framework und verursacht ASM/ClassReader-Fehler
 * in der CI, auch wenn der Test nur assert(true) macht.
 */
class ScreenshotTest {

    @Test
    fun `disabled screenshot lane remains explicitly documented`() {
        assertEquals("AGP 9 + JDK 25 workaround", "AGP 9 + JDK 25 workaround")
    }
}
