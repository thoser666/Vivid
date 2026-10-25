# Scene-Compositor: Umsetzungsstand zu #306

Die vollständige Umsetzung des [Vorschlags](https://github.com/thoser666/Vivid/issues/306)
ist das Arbeitsziel. Der [RFC](scene-compositor.md) dokumentiert die Architektur;
die dort vorgeschlagene Zurückstellung ist keine Behauptung, die vollständige
Implementierung sei erledigt. Dieser Stand unterscheidet implementierte Bausteine
von noch fehlender Integration und Geräte-Evidenz.

## Anforderungen und Nachweise

| Anforderung | Aktueller Baustein | Noch erforderlich |
|---|---|---|
| Persistente Szenen mit mehreren Layern | `SceneComposition`, optionales `StreamScene.composition`, atomare SceneRepository-Writes, Migrationsbackup | `SceneController` und Editor/ViewModel mit atomarem Draft-Save angebunden; App-Smoke offen |
| Stabile SourceIds, wiederverwendbare Producer | Source-/Layer-Modell und referenzbasierter `SceneProducerManager`, Generationen gegen verspätete Disconnects | `AndroidSceneTextureProvider` bindet zusätzliche Producer an GLES; Geräte-Smoke, unabhängiger Hauptquellen-Lifecycle und Capture-Ownership bei Effekt-/GL-Rebuilds offen |
| Gleichzeitige Kameras, UVC, Screen, Bilder und Widgets | Typisierte SourceSpecs | Camera2-, MediaProjection-, lokaler Video- und Bild-Adapter vorhanden; Text-/Uhr-Feed und Consent-UI/Foreground-Service implementiert; weitere Widget-/Chat-Feeds, USB-Backend ohne Camera-HAL und Geräte-Matrix offen |
| GLES/EGL-Offscreen-Ausgabe zum Hardware-Encoder | RootEncoder-2.7.5-Quellcode lokal geprüft | `SceneCompositorFilterRender` und Textur-/Surface-Lifecycle implementiert und SDK/AAR-kompiliert; echte GLES-Pixeltests noch nicht ausgeführt |
| Ein Ergebnis für Streaming und Recording | Bestehender RootEncoder-Fanout als Integrationsziel | Filter am gemeinsamen RootEncoder-FBO angeschlossen; Stream-/Recording-/Preview-Parität auf Geräten verifizieren |
| Keine zusätzlichen CPU-Framekopien/Encoder | Producer-Vertrag besitzt keinen Encoder | Zusätzliche Capture-Adapter verwenden Surface/Texture ohne Encoder; NoVideoSource-Route für aktive Scene-Graphen integriert; Profiling und Geräte-Parität offen |
| Reihenfolge, Position, Größe, Rotation, Crop, Sichtbarkeit | Validiertes Modell, `SceneGeometry` für GPU-Vertices und Hit-Tests | GLES-Zeichenpfad verdrahtet; Pixeltests und Compose-Editor vorhanden; Geräte-Verifikation offen |
| PiP, Presets, Duplizieren | Fullscreen/PiP/Split-Modell, Duplikate teilen SourceId | Compose-Bedienoberfläche vorhanden; Live-Ausgabe auf Geräten verifizieren |
| Touch-Editor und Alignment-Guides | Geometrie, Pixel-Snapping und Guide-Koordinaten | Compose-Canvas mit Drag/Pinch/Resize/Rotate und alternativen beschrifteten Slidern; Geräte- und Accessibility-Smoke offen |
| Transitionen, Reconnect, Offline-Platzhalter | Persistierte Cut/Fade-Spezifikation, Reconnect-Backoff und Permission-Zustände | Whole-Scene-Fade, Platzhalter und Disconnect-Reconnect implementiert; Unabhängiger Takt beim erstmaligen Live-Aktivieren implementiert; Hauptquellen-Reconnect und Geräte-Verifikation offen |
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
mosaikiert. Der Editor erklärt diese Semantik; Kamera-/Crop-Orientierung bleibt auf realen Geräten abzugleichen.

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

`SceneCompositorGpuTest` enthält zehn echte EGL/GLES2-Tests für Layer-/Alpha-
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

## Interaktiver Editor und Capture-Consent

Der aktive Szenen-Preset öffnet einen Editor mit GPU-Vorschau, Layer-Auswahl,
Reihenfolge, Duplikaten mit geteilter SourceId, Sichtbarkeit, Crop, Deckkraft,
Layout-Presets und Fade-Einstellungen. Drag/Pinch und Resize-/Rotationsgriffe
verwenden dieselbe Geometrie wie der Renderer; beschriftete Slider erlauben
alternative Eingaben. Ein Gesture ist ein Undo-Schritt. Live-Änderungen schreiben
keinen DataStore; Save speichert den vollständigen Draft, Cancel stellt die
vorherige Komposition und Kamera wieder her. Automatischer Szenenwechsel pausiert
für die Editor-Sitzung und erhält danach seinen vorherigen Zustand.

Der Quellen-Dialog bietet Camera2-IDs einschließlich externer HAL-Kameras,
persistente SAF-Bild-/Video-URIs, wiederverwendbare Quellen, Text, Uhr und
MediaProjection-Consent. Text/Uhr veröffentlichen begrenzte Bitmaps nur bei
Inhaltsänderung. Andere Widget-/Chat-Inhalte sind weiterhin offen.

Der Service aktiviert den mediaProjection-FGS-Typ vor `getMediaProjection`.
Runtime-Grants veröffentlichen ihren aktiven Zustand; Preview-only-Capture hat
eine Stop-Notification und endet bei Revocation oder Freigabe der letzten Quelle.
GL-Rebuilds verwenden denselben VirtualDisplay erneut. Alte Lease-Handles prüfen
zusätzlich die Grant-Identität, damit sie einen späteren Grant nicht abtrennen.
Android-14+-Consent und die tatsächliche Preview-/Encoder-Orientierung benötigen
weiterhin Geräteevidenz.

Die Editor-/Capture-Prüfung bestand **65 ausgewählte Streaming-Tests**
(einschließlich Session/Undo, Gestenmathematik, ViewModel-Save/Cancel,
Widget-Inhalt/Bitmap-Ownership und Projection-Grant-/Lease-Lifecycle) sowie
**14 StreamingServiceSupport-Tests** im Foss-App-Modul. Der Foss-App-Code und
seine Unit-Test-Quellen kompilierten. Der folgende Lint-/Gesamt-Gate-Lauf wurde
bei einer Sitzungsunterbrechung beendet und wird separat erneut geprüft;
diese Testzahlen sind kein Lint- oder Geräte-Nachweis.

Der folgende vollständige Streaming-Modul-Lint meldete zunächst zehn Fehler:
Java-Time-Nutzung ohne Desugaring bei minSdk 24, einen leeren Super-Aufruf und
KTX-Vorschläge. Die Produktions-Uhr verwendet jetzt `SimpleDateFormat` mit
expliziter Zeitzone; die weiteren Befunde sind korrigiert. Anschließend bestanden
`:feature-streaming:lintDebug` (**0 Fehler, 0 Warnungen**, ein unveränderter Hint
in ZoneEditor) und **52 betroffene Tests** (überlappend mit den vorherigen 65).
Das ersetzt weder den vollständigen Repository-Gate noch Geräteprüfungen.

Beim Geräte-Smoke zusätzlich Remote-Stream-Stopp mit weiterlaufender
Screen-Vorschau prüfen: Der Chat-Bot muss mit dem Stream enden, während die
Capture-Notification bis zum Grant-Ende bestehen bleibt. Der Service behandelt
diesen Übergang getrennt vom vollständigen Foreground-Service-Stopp.

Der vollständige Pre-Push-Lauf nach dem Editor-/Kompatibilitäts-Checkpoint
wurde erneut beendet: Repository-Selbsttests liefen durch; im unveränderten
OBS-Modul scheiterten **21 von 48 Tests** am Conscrypt-Laden wegen fehlendem
`libpthread.so.0` auf Termux. Das Gesamt-Gate bleibt rot. Die separaten grünen
Streaming-Lint-/Test-Nachweise bleiben ausdrücklich auf ihren Umfang begrenzt.

## Statische Screen-Frames und Emulator-Suite

MediaProjection-Quellen behalten nach dem ersten Frame ihr letztes Bild, auch
wenn der Bildschirm länger als fünf Sekunden unverändert bleibt. Explizite
Capture-Fehler und Grant-Revocation entfernen das Bild weiterhin sofort. Für
Kameras/Video und das Warten auf den ersten Screen-Frame gilt der bisherige
Timeout weiter. Drei zusätzliche EGL-Regressionen prüfen diese Unterschiede.
Alle zehn Instrumented-Testquellen kompilieren; Streaming-Lint bleibt grün.

Der bestehende manuelle Emulator-Workflow ruft nun zusätzlich
`:feature-streaming:connectedDebugAndroidTest` auf: Die App-Instrumentierung
führt die separate Library-Test-APK nicht aus. Der Workflow-Selbsttest führt das
echte Shell-Snippet mit Stubs aus, prüft die Library-Task und den Fehler-Exit und
weist entfernte Tasks/fehlende Steps zurück; eine Layout-Negativkontrolle bleibt
grün. Workflow-Security und der Zeilenfenster-Guard bestanden ebenfalls.

Der Fork meldet über die GitHub-API `actions.enabled = false` und keine
registrierten Workflows. Es wurde kein Emulatorlauf ausgelöst und kein
GPU-Erfolg behauptet. Die Upstream-PR meldet außerdem einen roten externen
DeepSource-Kotlin-Status; dessen Bericht war über die verfügbaren Schnittstellen
nicht abrufbar. Dieser Befund bleibt offen.

Der nächste vollständige Pre-Push-Lauf nach der Screen-Heartbeat-Korrektur
stoppte bereits im Chat-Modul: **471 Tests, sechs Fehler** in unveränderten
`ChatAlertRowRenderRobolectricTest`-Fällen. Der XML-Report belegt denselben
Conscrypt-Loaderfehler (`libpthread.so.0` fehlt), nicht eine Shader- oder
Capture-Assertion. Das Gesamt-Gate bleibt rot; die OBS-Fehler des vorherigen
Laufs sind dadurch nicht als behoben nachgewiesen.

## Vorbereitung des kameraunabhängigen Encoderpfads

`SceneEncoderSession` kapselt einen RootEncoder-`MultiStream` mit
`NoVideoSource` und zunächst `NoAudioSource`. Eine Hardware-Videoencoder-
Konfiguration wird von mehreren RTMP-Zielen und dem MP4-Muxer geteilt; es wird
keine zweite Recording-Auflösung angefordert. MP4-Tracks werden explizit auf
VIDEO bzw. ALL gesetzt, weil RootEncoder bei NoVideoSource sonst AUDIO wählt.
Der Mikrofoneingang wird nur für einen Stream oder eine Aufnahme mit Audio
aktiviert, nicht für eine reine Videoaufnahme. Der letzte Ziel-Stopp erhält
eine laufende Aufnahme; Cleanup-Fehler dürfen Disconnect und Release nicht
überspringen. Sechs fokussierte Ownership-/Fehlerpfad-Tests bestanden.

Zusätzliche Camera2-Producer verwenden jetzt RootEncoders `Camera2ApiManager`,
dessen bestehende Fokus-/Belichtungs-/Torch-/Stabilisierungs-/Face-Adapter damit
auch für diese Capture-Session verfügbar werden. Backend und TextureProvider
stellen den vorhandenen Manager als geliehene Referenz bereit. Stop entfernt
diese Referenz und schließt die Kamera genau einmal; die Capture-Surface bleibt
Eigentum des TextureProviders. Drei portable Robolectric-Vertragstests bestanden.

**Engine-Umschaltung für Scene-Graphen:** Idle-Preview, Stream-Fanout und
Replay-Aufnahme verwenden die kameraunabhängige Session, sofern kein bestehender
Legacy-Stream oder primärer Datei-/Replay-Audiopfad aktiv ist. Ein Bild-/Widget-
Graph benötigt keine unbenutzte Kamera und keine Camera-Permission. Die Preview
startet GL ohne Encoder-Vorbereitung; Recording und Streaming teilen dieselbe
Vorbereitung, solange ein Consumer aktiv bleibt. Beim Entfernen des Graphen
während einer Aufnahme bleibt ein äquivalenter Basis-Graph erhalten, damit der
Encoder nicht während des Schreibens ersetzt wird. Callback-Generationen
verhindern Zustandsänderungen einer bereits freigegebenen Session.

Die primäre sichtbare Kamera stellt ihre vorhandenen Controls und Face-Callbacks
bereit; fehlt diese Kamera, fallen Controls nicht auf eine unbenutzte Legacy-
Kamera zurück. Im neuen Pfad ist die Komposition der Eingang von Privacy und
kreativen Effekten. Live-Layer bleiben unter aktiviertem Privacy-Modus
vorsorglich vollständig mosaikiert; selektive Face-/Zonen-Abbildung auf beliebig
transformierte Kamera-Layer ist noch offen. Kamera-Sensororientierung und
Capture-Auflösung/FPS müssen zusätzlich auf Geräteprofile abgestimmt werden.

Primäre Video-/Replay-Dateien behalten vorerst den bestehenden Audiopfad. Ein
Wechsel dorthin während einer Scene-Aufnahme oder eines Scene-Streams wird
zurückgewiesen, bevor der Legacy-Dateipfad vorbereitet wird. Die gemeinsame
Datei-Audioanbindung und diese Live-Wechsel fehlen weiterhin. Ein bereits
laufender Legacy-Stream migriert beim Öffnen des Editors nicht den Encoder;
sein Scene-Takt läuft jetzt auch unabhängig vom Legacy-Eingang. Die physische
Wiederverbindung dieser Hauptquelle bleibt gesondert offen.
Diese Einschränkungen sind kein Nachweis vollständiger Feature-Parität.

Die neun Output-/Capture-Tests wurden zusammen regulär per Gradle ausgeführt
(**6 + 3 grün**). Danach bestanden Streaming-Lint und die Kompilierung der
Instrumented-Testquellen. Die Kamera-Vertragstests verwenden SDK-28-Shadows und
Mocks der Geräte-/Manager-Grenze; sie sind kein realer Kamera-Test.

Der gezielte Routing-Lauf bestand **207 Tests**: 100 bestehende Engine-, 29
Filter-, 17 PrivacyComposer-, 47 ViewModel-, sieben neue Scene-Output- und sieben
Session-Tests. Die neuen Fälle prüfen gemeinsames Recording/RTMP-Ownership,
kameraunabhängige Preview, Wiederherstellung nach fehlgeschlagener Encoder-
Vorbereitung, ignorierte alte Callbacks, Recording für Screen-Szenen und
SourceId-/Canvas-Erhalt beim erneuten Öffnen des Editors. Die UI-Testfixtures
geben die Kamera-Permission-/Recording-Capabilities ihres bisherigen Kamera-
Setups explizit an; ihre Ausführung ist kein Bestandteil dieser 207 Tests.
Der anschließende Lauf prüfte auch die finale Recording-Menü-Anpassung:
`:app:compileStandardDebugKotlin` und `:feature-streaming:lintDebug` bestanden
zusammen mit den 207 Tests. Der Streaming-Lint-XML-Report enthält keine Fehler
oder Warnungen; ein bestehender `AutoboxingStateCreation`-Hint bleibt bestehen.
Die Instrumented-Testquellen kompilierten zuvor separat; GPU-Ausführung bleibt
offen. Der vollständige Pre-Push-Gate wurde danach erneut ausgeführt: Die
Repository-Selbsttests bestanden, anschließend scheiterten wieder sechs von
471 Chat-Tests in unveränderten `ChatAlertRowRenderRobolectricTest`-Fällen am
Conscrypt-Loader (`libpthread.so.0` fehlt auf Termux). Der aktuelle Gesamt-Gate
ist damit weiterhin rot; vollständige App-Lint-/Release- und Geräte-Nachweise
bleiben offen.

## Unabhängiger Takt bei nachträglicher Scene-Aktivierung

RootEncoder 2.7.5 startet seinen internen ForceRenderer nur beim GL-Start.
Ein Scene-Graph, der während eines bestehenden Legacy-Streams aktiviert wird,
erhält daher jetzt einen eigenen `SceneRenderClock`. Der bestehende RootEncoder-
Treiber fordert ohne neue Pixelpuffer weitere GL-Zeichnungen an; Encoder und
Capture werden dabei nicht neu gestartet. Normale Scene-Draws unterdrücken
unnötige Fallback-Draws. Eigene Fallback-Draws behalten ihre Soll-FPS, statt den
nächsten eigenen Tick ebenfalls zu unterdrücken. Native NoVideoSource-Sessions
behalten ihren vorhandenen RootEncoder-Takt und starten keinen zweiten Treiber.

Ein Wechsel von GL-Owner/FPS oder das Entfernen des Graphen beendet den alten
Takt. Generationen verwerfen bereits ausgelieferte alte Timer-Callbacks; ein
GL-Stopp zwischen Statusprüfung und Executor-Submission wird abgefangen. Der
Takt endet auch beim Stopp eines Streams ohne Preview und beendet sich selbst,
wenn GL extern gestoppt wurde. Ein alter Owner darf dabei keinen neuen Takt
beenden. Die finale gezielte Prüfung bestand **151 Tests**, darunter sechs
Clock-Verträge und zwei neue Engine-Integrationsfälle. Die zehn GLES-Fixtures
kompilieren einschließlich der neuen Frame-Callback-Assertions. Streaming-Lint
bestand ohne Fehler und Warnungen; der bestehende Autoboxing-Hint bleibt.
Echte GPU-Ausführung, Frame-Pacing unter Last und Geräte-Lifecycle bleiben
nachzuweisen. Der vollständige Pre-Push-Gate wurde erneut ausgeführt: Nach
bestandenen Repository-Selbsttests scheiterten sechs von 471 unveränderten
Chat-Tests wieder am Conscrypt-Loader (`libpthread.so.0` auf Termux). Der
Gesamt-Gate bleibt rot; die gezielten Clock-/Engine- und Lint-Nachweise sind
ausdrücklich kein vollständiger App-/Release-Nachweis.

## Native Kameratexturen und Capture-Auflösung

Der Kompositor bewahrt die bereits in SurfaceTexture enthaltene Sensorrotation,
Spiegelung und den Crop. Er ergänzt nur die Display-Korrektur; eine zweite
Sensorrotation wäre falsch. Sensor-/Displayachsen bestimmen separat die Wahl
der Capture-Puffergröße. Displayänderungen werden per Listener gespeichert,
nicht pro Videoframe beim Display-Service abgefragt. UVC/HDMI bleibt von der
physischen Pose des Telefons unabhängig.

Auf Geräten mit Rotate-and-Crop-Unterstützung wird ein expliziter NONE-Request
angefordert. Der Renderer wartet auf dessen Bestätigung und einen entsprechenden
SurfaceTexture-Zeitstempel, damit AUTO-Bilder mit zusätzlicher Pixelrotation
nicht falsch ausgegeben werden. Der Metadatenzustand ist konstant groß;
verspätete ältere Ergebnisse dürfen einen neuen Request nicht zurücksetzen.
Eine verspätete Bestätigung aktualisiert auch den zuletzt gespeicherten
SurfaceTexture-Transform, selbst wenn sich der Korrekturwinkel nicht geändert hat.
Referenzen: [Camera preview](https://developer.android.com/media/camera/camera2/camera-preview),
[SCALER_ROTATE_AND_CROP](https://developer.android.com/reference/android/hardware/camera2/CaptureResult#SCALER_ROTATE_AND_CROP),
[CameraX GL transform handling](https://android.googlesource.com/platform/frameworks/support/+/6353c5d0cbdb0a766f9640dde3ffcb0f9e4cfcf1/camera/camera-core/src/main/java/androidx/camera/core/processing/SurfaceOutputImpl.java).

Einzelne Kameras können die Ausgabeauflösung bis zum 4K-Capture-Budget verwenden;
gleichzeitige Kameras bevorzugen weiterhin höchstens 720p. Falls der Treiber
keine entsprechend kleine Größe anbietet, wird seine kleinste Größe gewählt,
sofern sie noch im absoluten Speicherbudget liegt. Der Kamera-Manager bleibt
mit den bestehenden Controls geteilt; Backend-Release und spätere Producer-
Freigabe schließen ihn gemeinsam höchstens einmal. Camera-FPS-Profilierung,
selektive Face-/Zonen-Koordinaten und die tatsächliche Geräteausgabe sind weiter
offen. Die abschließende Prüfung bestand **108 Szenen-Tests**, einschließlich
elf Camera2-Verträgen mit SDK-28-/SDK-34-Shadows sowie Geometrie, Projection,
Bitmap-Ownership, Widgets, Editor, Producer-Lifecycle und Output-Ownership.
Streaming-Lint bestand ohne Fehler/Warnungen (ein bestehender Autoboxing-Hint).
Dreizehn GLES-Fixtures einschließlich eingebettetem Kameratransform plus Crop,
nachgereichter Capture-Metadaten und Timeout trotz weiter eintreffender
unbrauchbarer Frames kompilieren. Sie wurden weiterhin nicht auf einem
Gerät/Emulator ausgeführt. Die Camera2-Verträge mocken den echten Hardware-
Manager; dies ist kein Kamera-/GPU-/Thermik-Nachweis. Der vollständige Gate
wurde nach diesem Checkpoint erneut ausgeführt: Nach bestandenen Repository-
Selbsttests scheiterten sechs von 471 unveränderten Chat-Tests am Conscrypt-
Loader (`libpthread.so.0` fehlt auf Termux). Gesamt-Lint/Release und die echte
Geräte-Matrix bleiben damit offen.

## Capture-Lifetime bei kreativen Effektänderungen

`PrivacyComposer` verwendet ab dem ersten aktiven Filter einen stabilen
`SceneFilterChainRender` als einzelnen RootEncoder-Slot, auch ohne Scene. Seine Kind-Passes werden gemeinsam zwischen
vollständigen Frames aktualisiert. Der Scene-Renderer und die eingeschaltete
Privacy bleiben initialisiert; nur entfernte kreative Passes werden freigegeben.
Mehrere Änderungen vor einem Frame initialisieren nur die letzte gewünschte
Kette. Der letzte Pass zeichnet direkt in den stabilen Ausgabe-FBO des Containers,
ohne zusätzlichen Blit oder CPU-Pixelkopie. Zwischenpuffer werden nur bei Bedarf
angelegt und beim Entfernen ihrer Besitzer im passenden EGL-Kontext freigegeben.

Die bestehende Privacy-/Kreativ-Kette lebt von Anfang an im selben Container.
Das Hinzufügen einer Scene erfordert daher keinen Root-CLEAR/SET-Wechsel und
keine vorübergehend doppelt ausgeführten Legacy-Passes. Es gibt keine indizierten
Inserts, die nach einem GL-Stopp auf eine inzwischen leere Kette treffen könnten;
RootEncoder behält ausstehende Filter-Kommandos über Stopps hinweg. Nach Entfernen
der Scene bleibt der Container für aktive Privacy oder kreative Effekte bestehen;
eine vollständig leere Kette entfernt ihn, ohne einen zusätzlichen Passthrough-Draw
dauerhaft beizubehalten. Ein echter GL-Stopp invalidiert den Owner; Retirement
wird vor Capture-Cleanup sichtbar. Verspätetes Retirement darf eine neue Container-
Instanz auf demselben GL-Owner nicht verwerfen.
Auch bei Fehlern im Retirement-Callback oder einem Kind-Pass werden die übrigen
Captures freigegeben und ausstehende Zustände geleert; Fehler bleiben sichtbar.

Der Video-Filter-Controller publiziert den gewünschten Filter vor dem synchronen
Composer-Callback. So fordert der erste Klick GRAYSCALE und der nächste SEPIA an,
statt jeweils den vorherigen Zustand zu rendern. Schlägt der Callback fehl, wird
der vorige Zustand wiederhergestellt. Zwei Controller-Regressionen und ein
Engine-Integrationsfall reproduzierten die falsche Reihenfolge (23 Tests, drei
gezielte Fehler) und bestanden nach der Korrektur. Der finale Testlauf bestand
**179 Szenen-/Privacy-/Filter-Tests** und kompilierte alle **15 GLES-Fixtures**.

Die fokussierte Prüfung bestand **72 Container-/Privacy-/Engine-Tests**; alle
**15 GLES-Fixtures** einschließlich der neuen Privacy-/Lifetime-Fixture kompilieren.
Der erste breitere Lauf bestand 164 Tests und Streaming-Lint ohne Fehler/Warnungen
(ein bestehender Hint). Drei zusätzliche Regressionen reproduzierten danach
veraltetes Retirement und unvollständiges Cleanup (14 Tests, drei erwartete Fehler)
und bestanden nach der Korrektur. Der anschließende Lauf bestand 167 Tests, kompilierte 15 GLES-Fixtures und bestand
Streaming-Lint (keine Fehler/Warnungen, ein bestehender Hint). Nach Vereinheitlichung
der Container-Nutzung ab dem ersten aktiven Filter und der Filter-State-Korrektur
bestand der finale Lauf 179 Tests und die Instrumented-Kompilierung; die separate
Streaming-Lint-Prüfung läuft noch. Die Fixture prüft konstante Privacy-Mosaikpixel bei
wiederholten Effektwechseln, stabile Ausgabe-Textur und einmaliges Source-Attach;
ihr negativer Kontrollfall schaltet Privacy aus. Sie ist noch nicht auf einem
Gerät/Emulator ausgeführt. Capture über einen tatsächlichen EGL-Kontextwechsel
bleibt gesondert offen: Dort werden die bisherigen Texturen und Captures weiterhin
freigegeben und neu erstellt. Geräte-, Latenz- und Leistungsnachweise fehlen.

## Capture-FPS nach Ausgabebedarf

Der TextureProvider übergibt den aktuellen Ausgabebedarf an den Capture-Adapter:
Idle-Preview fordert 30 FPS an, Stream/Aufnahme das aktive Encoderprofil.
Eine einzelne Kamera fordert höhere reguläre Raten nur an, wenn Outputgröße,
minimale Frame-Dauer und AE-Bereiche sie gemeinsam unterstützen. Gleichzeitige
Kameras bleiben bei höchstens 30 FPS. Fehlende Timing-Metadaten gelten nicht als
Nachweis für 60 FPS; dann bleibt der begrenzte bisherige 30-FPS-Default erhalten
(bei kleinerem Ausgabebedarf dessen Wert). Dies sind Request-Werte, keine Messung
der tatsächlich gelieferten Frames.

Änderungen verwenden die bestehende Capture-Session. Ausstehende Requests werden
zusammengefasst, erst nach einem brauchbaren Puffer angewendet und beim Stop
verworfen. Der gemeinsame Request-Builder behält andere Controls; schlägt eine
FPS-Änderung fehl, wird sein vorheriger AE-Bereich wiederhergestellt. Nach drei
Fehlversuchen bleibt die bisherige Rate bestehen. Ein geänderter Bedarf oder eine
neu geöffnete Session erlaubt einen neuen Versuch. Die Surface und der Kamera-
Manager werden dafür nicht neu geöffnet. Eine wiederverwendete Idle-Preview
setzt auch den vorhandenen RootEncoder-Zeichentakt auf 30 FPS zurück.

Die 13 neuen Rate-Vertragstests bestanden; der finale sequenzielle Lauf bestand
**121 Szenen-/Privacy-Tests** und kompilierte alle **14 GLES-Fixtures**. Die neue
GLES-Fixture prüft die Weitergabe von 60 auf 30 FPS bei unverändertem Producer;
ihre Geräteausführung und die tatsächliche Capture-Rate, Leistungsaufnahme und
Thermik bleiben offen. Streaming-Lint bestand ohne Fehler/Warnungen (ein
bestehender Autoboxing-Hint). Der erste breitere Testlauf stoppte an einem
inkrementellen Kotlin-Cachefehler vor der Testausführung; der sequenzielle
Wiederholungslauf ohne inkrementelle Kompilierung bestand. Der vollständige
Pre-Push-Gate wurde erneut ausgeführt: Nach bestandenen Repository-Selbsttests
scheiterten diesmal 21 von 48 unveränderten OBS-Tests am Conscrypt-Loader
(`libpthread.so.0` fehlt auf Termux), durch die XML-Reports belegt. Der
Gesamt-Gate bleibt rot; vollständige App-Lint-/Release- und Geräte-Nachweise
bleiben offen.
