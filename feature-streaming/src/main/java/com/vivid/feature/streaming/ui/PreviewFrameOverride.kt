package com.vivid.feature.streaming.ui

import android.graphics.Bitmap

/**
 * Ersetzt die Kamera-Vorschau durch ein Standbild — ausschließlich für
 * Screenshot- und UI-Tests (fastlane-Lane `capture_play_screenshots`).
 *
 * Hintergrund: Der Emulator liefert für die Kamera ein Testmuster aus farbigen
 * Blöcken. Der Play-Screenshot zeigte deshalb statt einer Szene ein Muster, das
 * niemand als Produktbild veröffentlichen will. Das Instrumentation-Test-APK
 * läuft im selben Prozess wie die App, ein Prozess-weiter Haken genügt also:
 * der Test setzt [frame] vor dem Start der Activity, [StreamingSourcePreview]
 * rendert dann dieses Bitmap statt der GL-/Kamera-Vorschau.
 *
 * Ohne gesetzten Wert (`null`, der Produktionszustand) ändert sich nichts — die
 * echte Vorschau läuft unverändert über [android.view.SurfaceView] und
 * [com.vivid.feature.streaming.StreamingEngine].
 */
object PreviewFrameOverride {

    /** Standbild statt Kamera-Vorschau; `null` = echte Vorschau. */
    @Volatile
    var frame: Bitmap? = null
}
