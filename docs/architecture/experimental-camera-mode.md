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
Der gemeinsame [Szenenlayer](scenes-widgets.md) komponiert aktivierte Widgets
auch im experimentellen Modus in Vorschau, Aufnahme und Übertragung. Lokale
Bedienelemente und Statusanzeigen bleiben außerhalb des codierten Videos.

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
lokale Statusanzeige zeigt gemessene finale Encoder-FPS, Sendebitrate eines
aktiven Ziels und Zeit seit der ersten erfolgreichen Verbindung. Die drei
Indikatoren lassen sich unter Interface → Local overlays unabhängig ausblenden.
Ein ausstehender FPS-Messwert bleibt als ausstehend gekennzeichnet. Die Anzeige
wird nicht ins gesendete Video eingebrannt. Capture-Fallbacks und Fehler erscheinen
als zentrale, per Tap oder nach drei Sekunden schließende Mitteilungen.
Der monotone Session-Start bleibt beim Neuaufbau der UI erhalten, solange ein
Ziel live ist; nach Stopp/Verbindungsende startet der Timer bei der nächsten
Verbindung neu. Ein manueller Stopp über den Preview-Button braucht eine
Bestätigung; Abbrechen, Zurück und ein Klick außerhalb lassen den Stream laufen.
`60 FPS — experimentell` verspricht keine konstanten 60 FPS unter Last.
MP4/RTMP-PTS und vollständige Decodes dienen zusätzlich der Geräteverifikation.

## RootEncoder-Vertrag

RootEncoder 2.7.5 bietet im `Camera2Base` keinen öffentlichen External-Capture-
Hook. `RootEncoderExternalCapture` kapselt daher drei private Verträge:
`cameraManager`, `prepareGlView(...)` und `Camera2ApiManager.isRunning`.
Der Adapter konfiguriert denselben GL-Renderer und unterdrückt anschließend
`openLastCamera`, solange der alternative Capture-Pfad die Kamera besitzt.
Der Decoder liefert bereits native Landscape-Pixelachsen. Der Adapter setzt
`setCameraOrientation(0/90/180/270)` nach seiner Vorbereitung und die Engine erneut nach
`startRecord`/`startStream`: RootEncoder ruft bei diesen Starts intern nochmals
`prepareGlView` auf und setzt sonst die Camera2-Texturrotation von 270 Grad.
Diese Rotation auf Decoder-Frames verdrehte und verzerrte die GL-Eingabe.
Portrait und Landscape verwenden die ausgewählten codierten Pixelachsen.
Die Preview passt den vollständigen komponierten Frame ohne zusätzliche
Textrotation in ihre verfügbare Fläche ein.
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

`StreamStatusOverlayInstrumentedTest` prüft mit `runStreamHud=true` und einer
expliziten Loopback-RTMP-Adresse die lokalen Statusindikatoren, den laufenden Timer,
das Weiterlaufen nach Abbrechen und den bestätigten Stopp. Er verwendet keine
gespeicherten Streamziele und ändert keine gespeicherten Einstellungen.

## Latenz und Ausrichtung

Der Bridge-Encoder erhält `KEY_OPERATING_RATE=captureFps`, Realtime-Priorität
und einen optionalen Null-Latenz-Hinweis. `KEY_FRAME_RATE=60` allein beschreibt
die High-Speed-Eingabe nicht. Der optionale Hinweis wird nicht als bestätigte
Hardware-Eigenschaft ausgegeben.

Die Bridge leert beide Codecs in begrenzten, nicht blockierenden Batches. Vor
GL hält sie höchstens einen neuen dekodierten Frame und einen bereits an die
Surface übergebenen Frame. Die ursprüngliche PTS muss in `SurfaceTexture`
angekommen sein, bevor ein weiterer Frame übergeben wird. Bei GL-Rückstau wird
nur der wartende dekodierte Frame durch den neuesten ersetzt; komprimierte
P-Frames werden vollständig dekodiert, damit ihre Referenzen erhalten bleiben.
Filter und Overlays bleiben aktiv. Keine PTS wird auf die aktuelle Zeit umgeschrieben.

Die Engine wendet Portrait und den halben Umlauf für Reverse-Landscape auf
die Kameraquelle vor den Filtern an. Der externe Adapter merkt sich die Rotation
und stellt sie nach einem Encoder-Start wieder her. Widget-Text bleibt aufrecht.
`CameraOrientationInstrumentedTest` prüft die Eingabematrix und Preview-Übergänge;
`CameraOutputOrientationInstrumentedTest` prüft mit `runCameraOutputOrientation=true`
und `fps=30` oder `fps=60` die lokalen MP4-Pixelachsen und einen dekodierten
Szenenmarker in Portrait und Landscape, ohne eine externe Übertragung zu starten.

`CameraLatencyInstrumentedTest` ist opt-in (`runCameraLatency=true`, ausschließlich
Loopback-RTMP). Er misst das Frame-Alter am GL-Eingang nach fünf Sekunden Anlauf,
vergleicht Standard und Experimentell und verlangt standardmäßig P95 <250 ms.
Er prüft den Sensor-Zeitquellentyp und verwendet die passende Uhr. Dies ist kein
Motion-to-Photon- oder Ende-zu-Ende-Streaming-Latenztest.

## Bekannte Grenzen

Die High-Speed-Bridge benötigt zusätzliche Hardware-Codecs. 4K und langfristige
Thermalstabilität sind nicht bestätigt. Geräteprüfungen auf POCO X7 Pro und
Samsung Galaxy S21 belegen beide Kamerawege mit GL-Komposition, aber keine
konstanten 60 FPS unter beliebiger Last. Der Szenenlayer aktualisiert unabhängig
von der Video-Framerate mit höchstens 30 Hz. Capability-Angaben ersetzen weder
den gemessenen Durchsatz noch die Prüfung dekodierter Empfangsframes.
