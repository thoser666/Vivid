# 🔒 Architektur: Datenschutz-Anonymisierung im Videopfad (Auto-Gesichter + Zonen)

> **Status:** Skizze festgelegt (2026-09-26) · **P0 implementiert (2026-09-26)** — Filter-Komposition + Privacy-Shader verhaltensneutral · **Datum:** 2026-09-26
> **Tracked in:** [PARITY.md, Bucket „Datenschutz-Anonymisierung"](../../PARITY.md) · **Vivid-Zusatz** (Moblin hat keine Anonymisierung — analog Owner-Steuerung/Chat-TTS als eigener Nutzwert)
> **User-Entscheidung:** Stufe 2 (automatische Gesichtsanonymisierung) ist Ziel; Stufe 1 (Zonen) ist Fallback und P0-Baustein

---

## 1. Ziel

Passanten, Kennzeichen, Hausnummern und Bildschirme sollen **vor dem Senden** (und vor
Replay/Preview) unkenntlich gemacht werden können — direkt in der GL-Encoder-Pipeline,
nicht nachträglich im aufgenommenen Material. Zwei Mechanismen, die sich ergänzen:

1. **Manuelle Zonen** (Stufe 1, P0): 1–4 vom Streamer platzierte Ellipsen werden im
   Shader stark geblurrt. **Garantie-Mechanismus** — was in der Zone liegt, wird
   immer unkenntlich, egal was die Erkennung tut.
2. **Automatische Gesichter** (Stufe 2, P1–P3): On-Device-Gesichtserkennung
   (TensorFlow Lite + BlazeFace) liefert Gesichtsboxen, die derselbe Blur-Shader
   unkenntlich macht. **Unterstützungs-Mechanismus** — verfehlt gelegentlich
   Profile/Nachtbilder.

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
| ML-Stack | keiner (FOSS-Flavor: keine Google-Binaries — ML Kit scheidet aus, gleiche Kategorie wie Sentry) | TFLite (Apache-2.0) + BlazeFace-Modell (~300 KB) ist der F-Droid-taugliche Weg |
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
│     (b) FaceDetectionAnalyzer (TFLite, ~5–10 fps, P2)              │
├────────────────────────────────────────────────────────────────────┤
│ L1  PrivacyBlurFilterRender (BaseFilterRender, GLSL): bis zu N      │
│     Ellipsen-Uniforms (Zentrum + Radien), weiche Kante, Pixelate    │
├────────────────────────────────────────────────────────────────────┤
│ L0  Modelle: PrivacyZone (persistierbar), DetectionBox (temporär)   │
└────────────────────────────────────────────────────────────────────┘
```

- **L1-Shader** ist die gemeinsame Schnittstelle: Producer (a) und (b) schreiben nur
  Ellipsen-Koordinaten — ob eine Zone von Hand oder von BlazeFace kommt, ist dem
  Shader egal. Bis zu **N = 8 Ellipsen** (4 Zonen + 4 Gesichter, ggf. Hochzählen;
  Uniform-Array-Größe kompilierzeitfest).
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
| **P1** | **Zonen (Stufe 1, UI):** `PrivacyZone`-Modell (normierte Koordinaten 0–1, quellrelativ), ZoneRepository (DataStore), Zonen-Editor im Streaming-Screen (Touch-Positionieren/Größe, max. 4 Zonen), Master-Toggle „Anonymisierung“, i18n DE/EN/FR | Controller-/Repository-Tests, VM-Tests (Zonen → Uniform-Update), Robolectric-UI-Smoke, Manual-Test auf dem Gerät (Kamera + Screen-Capture) |
| **P2** | **Gesichtserkennung (Stufe 2):** `FaceDetectionAnalyzer` (TFLite-Interpreter, BlazeFace short-range ~300 KB, gedrosselt ~5–10 fps auf `ImageAnalysis`-UseCase; FOSS-Flavor: TFLite-Task-Dep ist Apache-2.0, keine Google-Binaries), IoU-Matching zur Box-Glättung (gegen Flackern), Mapping Box→Ellipsen-Uniform (inkl. Rotations-/Mirror-Korrektur der Frontkamera), Toggle „Gesichter unkenntlich machen“ | Analyzer-Test mit goldenen Bitmaps (Robolectric), Glättungs-Unit-Tests (IoU/Verlassen), Flavor-Paritäts-Test (FOSS + standard bauen), Akku-/FPS-Messung (Manual, dokumentiert) |
| **P3** | **Robustheit + Presets:** Preset „Alle Gesichter + meine Zonen“ (Start-Flow beim Go-Live), Persistenz je Quelle (Kamera vorne/hinten, Screen-Capture getrennt), Debug-Overlay (Zonen/Kasten sichtbar nur lokal), Doku-Finalisierung (user-guide, RELEASE.md-Anonymisierungs-Abschnitt) | E2E-Manual-Check (Go-Live mit Zonen + Face-Blur), Lang-Lauf (1 h Stream, kein Leak/Flackern) |

**Nicht in dieser Skizze:** Objekt-Erkennung über Gesichter hinaus (Kennzeichen/
Text-OCR — eigener Bucket, falls gewünscht), Audio-Anonymisierung (Stimmen-Verzerrung),
serverseitige/externe ML (bewusst: on-device only, §7).

## 6. Modul-Zuordnung

| Modul | Neu |
|---|---|
| `feature-streaming` | L1 Render + L3 Composer + L2 ZoneRepository/Analyzer-Verkabelung, Streaming-Screen-Editor (P1/P2) |
| `domain` | `PrivacyZone`-Modelle (normalisiert, serialisierbar) |
| `core` | DataStore-Persistenz (Zonen, Toggles), TFLite-Abhängigkeit (nur wenn P2 startet) |
| `feature-settings` | Anonymisierungs-Sektion (Master-Toggle, Face-Blur, ggf. Empfindlichkeit) |
| ML-Assets | BlazeFace-Modell als Asset im FOSS- **und** Standard-Flavor (Apache-2.0, redistributions-tauglich; kein Google-Binary) |

## 7. Datenschutz-Postulat (self-imposed)

- **On-device only:** Kein Frame, keine Erkennungs-Telemetrie verlässt das Gerät;
  das ML-Modell ist ein statisches Asset (kein Download, keine Update-Channel).
- **Keine Speicherung von Erkennungsergebnissen:** Boxen leben nur im RAM (StateFlow),
  keine Logs (LogRedactor-Konvention), keine Sentry-Extras.
- **Sichtbarkeit als Prinzip:** Die Anonymisierung ist auf der Vorschau immer
  sichtbar — der Streamer sieht exakt, was gesendet wird (§3).

## 8. Risiken & Gegenmaßnahmen

| Risiko | Einordnung | Gegenmaßnahme |
|---|---|---|
| `clearFilters()` der Bestands-Controller löscht Privacy-Render | real (P0-Kernproblem) | Kanonischer Rebuild (§4 Weg 2); Privacy-Render-Instanz wiederverwenden; Rebuild atomar (nie Kette ohne Privacy in der Zwischenzeit) |
| Render-Thread-Racing (Uniform-Update während draw) | niedrig–mittel | Uniform-Update nur über Render-Thread (Choreographer/Frame-Gate), `volatile`-Snapshot statt Lock im draw-Pfad |
| TFLite-Größe/Akku | mittel | Gedrosselte Analyse-FPS, Interpreter lazy, Modell ~300 KB; Messung in P2 (Akku/FPS dokumentiert) |
| Detektion verfehlt Gesichter (Profile, Nacht, Distanz) | real | Zonen als Garantie-Fallback (§1); UI-Hinweis „Erkennung unterstützt, ersetzt keine Prüfung“; Debug-Overlay zur Verifikation |
| FOSS-Flavor-Bruch durch ML-Dep | niedrig (TFLite Apache-2.0) | Nur TFLite-Interpreter + Modell-Asset; Flavor-Paritäts-Build in P2-Pflichttests |
| Kamera-Rotation/Mirror verschiebt Boxen | mittel | Normalisierte Koordinaten relativ zur analysierten Frame-Orientierung; Front-Mirror-Korrektur im Analyzer (P2), Manual-Test je Ausrichtung |
| Bestands-Tests frieren Single-Slot-Semantik ein | sicher (P0) | Umgeschriebene Tests als bewusster, dokumentierter Semantik-Wechsel (RELEASE.md); kein stiller Test-Input |

## 9. Offene Fragen (Entscheidungen während der Implementierung)

1. **Pixelate vs. Blur je Zone** (oder global)? Skizze: global umschaltbar, Default Blur.
2. **N-Grenze:** 8 Ellipsen (4+4) als Start — reicht das für „viele Gesichter“-Szenen,
   oder Uniform-Array dynamisch (Recompile bei Änderung)?
3. **Face-Blur-Toggle-Sichtbarkeit** während des Streams (Quick-Toggle im Streaming-Screen
   neben `!fx`-Button) — ja/nein?
4. **Screen-Capture-Zonen** quellrelativ (Fensterkoordinaten) oder global? Skizze: quellrelativ,
   Wechsel der Quelle resettet die Zonen-Editor-Ansicht, Persistenz bleibt je Quelle.

## 10. Verweise

- `feature-streaming/StreamingEngine.kt` (Filterpfad, `setVideoFilter`/`setLutPreset`/`toggleLowLightBoost`)
- `VideoFilterController`/`LutController`/`LowLightBoostController` (Single-Slot-Applier — P0-Umstellung)
- `LowLightBrightnessFilterRender` + `res/raw/simple_vertex.vsh`/`brightness_fragment.fsh` (Custom-Filter-Vorlage)
- `HaldClutFilterRender` (Bitmap-Asset-Pfad), `HaldClutLutBitmapRobolectricTest` (Robolectric-GL-Test-Muster)
- RootEncoder 2.7.5: `GlInterface.setFilter/addFilter(int, …)/removeFilter/clearFilters/filtersCount`,
  `MainRender.filterRenders: List<BaseFilterRender>` (FBO-Kette, `reOrderFilters`)
- PARITY.md: Bucket „Datenschutz-Anonymisierung“ (neu, diese Skizze)
- Konventionen: `LogRedactor` (nie ins Log), FOSS-Flavor-Grenze (keine Google-Binaries),
  Two-Commit-Muster (Code → PARITY → Dashboard)
