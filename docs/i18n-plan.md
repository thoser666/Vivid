# 🌍 I18n-Plan: String-Externalisierung & Lokalisierung

**Status:** ✅ Abgeschlossen (2026-08-20) · **Ziel erreicht:** Alle UI-Strings sind aus dem Compose-Code in Modul-`strings.xml` ausgelagert; Deutsch (Default) + Englisch (`values-en`) + Französisch (`values-fr`) sind in **allen** Modulen vollständig. CI-Gates (Externalisierungs- + Vollständigkeits-Check) sind aktiv.

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

**Bestehende Ressourcen:** `app/src/main/res/values/strings.xml` (Grundgerüst). `R.string`-Nutzung im Compose-Code war vor der Externalisierung fast null.

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
| `values-fr/` (fr) | Pflicht | Vollständige französische Übersetzung (seit 2026-08-20 in allen Modulen) |

**Mechanik:** Alle Module sind auf `values/` vollständig; `values-en` und `values-fr` wurden parallel ergänzt. Kein String darf ohne Übersetzung in `values-en` oder `values-fr` fehlen (CI-Check, siehe §5).

## 4. Was NICHT lokalisiert wird

- **Bot-Antworten & LLM-Prompts** (`feature-chat`, inkl. der `!diag`-Ausgabe in `AppChatStreamControl`): bewusst in der Sprache des Streamers/Viewers — der System-Prompt ist bereits konfigurierbar (Settings). Bot-Kommandos wie `!help`/`!diag` bleiben deterministisch (Englisch/Deutsch gemischt, dokumentiert in [ai-chat-bot.md](ai-chat-bot.md)).
- **Plattform-/Technik-Texte:** Stream-Keys, URLs, JSON-Feldnamen, Fehler von Dritt-Bibliotheken.
- **Technische Fehlerdetails:** Exception-Messages werden in lokalisierte Rahmen eingesetzt (z. B. „Update-Check fehlgeschlagen: %1$s“, „Fehler: %1$s“); das Detail selbst bleibt unübersetzt. Technische Fallbacks sind bewusst englisch (z. B. `unknown error`, `network error`).
- **Versionierungstexte:** Release-Kanal-Suffixe (`nightly`/`alpha`/`beta`/`rc`/`stable`) sind Teil der Versionsidentifikation und bleiben unübersetzt.
- **Log-Ausgaben** (dürfen englisch bleiben, sind nicht UI).

## 5. CI-/Qualitäts-Checks (alle aktiv)

1. **Externalisierungs-Gate:** `scripts/check_i18n.sh` — `Text(“…”)`, `contentDescription = “…”`, `label = “…”` / `title = “…”` in `src/main` der UI-Module (feature-settings, feature-obs-control, feature-streaming, feature-chat, feature-widgets, app) liefern **0 Treffer**. Ausnahmen: `AppChatStreamControl.kt` (`!diag`-Bot-Ausgabe), Bot-Antworten/-Befehle in feature-chat (BotCommandProcessor, ChatBotEngine, TwitchSendChatClient-Exceptions, TwitchModerationClient-Bestätigungen) und technische Konstanten (WidgetFormatters-Einheiten km/h/m) — bewusst nicht lokalisiert → verhindert Rückfall auf Hartkodierung.
2. **Vollständigkeits-Check:** derselbe Guard vergleicht `values/strings.xml` ↔ `values-en/strings.xml` ↔ `values-fr/strings.xml` pro Modul (fehlende Keys in einer Richtung = Fehler). Deckt **sieben Module** ab: feature-settings, feature-obs-control, feature-streaming, feature-chat, feature-widgets, app **und core** (seit 2026-08-21; `core` hat eigene Ressourcen, z. B. Update-Check-Fehlertexte — vorher nicht CI-gesichert).
3. **stream_url_hint-Inhalts-Guard:** Der Hinweis unter dem Stream-URL-Feld muss in **allen drei** Sprachen die Kernaussagen nennen (RTMP, SRT, Owncast, Presets bzw. préréglages) — die custom-Plattform-Fähigkeit bleibt so sichtbar.
4. **Selbsttest:** `scripts/test_check_i18n.sh` (5 Fixtures: sauber grün, hartkodierter String rot, fehlende Übersetzung rot, Hint ohne Owncast rot, Repo-Regression grün) — läuft in Pre-Push und CI.
5. **Lint:** Android-Lint meldet `HardcodedText`-Warnungen — als `warningsAsErrors` im CI aktiviert.

**Verdrahtung:** `scripts/check_i18n.sh` + `scripts/test_check_i18n.sh` in `scripts/pre-push.sh` (mit Assertionen in `scripts/test_pre_push.sh`) und im `guard-secrets`-Job von `.github/workflows/android-ci.yml`.

## 6. Aufwand & Abgrenzung

- **Umgesetzt (2026-08-20):** ~110+ Literale über 4 Module + Validierungs-/Notification-/Update-Fehlertexte; `values-en` **und** `values-fr` in app/core/feature-settings/feature-obs-control/feature-streaming (jeweils vollständig, CI-geprüft).
- **Nachgezogen (2026-08-20):** Chat-Overlay-Status (feature-chat) und Text-/Info-Widget-Labels (feature-widgets) externalisiert — beide Module haben jetzt eigene `res/values{,-en,-fr}`; der Guard prüft sie mit (6 Module).
- **Härtung (2026-08-21):** `core` in die Guard-Modul-Liste aufgenommen (7 Module) — `core` hat eigene lokalisierte Ressourcen (Update-Check-Fehlertexte), die vorher nicht CI-gesichert waren.
- **Kein UI-Umbau nötig gewesen** — reine Mechanik (Strings → Ressourcen); Enum-Anzeigenamen/Validierungsmeldungen wurden auf `@StringRes`-IDs umgestellt (Tests prüfen Ressourcen-IDs statt deutscher Texte).
- **Resultat:** PARITY-Zeile I18n ✅, README „In Progress“ leer → **letzter offener In-Progress-Punkt geschlossen**; Gesamt-Zähler 21/3/21.

---

*Siehe auch: [PARITY.md – Plattform & Grundlagen](../PARITY.md) · [README – Features](../README.md)*
