# Scene-Compositor: Umsetzungsstand zu #306

Die vollständige Umsetzung des [Vorschlags](https://github.com/thoser666/Vivid/issues/306)
ist das Arbeitsziel. Der [RFC](scene-compositor.md) dokumentiert die Architektur;
die dort vorgeschlagene Zurückstellung ist keine Behauptung, die vollständige
Implementierung sei erledigt. Dieser Stand unterscheidet implementierte Bausteine
von noch fehlender Integration und Geräte-Evidenz.

## Anforderungen und Nachweise

| Anforderung | Aktueller Baustein | Noch erforderlich |
|---|---|---|
| Persistente Szenen mit mehreren Layern | `SceneComposition`, optionales `StreamScene.composition`, atomare SceneRepository-Writes, Migrationsbackup | `SceneController`-Anbindung vorhanden; Editor-/ViewModel-Anbindung und App-Smoke offen |
| Stabile SourceIds, wiederverwendbare Producer | Source-/Layer-Modell und referenzbasierter `SceneProducerManager`, Generationen gegen verspätete Disconnects | `AndroidSceneTextureProvider` bindet zusätzliche Producer an GLES; Geräte-Smoke und unabhängiger Hauptquellen-Lifecycle offen |
| Gleichzeitige Kameras, UVC, Screen, Bilder und Widgets | Typisierte SourceSpecs | Camera2-, MediaProjection-, lokaler Video- und Bild-Adapter vorhanden; Widget-Datenfeed, Consent-UI/Foreground-Service, USB-Backend ohne Camera-HAL und Geräte-Matrix offen |
| GLES/EGL-Offscreen-Ausgabe zum Hardware-Encoder | RootEncoder-2.7.5-Quellcode lokal geprüft | `SceneCompositorFilterRender` und Textur-/Surface-Lifecycle implementiert und SDK/AAR-kompiliert; echte GLES-Pixeltests noch nicht ausgeführt |
| Ein Ergebnis für Streaming und Recording | Bestehender RootEncoder-Fanout als Integrationsziel | Filter am gemeinsamen RootEncoder-FBO angeschlossen; Stream-/Recording-/Preview-Parität auf Geräten verifizieren |
| Keine zusätzlichen CPU-Framekopien/Encoder | Producer-Vertrag besitzt keinen Encoder | Zusätzliche Capture-Adapter verwenden Surface/Texture ohne Encoder; Profiling und NoVideoSource-Route ohne unbenutzte Hauptkamera offen |
| Reihenfolge, Position, Größe, Rotation, Crop, Sichtbarkeit | Validiertes Modell, `SceneGeometry` für GPU-Vertices und Hit-Tests | GLES-Zeichenpfad verdrahtet; Pixeltests vorhanden, Editor und Geräte-Verifikation offen |
| PiP, Presets, Duplizieren | Fullscreen/PiP/Split-Modell, Duplikate teilen SourceId | Bedienoberfläche und Live-Ausgabe |
| Touch-Editor und Alignment-Guides | Geometrie, Pixel-Snapping und Guide-Koordinaten | Compose-Editor, Drag/Resize/Rotate, Accessibility |
| Transitionen, Reconnect, Offline-Platzhalter | Persistierte Cut/Fade-Spezifikation, Reconnect-Backoff und Permission-Zustände | Whole-Scene-Fade, Platzhalter und Disconnect-Reconnect implementiert; Hauptquellen-Reconnect, Clock bei erstmaligem Live-Aktivieren und Geräte-Verifikation offen |
| Performance, Speicher, Thermik, Latenz, Akku | Messverfahren im RFC | Baseline und echte Gerätemessungen |
| Bestehende Funktionen erhalten | Altes Szenenformat bleibt gültig | Vollständige Streaming-/Chat-/Widget-Suites und Geräte-Smoke |

## Lokale Verifikation

Die neuen Domain-/Producer-/Geometrie-Tests können auf der JVM ohne Android-Capture
laufen. Die separate Kotlin-2.4.21-/JUnit-6.1.3-Ausführung hat **35 Tests bestanden**,
einschließlich der echten `SceneRepository`-Klasse gegen DataStore 1.1.7 und
Coroutines 1.11.0. Sie prüft diese Bausteine und Persistenzverträge, keine
Android-App, keine dateibasierte DataStore-Integration und keinen Shader. Die regulären Tests
bleiben unter `domain/src/test`, `core/src/test` und `feature-streaming/src/test`.

Der erste Gradle-Download schlug mit einem Read-Timeout fehl. Die Distribution
9.8.1 wurde anschließend vollständig heruntergeladen und ZIP-geprüft; der
reguläre gezielte Gradle-Testlauf wurde erneut gestartet. Ein vollständiger
Android-Build oder ein Gerätetest ist damit noch nicht nachgewiesen.

## GPU- und Capture-Integration

Der Kompositor hängt nach Privacy/VideoFilter/LUT/LowLight in der kanonischen
Filterkette. Der Legacy-Eingang wird als vorhandene GPU-Textur referenziert;
weitere Quellen besitzen eigene SurfaceTexture-Eingänge, aber keinen Encoder.
Unter globalem Privacy-Modus werden zusätzliche Live-Quellen vorsorglich vollflächig
mosaikiert. Der Editor muss diese Semantik noch sichtbar machen und Kamera-/Crop-
Orientierung auf realen Geräten abgleichen.

Der erste reguläre Gradle-Lauf erreichte Domain-Tests (5 grün) und scheiterte dann
am x86-Linux-AAPT2 auf Android ARM64. Mit dem lokalen Override
`-Pandroid.aapt2FromMavenOverride=/data/data/com.termux/files/usr/bin/aapt2`
kompilieren die Android-Module und Unit-Test-Quellen. Im ersten gezielten Lauf
waren SceneRepository (14), PrivacyComposer (16), StreamingEngineFilters (27),
SceneController (5), Geometry (3), Producer (8), TransitionClock (4) und
AutoSceneSwitcher (9) grün. Drei neue Bitmap-Tests scheiterten vor ihren Assertions
an der glibc-Conscrypt-Bibliothek. Diese API-Ownership-Tests verwenden nun explizit
portable Legacy-Bitmap-Shadows ohne Conscrypt. Der anschließende gezielte Gradle-
Lauf war grün: **52 Domain-, 14 Repository- und 85 ausgewählte Streaming-Tests**.
Die Android-Instrumented-Testquellen kompilierten ebenfalls. Anschließende
Source-Rename-/Capture-Reuse-Regressionsprüfungen bestanden **15 Tests** (überlappend
mit dem vorherigen Lauf). Ein vollständiger Pre-Push-Lauf bleibt gesondert
nachzuweisen.

`SceneCompositorGpuTest` enthält sieben echte EGL/GLES2-Tests für Layer-/Alpha-
Blend, Crop/Rotation, Fade, Offline-Frames, Legacy-Orientierung, geteilte externe
Surfaces und Bilddatei-Upload. Der Fixture-Quellcode wurde gegen Android 37 und
RootEncoder 2.7.5 kompiliert. Kein angeschlossenes ADB-Gerät war verfügbar;
Kompilieren ist kein Nachweis der Shader-Ausgabe. Die Geräte-Tests müssen noch
laufen, bevor der Renderer als verifiziert gilt.

Der anschließende vollständige `scripts/pre-push.sh`-Lauf wurde ausgeführt. Die
frühen Repository-Guards liefen durch; der Gradle-Gesamtlauf stoppte in bestehenden
`ObsControlScreenRobolectricTest`-Fällen beim Laden von Conscrypt
(`libpthread.so.0` auf Android/Termux nicht vorhanden). Der vollständige Gate-Lauf
ist deshalb **nicht grün**, und nachfolgende Lint-/Release-Nachweise sind offen.
Die OBS-Tests wurden für diesen Kompositor-PR nicht geändert.
