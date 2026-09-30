#!/usr/bin/env bash
# Selbsttest fuer scripts/contributors_reminder.sh + den zugehoerigen Workflow
# (Offline, keine Netzwerk-Calls): beweist die Vertraege, die den Reminder
# sicher machen — State-Fenster/Marker, Fork-Erkennung inkl. geloeschter
# Forks, atomares State-Schreiben, Zeilenstatus-Parser (auch CRLF) und
# kein Bot-PR-Mechanik. Vertragsliste siehe CONTRIBUTING.md.
set -euo pipefail
cd "$(dirname "$0")/.."

script="scripts/contributors_reminder.sh"
workflow=".github/workflows/automation-contributors-reminder.yml"

PASS=0; FAIL=0
fail() { echo "❌ FAIL: $1"; FAIL=$((FAIL+1)); }
ok()   { PASS=$((PASS+1)); }

# ── C1: Script existiert und ist syntaktisch valide ──
if [[ -s "$script" ]] && bash -n "$script" 2>/dev/null; then ok; else fail "C1: $script fehlt, leer oder Syntaxfehler"; fi

# ── C2: Dedup-/State-Anker vorhanden ──
grep -Fq '<!-- contributors-reminder -->' "$script" && ok || fail "C2a: Issue-Marker fehlt (Dedup bricht)"
grep -Fq 'contributors-state:' "$script" && ok || fail "C2b: State-Token fehlt (Fenster bricht)"

# ── C3: State wird atomar im Issue-Body mitgeschrieben (ein API-Call) ──
grep -Fq 'body=@$BODY_FILE' "$script" && ok || fail "C3: Body muss per -F body=@-Datei atomar uebergehen"

# ── C4: Fork-Erkennung inkl. geloeschter Forks ──
grep -Fq 'head.repo == null' "$script" && ok || fail "C4a: geloeschte Forks (head.repo == null) werden nicht erkannt"
grep -Fq 'head.repo.fork == true' "$script" && ok || fail "C4b: aktive Forks werden nicht erkannt"

# ── C5: Keine Bot-PR-Mechanik (Vertrag bewusst nicht anwendbar) ──
if grep -Eq 'gh pr create|git push' "$script"; then fail "C5: Reminder darf keine Branches pushen / keine PRs erzeugen"; else ok; fi

# ── C6: ISO-Zeitstempel werden validiert, bevor sie ins --jq eingebettet werden ──
grep -Fq '^[0-9T:.Z-]+$' "$script" && ok || fail "C6: Zeitstempel-Validierung gegen Daten-in-Code fehlt"

# ── C7: Pipe-Zeichen in PR-Titeln werden escaped (Tabellen-Vertrag) ──
grep -Fq '${title//|/\\|}' "$script" && ok || fail "C7: Titel-Pipes werden nicht escaped"

# ── C8: Workflow — Default-Deny, least privilege, SHA-Pin, Trigger ──
[[ -s "$workflow" ]] || fail "C8a: Workflow fehlt"
head -30 "$workflow" | grep -Eq '^permissions:[[:space:]]*\{\}' && ok || fail "C8b: Top-Level-Permissions muessen leer sein"
grep -Eq 'issues:[[:space:]]*write' "$workflow" && ok || fail "C8c: Job-Permission muss genau issues: write sein"
grep -Eq 'actions/checkout@[0-9a-f]{40}' "$workflow" && ok || fail "C8d: Checkout muss auf 40-Zeichen-SHA gepinnt sein"
grep -Fq "github.actor != 'dependabot[bot]'" "$workflow" && ok || fail "C8e: Dependabot-Guard fehlt"
grep -Eq 'branches:[[:space:]]*$|branches: \[develop\]' "$workflow" && ok || fail "C8f: push-Trigger auf develop fehlt"
grep -Fq 'workflow_dispatch' "$workflow" && ok || fail "C8g: manueller Trigger fehlt"

# ── C10: --jq liefert Zeilen-Stream (kein Array-Literal) — Regression zum
# Smoke-Vorfall: "[[...] | .[]]" bleibt ein Array-Literal, gh druckt den
# Block als EINE JSON-Zeile und das IFS-Parsing erzeugt Müll-Befunde.
if grep -Fq -- '--jq ".[] | select(.merged_at' "$script"; then ok; else fail "C10a: Pull-Sammlung muss als Zeilen-Stream ge-jqt werden (.[] | …)"; fi
if grep -Fq '[[.[]' "$script"; then fail "C10b: Array-Klammer um den @tsv-Stream erzeugt eine Müll-JSON-Zeile"; else ok; fi

# ── C11: Öffentlicher Dank-/Ankündigungs-Kommentar am PR (Marker + Editierbarkeit) ──
grep -Fq 'contributors-credit' "$script" && ok || fail "C11a: Credit-Marker fehlt (Dank-Kommentar-Vertrag)"
grep -Fq 'credit_comment_for' "$script" && ok || fail "C11b: existierende Credit-Kommentare müssen vor dem Posten gesucht werden (Idempotenz)"
grep -Fq 'issues/comments/' "$script" && ok || fail "C11c: PATCH-Editierbarkeit fehlt (Ankündigung kann nicht zum Dank aktualisiert werden)"

# ── C12: Geschlossener Regelkreis — pending-Tracking im State-Kommentar ──
grep -Fq 'pending:' "$script" && ok || fail "C12a: pending-Liste fehlt im State-Token (Nachzieh-PRs werden sonst vergessen)"
grep -Fq 'diesen Kommentar automatisch zum Dank' "$script" \
  && ok || fail "C12b: Ankündigung muss das automatische Dank-Update versprechen (geschlossener Regelkreis)"

# ── C13: Autor-Erwähnung kommt aus der API (.user.login), nie als Code interpoliert ──
grep -Fq '.user.login' "$script" && ok || fail "C13a: Autor muss aus der Pulls-API kommen (.user.login)"
if grep -Eq 'user\\.login.*\|\|.*sh|\\$\(.*user\\.login' "$script"; then
  fail "C13b: user.login darf nicht in Shell-Kommandos interpoliert werden"
else
  ok
fi

# ── C14: Beide Modi mit @-Mention am Autor ──
grep -Fq '@$author' "$script" && ok || fail "C14: Dank/Ankündigung muss den Autor explizit erwähnen (@$author)"

# ── C9: status_for-Parser funktional testen (echter Code, Fixtures inkl. CRLF) ──
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fn=$(sed -n '/^status_for() {/,/^}/p' "$script")
[[ -n "$fn" ]] || fail "C9a: status_for kann nicht extrahiert werden"
printf '# Contributors\r\n\r\n| Person | Beitrag | Referenz | Status |\r\n|---|---|---|---|\r\n| smka | Sprachauswahl | #213 | offen |\r\n| smka | Kamera-Vorschau | #230 | umgesetzt |\r\n' > "$tmp/CONTRIBUTORS.md"
eval "$fn"
CONTRIBUTORS_FILE="$tmp/CONTRIBUTORS.md"
[[ "$(status_for 999)" == "fehlt" ]]            && ok || fail "C9b: fehlender Eintrag muss 'fehlt' melden (bekam: $(status_for 999))"
[[ "$(status_for 213)" == "offen" ]]            && ok || fail "C9c: 'offen' nach Merge muss erkannt werden (bekam: $(status_for 213))"
[[ "$(status_for 230)" == "umgesetzt" ]]      && ok || fail "C9d: gepflegter Eintrag (CRLF) muss 'umgesetzt' lesen (bekam: $(status_for 230))"
printf '| X | Y | #301 | offen |\n| X | Y | #301 | umgesetzt |\n' > "$tmp/dup.md"
CONTRIBUTORS_FILE="$tmp/dup.md"
[[ "$(status_for 301)" == "umgesetzt" ]]      && ok || fail "C9e: bei mehreren Zeilen muss die letzte gewinnen (bekam: $(status_for 301))"
printf '| X | Y | #401 | offen |\n' > "$tmp/bad.md"
CONTRIBUTORS_FILE="$tmp/bad.md"
[[ "$(status_for 401)" == "offen" ]]            && ok || fail "C9f: LF-Datei offen-Zeile (bekam: $(status_for 401))"

echo "✅ [contributors-reminder-test] $PASS Pass, $FAIL Fail"
[[ "$FAIL" -eq 0 ]]
