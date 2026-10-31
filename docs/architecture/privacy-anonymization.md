# 🔒 Architektur: Datenschutz-Anonymisierung im Videopfad (Auto-Gesichter + Zonen)

> **Status:** Skizze festgelegt (2026-09-26) · **P0 implementiert (2026-09-26)** — Filter-Komposition + Privacy-Shader verhaltensneutral · **P1 implementiert (2026-09-27)** — Zonen-Editor + ZoneRepository + Master-Toggle (Manual-Test auf dem Gerät steht aus) · **P2 implementiert (2026-10-16)** — Gesichtserkennung **Route A (RootEncoder-Hardware)** statt TFLite: Producer/Mapper/Smoother + Slot-Merge in der Engine, Persistenz `face_blur_enabled`, Capability-/Lifecycle-/Golden-Tests · **P2b implementiert (2026-10-16)** — Voll-Frame-Verpixelung (Standort-Schutz): Shader-Uniform `uFullFrame` + grobe Mosaik-Stufe `FULL_FRAME_PIXEL_GRID = 1/16`, Persistenz `full_frame_blur_enabled`, Toggle im Streaming-Screen · **P2 Schritt 3 implementiert (2026-10-31)** — Face-Blur-Toggle im Streaming-Screen (Gesichtszeile unter dem Master-Toggle, `face_blur_toggle`, i18n de/en/fr/ru) · **offen:** Geräte-Verifikation (Mapping-Konvention, Capability, fps, Voll-Frame-Stufe); P1-Manual-Test weiterhin offen · **Datum:** 2026-09-26
> **Tracked in:** [PARITY.md, Bucket „Datenschutz-Anonymisierung"](../../PARITY.md) · **Vivid-Zusatz** (Moblin hat keine Anonymisierung — analog Owner-Steuerung/Chat-TTS als eigener Nutzwert)
> **User-Entscheidung:** Stufe 2 (automatische Gesichtsanonymisierung) ist Ziel; Stufe 1 (Zonen) ist Fallback und P0-Baustein. **Umsetzung der Erkennung: Route A** — Hardware-Face-Detect des Kamera-Stacks, kein eigenes ML-Modell (Begründung §2/§6)

---

## 1. Ziel

Passanten, Kennzeichen, Hausnummern und Bildschirme sollen **vor dem Senden** (und vor
Replay/Preview) unkenntlich gemacht werden können — direkt in der GL-Encoder-Pipeline,
nicht nachträglich im aufgenommenen Material. Drei Mechanismen, die sich ergänzen:

1. **Manuelle Zonen** (Stufe 1, P0): 1–4 vom Streamer platzierte Ellipsen werden im
   Shader stark geblurrt. **Garantie-Mechanismus** — was in der Zone liegt, wird
   immer unkenntlich, egal was die Erkennung tut.
2. **Automatische Gesichter** (Stufe 2, P1–P3): On-Device-Gesichtserkennung
   liefert Gesichtsboxen, die derselbe Blur-Shader unkenntlich macht.
   **Unterstützungs-Mechanismus** — verfehlt gelegentlich Profile/Nachtbilder.
   P2 setzt das mit **Route A** um: die **Hardware-Erkennung des Kamera-Stacks**
   (`Camera2Base.enableFaceDetection`, RootEncoder 2.7.5) liefert pro Frame
   Boxen in sensoraktiven Koordinaten — ohne eigenes ML-Modell, ohne neue
   Dependency, ohne Asset (§2, §6). Genau wie die Zonen werden daraus nur
   Ellipsen-Uniforms; ob die Box von Hand oder von der Kamera kam, ist dem
   Shader egal (§3).
3. **Ganzbild-Verpixelung** (Stufe 1b, P2b): ein Shader-Uniform (`uFullFrame`)
   pixeliert das **gesamte** Bild — mit bewusst **grober** Mosaik-Stufe
   (`FULL_FRAME_PIXEL_GRID = 1/16`, ≈ 120 px bei 1080p) statt der feinen
   Zonen-Stufe (1/48). Damit bleiben Architektur und Umgebung des Streamers
   unlesbar und verraten den Standort auch dann nicht, wenn weder Zone noch
   Gesicht greift. **Standort-Mechanismus** — unabhängig von Zonen/Gesichtern,
   wirkt nur zusammen mit dem Master-Toggle.

**Hartes Postulat:** Anonymisierung ist *kein* kreativer Filter. Sie muss unabhängig
von LUT/VideoFilter/Low-Light wirken und darf durch deren Deaktivierung nie verloren
gehen (Garantie-Zonen) — das treibt die Filter-Kompositions-Strategie (§4).

## 2. Ist-Zustand (verifiziert, Stand dieser Skizze)

| Baustein | Heute | Anonymisierungstauglich? |
|---|---|---|
| GL-Filter-Pfad | RootEncoder 2.7.5, `GlInterface.setFilter/addFilter/clearFilters`; `MainRender` hält `List<BaseFilterRender>`-FBO-Kette (`previousTexId`-Weiterreichung, `reOrderFilters`) | ✅ `addFilter`/`removeFilter` existieren — **aber Vivid nutzt sie nicht** |
| Vivid-Controller | `VideoFilterController`, `LutController`, `LowLightBoostController` — **alle drei auf dem Single-Slot** (`setFilter`) und deaktivieren mit `clearFilters()` (löscht die ganze Kette) | ❌ Zwei aktive Filter kämpfen heute um denselben Slot; `clearFilters()` einer Komponente killt die anderen |
| Eigene Filter-Vorlagen | `LowLightBrightnessFilterRender` (custom GLSL `res/raw/`), `HaldClutFilterRender` (Bitmap-LUT) | ✅ Muster (Shader-Rohdateien, `GlUtil`, initGlFilter/drawFilter/disableResources/release) steht |
| Filter-Wirkort | `setFilter` wirkt auf GL-Pipeline von Encoder **und** Preview (beim LUT-PoC verifiziert) — auf allen Quellen (Kamera, Screen-Capture, Video-Player, `LowLightBoost`-Präzedenz) | ✅ Senden **und** Replay/Preview sind abgedeckt |
| ML-Stack | keiner (FOSS-Flavor: keine Google-Binaries — ML Kit scheidet aus, gleiche Kategorie wie Sentry). **P2 braucht bewusst keins:** die Boxen kommen aus `Camera2`/`Camera2Base.enableFaceDetection` (RootEncoder 2.7.5) | ✅ Route A ohne Dependency und ohne Asset; offen ist nur noch der Geräte-Nachweis, dass die jeweilige Kamera einen Face-Modus anbietet (§8) |
| Datenschutz-Bestand | `LogRedactor` (Keys/Tokens/Passwörter), Sentry-Error-Replay (Maskierung, Kamera-Vorschau schwarz), Remote-Control LAN+Token | Kontext, nicht Teil dieser Skizze |

## 3. Schichtenarchitektur

```
┌────────────────────────────────────────────────────────────────────┐
│ L4  Settings/StreamingScreen: Zonen-Editor (Touch), Master-Toggle, │
│     Face-Blur-Toggle (P2), Preset „Alle Gesichter“                 │
├────────────────────────────────────────────────────────────────────┤
│ L3  PrivacyComposer (neu): EIN an der GL-Pipeline hängender         │
│     PrivacyBlurFilterRender; Rebuild-Logik bei Kombinations-/      │
│     Deaktivierungs-Events; Position 0 = Anonymisierung zuerst      │
├────────────────────────────────────────────────────────────────────┤
│ L2  Producer: (a) ZoneRepository (statisch, persistiert),           │
│     (b) FaceDetectionProducer (RootEncoder-Hardware, P2)           │
├────────────────────────────────────────────────────────────────────┤
│ L1  PrivacyBlurFilterRender (BaseFilterRender, GLSL): bis zu N      │
│     Ellipsen-Uniforms (Zentrum + Radien), Voll-Frame-Flag +         │
│     Mosaik-Grid (P2b), weiche Kante, Pixelate                       │
├────────────────────────────────────────────────────────────────────┤
│ L0  Modelle: PrivacyZone (persistierbar), DetectedFace/FaceFrame   │
└────────────────────────────────────────────────────────────────────┘
```

- **L1-Shader** ist die gemeinsame Schnittstelle: Producer (a) und (b) schreiben nur
  Ellipsen-Koordinaten — ob eine Zone von Hand oder von der Kamera-Erkennung kommt,
  ist dem Shader egal. Bis zu **N = 8 Ellipsen** (4 Zonen + 4 Gesichter, ggf.
  Hochzählen; Uniform-Array-Größe kompilierzeitfest). P2b hängt zwei weitere
  Uniforms daran: `uFullFrame` (Ganzbild-Maske, unabhängig von den Slots —
  verbraucht **keinen** Ellipsen-Slot) und `uPixelGrid` (Mosaik-Kantenlänge:
  1/48 für Zonen/Gesichter, 1/16 fürs Ganzbild).
- **L3-Composer** ist der einzige Berührungspunkt mit `GlInterface` — die bestehenden
  Controller werden nicht verändert, sondern in den Rebuild einbezogen (§4).
- **Preview-Toggle** („nur Senden unkenntlich machen“) bewusst **nicht** vorgesehen:
  Was der Streamer nicht sieht, kann er nicht verifizieren — Anonymisierung ist immer
  auf Vorschau + Encoder sichtbar (Fail-safe, nicht Fail-hidden).

## 4. Kernentscheidung: Filter-Komposition (P0, verhaltensneutral)

**Problem:** Drei Controller + der neue Privacy-Filter müssen koexistieren, ohne dass
eine Komponente die andere auslöscht. RootEncoder bietet zwei Wege:

1. `addFilter(privacyRender)` / `removeFilter(privacyRender)` — Privacy-Filter lebt
   *parallel* in der `MainRender`-Liste, die Kreativ-Filter bleiben unberührt.
   ⚠️ Risiko: `clearFilters()` der bestehenden Controller löscht **auch** den
   Privacy-Render (MainRender.clearFilters leert die ganze Liste) — der Garantie-
   Postulat verletzt. Erst wenn alle Controller `clearFilters()`-frei sind, reicht Weg 1.
2. **Kanonischer Ketten-Rebuild (gewählt):** Ein einziger Codepfad baut die Filter-
   Kette aus dem gewünschten Soll-Zustand wieder auf:
   `Kette = [Privacy? (pos 0)] + [VideoFilter?] + [LUT?] + [LowLight?]`
   - Jeder Controller behält seine Logik (aktiver Zustand, StateFlow), aber der
     *Applier* wird zentral: statt `gl.setFilter(...)`/`gl.clearFilters()` ruft der
     Controller nur `composer.requestRebuild()` (oder reicht den neuen Zustand an
     `composer` weiter).
   - `requestRebuild()` konsolidiert Racing-Events (z. B. Filterwechsel während des
     Rebuilds) und applied den kompletten Soll-Zustand in **einem** `clearFilters()` +
     sequenziellen `addFilter(...)`-Zug (Choreographer-Frame-gate, Render-Thread).
   - **Zwischenzustand vermeiden:** Beim Rebuild wird der alte Zustand erst nach
     erfolgreichem Neuaufbau ersetzt (ein Frame mit alter Kette ist unkritisch; ein
     Frame *ohne* Privacy-Zonen wäre ein Leck — deshalb Privacy-Render-Instanz
     wiederverwenden, nicht neu erzeugen, und die Kette immer von position 0 her
     aufbauen).

**Wichtiger Nebeneffekt (Fix eines bestehenden Fehlers):** Heute exklusivisieren sich
VideoFilter/LUT/Low-Light gegenseitig über den Single-Slot und löschen sich per
`clearFilters()` gegenseitig weg. P0 behebt das beiläufig: kreative Filter werden
kombinierbar (Dokumentation der neuen Semantik in RELEASE.md + user-guide).

**Verhaltensneutralität von P0:** Sobald genau ein kreativer Filter aktiv ist,
entsteht exakt dieselbe Kette wie heute (`[Filter]`). Nur die Kombinationsfälle
(sind heute defekt: der zweite Filter verdrängt den ersten) ändern sich — und der
Privacy-Filter (der heute noch nicht existiert). Bestands-Tests
(`StreamingEngineFiltersTest`, `LutControllerTest`, `LowLightBoostControllerTest`,
`VideoFilterControllerTest`) laufen unverändert als Sicherheitsnetz; wo sie die
Single-Slot-Exklusivität **einfrieren**, werden sie auf die Composer-Semantik
umgeschrieben (dokumentiert, nicht still geändert).

## 5. Phasenplan (jede Phase: Tests + Doku + Gate, Zwei-Commit-Muster)

| Phase | Inhalt | Proof |
|---|---|---|
| **P0** ✅ erledigt (2026-09-26) | **Filter-Komposition (§4) + Privacy-Shader-Schnittstelle:** `PrivacyBlurFilterRender` (8 Ellipsen-Uniforms, Mosaik-Pixelierung in einem Pass, weiche Kante via smoothstep), `PrivacyComposer` mit kanonischem Rebuild (`clearFilters()` + `addFilter(...)` in Kanon-Reihenfolge; injizierbare Render-Fabriken → JVM-testbar), Engine-Applier auf Composer umgestellt; LutController ergänzt (Custom-LUT-Bitmap im Zustand, `createActiveLutRender` für den Rebuild), `reset*`-APIs rebuilden jetzt ebenfalls. Kein UI — Ellipsen nur programmatisch (`setPrivacyEllipses`). | feature-streaming **409 Tests grün** — Bestands-Suiten unverändert grün (JVM-Realität mit null-Fabriken reproduziert die Legacy-Kette exakt: `clearFilters` ohne `addFilter`), neu: `PrivacyComposerTest` (12: Kanon-Reihenfolge, Privacy @0 vor allen kreativen Filtern, Wiederverwendung der Privacy-Instanz, Idempotenz, fehlender Render wohlgeformt, Ellipsen-Forwarding ohne Ketten-Touch, Modell-Validierung) + `PrivacyBlurFilterRenderRobolectricTest` (3, Muster `HaldClutLutBitmapRobolectricTest`); app (foss+standard) kompiliert, Pre-Push-Gate grün |
| **P1** ✅ erledigt (2026-09-27) | **Zonen (Stufe 1, UI):** `PrivacyZone`-Modell (normierte Koordinaten 0–1, quellrelativ; domain), ZoneRepository (core, DataStore-JSON nach SceneRepository-Muster), Zonen-Editor im Streaming-Screen (`ZoneEditorOverlay`: Touch-Positionieren/Größe, max. 4 Zonen, nur im Idle-Modus), Master-Toggle „Anonymisierung“ in der Szenen-Leiste (wirkt auch während des Streams), i18n DE/EN/FR. Engine: Soll-Zustand + Anwendung bei Stream-Start/Quellwechsel, Y-Flip-Mapping `toPrivacyEllipse`, Toggle-Nachholen beim Go-Live. | ZoneRepositoryTest (10: Roundtrip/Cap/korrupte Zelle/Toggle), VM-Tests (3: Toggle-Forwarding, Init-Übernahme, Zonen-Speichern), Engine-Mapping/Idle-Guard (3), Robolectric-UI-Smoke (9); 744 Tests grün, app (foss+standard) kompiliert. Manual-Test auf dem Gerät (Kamera + Screen-Capture) steht aus |
| **P2** ✅ erledigt (2026-10-16, **Route A**) | **Gesichtserkennung (Stufe 2) über die Hardware-Erkennung des Kamera-Stacks:** `FaceDetectionProducer` (`Camera2Base.enableFaceDetection`, Nachversuche 10 × 200 ms mit Generation-Guard, Capability-Meldung `available` = `null`/`true`/`false`, Wunsch = Master- **und** P2-Toggle), `FaceEllipseMapper` (sensoraktive Box → Textur-Koordinaten, Konvention Front `(sensor+display)%360` / Back `(sensor-display+360)%360` ohne Mirror-Kompensation, Y-Flip identisch zu `toPrivacyEllipse`, Score-Filter ≥ 40, Cap 4), `FaceSmoother` (IoU-Matching über Ellipsen-Bounding-Boxen, Hysterese 2 Frames an / 3 Frames Haltezeit, Glättung 0.5, stabile Slot-Reihenfolge), **Slot-Merge in der Engine** (Zonen zuerst, harte Grenze `PrivacyBlurFilterRender.MAX_ELLIPSES = 8`, Gesichts-Frames setzen **nur die Uniforms — bewusst kein Ketten-Rebuild**), Lifecycle an beiden Kamerapfaden (Stream-Encoder nach `startStream` + Idle-Preview nach `openCameraId`, Detach beim Stopp/Quellwechsel), Persistenz `face_blur_enabled` in `ZoneRepository` + Rückstellung in der VM-`init`, kein UI (Toggle in Streaming-/Settings-Screen = Schritt 3, danach P3) | feature-streaming **558 Tests grün** — neu (35): `FaceEllipseMapperTest` (11: Goldenwerte der Konvention, Display-Rotation, Score-Grenze, Cap-Reihenfolge, Clamp, degenerate Boxen, 0×0-Frame, Budget), `FaceSmootherTest` (7: Eintrittshysterese, Blend-Folge 0.25→0.275→0.2875, Haltezeit 3+1, Positionswechsel, Slot-Cap, Reihenfolge, Reset), `FaceDetectionProducerTest` (10: Wunsch/Ziel-Trennung, Retry-Budget, Generation nach `detach`, Capability-Meldung), `StreamingEngineFacesTest` (7: Merge ≤ 8 **ohne** `clearFilters`, Zonen-Garantie zuerst, Toggle-Gating, Detach am Stopp, Idle-Preview-Pfad, Multi-Target); `ZoneRepositoryTest` +3 (Default/Persistenz/Unabhängigkeit), `:core:testDebugUnitTest` grün; Bestands-Suiten unverändert. **P2 Schritt 3 (2026-10-31) nachgezogen:** Face-Blur-Toggle in der Szenen-Leiste (Zeile „Gesichter automatisch verpixeln“ unter dem Master-Toggle, nur sichtbar wenn die Anonymisierung wirkt, `testTag("face_blur_toggle")`, i18n de/en/fr/ru) — Engine/Persistenz waren seit P2 fertig, es fehlte nur die Bedienung. **Beweis-Lücken:** Geräte-Verifikation Konvention/Capability/fps, Akku-/FPS-Messung |
| **P2b** ✅ erledigt (2026-10-16) | **Ganzbild-Verpixelung (Standort-Schutz):** der gesamte Bildausschnitt wird grob verpixelt, damit Architektur und Umgebung den Standort nicht verraten — unabhängig von Zonen/Gesichtern, wirkt nur mit dem Master-Toggle. Shader: `uFullFrame` (Ganzbild-Maske, `mask = max(mask, uFullFrame)`, kein Ecken-Leck) + `uPixelGrid` (ersetzt die feste `PIXEL_GRID`-Konstante; Fallback 1/48, solange der Uniform unsetzt ist). `PrivacyBlurFilterRender`: `setFullFrame`/`setPixelGrid` als framesynchrone Snapshots wie die Ellipsen, Konstanten `DEFAULT_PIXEL_GRID = 1/48` (≈ 23 px bei 1080p) und `FULL_FRAME_PIXEL_GRID = 1/16` (≈ 120 px bei 1080p — erst diese Stufe macht Umgebung unlesbar), Test-Lesegrößen. `PrivacyComposer.setFullFrame(enabled)`: schaltet Flag **und** Grid-Preset, ohne die Kette neu zu bauen (der wiederverwendete Render-Instanz trägt den Zustand über Rebuilds). Engine: `setFullFramePrivacy`/`fullFramePrivacy` (idempotent, reiner Soll-Zustand — **kein** Ellipsen-Slot-Verbrauch, `StreamingEngineFullFrameTest`). Persistenz `full_frame_blur_enabled` in `ZoneRepository` + Init-Restore in `StreamingViewModel`. UI: zweite Zeile unter dem Master-Toggle in der Szenen-Leiste (nur sichtbar wenn die Anonymisierung wirkt), Tag `full_frame_toggle`, i18n de/en/fr/ru. | feature-streaming + core grün — neu: `PrivacyBlurFilterRenderRobolectricTest` +2 (Snapshots/Defaults, Grid-Verträge), `PrivacyComposerTest` +3 (Forward ohne Rebuild, no-op ohne Render, Zustand über Rebuild), `StreamingEngineFullFrameTest` (3: Idempotenz, Master-Zyklus, slots bleiben frei), `ZoneRepositoryTest` +2 (Default, Unabhängigkeit), `StreamingViewModelTest` +2 (Persistenz/Forward, Init-Restore) + Mock-Stubs, `StreamingScreenRobolectricTest` +2 (Sichtbarkeit/Gating, Forward) |
| **P3** | **Robustheit + Presets:** Preset „Alle Gesichter + meine Zonen“ (Start-Flow beim Go-Live), Persistenz je Quelle (Kamera vorne/hinten, Screen-Capture getrennt), Debug-Overlay (Zonen/Kasten sichtbar nur lokal), Doku-Finalisierung (user-guide, RELEASE.md-Anonymisierungs-Abschnitt) | E2E-Manual-Check (Go-Live mit Zonen + Face-Blur), Lang-Lauf (1 h Stream, kein Leak/Flackern) |

**Nicht in dieser Skizze:** Objekt-Erkennung über Gesichter hinaus (Kennzeichen/
Text-OCR — eigener Bucket, falls gewünscht), Audio-Anonymisierung (Stimmen-Verzerrung),
serverseitige/externe ML (bewusst: on-device only, §7).

## 6. Modul-Zuordnung

| Modul | Neu |
|---|---|
| `feature-streaming` | L1 Render + L3 Composer + L2-Verkabelung (`ZoneRepository`-Soll-Zustand, `FaceDetectionProducer`), `FaceEllipseMapper`/`FaceSmoother`/Slot-Merge in `StreamingEngine`, Streaming-Screen-Editor (P1), Ganzbild-Toggle (P2b, ✅), Face-Blur-Toggle (P2-Schritt 3, ✅) |
| `domain` | `PrivacyZone`-Modelle (normalisiert, serialisierbar) |
| `core` | DataStore-Persistenz (Zonen, Toggles inkl. `face_blur_enabled` + `full_frame_blur_enabled`) — **keine** ML-Abhängigkeit, kein Asset |
| `feature-settings` | Anonymisierungs-Sektion (Master-Toggle, Face-Blur, ggf. Empfindlichkeit) — P3 |
| ML-Assets | **entfällt (Route A)** — kein Modell, kein Download, kein FOSS-/Standard-Flavor-Unterschied |

**Route A (P2-Entscheidung):** Die Skizze plante ursprünglich `FaceDetectionAnalyzer`
mit TFLite/BlazeFace (~300 KB Asset, Interpreter-Pflege, Flavor-Paritäts-Test). P2
setzt stattdessen die **bereits vorhandene Hardware-Face-Detect-Erkennung** von
RootEncoder 2.7.5 ein (`Camera2Base.enableFaceDetection` bzw.
`Camera2ApiManager` im Idle-Pfad). Ergebnis: **keine neue Dependency, kein Asset,
kein FOSS-Risiko** — der Preis ist die Geräteabhängigkeit der Capability
(`AVAIL_FACE_DETECT_MODE`, §8) und die offene Verifikation der
Rotations-/Mirror-Konvention (§8).

## 7. Datenschutz-Postulat (self-imposed)

- **On-device only:** Kein Frame, keine Erkennungs-Telemetrie verlässt das Gerät;
  mit Route A gibt es **gar kein ML-Modell** — die Erkennung läuft im
  Kamera-Stack, ohne Download und ohne Update-Channel.
- **Keine Speicherung von Erkennungsergebnissen:** Boxen leben nur im RAM (StateFlow),
  keine Logs (LogRedactor-Konvention), keine Sentry-Extras.
- **Sichtbarkeit als Prinzip:** Die Anonymisierung ist auf der Vorschau immer
  sichtbar — der Streamer sieht exakt, was gesendet wird (§3).

## 8. Risiken & Gegenmaßnahmen

| Risiko | Einordnung | Gegenmaßnahme |
|---|---|---|
| `clearFilters()` der Bestands-Controller löscht Privacy-Render | real (P0-Kernproblem) | Kanonischer Rebuild (§4 Weg 2); Privacy-Render-Instanz wiederverwenden; Rebuild atomar (nie Kette ohne Privacy in der Zwischenzeit) |
| Render-Thread-Racing (Uniform-Update während draw) | niedrig–mittel | Uniform-Update nur über Render-Thread (Choreographer/Frame-Gate), `volatile`-Snapshot statt Lock im draw-Pfad |
| Kein Face-Modus am Gerät (Emulator, manche Rückkameras) | real (Route A) | `FaceDetectionProducer` meldet `faceDetectionAvailable` (`null` = nie versucht, `false` = Capability nicht erfüllt); 10 Nachversuche à 200 ms decken die Kamera-Öffnung ab. Ohne Modus bleiben **manuelle Zonen** unberührt wirksam (§1 Garantie) |
| Erkennung zu teuer für den Stream (fps/Verzögerung) | mittel | Gesichts-Frames aktualisieren **nur die Uniforms** — ausdrücklich kein Ketten-Rebuild (`StreamingEngineFacesTest`); Akku-/FPS-Messung bleibt P2-Manual-Proof |
| Detektion verfehlt Gesichter (Profile, Nacht, Distanz) | real | Zonen als Garantie-Fallback (§1); UI-Hinweis „Erkennung unterstützt, ersetzt keine Prüfung“ (Schritt 3/P3); Debug-Overlay zur Verifikation |
| Kamera-Rotation/Mirror verschiebt Boxen | mittel (**Gerätetest offen**) | Feste Konvention in `FaceEllipseMapper` (spiegelungsfrei, Front/Back + Display-Rotation, Y-Flip wie die Zonen) mit Goldenwerten in `FaceEllipseMapperTest`; eine Umkehrung betrifft nur `rotationDegreesFor` und die Konventions-KDoc. Manuelles Prüfen je Ausrichtung + Quelle (Kamera/Screen-Capture) |
| Ganzbild-Mosaik lässt Formen noch erkennbar (Standort-Leck bei zu feinem Grid) | real (P2b existiert genau deshalb) | Getrennte Grid-Stufen: Zonen/Gesichter 1/48 (Details weg, Silhouetten bleiben als Prüfhinweis), Ganzbild **1/16** (≈ 120 px bei 1080p) — Architektur ist dann nicht mehr lesbar. Die Stufe ist der zentrale Geräte-Proof (1 h Stream: bleibt Umgebung erkennbar, dann grober); bewusst kein Gaussian-Blur (Ein-Pass-Shader, ein zweiter FBO-Pass wäre teuer und flackert) |
| Bestands-Tests frieren Single-Slot-Semantik ein | sicher (P0) | Umgeschriebene Tests als bewusster, dokumentierter Semantik-Wechsel (RELEASE.md); kein stiller Test-Input |

## 9. Offene Fragen (Entscheidungen während der Implementierung)

1. **Pixelate vs. Blur je Zone** (oder global)? Skizze: global umschaltbar, Default Blur.
   *In P2b entschieden (2026-10-16):* **Pixelate bleibt** — es ist der Ein-Pass des
   bestehenden Shaders, ohne Extra-Framebuffer und ohne Flackern. Statt Blur wird die
   **Stufe** variiert: 1/48 für Zonen/Gesichter (Prüfbarkeit), 1/16 fürs Ganzbild
   (`FULL_FRAME_PIXEL_GRID`, Standort-Schutz). Ein echter Blur käme nur mit einem
   zweiten FBO-Pass und ist bewusst nicht Teil dieser Skizze.
2. **N-Grenze:** 8 Ellipsen (4+4) als Start — reicht das für „viele Gesichter“-Szenen,
   oder Uniform-Array dynamisch (Recompile bei Änderung)?
   *In P2 festgelegt:* harte Grenze 8 (`MAX_ELLIPSES`), **Zonen zuerst**, Gesichter
   füllen nur den freien Rest (`budget = 8 - Zonen`), Überschuss wird verworfen —
   nie über 8, weil `setEllipses` die Grenze per `require` erzwingt. Offen bleibt
   nur, ob 4 Gesichter-Slots in dichten Szenen reichen (Geräte-Messung).
3. **Face-Blur-Toggle-Sichtbarkeit** während des Streams (Quick-Toggle im Streaming-Screen
   neben `!fx`-Button) — ja/nein? → *In P2 Schritt 3 entschieden (2026-10-31):* **ja**, als
   eigene Zeile unter dem Anonymisierungs-Master-Toggle (nicht neben `!fx` — der Master-Toggle
   ist das Sicherheitsnetz, die Unterzeilen erscheinen nur bei aktivem Master und wirken auch
   während des Streams; `face_blur_enabled` wird von der VM-`init` zurückgestellt). Auf
   Geräten ohne Face-Capability ist der Toggle wirkungslos — bewusst kein Ausblenden, die
   Capability-Meldung des Producerns bleibt der Anker für eine spätere Sperre/Ausgrauung (§8).
4. **Screen-Capture-Zonen** quellrelativ (Fensterkoordinaten) oder global? Skizze: quellrelativ,
   Wechsel der Quelle resettet die Zonen-Editor-Ansicht, Persistenz bleibt je Quelle.
5. **Erkennungs-Route:** entschieden 2026-10-16 auf **Route A** (Hardware, §6);
   TFLite/BlazeFace nur Revival, wenn die Hardware-Erkennung auf den Zielgeräten
   durchgängig versagt (§8 Capability-Zeile).

## 10. Verweise

- `feature-streaming/StreamingEngine.kt` (Filterpfad, `setVideoFilter`/`setLutPreset`/`toggleLowLightBoost`; P2-Block: `faceDetectionDesired`, `setFaceBlurEnabled`, `attachFaceDetection`, `publishPrivacyEllipses`, `publishedPrivacyEllipses`, injizierbare `cameraFaceDetectableFactory`/`idleFaceDetectableFactory`/`displayRotationDegrees`)
- `feature-streaming/FaceEllipseMapper.kt` + `FaceSmoother.kt` + `FaceDetectionProducer.kt` (Route A: Abbildung, Glättung, Lebenszyklus/Capability)
- Tests: `FaceEllipseMapperTest`, `FaceSmootherTest`, `FaceDetectionProducerTest`, `StreamingEngineFacesTest`, `StreamingEngineFullFrameTest` (P2b), `PrivacyComposerTest`, `PrivacyBlurFilterRenderRobolectricTest`, `StreamingEngineFiltersTest` (P1-Mapping/Idle-Guard)
- `core/.../ZoneRepository.kt` (`zonesFlow`/`faceBlurEnabledFlow`/`fullFrameBlurEnabledFlow`, `setFaceBlurEnabled`/`setFullFrameBlurEnabled`) + `ZoneRepositoryTest`
- `VideoFilterController`/`LutController`/`LowLightBoostController` (Single-Slot-Applier — P0-Umstellung)
- `PrivacyBlurFilterRender` (`MAX_ELLIPSES = 8`, `setEllipses`-`require`, P2b: `setFullFrame`/`setPixelGrid` + `DEFAULT_PIXEL_GRID`/`FULL_FRAME_PIXEL_GRID`) + `PrivacyComposer` (Rebuild vs. Uniform-Update, P2b: `setFullFrame`)
- `res/raw/privacy_blur_fragment.fsh` (Ellipsen-Schleife + `uFullFrame`-Maske + `uPixelGrid`-Fallback)
- `StreamingEngine.kt` P2b-Block (`fullFramePrivacy`, `setFullFramePrivacy`) — reiner Soll-Zustand, kein Slot-Verbrauch
- `LowLightBrightnessFilterRender` + `res/raw/simple_vertex.vsh`/`brightness_fragment.fsh` (Custom-Filter-Vorlage)
- `HaldClutFilterRender` (Bitmap-Asset-Pfad), `HaldClutLutBitmapRobolectricTest` (Robolectric-GL-Test-Muster)
- RootEncoder 2.7.5: `GlInterface.setFilter/addFilter(int, …)/removeFilter/clearFilters/filtersCount`,
  `MainRender.filterRenders: List<BaseFilterRender>` (FBO-Kette, `reOrderFilters`),
  `Camera2Base.enableFaceDetection(FaceDetectorCallback)/disableFaceDetection/isFaceDetectionEnabled`,
  `FaceDetectorCallback.onGetFaces(Array<Face>, Rect?, Int)` (skaliertes Aktiv-Array + Sensor-Orientierung),
  `FaceDetectorUtil.camera2Parse` (**nicht** genutzt — Ergebnis-Bereich nicht vertrauenswürdig, Konvention selbst festgelegt)
- PARITY.md: Bucket „Datenschutz-Anonymisierung“ (neu, diese Skizze)
- Konventionen: `LogRedactor` (nie ins Log), FOSS-Flavor-Grenze (keine Google-Binaries),
  Two-Commit-Muster (Code → PARITY → Dashboard)
