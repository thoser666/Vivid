#!/usr/bin/env bash
# Selbsttest für scripts/check_contributors.sh (Offline-Fixtures, 10 Fälle).
# Der Guard liest CONTRIBUTORS_FILE (Env) — fertige Fixtures beweisen, dass
# gültige Tabellen grün und jede Vertragsverletzung rot gemeldet wird.
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
| smka (Ilya K) | In-App-Sprachauswahl + russische Lokalisierung | #213 | offen |
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

echo "▶ [contributors-guard-test] $PASS Pass, $FAIL Fail"
if [[ "$FAIL" -gt 0 ]]; then
  echo "❌ [contributors-guard-test] Contributors-Guard vertragstreu (C1–C10): $FAIL Fall/Fälle rot."
  exit 1
fi
echo "✅ [contributors-guard-test] Contributors-Guard vertragstreu (C1–C10)."