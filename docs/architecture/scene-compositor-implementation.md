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
| Gleichzeitige Kameras, UVC, Screen, Bilder und Widgets | Typisierte SourceSpecs | Camera2-, MediaProjection-, lokaler Video- und Bild-Adapter vorhanden; Text-/Uhr-Feed und Consent-UI/Foreground-Service implementiert; USB-Backend ohne Camera-HAL mit Consent/Editor implementiert; weitere Widget-/Chat-Feeds und Geräte-Matrix offen |
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
Legacy-Stream aktiv ist. Primäre Dateien und Replays verwenden inzwischen den
weiter unten beschriebenen gemeinsamen Audioeingang. Ein Bild-/Widget-Graph benötigt keine unbenutzte Kamera und keine Camera-Permission. Die Preview
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

Primäre Video-/Replay-Dateien sind im späteren Playhead-/Handoff-Abschnitt an
den vorhandenen Szenen-Audioencoder angeschlossen. Live-Wechsel innerhalb der
nativen Route bereiten keinen zusätzlichen Legacy-Datei-Encoder vor. Ein bereits
laufender Legacy-Stream migriert beim Öffnen des Editors weiterhin nicht den Encoder;
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

Die finale Prüfung bestand **179 Szenen-/Privacy-/Filter-Tests** und kompilierte
alle **15 GLES-Fixtures**. Streaming-Lint bestand ohne Fehler/Warnungen (ein
bestehender Autoboxing-Hint). Die Retirement-/Cleanup-Regressionen reproduzierten
zuvor drei gezielte Fehler in 14 Tests; nach Korrektur bestanden sie. Die finale
Container-Nutzung gilt ab dem ersten aktiven Filter. Der vollständige Repository-
Gate wurde ausgeführt: Nach bestandenen Repository-Selbsttests scheiterten sechs
von 471 unveränderten Chat-Tests am Conscrypt-Loader (`libpthread.so.0` fehlt auf
Termux), durch die XML-Reports bestätigt. Das Gesamt-Gate bleibt rot; vollständige
App-Lint-/Release- und Geräte-Nachweise bleiben offen.
Die Fixture prüft konstante Privacy-Mosaikpixel bei
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

## USB-Kameras ohne Android-Camera-HAL

Der zusätzliche UVC-Adapter verwendet `com.herohan:UVCAndroid:1.0.13` nur für
Capture auf die vorhandene geliehene Surface. Er besitzt keinen Encoder und
verwendet weder Bitmap-Framecallbacks noch die Recording-/Storage-Helfer des SDK.
Öffnen, Moduswechsel und Schließen laufen auf einem eigenen seriellen Worker,
nicht im GL-Draw. Die Oberfläche bietet einen USB-Quellenpicker und Androids
CAMERA-/USB-Consent-Dialoge. Wiederholte Quellen teilen ihre bestehende SourceId;
ein zweiter Producer für dasselbe physische Gerät wird abgewiesen.

Persistierte Identitäten enthalten VID/PID, die nach erteilter Permission lesbare
Seriennummer und einen Pfad-Hinweis. Eindeutige Seriennummern überleben eine neue
Busadresse; doppelte Seriennummern und seriennummerlose Geräte werden nicht nach
zufälliger Enumeration gewählt. Eine unbekannte Seriennummer wird erst nach
Consent erneut aufgelöst. Ein Grant für ein anderes Gerät bindet eine bestehende
Quelle nicht automatisch um. Grants werden nicht persistiert; Rotation hält den
laufenden Consent, Prozessverlust prüft nur einen tatsächlich vorhandenen Grant.

Der SDK-Monitor wird nicht registriert und fordert selbst keine Permission an.
Der Adapter erzeugt über einen mit Consumer-Regeln erhaltenen privaten
Konstruktor der gepinnten App-Bibliothek einen noch ungeöffneten ControlBlock;
dessen SDK-Clone öffnet nach der echten Permission-Prüfung den Device-FD.
Zwei Tests prüfen diesen Vertrag an den tatsächlichen AAR-Java-Klassen, ohne
Native-Capture zu starten. App-eigene nicht exportierte Receiver behandeln
Consent/Detach. USB-Host bleibt optional. Die App entfernt die unbenutzten Storage-Rechte explizit. Selektoren auf das SDK
haben sowohl auf Library- als auch auf App-Ebene die SDK-Nodes abgewiesen, aber
die Marker selbst als Berechtigungen im finalen Manifest hinterlassen. Ein neuer
App-Manifest-Vertrag prüft diesen konkreten Merge-Fehler. Die bestehende Debug-
Deklaration für Screenshots (maxSdk 18) überschreibt die WRITE-Removal-Regel;
die finalen Debug-/Release-Manifeste werden gesondert geprüft. Lizenztexte und Quellenverweise liegen in den APK-Assets unter
`licenses/uvc-android`.

Moduswahl begrenzt Kanten auf 4096 und Pixel auf 4K; konkurrierende Kameras
bevorzugen 720p und höchstens 30 FPS. Ratenänderungen werden zusammengefasst;
fehlgeschlagene Änderungen versuchen den vorherigen Modus wiederherzustellen.
Stop/Detach entwerten ausstehende Arbeit und geben das physische Lease erst nach
Driver-Cleanup frei. Das zuletzt gemeldete Soll-FPS ist keine gemessene Framerate.

Der erste finale USB-Lauf bestand **50 Tests** für Locator, Auswahl, Consent,
Activity-Rotation/Prozessverlust, Modusbudget, Worker-/Lease-Lifecycle und den
gepinnten SDK-ControlBlock-Vertrag. Der Streaming-Code einschließlich Picker
kompilierte in diesem Lauf. JNI-Capture, tatsächliche USB-Permission-Dialoge,
Hotplug, gemischtes Camera2/UVC-Capture, Orientierung, Stream-/Recording-Parität,
R8/Release und Performance benötigen weiterhin reale Geräte-Evidenz. Der
vollständige Repository-Gate und der aktuelle Lint werden gesondert nachgewiesen.

Der anschließende breitere Lauf bestand **182 Scene-Tests**, darunter **59 USB-
Tests**, und kompilierte die Standard-Debug-App sowie alle GLES-Fixtures.
Sechs zusätzliche Editor-Identitätsfälle prüfen unter anderem SourceId-/Label-
Erhalt nach Bus-Renummerierung, abweichende Seriennummern nach Consent und doppelte
Firmware-Seriennummern. Zwei weitere Worker-Fälle prüfen begrenzte Modus-Retries
und Stop vor einer ausstehenden Neuverhandlung. Der feste 30-FPS-Modus bleibt
auch bei 24-FPS-Ausgabe und konkurrierender Kamera verwendbar; der Kompositor
samplet mit seiner Ausgaberate. Der entsprechende neue Test scheiterte zuvor
gezielt (19 Tests, ein Fehler) und bestand nach Korrektur im breiteren Lauf.
Der erste Streaming-Lint-Lauf bestand ohne Fehler/Warnungen (ein bestehender
Autoboxing-Hint); nach den folgenden Helper-/Modus-/Manifest-Änderungen bestand auch der
abschließende Streaming-Lint ohne Fehler/Warnungen mit demselben bestehenden Hint.

Der Manifest-Vertrag reproduzierte den Storage-Leak mit **drei Tests, einem
gezielten Fehler**; optionaler USB-Host und interne Activity bestanden bereits.
Das Verschieben der Selektoren ins App-Manifest allein beseitigte den Fehler
nicht. Explizite App-Removals ohne Selektor und die eigene Debug-Überschreibung
bestanden danach **alle drei Tests**. Beide finalen Manifeste wurden zusätzlich
geparst: Debug enthält ausschließlich das bestehende WRITE-Recht mit maxSdk 18,
Release kein Storage-Recht. USB-Host ist in beiden optional und die Consent-
Activity nicht exportiert. Dies ist ein Merge-/PackageManager-Nachweis, kein
realer Android-USB-Permission- oder JNI-Capture-Test.

Der vollständige Pre-Push-Gate des USB-Checkpoints wurde nach Code- und Parity-
Commit ausgeführt. Die Repository-Selbsttests bestanden; anschließend scheiterten
**21 von 48 unveränderten OBS-Tests** am Conscrypt-Laden (`libpthread.so.0` fehlt
auf Termux). Alle 21 XML-Fehler belegen diesen Loader-Pfad. Der Gesamt-Gate bleibt
rot; die separaten 182 Szenen-/drei Manifest-Tests und Streaming-Lint sind kein
vollständiger App-/Release- oder Geräte-Nachweis.


### Stabiler Audioeingang und Datei-Audioadapter (Zwischenstand)

Die native Szene installiert jetzt einen dauerhaften `SceneAudioRouterSource`
als Eingang des vorhandenen RootEncoder-Audioencoders. Mikrofon und deaktivierter
Eingang wechseln innerhalb dieses Routers. Zusätzliche Audioeingänge lassen sich
vorbereiten und austauschen, ohne Video-/Audioformat, RTMP-Ziele oder MP4-Muxer neu
anzulegen. Vorbereitung schlägt vor dem Stop des alten Eingangs fehl; ein
fehlgeschlagener Start versucht einen frischen alten Eingang und anschließend
Stille. Generationen sperren verspätete PCM-Callbacks. Stop/Release warten außerhalb
der PCM-Sperre auf Producer-Threads. Aufnahme ohne Audio und reine Vorschau öffnen
weiterhin kein Mikrofon.

Der getestete Datei-Adapter nutzt RootEncoders AudioDecoder mit gültigen
MediaCodec-Bufferbereichen statt dessen wiederverwendetem vollständigem PCM-Array.
Media3 Sonic konvertiert PCM16-Mono/Stereo auf das feste Encoderprofil und erzeugt
eigene, frame-ausgerichtete Pakete von höchstens `AudioEncoder.inputSize` Bytes.
Das ist notwendig, weil RootEncoders Encoder größere Frames abschneidet. Zeitstempel
folgen der ausgegebenen Samplezahl; Seek verwirft alte Resampler-Daten unter der
Decoder-Sperre. EOF/Decoderfehler liefern Stille, niemals Mikrofon. Seek nach EOF
öffnet einen frischen Decoder. Absolute Replay-Pfade werden als Datei-URI geöffnet.

Beim ersten Audio-Checkpoint war der Adapter noch nicht in StreamingEngine
verdrahtet; diese frühere Einschränkung wird durch den folgenden Integrationsstand
ersetzt. Adapter-/Router-Tests allein beweisen weder MediaCodec-Gerätebetrieb
noch A/V-Synchronität.

Der abschließende lokale Szenenlauf bestand **211 Tests** ohne Fehler oder Skips,
darunter sieben PCM-, elf Datei-Adapter-, neun Router- und neun Encoder-Session-
Tests. Der große 8-kHz-Mono-zu-32-kHz-Stereo-Fall prüft verlustfreie Paketierung
und fortlaufende Zeitstempel; der Cleanup-Fall wartet auf einen echten parallelen
Callback, um eine unter der Delivery-Sperre blockierende Freigabe zu erkennen.

Auch Standard-Debug-App und instrumentierte Quellen kompilierten danach. Der
Streaming-Lint bestand ohne Fehler/Warnungen mit dem bestehenden
`AutoboxingStateCreation`-Hint. I18n- und Markdown-Anker-Guards bestanden.

Der vollständige Pre-Push-Gate nach diesem Audio-Checkpoint bestand die
Repository-Selbsttests (PyPI-Closure-Drift nur advisory) und scheiterte anschließend
an **sechs von 471 unveränderten Chat-Tests**. Alle sechs XML-Fehler zeigen den
Conscrypt-Native-Loader mit fehlender `libpthread.so.0` auf Termux. Der Gate hat
folglich keinen vollständigen App-/Release-Erfolg bewiesen. Die oben genannten
211 Szenentests und der separate Streaming-Lint bleiben separat nachgewiesen.


### Datei-/Replay-Playhead und Live-Routing

StreamingEngine wählt jetzt auch für primäre Datei-/Replay-Quellen die native
Szenenroute. Ein globaler Dateiwechsel während Stream/Aufnahme bereitet keinen
neuen Legacy-Datei-Encoder vor. Ein MediaExtractor-Probe prüft vor der Auswahl
einen lesbaren Video-Track; Fehler lassen die aktive Quelle unverändert.

Video-Layer besitzen einen eigenen HandlerThread für sämtliche MediaPlayer-
Aufrufe und Callbacks. Die Hardware-Encoder der Ausgabe gehören weiterhin allein
der SceneEncoderSession. Der Player wird vor der Freigabe seiner geliehenen
Surface beendet. GL meldet den ersten nutzbaren Frame; spätere Frames erzeugen
keine synchronen Worker-Aufrufe. Der lesbare Playhead enthält eine nur für diese
Capture-Lebensdauer gültige Identität und eine Schleifennummer.

Ein sichtbares primäres Video liefert zunächst Stille. Nach dem ersten nutzbaren
Frame startet sein Datei-Audio am aktuellen Video-Playhead. Der Video-Player
steuert Schleifen explizit und meldet die neue Schleife erst nach Abschluss des
asynchronen MediaPlayer-Seeks. Währenddessen ist kein neuer Playhead verfügbar.
Die Audioquelle spult nach einer passenden aktuellen Schleifenmeldung mit. Veraltete Capture-Identitäten und bereits verarbeitete
Schleifen werden verworfen. Ein ausgeblendetes primäres Video kann unabhängig
Audio liefern; beim Wiedereinblenden wechselt es auf den neuen sichtbaren
Playhead. Video-/Audioformat und Stream-/Recording-Consumer bleiben dabei erhalten.

Ein globaler Quellenwechsel bindet die primären Ebenen an eine passende
Capture-Konfiguration. Die explizite Zuordnung und Migration alter Graphen sind
im Abschnitt „Primäre Ebenen und wiederverwendbare Capture-Konfigurationen“
beschrieben. Fehlt die primäre Ansicht, kommt eine neue untere Ebene hinzu;
Bilder/Widgets werden nicht als beliebiger Fallback überschrieben. Volle Graphen
weisen diese Ergänzung zurück. Bestehende Ebenen-IDs und Transformationen bleiben
bei weiteren Wechseln erhalten.

Gemessene A/V-Synchronität und Schleifenlücken auf realen MediaCodec-Geräten
bleiben offen. Der Übergang vorbereiteter Legacy-Datei-Sessions wird im folgenden
Handoff-Abschnitt ergänzt. Der neue Pfad ist kein Geräte-/Performance-Nachweis.


Die App und instrumentierten Quellen kompilierten mit dem neuen Playback-Pfad.
Vier Tests gegen das zusammengeführte App-Manifest bestanden. Die Streaming-
Library deklariert Backup-/Cleartext-Sperren explizit; sie entsprechen den bereits
vorhandenen App-Regeln (`allowBackup=false`, Network-Security-Config mit gesperrtem
Cleartext-HTTP). Keine Security-Regel wurde unterdrückt oder gelockert. Der externe
Sonar-/DeepSource-Gate muss am veröffentlichten neuen Stand erneut geprüft werden.

Der abschließende Szenenlauf bestand **226 Tests** ohne Fehler, Errors oder Skips.
Darunter liegen sechs Playhead-/Video-Producer-Fälle, zwei echte Background-Looper-
Fälle, vier Primary-Source-Fälle und 15 Engine-Szenenfälle. Der asynchrone Seek-Test
hält den Abschluss zurück und prüft, dass vorher weder eine neue Schleife noch
ein alter EOF-Playhead an Audio gemeldet wird. Die Tests verwenden keine realen
MediaPlayer-/MediaCodec-Decodes. Der gesonderte Streaming-Lint läuft anschließend;
sein Ergebnis und der vollständige Gate werden separat erfasst.


### Handoff vorbereiteter Legacy-Dateiquellen

Vor einer neuen nativen Session stoppt StreamingEngine vorhandene, nicht aktive
Legacy-Datei-/Replay-Quellen. RootEncoders gepinnter `FromFileBase.stopStream()`
gibt auch die vorbereiteten Decoder und Encoder frei. Dateiauswahl bleibt erhalten;
Registry-Factories verweisen anschließend auf die native Route. Eine volatile
Generation entwertet alte Datei-ConnectChecker vor dem Stop, damit verspätete
Disconnect-/Auth-/Bitrate-Ereignisse keine neue Szene verändern.

Nach Entfernen einer inaktiven nativen Szene werden keine Legacy-Encoder sofort
neu angelegt. Erst der nächste Datei-/Replay-Stream erzeugt eine frische Quelle,
bereitet die gespeicherte Auswahl vor und stellt ihre Registry-Zuordnung wieder
her. Eine aktive Legacy-Übertragung wird auf diesem Weg nicht ersetzt.

Native Dateiänderungen sind transaktional: Bei fehlendem Platz für eine primäre
Ebene bleiben die alte Dokument-URI beziehungsweise Replay-Datei und der Graph
erhalten. Die lokalen URI-/Track-Prüfungen gelten für den neuen nativen Pfad;
der bestehende Legacy-Datei-Eingang behält seinen bisherigen RootEncoder-Vertrag.
Die vorangegangenen drei Lint-Fehler (`UseKtx`) wurden korrigiert; die breitere
Engine-/Szenen-Verifikation und der erneute Lint werden gesondert erfasst.


Normale primäre Filme enden wie im bisherigen Video-Player nach einem Durchlauf;
Replays und unabhängige Video-Layer wiederholen sich. Auch der reine Audio-Pfad
eines ausgeblendeten primären Films wird nicht automatisch wiederholt. Ein
normal beendeter beziehungsweise variabler Video-Track hält seinen letzten
nutzbaren Frame. Explizite MediaPlayer-Fehler entwerten ihn und lösen Recovery
aus. Der Initial-Frame-Timeout bleibt aktiv, damit ein defekter Start nicht als
gesunde Dauerquelle behandelt wird.

Der erste breitere Handoff-Lauf bestand 387 Engine-/Szenentests. Nach Ergänzung
der Zielstatus-Regression bestanden 388 Tests. Die abschließende Verifikation
einschließlich des Movie-EOF-Vertrags wird nachfolgend gesondert dokumentiert.

Der abschließende breite Lauf bestand **390 Engine-/Szenentests**, darunter
**233 Szenentests**, ohne Fehler oder Skips. Die Datei-Handoff-Fälle prüfen
Stop vor nativer Vorbereitung, abgewehrte alte Transport-Callbacks und frische
Legacy-Vorbereitung erst beim späteren Start. Movie-EOF und Replay-/Layer-Loops
sind getrennt geprüft. Danach kompilierten App und instrumentierte Quellen;
Streaming-Lint bestand ohne Fehler/Warnungen mit dem bestehenden Autoboxing-Hint.
I18n- und Markdown-Anker-Guards bestanden ebenfalls. Vier bereits ausgeführte
Tests prüfen die unveränderten finalen Manifest-Eigenschaften. Geräte-/A/V-/
Performance-Messungen und vollständiger App-/Release-Gate bleiben offen.

Der vollständige Pre-Push-Gate nach Code-/Parity-Commit bestand die Repository-
Selbsttests und scheiterte an **sechs von 471 unveränderten Chat-Tests**. Alle
sechs XML-Fehler belegen Conscrypts fehlende `libpthread.so.0` auf Termux. Kein
Test wurde abgeschwächt. Der breitere 390-Test-Lauf und der separat bestandene
Streaming-Lint sind weiterhin kein vollständiger App-/Release-/Geräte-Nachweis.


### Primäre Ebenen und wiederverwendbare Capture-Konfigurationen

Globale Eingangswechsel binden jetzt ausdrücklich markierte Ebenen um, statt
Typ oder Locator einer bestehenden SourceId zu verändern. Duplikate behalten
`followsPrimaryInput`; unabhängige Ansichten derselben Kamera behalten ihre
Quelle und Transformation. `primaryInputConfigured` unterscheidet alte, noch
unmarkierte Kompositionen von bewusst gelöschten primären Ansichten. Nur alte
Kompositionen übernehmen die bisherige Eingangsquelle automatisch. Nach einer
bewussten Löschung erzeugt der nächste globale Wechsel eine neue untere Ebene.

Kamerawechsel suchen zuerst eine vorhandene physische Capture-Konfiguration.
Camera2- und externe HAL-Aliase können dieselbe Kamera wiederverwenden; rohe
USB-Locators bleiben getrennt. Die Standardkamera wird über ihre konkrete ID
aufgelöst. Nach einem Berechtigungsdialog wird diese Auflösung wiederholt, weil
die Kamera-ID vorher fehlen kann. Der ausstehende Quelldeskriptor überlebt eine
Activity-Neuerstellung; Sitzungen und Grants werden dabei nicht gespeichert.
Ein explizites Hinzufügen versucht auch eine zuvor durch fehlende Berechtigung
blockierte Quelle erneut. Bereits laufende Kameras aktualisieren beim Wechsel
der primären Ansicht die Steuerungs- und Gesichtserkennungsanbindung ohne
erneutes Öffnen des Geräts. Screen-Consent bevorzugt ebenfalls die gebundene
primäre Ansicht gegenüber unbenutzten Quellen im Pool.

Unbenutzte Eingangsquellen bleiben für spätere Wechsel im Pool. Bei acht Quellen
kann nur die ersetzte, anschließend unreferenzierte Eingangsquelle Platz machen;
unabhängig verwendete und andere gespeicherte Quellen werden nicht verdrängt.
Die Ebenengrenze bleibt bestehen. Beim Zurückwechseln von einem Film zu einer
bereits eingeblendeten Kamera entsteht dadurch kein zweiter Producer für
dasselbe physische Gerät.

`SceneSource.videoLoop` trennt einmalige Filme und wiederholende Videos auch
bei identischer URI. Producer-Vergleich, Transition-Prüfung und Audioauswahl
berücksichtigen diesen Modus. Ein globaler Filmwechsel verändert damit keine
unabhängige wiederholende Ansicht derselben Datei. Die neuen Felder sind
optionale, voreingestellte Ergänzungen von Schema 2; alte JSON-Dokumente bleiben
lesbar. Legacy-Replays erhalten ausdrücklich den Wiederholungsmodus. Die
bestehende Schreibsperre für unbekannte zukünftige Schemas bleibt erhalten.

Der Regressionstest für Film → vorhandene Kamera → Film wurde zunächst gegen
die alte Implementierung ausgeführt und scheiterte an zwei statt einem
Kamera-Producer (`vivid-306-source-reuse-red.log`). Die Negativkontrollen blieben
grün. Die neuen Tests prüfen zusätzlich unabhängige Ansichten, Duplikate,
bewusste Löschung, Poolgrenzen, Kamera-Aliase, Playback-Modi, Consent-Ziel und
die Wiederherstellung ausstehender Kameraauswahl. Der abschließende Lauf bestand 408 Engine-/Szenentests, 55 Domain-Tests und
14 SceneRepository-Tests ohne Fehler oder Skips. Standard-Debug-App und
instrumentierte Testquellen kompilierten; Streaming-Lint bestand separat.
I18n- und Markdown-Anker-Prüfungen bestanden ebenfalls. Die Ergebnisse stehen
in `vivid-306-source-binding-latest-tests-retry.log` und
`vivid-306-source-binding-build-lint.log` außerhalb des Repositorys. Dies ist
weiterhin kein Nachweis tatsächlicher Geräte-Captures oder GLES-Ausführung.


Der vollständige Pre-Push-Lauf für `cbfc673` bestand die Repository-Selbsttests,
endete aber in `:feature-obs-control:testDebugUnitTest` mit 21 Fehlern unter
48 Tests. Alle 21 XML-Fehler enthalten Conscrypts `UnsatisfiedLinkError` wegen
des auf Termux fehlenden `libpthread.so.0`. Die OBS-Dateien sind in diesem
Checkpoint unverändert. Das Protokoll ist
`vivid-306-source-binding-pre-push.log`. Der Gate ist damit ausdrücklich rot;
die separat bestandenen Tests und Streaming-Lint ersetzen weder den vollständigen
Gate noch Geräte-/Performance-Prüfungen. Kein Test und keine Sicherheitsregel
wurden dafür abgeschwächt.


### Bot-Befunde für PR #330

Der Review-Stand `8dcf88b1` enthielt 23 offene Sonar-Befunde und 59 eingeführte
DeepSource-Befunde. Beide vollständigen Berichte wurden vor der Überarbeitung
abgerufen; DeepSources erste HTML-Seite allein zeigte nur 15 von 59 Einträgen.
Die Überarbeitung trennt Texture-Reconciliation, Frame-Sampling und Health-
Überwachung, USB-Neukonfiguration und Rollback sowie Datei-Playback-Callbacks.
Die Engine trennt Szenenzuordnung, Legacy-Quellenwechsel, Kamera-Steuerung und
Dateiaudio-Auswahl. Im Editor liegen Permission-Aktionen, Picker, Ebenen-
Eigenschaften, Zeichenoperationen und Gestenverarbeitung in eigenen Funktionen.

Wiederholte Fixture-IDs und URIs sind benannte Konstanten. Private Datenhalter
sind explizite Datenklassen; unnötige Boolean-Zweige und leere Catch-Blöcke sind
entfernt. Cleanup-Fehler werden protokolliert. PCM-Buffer-Helfer verwenden bewusst
den `java.nio.Buffer`-Methodenvertrag, damit die API-21-kompatiblen Deskriptoren
beim Entfernen expliziter Casts erhalten bleiben. Der Video-Worker wartet trotz
Unterbrechung auf die bereits gepostete Operation und stellt den Interrupt-Status
anschließend wieder her; Fehler des Workers werden weiterhin mit ihrer Ursache
weitergereicht. Save-Cancellation und nicht wiederherstellbare Errors werden
weitergeworfen. Keine Analyse-Regel wird dafür unterdrückt.

Die lokale Regression bestand 462 Engine-/Szenen-/ViewModel-Tests, 55 Domain-
Tests und 14 SceneRepository-Tests ohne Fehler oder Skips. Auch der abschließende
Lauf nach der letzten Render-Routing-Extraktion bestand; instrumentierte Quellen
kompilierten. Protokolle: `vivid-330-bot-tests.log` und
`vivid-330-bot-final-tests.log` außerhalb des Repositorys. Die
Standard-Debug-App kompilierte ebenfalls; Streaming-Lint bestand mit null Fehlern
und Warnungen sowie dem bestehenden Autoboxing-Hinweis
(`vivid-330-bot-build-lint.log`). I18n- und Markdown-Anker-Prüfungen bestanden.
Vollständiger Gate und neue Bot-Analyse werden am veröffentlichten Commit ergänzt.


Der vollständige Gate für `73569c5` bestand die Repository-Selbsttests und
endete in `:feature-chat:testDebugUnitTest`: sechs Fehler unter 471 Tests.
Alle sechs XML-Fehler zeigen erneut Conscrypts fehlendes `libpthread.so.0` auf
Termux; die Chat-Implementierung ist unverändert. Protokoll:
`vivid-330-bot-pre-push.log`. Der vollständige Gate bleibt rot, unabhängig von
den separat bestandenen 531 Tests, der App-/Instrumentierungs-Kompilation und
Streaming-Lint. Es wurde keine Test- oder Analyse-Regel gelockert.


Die erste Neuanalyse am veröffentlichten `b7288468` meldete noch zwei Sonar-
und elf DeepSource-Befunde; alle ursprünglichen Komplexitätsbefunde waren
beseitigt. Der Folgepatch bündelt USB-Resultat und ausstehende Identität in einem
Wertobjekt, entfernt den unbenutzten Picker-Parameter und die Namensverschattung.
Exception-Klassifizierung ist von den Catch-Blöcken getrennt. USB-Rollback erhält
eine nachweislich vorhandene frühere Konfiguration erst nach dem Stop-/Null-Guard.
Die nullable Rückgabetypen von aktiver Replay-Datei und selektierter Ebene werden
unverändert durch Kotlin inferiert. Ein fehlender GL-Owner ist beim Rebuild ein
explizit unterstützter Standardfall; der vorhandene Null-Owner-Test prüft auch den
parameterlosen Aufruf. Diese Bereinigung ändert keine Grant- oder Capture-Regel.


Der Folgepatch bestand 479 Engine-/Szenen-/ViewModel-/Privacy-Tests ohne Fehler
oder Skips; die unveränderten Domain-/Repository-Fälle waren mit 55/14 Tests
bereits bestanden. Instrumentierte Quellen und Standard-Debug-App kompilierten.
Streaming-Lint bestand erneut mit null Fehlern und Warnungen (ein bestehender
Hinweis). I18n und Markdown-Anker bestanden ebenfalls. Protokolle:
`vivid-330-bot-followup-tests.log` und
`vivid-330-bot-followup-build-lint.log` außerhalb des Repositorys.


Auch der vollständige Gate für `0a7b42f` bestand die Repository-Selbsttests und
stoppte bei denselben sechs von 471 unveränderten Chat-Tests. Alle sechs frischen
XML-Fehler enthalten Conscrypt und das auf Termux fehlende `libpthread.so.0`.
Protokoll: `vivid-330-bot-followup-pre-push.log`. Der vollständige Gate bleibt
rot; der Folgepatch ist separat durch Tests, App-/Instrumentierungs-Kompilation
und Streaming-Lint geprüft. Keine Test- oder Analyse-Regel wurde abgeschwächt.


Am veröffentlichten `2bc54bee` blieben je ein Sonar- und DeepSource-Befund.
Die letzte Bereinigung trennt die Auflösung eines optional vorhandenen GL-Owners
von der synchronisierten Verarbeitung mit vorhandenem Owner. Engine-Aufrufe
verwenden ihren bisherigen Owner-Snapshot; fehlende Owner erzeugen keine GL-
Kommandos. Der Null-Owner-Test prüft dies ausdrücklich über die leere Kommandoliste.
Der Replay-Getter nennt seinen nullable Typ und die Rückkehr bei fehlender Quelle
explizit; die existierenden Null-/Rollback-Tests bleiben erhalten. Der komplette
gezielte Lauf bestand erneut 479 Tests ohne Fehler oder Skips, instrumentierte
Quellen kompilierten (`vivid-330-bot-null-contract-tests.log`).


Auch diese Revision bestand die Standard-Debug-Kompilation und Streaming-Lint
mit null Fehlern und Warnungen (ein bestehender Hinweis), dazu I18n und
Markdown-Anker. Protokoll: `vivid-330-bot-null-contract-build-lint.log`.


Der vollständige Gate für `6a663d3` bestand die Repository-Selbsttests und
stoppte wieder bei sechs von 471 unveränderten Chat-Tests. Alle sechs frischen
XML-Fehler zeigen Conscrypts auf Termux fehlendes `libpthread.so.0`.
Protokoll: `vivid-330-bot-null-contract-pre-push.log`. Der vollständige Gate
bleibt rot; die getrennten Tests und Streaming-Lint sind kein Ersatz für einen
vollständigen Lauf auf einem unterstützten Runner.


## Integration mit develop (2026-10-26)

Der Konfliktlauf integriert develop bis `8064da7a`, einschließlich des
experimentellen Kamera-Pfads aus #322, der neuen Statusanzeige und der
Abhängigkeitsupdates. Szenen behalten ihren eigenen Encoder-/Capture-Pfad;
Legacy-Kamera-Ausgaben behalten asynchrone Vorbereitung, Abbruch und
High-Speed-Fallback. Der Preview-Halbdrehungs-Fix gilt im Legacy-Zweig,
während Szenen ihre Canvas-Geometrie behalten. Beide Sprachsatz-Erweiterungen
und die reflektierten USB-/RootEncoder-Verträge bleiben erhalten.

Im experimentellen Pfad wird der stabile SceneFilterChainRender an Position
null eingefügt. Effektwechsel aktualisieren nur dessen Kinder, ohne externe
Overlays oder die aktive Capture-Kette zu entfernen. Der übernommene Test
prüft Overlay-Identität, Kinder-Reihenfolge und genau eine Root-Einfügung.
I18n, Markdown-Anker und der kombinierte Emulator-Matrix-Selbsttest sind grün.

Der Integrationslauf deckte zusätzlich den automatisch gemergten
Recording-Helper auf: Er greift jetzt über das ViewModel auf die Engine zu,
um die Szenen-Kameraberechtigung zu prüfen. Die Plain-JVM-Szenenfixture
injeziert die monotone Uhr für den übernommenen Live-Timer und prüft dessen
Start und Reset. Vorher wurden vier Fälle durch den ungemockten SystemClock-
Aufruf rot. Danach bestanden 493 gezielte Tests ohne Fehler oder Skips,
Standard-App und Android-Instrumentierungsquellen kompilierten
(`vivid-330-merge-verified-tests.log`).

Während der Validierung rückte develop bis `54e38d12` vor. Der zusätzliche
Merge übernimmt nur Sentry-/CI-Skripte und Dokumentation; die geprüften
Android-Quellen bleiben gleich. Beide Parity-Protokolle bleiben erhalten.

Streaming-Lint bestand mit null Fehlern/Warnungen und einem bestehenden
Hinweis (`vivid-330-merge-lint.log`). Der vollständige pre-push bestand
die Repository-Selbsttests und stoppte bei sechs von 475 Chat-Tests. Alle
sechs frischen XML-Fehler enthalten Conscrypt und das auf Termux fehlende
`libpthread.so.0` (`vivid-330-merge-pre-push.log`). Der Gesamt-Gate bleibt
rot; keine Tests oder Regeln wurden abgeschwächt. Der dokumentierte
einmalige Hook-Bypass wird erst nach diesem vollständig beendeten Lauf
verwendet.


## Letzter Sonar-Befund vor Review (2026-10-26)

Sonar meldete in `attachPreviewIfRunning` eine kognitive Komplexität von
22 statt höchstens 15 (`kotlin:S3776`, `AaF4RzojzN5-6qs1vs4s`). Die Legacy-
Orientierung liegt nun in `configureLegacyPreviewOrientation`; Aufrufreihenfolge,
Halbdrehung vor Filtern, externe Capture-Orientierung und Preview-Crop bleiben
gleich. Szenen verwenden weiterhin ihre Canvas-Geometrie. Keine Analyse-Regel
wird unterdrückt.

Die abschließende Helper-Revision bestand erneut 493 Tests ohne Fehler/Skips
(`vivid-330-last-sonar-tests.log`) und Streaming-Lint mit null Fehlern/Warnungen
und einem bestehenden Hinweis (`vivid-330-last-sonar-lint.log`). Der vollständige
pre-push bestand die Repository-Selbsttests und stoppte erneut bei sechs von
475 Chat-Tests; alle frischen Fehler zeigen Conscrypts auf Termux fehlendes
`libpthread.so.0` (`vivid-330-last-sonar-pre-push.log`). Der vollständige Gate
bleibt rot; keine Tests oder Analyse-Regeln wurden abgeschwächt.


## CI-Screen-Fixtures (2026-10-26)

Run `40333111851`, Job `121611016077`, meldete 40 Fehler bei 860
Streaming-Tests. Die heruntergeladenen XML-Artefakte zeigen in beiden
Screen-Testklassen denselben ClassCast von `Object` zu `SceneEditorState`.
Die relaxed ViewModel-Mocks lieferten für den neu gesammelten generischen
StateFlow keinen typisierten Zustand. Beide Fixtures liefern nun echte
MutableStateFlows für den geschlossenen Editor und die leere Producer-Map;
die Permission-Fixture liefert außerdem den expliziten Full-Frame-Privacy-
Zustand. Produktionscode und bestehende Render-/Permission-Assertions bleiben
unverändert.

Die korrigierten Unit-Test-Quellen kompilierten
(`vivid-330-ci-fixture-compile.log`). Der lokale pre-push bestand wieder
die Repository-Selbsttests und stoppte bei sechs von 475 Chat-Tests; alle
frischen XML-Fehler zeigen Conscrypts fehlendes `libpthread.so.0` auf Termux
(`vivid-330-ci-fixture-pre-push.log`). Die UI-Ausführung wird deshalb im
anschließenden Linux-CI-Lauf verifiziert; kein lokaler UI-Erfolg wird behauptet.
