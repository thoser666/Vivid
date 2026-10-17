# USB-Kamera und HDMI-Capture-Card (#305)

Vivid bietet eine externe USB/UVC-Videoquelle über Android USB-Host/OTG an.
Es gibt genau eine aktive externe Kamera. USB-Audio und gleichzeitige
Videoebenen gehören nicht zu dieser Implementierung. **Hardwarefreigabe steht
noch aus:** In der Entwicklungsumgebung war kein Android-/USB-Gerät verfügbar.
Die unten aufgeführten Übertragungs- und Aufnahmeprüfungen sind nicht ausgeführt.

## Bedienung

1. USB-Webcam oder UVC-HDMI-Capture-Card über OTG anschließen. Bei höherem
   Strombedarf einen versorgten USB-Hub verwenden.
2. Unter Streaming-Steuerung **USB-Kamera / Capture-Card** öffnen, das konkrete
   Gerät wählen und **Verbinden** drücken. Erst diese Aktion fragt erforderliche
   Kamera- und USB-Berechtigungen ab. Ein neu angeschlossenes Gerät übernimmt
   niemals automatisch die aktive Quelle.
3. Nach erfolgreichem Öffnen einen tatsächlich gemeldeten Modus wählen
   (Auflösung, Bildrate, MJPEG/YUY2). Der Wunschmodus wird beim nächsten
   Verbinden erneut geprüft. Nicht verfügbare Wünsche erzeugen einen Hinweis;
   der tatsächlich ausgehandelte Modus bleibt sichtbar.
4. Dialog schließen: dieselbe Capture-Surface liefert die Live-Vorschau,
   RTMP/RTMPS und lokale MP4-Aufnahmen. Mikrofon-Audio bleibt verfügbar;
   der gespeicherte Aufnahme-Audiomodus bestimmt die MP4-Spuren.
5. Vor Geräte-, Modus- oder Quellenwechsel Stream und Aufnahme stoppen.
   **Trennen** beendet laufende USB-Ausgaben bewusst. Nach Entfernen bleibt der
   USB-Fehler sichtbar; erneut verbinden oder Telefonkamera wählen.

Die Geräteauswahl unterscheidet auch zwei baugleiche Geräte. Gerätepfade und
Seriennummern werden nicht gespeichert. Nur VID:PID und Wunschmodus liegen in
DataStore. Szenen speichern USB bewusst als **CAMERA-Fallback**, wie bisher
beim Datei-Player: keine neue serialisierte Enum und kein blindes Wiederöffnen
eines womöglich anderen USB-Geräts nach einem Neustart.

## Architektur und Lebenszyklus

Die Telefonkamera behält `MultiCamera2`. USB verwendet RootEncoder
`MultiStream` **2.7.5**, dessen Konstruktor eine eigene VideoSource und eine
MicrophoneSource akzeptiert. Eine Vivid-eigene `VividUvcVideoSource` verbindet
das ausgewählte Gerät über UVCAndroid **1.0.13** mit einer Surface aus
RootEncoders SurfaceTexture. Native UVC-Decodierung → Surface → GL → MediaCodec
liefert einen H.264/AAC-Ausgang (Capture-Auflösung/FPS, gespeicherte Bitrate) an zwei unabhängige RTMP-Clients und den
MP4-Controller. Keine zweite Kamera und kein Bitmap-Pfad für die Vorschau.

Das Upstream-Beispiel `CameraUvcSource` wählt jedes neu angeschlossene Gerät,
ignoriert den gewünschten Modus und behandelt Fehler/Detach nicht. Deshalb
verwendet Vivid die Helper-API direkt. `extra-sources` und alle anderen
RootEncoder-Artefakte bleiben auf 2.7.5. Die zusätzliche direkte
`com.herohan:UVCAndroid:1.0.13`-Abhängigkeit ist nötig, weil Upstream sie mit
`implementation` statt `api` veröffentlicht.

**Wichtig:** UVCAndroid 1.0.13 erwartet in `Size.type` die Deskriptorwerte
`UVC_VS_FRAME_UNCOMPRESSED = 5` / `UVC_VS_FRAME_MJPEG = 7`.
Die nativen `FRAME_FORMAT_*`-Werte 0/1 sind dort falsch. Bildraten stammen aus
`Size.fpsList`, der tatsächliche Modus aus `previewSize` nach `onCameraOpen`.
HEVC-/Camera2-Profile werden für USB nicht übernommen; H.264/AAC ist der
bewusste RTMP-Kompatibilitätspfad. Adaptive Bitrate bleibt anwendbar.
Ein RAW-Frame-Callback bestätigt die erste Bildlieferung ohne Java-Framekopien;
die eigentlichen Bilder bleiben im nativen Surface-/GPU-Pfad. Ein RTMP-Ziel
wird erst nach dieser Bestätigung als STREAMING angezeigt.

Ein Anwendungssingleton besitzt den ausgewählten Helper. Sperre und
Generationsnummer schützen Geräte-/Surfacebesitz; RootEncoder-/RTMP-Ereignisse
werden für USB auf Main serialisiert. Der Helper verwendet seinen eigenen
Worker; native Initialisierung und Deskriptorabfragen laufen außerhalb des
UI-Threads. Öffnen und erster Frame haben einen begrenzten Timeout.
Späte Callbacks aus freigegebenen Sessions werden verworfen. Die Aktivierung
in der SourceRegistry erfolgt erst nach erfolgreichem Geräteöffnen.

Die UI-Surface wird separat an GL angehängt. Entfernen der Activity-Vorschau
stoppt weder Streaming noch Aufnahme. Derselbe Foreground-Service besitzt
auch eine alleinstehende lokale Aufnahme, inklusive WakeLock und Stop-Aktion.
Der letzte RTMP-Stopp erhält einen laufenden Recorder; Recording-Stopp erhält
laufende RTMP-Ziele. Der Track-Controller wird **vor** der Encoder-Vorbereitung
installiert und während eines Streams nicht ersetzt, damit die bereits
empfangenen Codec-Formate für eine später begonnene Aufnahme erhalten bleiben.
Bei Detach werden Recorder und Ziele beendet und Ressourcen freigegeben; eine
Datei wird soweit möglich abgeschlossen und als unterbrochen gekennzeichnet.
Eine ohne Abschluss entstandene Datei ist kein nachgewiesen gültiges MP4.

Camera2-spezifische Steuerungen (Torch/Fokus/Stabilisierung/Belichtung) sind für
USB gesperrt. Generische GL-Effekte verwenden die aktive USB-GL-Pipeline.
USB startet niemals MediaProjection. Aufnahmen nutzen dasselbe app-interne
`replays/`-Verzeichnis und dieselbe Replay-Bibliothek.

## Plattform, Abhängigkeiten und Lizenzen

- Android minSdk 24 in Vivid; UVCAndroid minSdk 21.
- USB-Host ist optional (`required=false`), auch im gemergten Manifest.
- Ab Android 9 benötigt eine App mit targetSdk ≥28 für USB-Videoklassen
  zusätzlich die Runtime-Permission CAMERA; USB-Host-Gewährung bleibt ein
  eigener Ablauf. Quelle: [Android UsbManager](https://developer.android.com/reference/android/hardware/usb/UsbManager#requestPermission(android.hardware.usb.UsbDevice,android.app.PendingIntent)).
- Paketgebundenes mutables PendingIntent für Framework-Grant-Extras;
  anfragebezogene URI verwirft alte Antworten. Empfänger nicht exportiert.
- Die ungenutzten Storage-Permissions des UVC-AAR (einschließlich
  MANAGE_EXTERNAL_STORAGE) werden aus dem App-Manifest entfernt. Der bereits
  vorhandene Debug-Screenshot-Eintrag WRITE_EXTERNAL_STORAGE mit maxSdkVersion
  18 bleibt unter Vivids minSdk 24 unwirksam.
- AAR enthält armeabi-v7a, arm64-v8a, x86 und x86_64, jeweils fünf native
  Bibliotheken. Native LinkageError wird vor Helper-Start abgefangen und als
  lokalisierter Fehler ausgegeben. Keine Vendor-/Product-Allowlist.
- `UVCAndroid-1.0.13.aar`: 2,761,961 Bytes;
  SHA-256 `4f1295a6c6b5e8590fb7dde3c60f30a94166db45c2d07efa63c85d5da8bfff17`.
  Alle fünf arm64-ELFs haben LOAD-Alignment `0x4000` (16 KiB).
  Dies ersetzt keinen Test auf einem Android-Gerät mit 16-KiB-Seiten.
- RootEncoder/UVCAndroid: Apache-2.0; native Teilbibliotheken haben eigene
  Lizenzen, insbesondere libusb LGPL-2.1-or-later. Volltexte, Quellenverweise
  und Attribution liegen als APK-Assets in `third_party/uvc/NOTICE.txt`.
  Native Libraries und Gradle-Caches wurden nicht verändert.
- Reproduzierbare native Quellen: UVCAndroid-Commit
  `6018c7f20238832929cf61427427bb7828056260` (Tag 1.0.13).
  Bei Weiterverteilung des APK muss der LGPL-Quellzugang erhalten bleiben;
  der NOTICE verweist auf den vollständigen Quellarchiv-Download und Buildskripte.

### Bestehende FOSS-Packaging-Einschränkung

Der Dependency-Audit zeigt, dass `fossDebugRuntimeClasspath` am Ausgangsstand
bereits Sentry-Artefakte aus der automatischen Installation des vorhandenen
Sentry-Gradle-Plugins enthält. `FOSS_BUILD` verhindert die Initialisierung zur
Laufzeit; das ist keine Abwesenheit der SDK-Abhängigkeiten. UVC führt keine
Sentry-Abhängigkeit ein. Eine vollständige Entfernung erfordert die Trennung
der bereits gemeinsam kompilierten Sentry-Initialisierung/Filter in Flavor-
Quellsets und wird hier nicht als erledigt behauptet. Der Draft-PR benennt
dies als separate Einschränkung des vorhandenen FOSS-Builds.

## Validierung und offene Hardware-Abnahme

Automatische Nachweise stehen im PR. JVM-Tests prüfen Auswahl/Berechtigung,
Modusabbildung, erste Frames, alte Callbacks, Target-Ausfälle und gemeinsame
Recorder-/Encoderbesitzverhältnisse mit injizierten Fakes. Ein Robolectric-
Smoke-Test initialisiert den echten MultiStream-Konstruktor mit eigener Quelle
und zwei RTMP-Clients. Diese Tests belegen keine echte USB-Übertragung oder
decodierbare Encoderausgabe.

| Gerät | Vorschau | RTMP | 2 Ziele | MP4 offline | MP4 + RTMP | Unplug | Modi |
|---|---|---|---|---|---|---|---|
| USB-UVC-Webcam | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |
| HDMI-UVC-Capture-Card | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED | NOT TESTED |

Für die Freigabe Android-Version, Telefon/Chipset, VID/PID, ausgehandelte Modi
und OTG-/Hub-/Stromversorgung festhalten. Mit 640×480 oder 1280×720 beginnen.
RTMP und MP4 mit ffprobe/MediaExtractor auf decodierbare Video-/Audiotracks,
Größe, Zeitstempel, Dauer, Rotation und A/V-Synchronität prüfen. Separat testen:
Start RTMP während Recording, Stopp beider Ziele während Recording,
Activity-Recreate/Hintergrund, Berechtigungsverweigerung, wiederholtes Detach,
baugleiche Geräte und durch andere Apps belegte Kameras. Webcam-Erfolg allein
belegt keine HDMI-Capture-Card-Kompatibilität.
