# Experimenteller Kameramodus mit GL

In **Einstellungen → Kamera-Steuerung → Kameramodus** stehen `Standard` und
`60 FPS — experimentell` zur Auswahl. Standard bleibt der bestehende normale
Camera2-Pfad. Die Auswahl wird in DataStore gespeichert und vor Aufnahme sowie
vor lokalen und entfernten Streamstarts gelesen.

Der experimentelle Pfad verwendet ausschließlich gemeldete feste High-Speed-
Bereiche ab 120 FPS und Hardware-Codecs:

```text
Camera2 High-Speed → AVC Surface-Encoder (max. 60 FPS)
                 → Hardware-Decoder → bestehendes Vivid-GL
                 → finaler Encoder → MP4 und/oder RTMP
```

GL-Effekte und Overlays werden nicht für einen direkten Encoder-Pfad umgangen.
Im experimentellen Modus ersetzt der PrivacyComposer nur seine eigenen Effekte;
extern hinzugefügte GL-Overlays bleiben bei einem Ketten-Rebuild erhalten.
Die vorhandenen Compose-Overlays bleiben ebenfalls im bestehenden UI-Pfad.
Die Einstellung fügt keine neue Übertragung von bisher ausschließlich in der
Vorschau dargestellten Compose-Widgets in das codierte Video hinzu.

## Zulassung und Lebenszyklus

- Android 10 oder neuer; Kamera mit `CONSTRAINED_HIGH_SPEED_VIDEO` und passender
  Auflösung plus festem FPS-Bereich. Der kleinste passende Bereich wird gewählt.
- Die erste Version unterstützt vorhandene 60-FPS-Ausgabe-Presets: 1080p
  (`FHD30` wird bei gleicher Auflösung auf `FHD60` angehoben) und 4K, sofern alle
  Anforderungen tatsächlich erfüllt sind. 720p/480p-Presets sind gesperrt.
- Hardware-AVC-Encoder und -Decoder müssen die Auflösung bei 60 FPS unterstützen;
  der finale Codec wird ebenfalls geprüft. Gleichzeitige Codec-Verfügbarkeit
  lässt sich erst während der Vorbereitung prüfen.
- Umschalten ist während Vorbereitung, Aufnahme und Stream gesperrt. Eine
  laufende Aufnahme und ein später gestarteter RTMP-Stream teilen denselben
  Capture-Pfad. Das Stoppen eines Verbrauchers lässt den anderen weiterlaufen.
- Vorbereitung läuft außerhalb des UI-Threads und wartet begrenzt auf echte
  dekodierte Kamerabilder. Ein Stop während Vorbereitung verhindert einen
  nachträglichen Start. Ungeeignete Konfigurationen oder vorbereitungsbedingte
  Fehler wechseln sichtbar zum normalen Camera2-Pfad mit kompatiblem Profil.
- Ein Fehler nach dem Start stoppt Aufnahme und Stream mit sichtbarer Meldung;
  es gibt keinen verdeckten Pfadwechsel während einer laufenden Sitzung.
- Die Kamera-Steuerung während High-Speed-Capture ist eingeschränkt und im
  UI beschrieben. Bestehende Zoom/Fokus/Torch/Belichtungs-Aufrufe werden nicht
  an den inaktiven normalen Camera2-Manager weitergereicht.

## Angefordert, angewendet und gemessen

Gespeicherter Modus, angewendeter Capture-Modus, tatsächlich vorbereitetes
Encoder-Profil und gemessene finale Encoder-FPS sind getrennte Zustände. Die
FPS-Anzeige nutzt RootEncoders Callback für tatsächlich ausgegebene Frames
pro Realzeitfenster; sie übernimmt keine Kamera- oder Encoder-Metadaten.
`60 FPS — experimentell` verspricht daher keine konstanten 60 FPS unter Last.
MP4/RTMP-PTS und vollständige Decodes dienen zusätzlich der Geräteverifikation.

## RootEncoder-Vertrag

RootEncoder 2.7.5 bietet im `Camera2Base` keinen öffentlichen External-Capture-
Hook. `RootEncoderExternalCapture` kapselt daher drei private Verträge:
`cameraManager`, `prepareGlView(...)` und `Camera2ApiManager.isRunning`.
Der Adapter konfiguriert denselben GL-Renderer und unterdrückt anschließend
`openLastCamera`, solange der alternative Capture-Pfad die Kamera besitzt.
Der Decoder liefert bereits native Landscape-Pixelachsen. Der Adapter setzt
`setCameraOrientation(0/180)` nach seiner Vorbereitung und die Engine erneut nach
`startRecord`/`startStream`: RootEncoder ruft bei diesen Starts intern nochmals
`prepareGlView` auf und setzt sonst die Camera2-Texturrotation von 270 Grad.
Diese Rotation auf Decoder-Frames verdrehte und verzerrte die GL-Eingabe.
Display-Rotation und Center-Crop bleiben im separaten Preview-Schritt.
Consumer-R8-Regeln sichern die reflektierten Namen. Bei einem Versionswechsel
müssen diese Verträge und die Geräteprüfung erneut verifiziert werden; ein
inkompatibler Vertrag fällt während Vorbereitung auf den Standardpfad zurück.

## Tests

Unit-Tests prüfen Capability-Auswahl, DataStore-Roundtrip, Vorbereitung vor
Transport, Ressourcenfreigabe und Standard-Fallback, Stop während ausstehender
Vorbereitung, Modussperre sowie Erhalt externer GL-Overlays. Compose-Tests prüfen
Sperren, Nichtverfügbarkeit und die Anzeige von Fallback und gemessenen FPS.

`ExperimentalCameraInstrumentedTest` ist ein expliziter Opt-in-Test mit
`runExperimentalCamera=true` und einem `localRtmpUrl` unter `127.0.0.1`.
Er zeichnet mit Audio auf, fügt Graustufen und einen GL-Text-Overlay hinzu,
startet lokales RTMP auf derselben Aufnahme, stoppt beide Verbraucher getrennt
und startet anschließend eine Standardaufnahme. Gespeicherte externe
Streamziele werden nie verwendet und App-Daten nicht gelöscht.

## Gerätebeleg vom 2026-10-06

POCO X7 Pro (2412DPC0AG, Android 16), Hauptkamera, 1080p/H.264,
High-Speed-Capture 120 → Decoder → Vivid-GL → finaler Encoder mit Audio:

| Lauf | MP4-FPS | RTMP-FPS | Vollständig dekodierte Frames MP4 / RTMP | Benachbarte Duplikate |
|------|---------|----------|----------------------------------------|----------------------|
| 30 Sekunden | 57,33 | 57,33 | 1774 / 1715 | 0 / 0 |
| 120 Sekunden | 52,58 | 52,59 | 6372 / 6311 | 0 / 0 |

FPS wurden nach fünf Sekunden Anlauf anhand der Video-PTS berechnet;
Metadaten wie `60/1` wurden nicht als Messwert übernommen. Die letzten
30 Sekunden des langen Laufs lagen bei 52,51 FPS. Beide Tracks enthielten
AAC mit 32 kHz; Graustufen und ein roter GL-Text-Overlay waren aktiv.
Der anschließende Wechsel auf Standard und eine reine Videoaufnahme waren
erfolgreich. **Konstante 60 FPS auf POCO sind damit nicht bestätigt.**
Samsung und 4K wurden mit dieser Vivid-Integration noch nicht auf einem
Gerät geprüft. Es gibt noch keinen Langzeit-/Thermal-Nachweis.

Alle Modul-Unit-Tests, Standard-App-Tests, Lint und FOSS-Debug-/R8-Release-
Build bestanden. Das vollständige `scripts/pre-push.sh` stoppt zuvor bei
`test_contributors_reminder.sh` C18 (`Mutation konnte nicht angewendet werden`);
derselbe Fehler wurde auf dem unveränderten Ausgangsbranch reproduziert.
Die betroffenen Workflow-/Contributor-Dateien wurden nicht verändert.

Rohvideos, vollständige Decode-Hashes und Testprotokolle liegen lokal unter
`build/reports/experimental-camera-mode-2026-10-06/` (nicht eingecheckt).

## Orientierungsregression

`CameraOrientationInstrumentedTest` ist ein separater Opt-in-Test mit
`runCameraOrientation=true` und einem expliziten Loopback-RTMP-Ziel. Er prüft
Standard und Experimentell in Portrait, Landscape und Reverse-Landscape,
wartet auf echte Encoder-FPS und kopiert die Preview-Surface über PixelCopy
vor Start, im Stream und nach Stop. Die experimentelle Input-Rotationsmatrix
muss nach Start identisch bleiben. Auf dem vorherigen APK schlug dieser
Regressionstest fehl; nach der Korrektur bestehen alle sechs Übergänge auf
POCO. Die Preview-Bildpaare wurden zusätzlich visuell geprüft.

## Latenz und umgekehrtes Landscape (2026-10-06)

Auf POCO lagen die bereits am Capture-Encoder ausgegebenen Frames zuvor rund
830 ms zurück; die GL-Queue war dabei fast leer. `KEY_FRAME_RATE=60` allein
beschreibt nicht die 120-FPS-Eingabe. Der Bridge-Encoder erhält jetzt zusätzlich
`KEY_OPERATING_RATE=captureFps`, Realtime-Priorität und einen optionalen
Null-Latenz-Hinweis. Die [Android-Referenz](https://developer.android.com/reference/android/media/MediaFormat#KEY_OPERATING_RATE)
beschreibt Operating Rate ausdrücklich für High-Speed-Eingaben mit abweichender
Wiedergaberate. Der POCO-Encoder meldet weiterhin `latency=2`: der optionale
Null-Latenz-Hinweis wird nicht als bestätigte Eigenschaft ausgegeben.

Die Bridge leert beide Codecs in begrenzten, nicht blockierenden Batches. Vor
GL hält sie höchstens einen neuen dekodierten Frame und einen bereits an die
Surface übergebenen Frame. Die ursprüngliche PTS muss in `SurfaceTexture`
angekommen sein, bevor ein weiterer Frame übergeben wird. Bei GL-Rückstau wird
nur der wartende **dekodierte** Frame durch den neuesten ersetzt; komprimierte
P-Frames werden vollständig dekodiert, damit ihre Referenzen erhalten bleiben.
Filter und Overlays bleiben aktiv. Keine PTS wird auf die aktuelle Zeit umgeschrieben.

Der zweite Orientierungsfehler betraf die Ausgabe bei Reverse-Landscape:
Preview drehte um 180 Grad, der Stream nicht. Der halbe Umlauf wird jetzt auf
die Kameraquelle **vor** den Filtern angewendet, bei Standard und Experimentell.
Die Preview kompensiert denselben Umlauf; Overlay-Text wird nicht auf den Kopf
gedreht. Der externe Adapter merkt sich 0/180 Grad und stellt diesen Wert auch
nach einem späteren Encoder-Start wieder her. Native Landscape-Ausgabe wird bei
Experimentell auch ohne gespeichertes Encoder-Preset korrekt berücksichtigt.

`CameraLatencyInstrumentedTest` ist opt-in (`runCameraLatency=true`, ausschließlich
Loopback-RTMP). Er misst den Frame-Alter am GL-Eingang nach fünf Sekunden Anlauf,
vergleicht Standard und Experimentell und verlangt standardmäßig P95 <250 ms.
Camera2-SurfaceTexture verwendet bei REALTIME-Sensoren die Boot-Zeit, ein direkt
an Camera2 gegebener Encoder-Surface die monotone Zeit. Der Test prüft den
Sensor-Zeitquellentyp und verwendet für jede Route die passende Uhr; siehe
[Android CameraX Timebase](https://android.googlesource.com/platform/frameworks/support/+/d62cb99c5e97384ccd6d0a97707f656ffb4f35cf/camera/camera-video/src/main/java/androidx/camera/video/VideoCapture.java).
Dies ist kein Motion-to-Photon- oder Ende-zu-Ende-Streaming-Latenztest.

POCO, 1080p/H.264, jeweils 60 Sekunden mit Graustufen, rotem GL-Text und einer
expliziten GL-Pause von 250 ms:

| Route | Median Frame-Alter | P95 | Tatsächlich dekodierte RTMP-FPS |
|-------|--------------------|-----|-------------------------------|
| Standard | 112 ms | 132 ms | 29,90 |
| Experimentell | 102 ms | 117 ms | 59,29 |

Nach der erzwungenen Pause fiel der experimentelle Frame-Alter innerhalb von
31 ms nach der ersten verzögerten Messung wieder unter 150 ms. Der gleiche
Regressions-Test auf der alten Bridge schlägt mit P95 **950 ms** fehl.
Eine zusätzliche 30-Sekunden-Aufnahme mit Audio und gleichzeitigem RTMP hielt
58,01 FPS in MP4 und 57,93 FPS im RTMP-Segment; beide H.264/1080p plus AAC/32 kHz.
Vollständige Decodes: 1792 MP4-, 1728 RTMP-Audio- und 3449 RTMP-Latenztest-Frames,
keine benachbarten Hash-Duplikate. **Konstante 60 FPS unter beliebiger Last sind
weiterhin nicht bestätigt.** Die Verbesserung betrifft gemessene Frame-Frische.

Der Reverse-Landscape-Gerätetest ist auf dem alten Orientierungsverhalten rot
und nach dem Fix grün. Sechs Idle → Stream → Idle-Übergänge, PixelCopy-Vorschauen
und tatsächlich empfangene RTMP-Bilder wurden geprüft. Das alte Reverse-Bild
passt erst nach 180 Grad zur Preview, die korrigierten Landscape-Bilder beider
Modi passen ohne weitere Drehung. Lokale Rohdaten und Messbericht:
`build/reports/camera-latency-2026-10-06/` (nicht eingecheckt).
