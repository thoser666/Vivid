# Architektur: GPU-Szenenkompositor

> **Status:** RFC zur Review; inkrementelle Implementierung auf dem Feature-Branch, Gesamtumfang offen · **Datum:** 2026-10-24
> **Issue:** [#306](https://github.com/thoser666/Vivid/issues/306)
> **Entscheidungsvorschlag:** Phase 1 dokumentieren; Phasen 2–4 zurückstellen.
> Der Maintainer entscheidet über Annahme und spätere Milestones.

Der vollständige Implementierungsauftrag wird im [Umsetzungsstand](scene-compositor-implementation.md) nachverfolgt. Die folgenden Phasen-Gates bleiben Design- und Verifikationskriterien.

## 1. Ziel und Abgrenzung

**Priorität 1 sind bewusst eingebrannte Overlays im kodierten Video**, zuerst ein
statisches Logo über einer Kamera. Zuschauer sehen damit dasselbe Overlay im
Stream und in der Aufnahme. Chat und dynamische Widgets sind mögliche spätere
Konsumenten dieses Pfads, keine automatisch mitgelieferte Funktion.
**Priorität 2 ist PiP mit mehreren unabhängig laufenden Videoquellen.** Das hat
andere Geräte-, Lifecycle- und Leistungsanforderungen und erhält ein eigenes Gate.

Dieser RFC beschreibt die Architektur zu #306. Der verlinkte Umsetzungsstand
führt die inzwischen begonnenen Runtime-Änderungen und offenen Nachweise auf.
Editor, vollständige Quellenparität und Gerätemessungen sind noch nicht fertig.
Vorhandener Chat, Bot, Widgets, Replays, Audio und Multi-Streaming bleiben
wiederzuverwendende Systeme. Ein Mobilgerät bekommt zunächst einfache
Layout-Presets; ein freier Drag/Resize/Rotate-Editor ist kein Einstiegsziel.

## 2. Ist-Zustand und Vergleich

Die folgenden Aussagen wurden gegen `develop`, Commit `2752e6f`, geprüft.
Die Versionsquelle ist der [Versionskatalog](../../gradle/libs.versions.toml):
RootEncoder 2.7.5, minSdk 24. Ältere Versionsangaben in Übersichten sind keine
Grundlage für die Pipeline-Entscheidung.

| Baustein | Verifizierter Zustand | Konsequenz |
|---|---|---|
| [StreamScene](../../domain/src/main/java/com/vivid/core/data/StreamScene.kt) | Ein `videoSource`-Enum, Widget-Flags und primäres Stream-Ziel; keine Layer-Transforms | Szenen sind Konfigurationspresets, kein Kompositionsmodell |
| [SceneController](../../feature-streaming/src/main/java/com/vivid/feature/streaming/scene/SceneController.kt) | Wendet Settings und eine Quelle an | Bestehenden Preset-Vertrag erhalten |
| [StreamingEngine](../../feature-streaming/src/main/java/com/vivid/feature/streaming/StreamingEngine.kt) | Kamera über `MultiCamera2`; `switchSource` wechselt den aktiven Quellpfad | Bestehende Quellen lassen sich nicht einfach gleichzeitig einschalten |
| [VideoSourceRegistry](../../feature-streaming/src/main/java/com/vivid/feature/streaming/source/VideoSource.kt) | Factories nach `VideoSourceKind`, ein `activeSource` | Factory-Muster wiederverwenden; für parallele Producer reicht die Singleton-Auswahl nicht |
| [DisplayFactory](../../feature-streaming/src/main/java/com/vivid/feature/streaming/source/DisplayFactory.kt), [PlayerFactory](../../feature-streaming/src/main/java/com/vivid/feature/streaming/source/PlayerFactory.kt) | `MultiDisplay` bzw. `MultiFromFile` besitzen eigene Encoder-/GL-Pipelines | Für Multi-Source werden encoderfreie Producer benötigt |
| [StreamingScreen](../../feature-streaming/src/main/java/com/vivid/feature/streaming/ui/StreamingScreen.kt) | Chat, Text/Info, Bild, QR, Slideshow und Untertitel als Compose-Overlays über dem Preview | Erhalten der UI bedeutet nicht Einbrennen in den Stream |
| [PrivacyComposer](../../feature-streaming/src/main/java/com/vivid/feature/streaming/PrivacyComposer.kt) | Soll-Zustand → `clearFilters` + `addFilter`, Privacy zuerst, dann VideoFilter/LUT/LowLight | Genau einen Besitzer der Filterkette behalten |
| [UvcVideoSource](../../feature-streaming/src/main/java/com/vivid/feature/streaming/source/UvcVideoSource.kt) | `MultiDisplay`, Consent via `sendIntent`; injizierter `usbManager` ungenutzt | Kein Nachweis für echte USB-Frames; UVC nicht als fertigen Producer einplanen |

Eigene `BaseFilterRender`-Implementierungen (`PrivacyBlurFilterRender`,
`HaldClutFilterRender`) zeigen das Integrationsmuster. Bestehende Tests und
RootEncoder-spezifische Annahmen (`FaceEllipseMapper`,
`RootEncoderBitrateDiagnostics`) müssen bei jeder Bibliotheksänderung erneut
geprüft werden. Die [WHIP-Skizze](../whip-spike.md) beschreibt einen separaten
Ingest-Pfad; ihre Existenz beweist keine gemeinsame RTMP/WHIP-Komposition.

### KrinikCam als Referenz

Das Issue nennt [KrinikCam](https://github.com/MikalaiKryvusha/KrinikCam) als
Inspiration für „camera as layer“. Beim RFC-Abgleich konnte `gh repo clone`
den angegebenen Repository-Namen nicht auflösen. Ein Codevergleich, eine
Lizenzprüfung und Aussagen über dessen EGL-/Encoder-Implementierung sind daher
**offen**. Übernommen wird nur das im Issue beschriebene Konzept: Producer und
Darstellungs-Layer trennen. Vivid hat heute dagegen Quellobjekte einschließlich
Encoder. Vor einer späteren Umsetzung muss ein erreichbarer, auf einen Commit
fixierter Referenzstand geprüft werden; Codeübernahme setzt Lizenzprüfung voraus.
Das RFC-Gate hängt nicht von ungeprüften Eigenschaften dieses Projekts ab.

## 3. Pipeline-Entscheidung und Ausstieg

| Option | Nutzen | Kosten und Ausstieg |
|---|---|---|
| A: RootEncoder-GL um zusätzliche Textur erweitern | Bestehende Kamera, Audio, Encoder, Recording und Multi-Target-Fanout bleiben zuständig; kleinster Eingriff | An 2.7.5 gekoppelte GL-/Textur-Lifecycle-Verträge. Falls Render-Thread, Context-Verlust oder Preview/Encoder-Parität nicht verlässlich integrierbar sind: Proof stoppen, Overlay-Slot entfernen und Bestandskette nutzen |
| B: Eigener EGL/FBO-Kompositor mit MediaCodec-Ausgabe | Freie Producer-Anzahl, Surface-Eingänge und einheitliche Frame-Steuerung | Eigene EGL-Ownership, Clock, Encoder, Audio-Sync und Transportanbindung; Multi-Target-Funktionen müssten ersetzt oder adaptiert werden. Nur nach separatem RFC/Spike; bei fehlender Parität separaten experimentellen Pfad verwerfen, Bestand weiterbetreiben |

**Empfehlung: A ausschließlich für den kleinsten Textur-Proof evaluieren.**
Das ist keine Zusage, dass die aktuelle RootEncoder-API Multi-Camera unterstützt.
Nicht mehrere `MultiCamera2`/`MultiDisplay`-Encoder-Ausgaben dekodieren und wieder
komponieren. Ziel ist langfristig ein GPU-Bild, das Preview und genau eine
Video-Encoding-Session pro Ausgabeprofil versorgt. Mehrere Netzwerkziele nutzen
den vorhandenen Fanout; ein anderes Auflösungs-/Codec-Profil kann weiterhin
einen eigenen Encoder benötigen. Recording muss die vorhandene gemeinsame
Kodierung verwenden, soweit Format und API dies erlauben; im Proof nachweisen.

```text
Textur-Proof (vorgeschlagen):
Kamera → Privacy → VideoFilter → LUT → LowLight → Logo-Textur
                                                ↓
                          gemeinsames Bild → Preview / Encoder
                                                         ↓
                                             Streaming + Recording

Späteres Zielbild (separat freizugeben):
Producer[SourceId] → quellbezogene Privacy → Layer-Transforms/Blend
                                              ↓
                         gemeinsame Effekte → Preview / Encoder
```

Privacy bleibt im Proof vor allen kreativen Filtern; das Logo wird zuletzt
gefügt, damit es nicht ungewollt eingefärbt wird. Es darf keinen konkurrierenden
`setFilter`-/`clearFilters`-Aufrufer geben: der neue optionale Slot gehört in den
kanonischen Rebuild des `PrivacyComposer`. Bei mehreren Live-Quellen braucht
jede Quelle vor dem Blend eine eigene, richtig zugeordnete Anonymisierung;
ein globaler Kamera-Filter schützt zusätzliche Quellen nicht automatisch.

## 4. Kleinstes Experiment und Fallback

Ein späterer, separat freigegebener Proof umfasst **eine bestehende Kamera plus
eine statische RGBA-Logo-Textur**. Keine neue Quelle, kein neuer Encoder, kein
neuer EGL-Kontext und keine Szene-v2-Migration. Ein einfacher Schalter genügt.

- Textur nur bei Asset-Änderung hochladen, Blend mit definierter Alpha-Konvention;
  keine CPU-Kopie des Kameraframes, kein `glReadPixels` im Produktionspfad.
- GL-Erzeugung, Upload und Freigabe auf dem RootEncoder-Render-Thread ausführen;
  Context-Verlust lädt das Asset erneut. Render-Instanzen nicht über zerstörte
  Kontexte hinweg teilen. Wiederholte Ketten-Rebuilds dürfen keine Textur leaken.
- Preview, lokal gespeichertes Video und empfangenes Stream-Video auf Position,
  Crop, Alpha, Spiegelung und Rotation vergleichen, Portrait und Landscape.
- Kamera + Privacy + LUT/LowLight, Start/Stop, Background/Foreground,
  Preview-Abbau und Wiederanbindung, Recording und zwei Stream-Ziele prüfen.
  Foreground-Service und Android-Berechtigungen bleiben maßgeblich.
- Bestehende Compose-Overlays bleiben lokale Bedienoberfläche. Einbrennen ist
  explizites Opt-in, besonders für Chat und Standortdaten; der Proof erfasst
  weder die komplette Activity noch Bedienelemente als Bitmap.

**Abbruch/Fallback:** Default aus, bei nicht unterstützter Textur oder GL-Fehler
nur den optionalen Logo-Slot deaktivieren, Bestandskette einschließlich Privacy
wiederherstellen und Fehler sichtbar melden. Ist Privacy nicht gewährleistet,
Ausgabe unterbrechen statt ungeschützte Frames senden. Kein automatischer
Pipeline-Wechsel mitten im Stream. Eine spätere alternative Pipeline wird nur
im Idle-Modus gewählt; Szenendaten bleiben beim Rückweg erhalten.

## 5. Späteres Szenen- und Producer-Modell

Dies ist ein Zielvertrag, kein in diesem PR einzuführendes API:

| Objekt | Vertrag |
|---|---|
| `SourceId` / `SourceSpec` | Persistente, von Typ und Layer unabhängige ID; Kameragerät, MediaProjection, Datei/Bild oder später USB-Gerät. Keine GL-Handles oder Consent-Tokens persistieren |
| Producer-Session | Liefert Surface/Texture + Zeitstempel ohne eigenen Encoder; besitzt Ressourcen und Capability-/Fehlerzustand. Laufzeit-Handles gehören in `feature-streaming` |
| Layer | Eigene `LayerId`, Referenz auf `SourceId`, stabile Reihenfolge, Sichtbarkeit, Opazität, Position/Größe normalisiert, Rotation und quellrelativer Crop |
| Szene v2 | Geordnete Layer-Liste und Canvas-Seitenverhältnis; serialisierbare Spezifikation in `domain`, Persistenz nach SceneRepository-Muster |

Zwei Layer mit derselben `SourceId` duplizieren die Darstellung, nicht Kamera,
Decoder oder Encoder. Session-Manager führt Referenzzählung über aktive Szenen
und Übergänge, stoppt unbenutzte Producer und begrenzt Anzahl, Auflösung und
Puffer. Unsichtbare, sonst unreferenzierte Quellen werden nach definierter
Idle-Policy freigegeben. Nicht nutzbare Quellen zeigen einen neutralen
Offline-Platzhalter; kein unbegrenzt eingefrorenes Kamerabild. Reconnect mit
begrenztem Backoff; verweigerte Berechtigung erfordert Nutzeraktion.

Transforms erhalten einen dokumentierten Vertrag: zuerst quellrelativer Crop,
dann Skalierung auf Layer-Größe, Rotation um Layer-Mitte, Translation und Blend
in Listenreihenfolge. Rotation/Spiegelung des Kameraeingangs werden vorher
normalisiert. Ein normalisierter Koordinatenraum verhindert geräteabhängige
Pixel-Persistenz; Letterboxing und Touch-Hit-Tests brauchen dieselbe Abbildung.

Spätere Frame-Clock: Ausgabetakt bleibt unabhängig von Producer-FPS, pro Quelle
höchstens das neueste vollständige Frame konsumieren und alte Frames verwerfen.
Keine unbeschränkten Queues; Audio bleibt auf der bestehenden Master-Clock.
Übergänge halten alte/neue Quellen nur für begrenzte Dauer gleichzeitig aktiv.

Migration erst nach Freigabe: alte `StreamScene` ergibt genau einen Vollbild-Layer
und unveränderte Widget-/Ziel-Settings. Versioniertes Format, Roundtrip- und
Korruptionsprüfungen, alte Daten vor Migration bewahren. Neue Szenen nicht
stillschweigend in ein verlustbehaftetes altes Format zurückschreiben.
Das derzeitige `VideoSource`-Interface bleibt zunächst Adapter für den
Bestandspfad; es wird nicht durch einen Aufruf von `start()` zum Texture-Producer.

## 6. Referenzgeräte und Capability-Gates

Keine Zusage anhand der Android-Version allein. Vor dem Experiment konkrete
Modell-, SoC-, RAM-, Android-, GLES-/Treiber- und Kamera-IDs im Messprotokoll
festhalten. Die folgenden Klassen sind vorgeschlagen; physische Geräte und
Verfügbarkeit müssen vor Phase 2 benannt werden.

| Klasse | Prüfauftrag |
|---|---|
| A: minSdk-24-Gerät, GLES2-Pfad | Eine Kamera + Logo bei 720p30; fehlende GLES3-Funktionen dürfen den Bestand nicht ausschließen |
| B: ARM64-Mittelklasse, Android 11+, GLES3 | Hauptreferenz: 1080p30, H.264 + Audio, Preview, RTMP + Recording; Dauerlast auf Akku |
| C: Gerät mit gemeldeter Concurrent-Camera-Kombination | Erst Phase 3: zwei tatsächlich gleichzeitig geöffnete Kameras bei unterstützten Größen/FPS; kein allgemeines Dual-Camera-Versprechen |

Android stellt [Concurrent-Camera-IDs](https://developer.android.com/reference/android/hardware/camera2/CameraManager#getConcurrentCameraIds())
ab API 30 bereit; leere Mengen, ältere Geräte und nicht unterstützte
Stream-Kombinationen bekommen nur Single-Camera-Layouts. GLES-Version und
benötigte Extensions zur Laufzeit prüfen; GLES3 ist keine minSdk-24-Garantie.
MediaProjection benötigt eigenen Consent und Lifecycle. Echte UVC-Erfassung,
USB-Permission und Disconnect-Behandlung müssen erst unabhängig nachgewiesen
werden. Kamera + Screen + USB gleichzeitig ist kein Phase-2-Abnahmekriterium.

## 7. Baseline, Messverfahren und Stop-Kriterien

**Es liegen für diesen RFC keine Gerätemessungen vor.** Die Baseline ist eine
Voraussetzung vor Umsetzung/Bewertung des Proofs, kein erfundener Zahlenwert.
Pro Klasse Baseline-Commit und später Proof-Commit protokollieren. Drei gepaarte
Läufe je 20 Minuten nach gleichem Warmup, gleichem Akkubereich,
Umgebungstemperatur, Helligkeit, Netz und Kameramotiv; Reihenfolge variieren.
720p30 auf A und 1080p30 auf B, gleicher Codec/Bitrate, gleiche Audioeinstellungen,
Privacy-/Effektzustände und gleiche Preview-/Recording-/Stream-Ziele.

| Messwert | Verfahren / Evidenz |
|---|---|
| Frame-Latenz p50/p95 | Instrumentierte monotone Kamera-/Render-/Encoder-Zeitstempel mit dokumentierter Clock-Zuordnung; separat optischer Zeitcode-Test am Empfänger für Ende-zu-Ende-Latenz |
| Frames / Drops | Eingangs-, Render-, Encoder- und Empfängerzähler; Netzverluste getrennt von Render-Drops zählen |
| CPU / GPU | Perfetto-Trace und Prozess-CPU; GPU-Zeit/Counters nur soweit Treiber sie liefert, sonst als nicht verfügbar markieren |
| Speicher | `dumpsys meminfo`, PSS und Native/Graphics vor/nach Dauerlast und wiederholten Start/Stop-/Rebuild-Zyklen |
| Thermik / Akku | Thermal-Status soweit API unterstützt, Temperatur-/Throttling-Verlauf; Batterieladungszähler oder externer Leistungsmesser, inklusive Messauflösung; kein Laden während Akkuvergleich |
| Output-Parität | Vergleichbare Frames aus Preview, Recording und empfangenem Stream; Privacy, Alpha, Crop, Rotation und Audio-Sync |

Vorgeschlagene, vor dem Proof vom Maintainer zu bestätigende Grenzwerte:
p95-Pipeline-Mehrlatenz höchstens ein 30-fps-Frame (33,3 ms), Drop-Rate höchstens
0,5 Prozentpunkte über Baseline, CPU- und Energie-Mehrbedarf jeweils höchstens
10 % relativ bei hinreichender Messauflösung. Kein neues thermisches Throttling,
kein stetiges Speicherwachstum über Start/Stop-Zyklen und kein Verlust von
Privacy/Output-Parität. GPU-Daten fehlen → transparent ausweisen, mit Render-Zeit
und Drops bewerten, keinen GPU-Erfolg behaupten. Zu grobe Akku-/Clock-Messung →
Ergebnis unentschieden und Messung verbessern. Grenzwertverletzung oder nicht
reproduzierbare Messung blockiert Phase 3; Qualität nicht stillschweigend senken.

## 8. Phasen und Entscheidungs-Gates

| Phase | Liefergegenstand | Voraussetzung für den nächsten Schritt |
|---|---|---|
| 1: RFC | Priorität, Bestandsabgleich, Optionen, Modell und Messplan | Maintainer-Review beantwortet Geräteauswahl, Grenzwerte und Option A; kein Runtime-Code |
| 2: Textur-Proof (zurückgestellt) | Baseline zuerst, dann eine Kamera + Logo durch bestehende Filterkette | Reale Geräte A/B, Messprotokolle und Output-Parität; automatisierte Ketten-/Lifecycle-Tests plus bestehende Streaming-Suites; eigener Implementierungsauftrag |
| 3: Producer / Multi-Source (zurückgestellt) | Encoderfreie Producer, SourceIds, Szene v2, Bild/PiP, Persistenz; zwei Live-Quellen nur bei Capability | Eigenes Design-Review für EGL/Clock/Privacy, Migrationstests, Disconnect-/Reconnect- und Dauerlastnachweise; separates UVC-Gate |
| 4: Bedienung (zurückgestellt) | Presets zuerst; später optional Touch-Transforms, Guides und Transitionen | Einhändige Bedienbarkeit und Accessibility testen; Übergänge unter Ressourcenbudget, erneute Thermikmessung |

Das ist keine Termin- oder Milestone-Zusage. Der [Vision-Check](../vision.md)
ergibt für den vollständigen Kompositor: Streamer-Nutzen ja; mobile Bedienung
bedingt; keine durch #306 belegte Moblin-Paritätslücke; Wartbarkeit bedingt;
FOSS ja. Damit ist das Gesamtpaket gemäß Maintainer-Review zurückzustellen.
Ein kleiner Overlay-Proof hat einen engeren Nutzen-/Aufwandsrahmen, muss aber
separat priorisiert werden. Keine proprietären Pflicht-SDKs, keine Ablösung von
Chat, Widget-Datenlieferanten, Bot, Streaming-Transport oder Audio.

## 9. Review-Fragen

1. Wird eingebranntes Logo/Overlay als erstes Ziel bestätigt, PiP erst danach?
2. Welche konkreten Geräte vertreten A/B, wer führt die Messungen durch?
3. Sind die relativen Budgets und die 20-Minuten-Dauer ausreichend?
4. Kann RootEncoder 2.7.5 Textur-Lifecycle und gemeinsame Ausgabe mit A erfüllen?
   Diese Frage beantwortet erst der freigegebene Proof; B ist kein impliziter Ersatz.
5. Ist eine erreichbare KrinikCam-Referenz vorhanden? Bis dahin bleibt der
   Implementierungsvergleich offen.
