#!/usr/bin/env bash
# Selbsttest für scripts/check_contributors.sh (Offline-Fixtures, 17 Fälle).
# Der Guard liest CONTRIBUTORS_FILE (Env) — fertige Fixtures beweisen, dass
# gültige Tabellen grün und jede Vertragsverletzung rot gemeldet wird.
# C14–C17 legen zusätzlich den Vertrag der Pre-Merge-Ankündigung fest (#330):
# angekündigte Zeile in der realen CONTRIBUTORS.md, EN/DE-Absätze in
# CONTRIBUTING.md und der belegte Umstand, dass das Flippen Handarbeit ist.
set -euo pipefail
cd "$(dirname "$0")/.."
guard="$PWD/scripts/check_contributors.sh"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

PASS=0; FAIL=0
fail() { echo "❌ FAIL: $1"; FAIL=$((FAIL+1)); }
ok()   { PASS=$((PASS+1)); }

valid_2rows() {
  cat > "$1" <<'MD'
# Contributors

| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka (Ilya K) | In-App-Sprachauswahl + russische Lokalisierung (Appearance, values-ru) | #213 | offen |
| Ada Lovelace | Chat-Farbverlauf-Bugfix | #99 | umgesetzt |
MD
}

# C1: gültige Tabelle → grün
valid_2rows "$tmp/good.md"
if CONTRIBUTORS_FILE="$tmp/good.md" bash "$guard" >/dev/null 2>&1; then ok; else fail "C1: gültige Tabelle muss grün sein"; fi

# C2: Datei fehlt → rot
if CONTRIBUTORS_FILE="$tmp/missing.md" bash "$guard" >/dev/null 2>&1; then fail "C2: fehlende Datei muss rot sein"; else ok; fi

# C3: falscher Header → rot
cat > "$tmp/badheader.md" <<'MD'
| Name | Beitrag | Ref | Status |
|------|---------|-----|--------|
| smka | Russisch | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/badheader.md" bash "$guard" >/dev/null 2>&1; then fail "C3: falscher Header muss rot sein"; else ok; fi

# C4: ungültiger Status → rot
cat > "$tmp/badstatus.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka | Russisch | #213 | teilweise |
MD
if CONTRIBUTORS_FILE="$tmp/badstatus.md" bash "$guard" >/dev/null 2>&1; then fail "C4: Status außerhalb der Allowlist muss rot sein"; else ok; fi

# C5: Referenz nicht #<Nummer> → rot
cat > "$tmp/badref.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka | Russisch | issue-213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/badref.md" bash "$guard" >/dev/null 2>&1; then fail "C5: Referenz ohne #<Nummer> muss rot sein"; else ok; fi

# C6: doppelte Referenz → rot
cat > "$tmp/dupref.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| A | eins | #213 | offen |
| B | zwei | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/dupref.md" bash "$guard" >/dev/null 2>&1; then fail "C6: doppelte Referenz muss rot sein"; else ok; fi

# C7: leere Person → rot
cat > "$tmp/emptyperson.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
|  | Russisch | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/emptyperson.md" bash "$guard" >/dev/null 2>&1; then fail "C7: leere Person muss rot sein"; else ok; fi

# C8: leerer Beitrag → rot
cat > "$tmp/emptybeitrag.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka |  | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/emptybeitrag.md" bash "$guard" >/dev/null 2>&1; then fail "C8: leerer Beitrag muss rot sein"; else ok; fi

# C9: Tabelle ohne Datenzeilen → rot
cat > "$tmp/norows.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
MD
if CONTRIBUTORS_FILE="$tmp/norows.md" bash "$guard" >/dev/null 2>&1; then fail "C9: leere Tabelle muss rot sein"; else ok; fi

# C10: das reale CONTRIBUTORS.md (Repo-Root) → grün
if CONTRIBUTORS_FILE="$PWD/CONTRIBUTORS.md" bash "$guard" >/dev/null 2>&1; then ok; else fail "C10: reale CONTRIBUTORS.md muss grün sein"; fi

# C11: abgeschnittene Zeile mit offenem Klammerrest → rot (#278-Defekt)
cat > "$tmp/truncated.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka (Ilya K) | Streaming-Bitrate an RootEncoder-Grenze (kbit/s vs bit/s) + Entwicklungsgate (Parity-Guard: 8-Zeichen-Hashes und Squash-Merge-Commits aus entitlement | #276 | umgesetzt |
MD
if CONTRIBUTORS_FILE="$tmp/truncated.md" bash "$guard" >/dev/null 2>&1; then fail "C11: abgeschnittene Zeile mit unbalancierter Klammer muss rot sein"; else ok; fi

# C12: ungerade Backtick-Anzahl (abgeschnittener Code-Span) → rot
cat > "$tmp/oddbtick.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka | Russische Lokalisierung in `values-ru und values-de | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/oddbtick.md" bash "$guard" >/dev/null 2>&1; then fail "C12: ungerade Backtick-Anzahl muss rot sein"; else ok; fi

# C13: unbalancierte Klammer in Person → rot
cat > "$tmp/badperson.md" <<'MD'
| Person | Beitrag | Referenz | Status |
|--------|---------|----------|--------|
| smka (Ilya K | Russisch | #213 | offen |
MD
if CONTRIBUTORS_FILE="$tmp/badperson.md" bash "$guard" >/dev/null 2>&1; then fail "C13: unbalancierte Klammer in Person muss rot sein"; else ok; fi

# C14: Pre-Merge-Ankündigung (#330) — die reale CONTRIBUTORS.md trägt die
# angekündigte Zeile des offenen Fork-PRs mit gültigem Status. Der Status selbst
# ist in C4/C10 gegen die Allowlist geprüft; hier sitzt die Zeile am richtigen Ort.
if grep -Eq '\| #330 \| (offen|umgesetzt) \|' "$PWD/CONTRIBUTORS.md"; then ok
else fail "C14: angekündigte Zeile für #330 fehlt in der realen CONTRIBUTORS.md"; fi

# C15/C16: CONTRIBUTING.md trägt den Ankündigungs-Absatz in BEIDEN Sprachen —
# ein Sprachstand ohne den Absatz bricht die EN/DE-Parität des Vertrags.
if grep -Fq '**Pre-merge announcement (practice since #330).**' "$PWD/CONTRIBUTING.md"; then ok
else fail "C15: englischer Pre-merge-announcement-Absatz fehlt in CONTRIBUTING.md"; fi
if grep -Fq '**Ankündigung vor dem Merge (Praxis seit #330).**' "$PWD/CONTRIBUTING.md"; then ok
else fail "C16: deutscher Ankündigung-vor-dem-Merge-Absatz fehlt in CONTRIBUTING.md"; fi

# C17: Das Flippen `offen` → `umgesetzt` ist Maintainer-Handarbeit. Die Doku
# behauptete zuerst, der Reminder flippe die Zeile selbst — der Workflow hat
# aber kein `contents: write` und das Skript schreibt CONTRIBUTORS.md nie.
# Beide Seiten des Widerspruchs bleiben bewacht: Landet der alte Satz zurück,
# oder rückt dem Workflow schreibender Inhalt ein, muss Doku und Test gemeinsam
# aktualisiert werden.
if grep -Fq 'reminder flips the row' "$PWD/CONTRIBUTING.md" \
  || grep -Fq 'Reminder flippt die Zeile' "$PWD/CONTRIBUTING.md"; then
  fail "C17a: CONTRIBUTING.md behauptet wieder einen automatischen Flip durch den Reminder"
else ok; fi
if grep -Eq 'contents:[[:space:]]*write' .github/workflows/automation-contributors-reminder.yml; then
  fail "C17b: Reminder-Workflow hat contents: write — der Flip ist dann automatisiert, Doku/Test müssen mit"
else ok; fi

echo "▶ [contributors-guard-test] $PASS Pass, $FAIL Fail"
if [[ "$FAIL" -gt 0 ]]; then
  echo "❌ [contributors-guard-test] Contributors-Guard vertragstreu (C1–C17): $FAIL Fall/Fälle rot."
  exit 1
fi
echo "✅ [contributors-guard-test] Contributors-Guard vertragstreu (C1–C17)."