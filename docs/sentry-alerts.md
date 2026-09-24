# Sentry-Alerts → GitHub-Issues (automatische Dringlichkeits-Triage)

Sentry-Reports werden automatisch als GitHub-Issues in diesem Repo erstellt
und sofort mit einem **Dringlichkeits-Label** versehen. Diese Seite
dokumentiert den Einrichtungs-Click-Pfad (einmalig in der Sentry-Web-UI) und
die Label-Logik.

## 1. Sentry-GitHub-Integration verbinden (einmalig)

1. <https://privat-jb.sentry.io/settings/integrations/github/> öffnen
   (Settings → Integrations → GitHub)
2. **Add to Project / Install** — den GitHub-Flow durchlaufen lassen
   (Repo-Zugriff auf `thoser666/Vivid` gewähren).
3. Zurück in Sentry: die Integration öffnen → **Repository** `thoser666/Vivid`
   verknüpfen (Configurations → Link Repository).

## 2. Alert-Regel mit Issue-Erstellung (einmalig pro Regel)

1. <https://privat-jb.sentry.io/alerts/rules/vivid/new/> öffnen
   (Alerts → Vivid → Create Alert).
2. Typ: **Issue Alert** — Conditions z. B. „A new issue is created" bzw.
   „The issue changes state from unresolved to resolved" umgekehrt;
   für crashes: Filter „The event's type is error" + Level-Filter.
3. **Filter nach Level:** Für eine Stufen-Regel im Environment/Level-Filter
   das gewünschte Level festlegen (z. B. `error`/`fatal`) — die Regel-Actions
   unten schreiben dieses Level als `Severity:`-Zeile ins Issue.
4. **Action hinzufügen:** „**Create a GitHub issue**" → Project `thoser666/Vivid`
   wählen. Das vom Sentry-Server erzeugte Issue-Template enthält u. a.
   `Sentry Issue: …` + Stacktrace (genau diese Marker nutzt der Triage-Step).
5. Optional zweite Regel für niedrigere Levels (z. B. `warning`), gleiche
   Action — Dringlichkeit kommt unten an.

> **Warum die native Integration und nicht Polling?** Der Sentry-Server öffnet
> das Issue **sofort beim Event** (Echtzeit, ohne PAT, ohne Rate-Limits) —
> ein GitHub-seitiger Poller wäre seconds-to-minutes hinterher und bräuchte
> ein Sentry-Lese-Token.

## 3. Automatische Dringlichkeit im Repo (läuft schon)

`.github/workflows/automation-sentry-triage.yml` triggert bei
`issues: [opened, reopened]` und setzt — idempotent, ohne Auto-Kommentar:

| Bedingung | Ergebnis |
|---|---|
| `Severity:`-Zeile im Issue-Body (von der Alert-Regel) | `severity:critical` (fatal/critical) · `severity:high` (error/high) · `severity:medium` (warning/medium) · `severity:low` (info/low) |
| Crash-Signal im Stacktrace (`ExceptionInInitializerError`, `PatternSyntaxException`, `UnsatisfiedLinkError`, `FATAL EXCEPTION`) oder `crash`-Label/Titel | mindestens `severity:high` |
| Kein Match (unbestimmter Report) | `severity:low` — als Aufforderung zur manuellen Triage |

Zusätzlich bekommt jedes Sentry-Report-Issue das Label **`sentry`**
(Sichtbarkeit, Filterbarkeit). Bestehende `severity:*`-Labels werden nie
herabgestuft (kein Downgrade bestehender Bewertungen).

**Sonderpfad `severity:critical`:** Critical-Issues erhalten zusätzlich das
Label **`crash`** (Filterbarkeit im Board) und der Issue-Body wird um einen
Hinweis auf das [CrashAdvisoryRegistry-Verfahren](https://github.com/thoser666/Vivid/blob/develop/core/src/main/java/com/vivid/core/startup/CrashAdvisory.kt)
ergänzt (versionCode-Range + Workaround/Kill-Switch pro Release, siehe
RELEASE.md → 🛰️ Sentry-Ops). Die Anreicherung ist marker-basiert idempotent
(`<!-- crash-advisory-registry-hint -->`) — der Sentry-Body wird nicht
überschrieben, ein Re-Sync fügt den Hinweis nicht doppelt ein.

## 4. Label-Referenz

| Label | Farbe | Bedeutung |
|---|---|---|
| `sentry` | `#3b82f6` | Automatisch aus Sentry erstellt (Report/Alert) |
| `severity:critical` | `#b91c1c` | Kritisch — Crash/Totalausfall, sofort |
| `severity:high` | `#ea580c` | Hoch — Crash-Klasse/Kernfunktion blockiert |
| `severity:medium` | `#d97706` | Mittel — Beeinträchtigung mit Workaround |
| `severity:low` | `#ca8a04` | Niedrig — kosmetisch/unbestimmt, Triage |

## 5. Selbsttest & Verträge

- `scripts/test_sentry_triage_workflow.sh` (W1–W9) im Pre-Push-Gate prüft
  Trigger (kein `labeled`-Event — Loop-Schutz), `permissions: {}` top-level
  mit Job nur `issues: write`, den github-script-SHA-Pin, Severity-Parsing,
  Crash-Signale, Fallback-Label und Idempotenz.
- Re-Labeling bei `reopened` ist absichtlich aktiv — ein wieder geöffnetes
  Sentry-Issue (Regression) bekommt die aktualisierte Dringlichkeit.
