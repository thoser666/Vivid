# 🌍 I18n-Plan: String-Externalisierung & Lokalisierung

**Status:** 🚧 In Arbeit · **Ziel:** Alle UI-Strings aus dem Compose-Code in `strings.xml` auslagern und mindestens Deutsch (Default) + Englisch vollständig liefern.

> Hintergrund: Der PARITY-Punkt „I18n (lokalisierte Strings)“ (Plattform & Grundlagen) ist **nur als Grundgerüst** angelegt — die Compose-UI nutzt überwiegend hartkodierte deutsche Literale. Dieses Dokument ist der konkrete Arbeitsplan, um den Punkt abzuschließen.

---

## 1. Ist-Zustand (Messung, 2026-08-18)

| Modul | `Text("…")`-Literale | Sonstige Strings (Labels, `contentDescription`, Fehlermeldungen, Logs) |
|-------|----------------------|------------------------------------------------------------------------|
| `feature-settings` | 53 | Sektions-Titel, Dialoge, Toggle-Labels, Platzhalter — der größte Brocken |
| `feature-obs-control` | 10 | OBS-Formular-Labels, Verbindungs-Status |
| `app` | 7 | About/Update, Streaming-Screen-Status |
| `feature-streaming` | 4 | Status-Texte im StreamingScreen |
| `feature-chat` | 0 | — (kein Compose; Bot-Texte sind **bewusst** nicht zu lokalisieren, siehe §6) |
| `feature-widgets`, `core`, `domain` | 0 | — |
| **Summe** | **≈74** | zzgl. ~15–25 weitere Muster (z. B. `"…" + variable`-Konkatenation) |

**Bestehende Ressourcen:** `app/src/main/res/values/strings.xml` (Grundgerüst) + `values-fr/strings.xml` (partiell). `R.string`-Nutzung im Compose-Code ist aktuell fast null.

## 2. Vorgehen pro Modul (Reihenfolge = Aufwand absteigend)

1. **`feature-settings`** — alle 53+ Literale externalisieren (größter Block, deckt die komplette Menüstruktur ab: Übersicht, 5 Sub-Screens, Dialoge).
2. **`feature-obs-control`** — 10+ Literale (OBS-Formular, Status).
3. **`app`** — 7+ Literale (About/Update, Streaming-Status, Benachrichtigungstexte des Foreground-Service).
4. **`feature-streaming`** — 4+ Literale (Status-/Fehlertexte auf dem StreamingScreen).
5. **Querschnitt** — `contentDescription`-Werte (Barrierefreiheit), `Placeholder`-Texte, `Snackbar`/Dialog-Meldungen, Validierungsmeldungen (`StreamConfigValidator`).

**Technik pro Modul:**
- String-Ressource im Modul-eigenen `res/values/strings.xml` anlegen (Compose-Module haben eigene `res`-Ordner) — **nicht** zentral in `app` sammeln, damit die Module unabhängig bleiben.
- Im Compose-Code: `stringResource(R.string.key)` (Composable-Kontext) bzw. `context.getString(...)` (Non-Composable/ViewModel).
- **Platzhalter:** `%1$s`, `%2$d` mit `stringResource(R.string.key, arg1, arg2)`; Pluralformen via `<plurals>` (z. B. „1 Antwort“ / „n Antworten“).
- Komposition: `stringResource` nur in `@Composable`-Funktionen aufrufen, nie in `remember`-Blöcken speichern, die den Config-Change (Sprachwechsel) überleben müssen — bei Bedarf `LocalConfiguration.current` beachten.

## 3. Sprachstrategie

| Locale | Status | Zweck |
|--------|--------|-------|
| `values/` (de, Default) | Pflicht | App-Sprache bleibt Deutsch (Zielgruppe IRL-Streamer DACH) |
| `values-en/` (en) | Pflicht | Vollständige englische Übersetzung |
| `values-fr/` (fr) | Bestehend | Vorhandene Teilübersetzung pflegen/ergänzen (nice-to-have) |

**Mechanik:** Nach der Externalisierung sind alle Module auf `values/` vollständig; danach `values-en` und `values-fr` parallel ergänzen. Kein String darf ohne Übersetzung in `values-en` fehlen (CI-Check, siehe §5).

## 4. Was NICHT lokalisiert wird

- **Bot-Antworten & LLM-Prompts** (`feature-chat`): bewusst in der Sprache des Streamers/Viewers — der System-Prompt ist bereits konfigurierbar (Settings). Bot-Kommandos wie `!help`/`!diag` bleiben deterministisch (Englisch/Deutsch gemischt, dokumentiert in [ai-chat-bot.md](ai-chat-bot.md)).
- **Plattform-/Technik-Texte:** Stream-Keys, URLs, JSON-Feldnamen, Fehler von Dritt-Bibliotheken.
- **Log-Ausgaben** (dürfen englisch bleiben, sind nicht UI).

## 5. CI-/Qualitäts-Checks

1. **Externalisierungs-Gate:** Neuer Guard-Check (analog `scripts/guard_secrets.sh`): `git grep 'Text("'` in den UI-Modulen darf **keine** Treffer mehr liefern (Ausnahme: erlaubte Dateien wie Test-Fixtures) → verhindert Rückfall auf Hartcodierung.
2. **Vollständigkeits-Check:** Skript vergleicht `values/strings.xml` gegen `values-en/strings.xml` (fehlende Keys = Fehler) — analog zum Play-Metadaten-Check `scripts/check_play_metadata.sh`.
3. **Lint:** Android-Lint meldet bereits `HardcodedText`-Warnungen — als `warningsAsErrors` im CI aktiviert.

## 6. Aufwand & Abgrenzung

- **Geschätzt:** ~74 Literale + ~20 weitere Muster über 4 Module; reine Externalisierung ≈ 1–2 Sessions, Übersetzung EN/FR zusätzlich.
- **Kein UI-Umbau nötig** — reine Mechanik (Strings → Ressourcen), keine Verhaltensänderung.
- Danach: PARITY-Zeile I18n auf ✅, README „In Progress“ leer → **letzter offener In-Progress-Punkt geschlossen**.

---

*Siehe auch: [PARITY.md – Plattform & Grundlagen](../PARITY.md) · [README – Features](../README.md)*
