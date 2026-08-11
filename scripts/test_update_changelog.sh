#!/usr/bin/env bash
# Tests für scripts/update_changelog.sh — ohne Netzwerkzugriff:
# ein Fake-`gh` (in PATH) liefert feste Fixtures, der echte Workflow läuft dagegen.
# Aufruf: bash scripts/test_update_changelog.sh   (Exit-Code 0 = alle grün)
#
# Getestet wird:
#   T1  Idempotenz: alle Releases dokumentiert -> keine Änderung, Datei bleibt byte-identisch
#   T2  Prepend: neues Release kommt oben rein, bestehende bleiben, Badges korrekt,
#       ein auf GitHub bereits gelöschtes Nightly bleibt erhalten
#   T3  Leerer Block: alle Releases werden neu eingefügt
#   T4  Fehlende Marker: Skript bricht mit Fehler ab (Exit != 0)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

PASS=0
FAIL=0
check() { # $1=Name, rest=Kommando (wird direkt ausgeführt, kein eval)
  local name="$1"
  shift
  if "$@" >/dev/null 2>&1; then
    PASS=$((PASS + 1))
    echo "PASS: $name"
  else
    FAIL=$((FAIL + 1))
    echo "FAIL: $name"
  fi
}

# --- Fixtures + Fake-gh aufbauen -------------------------------------------
FIXTURES="$SANDBOX/fixtures"
mkdir -p "$FIXTURES/bin"

# Das ist das Ergebnis, das echtes `gh release list ... --jq '<filter>'` liefern
# würde: keine Drafts, sortiert nach publishedAt absteigend.
cat > "$FIXTURES/list.json" <<'EOF'
[
  {"tagName":"nightly-20260811-0000","name":"Vivid nightly (0.2.0-nightly.99)","isDraft":false,"publishedAt":"2026-08-11T00:00:00Z"},
  {"tagName":"nightly-20260810-1200","name":"Vivid nightly (0.2.0-nightly.90)","isDraft":false,"publishedAt":"2026-08-10T12:00:00Z"},
  {"tagName":"v0.3.0-alpha","name":"Vivid v0.3.0-alpha","isDraft":false,"publishedAt":"2026-08-09T10:00:00Z"},
  {"tagName":"v0.2.0","name":"Vivid v0.2.0","isDraft":false,"publishedAt":"2026-08-08T09:00:00Z"}
]
EOF

printf '%s' 'Vivid nightly (0.2.0-nightly.99)' > "$FIXTURES/nightly-20260811-0000.name"
printf '%s' '2026-08-11T00:00:00Z' > "$FIXTURES/nightly-20260811-0000.publishedAt"
printf '%s' 'Nightly feature build for testing.' > "$FIXTURES/nightly-20260811-0000.body"
printf '%s' 'Vivid nightly (0.2.0-nightly.90)' > "$FIXTURES/nightly-20260810-1200.name"
printf '%s' '2026-08-10T12:00:00Z' > "$FIXTURES/nightly-20260810-1200.publishedAt"
printf '%s' 'Nightly feature build for testing.' > "$FIXTURES/nightly-20260810-1200.body"
printf '%s' 'Vivid v0.3.0-alpha' > "$FIXTURES/v0.3.0-alpha.name"
printf '%s' '2026-08-09T10:00:00Z' > "$FIXTURES/v0.3.0-alpha.publishedAt"
printf '%s' 'Alpha test release.' > "$FIXTURES/v0.3.0-alpha.body"
printf '%s' 'Vivid v0.2.0' > "$FIXTURES/v0.2.0.name"
printf '%s' '2026-08-08T09:00:00Z' > "$FIXTURES/v0.2.0.publishedAt"
: > "$FIXTURES/v0.2.0.body" # leerer Body (wird ignoriert, kein Absturz)

cat > "$FIXTURES/bin/gh" <<EOF
#!/usr/bin/env bash
# gh <subcommand> ... — erster Arg ist "release", zweiter list|view
case "\$1/\$2" in
  release/list) cat "$FIXTURES/list.json" ;;
  release/view)
    tag="\$3"
    field=""
    prev=""
    for a in "\$@"; do
      if [ "\$prev" = "--json" ]; then field="\$a"; fi
      prev="\$a"
    done
    case "\$field" in
      name) cat "$FIXTURES/\$tag.name" ;;
      publishedAt) cat "$FIXTURES/\$tag.publishedAt" ;;
      body) cat "$FIXTURES/\$tag.body" ;;
      *) exit 2 ;;
    esac
    ;;
  *) exit 2 ;;
esac
EOF
chmod +x "$FIXTURES/bin/gh"
export PATH="$FIXTURES/bin:$PATH"
export GITHUB_REPOSITORY="test/vivid"
run_script() { bash "$SCRIPT_DIR/update_changelog.sh" "$@"; }

begin() { # $1=Datei -> Header + START-Marker (Einträge kommen dazwischen)
  cat > "$1" <<'EOF'
# Test Changelog

<!-- CHANGELOG-START -->
EOF
}
end() { # $1=Datei -> END-Marker anhängen
  printf '%s\n' '<!-- CHANGELOG-END -->' >> "$1"
}

# --- T1: Idempotenz ---------------------------------------------------------
begin "$SANDBOX/t1.md"
cat >> "$SANDBOX/t1.md" <<'EOF'
## NIGHTLY 0.2.0-nightly.99 - 2026-08-11
[GitHub-Release](https://github.com/test/vivid/releases/tag/nightly-20260811-0000)
## NIGHTLY 0.2.0-nightly.90 - 2026-08-10
[GitHub-Release](https://github.com/test/vivid/releases/tag/nightly-20260810-1200)
## ALPHA v0.3.0-alpha - 2026-08-09
[GitHub-Release](https://github.com/test/vivid/releases/tag/v0.3.0-alpha)
## STABLE v0.2.0 - 2026-08-08
[GitHub-Release](https://github.com/test/vivid/releases/tag/v0.2.0)
EOF
end "$SANDBOX/t1.md"
before="$(sha256sum "$SANDBOX/t1.md" | cut -d' ' -f1)"
out="$(run_script "$SANDBOX/t1.md")"
after="$(sha256sum "$SANDBOX/t1.md" | cut -d' ' -f1)"
check "T1 meldet 'bereits aktuell'" grep -q "bereits aktuell" <<<"$out"
check "T1 Datei byte-identisch" test "$before" = "$after"

# --- T2: Prepend + Badges + gelöschtes Nightly bleibt -----------------------
begin "$SANDBOX/t2.md"
cat >> "$SANDBOX/t2.md" <<'EOF'
## NIGHTLY 0.2.0-nightly.90 - 2026-08-10
[GitHub-Release](https://github.com/test/vivid/releases/tag/nightly-20260810-1200)
## DELETED-NIGHTLY 0.2.0-nightly.80 - 2026-08-09
[GitHub-Release](https://github.com/test/vivid/releases/tag/nightly-20260809-0800)
EOF
end "$SANDBOX/t2.md"
run_script "$SANDBOX/t2.md" >/dev/null

check "T2 neues nightly eingefuegt" grep -q "0.2.0-nightly.99" "$SANDBOX/t2.md"
check "T2 Nightly-Badge" grep -q '\*\*Nightly\*\*' "$SANDBOX/t2.md"
check "T2 Alpha-Eintrag" grep -q "v0.3.0-alpha" "$SANDBOX/t2.md"
check "T2 Stable-Eintrag" grep -q "v0.2.0" "$SANDBOX/t2.md"
check "T2 Artefakte-Zeile (nightly)" grep -q "app-release.apk" "$SANDBOX/t2.md"
check "T2 geloeschtes Nightly bleibt" grep -q "nightly-20260809-0800" "$SANDBOX/t2.md"

line_new="$(grep -n "nightly-20260811-0000" "$SANDBOX/t2.md" | head -1 | cut -d: -f1)"
line_alpha="$(grep -n "v0.3.0-alpha" "$SANDBOX/t2.md" | head -1 | cut -d: -f1)"
line_old="$(grep -n "nightly-20260810-1200" "$SANDBOX/t2.md" | head -1 | cut -d: -f1)"
check "T2 Reihenfolge: neu vor alpha" test "$line_new" -lt "$line_alpha"
check "T2 Reihenfolge: alpha vor alt" test "$line_alpha" -lt "$line_old"

# --- T3: Leerer Block -> alle Releases werden eingefügt ---------------------
begin "$SANDBOX/t3.md"
end "$SANDBOX/t3.md"
run_script "$SANDBOX/t3.md" >/dev/null
check "T3 alle 4 Eintraege eingefuegt" test "$(grep -c '^## ' "$SANDBOX/t3.md")" -eq 4

# --- T4: Fehlende Marker -> Fehler (Exit != 0) -------------------------------
printf 'no markers here\n' > "$SANDBOX/t4.md"
if run_script "$SANDBOX/t4.md" >/dev/null 2>&1; then
  FAIL=$((FAIL + 1))
  echo "FAIL: T4 fehlende Marker -> Fehler erwartet"
else
  PASS=$((PASS + 1))
  echo "PASS: T4 fehlende Marker -> Fehler"
fi

# --- Ergebnis ---------------------------------------------------------------
echo ""
echo "Ergebnis: $PASS bestanden, $FAIL fehlgeschlagen"
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
