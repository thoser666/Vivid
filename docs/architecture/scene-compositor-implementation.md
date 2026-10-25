# Scene-Compositor: Umsetzungsstand zu #306

Die vollständige Umsetzung des [Vorschlags](https://github.com/thoser666/Vivid/issues/306)
ist das Arbeitsziel. Der [RFC](scene-compositor.md) dokumentiert die Architektur;
die dort vorgeschlagene Zurückstellung ist keine Behauptung, die vollständige
Implementierung sei erledigt. Dieser Stand unterscheidet implementierte Bausteine
von noch fehlender Integration und Geräte-Evidenz.

## Anforderungen und Nachweise

| Anforderung | Aktueller Baustein | Noch erforderlich |
|---|---|---|
| Persistente Szenen mit mehreren Layern | `SceneComposition`, optionales `StreamScene.composition`, atomare SceneRepository-Writes, Migrationsbackup | Runtime-/Editor-Anbindung |
| Stabile SourceIds, wiederverwendbare Producer | Source-/Layer-Modell und referenzbasierter `SceneProducerManager`, Generationen gegen verspätete Disconnects | An echte Android-Capture-Sessions und Render-Thread anbinden |
| Gleichzeitige Kameras, UVC, Screen, Bilder und Widgets | Typisierte SourceSpecs | Encoderfreie Adapter, Capability-Matrix, USB-Backend und Berechtigungen |
| GLES/EGL-Offscreen-Ausgabe zum Hardware-Encoder | RootEncoder-2.7.5-Quellcode lokal geprüft | Kompositor-Filter, Textur-/Surface-Lifecycle und Geräte-Smoke |
| Ein Ergebnis für Streaming und Recording | Bestehender RootEncoder-Fanout als Integrationsziel | Gemeinsamen Output anschließen und Frame-Parität verifizieren |
| Keine zusätzlichen CPU-Framekopien/Encoder | Producer-Vertrag besitzt keinen Encoder | Capture-/Render-Implementierung und Profiling |
| Reihenfolge, Position, Größe, Rotation, Crop, Sichtbarkeit | Validiertes Modell, `SceneGeometry` für GPU-Vertices und Hit-Tests | Shader-/Editor-Verkabelung |
| PiP, Presets, Duplizieren | Fullscreen/PiP/Split-Modell, Duplikate teilen SourceId | Bedienoberfläche und Live-Ausgabe |
| Touch-Editor und Alignment-Guides | Geometrie, Pixel-Snapping und Guide-Koordinaten | Compose-Editor, Drag/Resize/Rotate, Accessibility |
| Transitionen, Reconnect, Offline-Platzhalter | Persistierte Cut/Fade-Spezifikation, Reconnect-Backoff und Permission-Zustände | Frame-Clock/Blend, Platzhalter-Rendering, echte Disconnects |
| Performance, Speicher, Thermik, Latenz, Akku | Messverfahren im RFC | Baseline und echte Gerätemessungen |
| Bestehende Funktionen erhalten | Altes Szenenformat bleibt gültig | Vollständige Streaming-/Chat-/Widget-Suites und Geräte-Smoke |

## Lokale Verifikation

Die neuen Domain-/Producer-/Geometrie-Tests können auf der JVM ohne Android-Capture
laufen. Die separate Kotlin-2.4.21-/JUnit-6.1.3-Ausführung hat **30 Tests bestanden**,
einschließlich der echten `SceneRepository`-Klasse gegen DataStore 1.1.7 und
Coroutines 1.11.0. Sie prüft diese Bausteine und Persistenzverträge, keine
Android-App, keine dateibasierte DataStore-Integration und keinen Shader. Die regulären Tests
bleiben unter `domain/src/test`, `core/src/test` und `feature-streaming/src/test`.

Der erste Gradle-Download schlug mit einem Read-Timeout fehl. Die Distribution
9.8.1 wurde anschließend vollständig heruntergeladen und ZIP-geprüft; der
reguläre gezielte Gradle-Testlauf wurde erneut gestartet. Ein vollständiger
Android-Build oder ein Gerätetest ist damit noch nicht nachgewiesen.
