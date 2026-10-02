#!/usr/bin/env bash
# Selbsttest: Play-Secrets-Guard (scripts/check_play_secrets.sh +
# Verdrahtung im release-pipeline.yml) — offline, ohne Secrets.
#
#   P1 Guard-Skript existiert, ausführbar, bash-Syntax ok
#   P2 Skip-Pfad: ohne Secrets → ready=false auf stdout, exit 0,
#      ::notice::-Meldung auf STDERR (GITHUB_OUTPUT-Vertrag, Vorfall
#      02.10.2026), nennt alle fünf fehlenden Posten
#   P3 Ready-Pfad: alle Secrets gesetzt (PLAY_JSON_KEY_DATA-Variante) →
#      ready=true auf stdout, exit 0, keine notice auf stdout UND stderr
#   P4 Ready-Pfad: PLAY_JSON_KEY_FILE-Variante → ready=true
#   P5 Teilsatz: nur Keystore-Set, kein Credential → ready=false
#   P6 Workflow-Verdrahtung: Step ruft das Skript, id play_secrets,
#      beide Folge-Steps gekoppelt an ready == 'true'
#   P7 notice nennt Doku-Pfad (docs/distribution.md)
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "❌ [play-guard-test] $1"; exit 1; }

guard=scripts/check_play_secrets.sh
workflow=.github/workflows/release-pipeline.yml
tmp_err=$(mktemp)
trap 'rm -f "$tmp_err"' EXIT

# P1
[[ -s "$guard" ]] || fail "Guard-Skript fehlt oder ist leer"
[[ -x "$guard" ]] || fail "Guard-Skript ist nicht ausführbar"
bash -n "$guard" || fail "Guard-Skript: bash-Syntaxfehler"

# P2 — stdout enthaelt NUR ready=false (GITHUB_OUTPUT-Format), die
# ::notice::-Meldung muss auf stderr landen (99e20cf2: Notice im stdout
# vergiftet $GITHUB_OUTPUT und macht den Job rot).
out=$(env UPLOAD_KEYSTORE_BASE64= UPLOAD_KEYSTORE_PASSWORD= UPLOAD_KEY_ALIAS= \
  UPLOAD_KEY_PASSWORD= PLAY_JSON_KEY_FILE= PLAY_JSON_KEY_DATA= bash "$guard" 2>"$tmp_err") || \
  fail "Skip-Pfad: Guard darf nicht scheitern (exit 0 Vertrag)"
grep -q "ready=false" <<<"$out" || fail "Skip-Pfad: ready=false erwartet"
grep -q "::notice::" "$tmp_err" || fail "Skip-Pfad: ::notice::-Meldung auf stderr erwartet"
grep -q "::notice::" <<<"$out" && fail "Skip-Pfad: ::notice:: darf nicht im stdout/GITHUB_OUTPUT landen"
for s in UPLOAD_KEYSTORE_BASE64 UPLOAD_KEYSTORE_PASSWORD UPLOAD_KEY_ALIAS \
  UPLOAD_KEY_PASSWORD PLAY_JSON_KEY_FILE_or_PLAY_JSON_KEY_DATA; do
  grep -q "$s" "$tmp_err" || fail "Skip-Pfad: fehlender Posten nicht genannt: $s"
done

# P3
out=$(UPLOAD_KEYSTORE_BASE64=k UPLOAD_KEYSTORE_PASSWORD=p UPLOAD_KEY_ALIAS=a \
  UPLOAD_KEY_PASSWORD=q PLAY_JSON_KEY_FILE= PLAY_JSON_KEY_DATA='{}' bash "$guard" 2>"$tmp_err")
grep -q "ready=true" <<<"$out" || fail "Ready-Pfad (DATA): ready=true erwartet"
grep -q "::notice::" <<<"$out" && fail "Ready-Pfad (DATA): keine notice auf stdout erwartet"
grep -q "::notice::" "$tmp_err" && fail "Ready-Pfad (DATA): keine notice auf stderr erwartet"

# P4
out=$(UPLOAD_KEYSTORE_BASE64=k UPLOAD_KEYSTORE_PASSWORD=p UPLOAD_KEY_ALIAS=a \
  UPLOAD_KEY_PASSWORD=q PLAY_JSON_KEY_FILE=/tmp/sa.json PLAY_JSON_KEY_DATA= bash "$guard")
grep -q "ready=true" <<<"$out" || fail "Ready-Pfad (FILE): ready=true erwartet"

# P5
out=$(UPLOAD_KEYSTORE_BASE64=k UPLOAD_KEYSTORE_PASSWORD=p UPLOAD_KEY_ALIAS=a \
  UPLOAD_KEY_PASSWORD=q PLAY_JSON_KEY_FILE= PLAY_JSON_KEY_DATA= bash "$guard")
grep -q "ready=false" <<<"$out" || fail "Teilsatz: ohne Credential muss ready=false sein"

# P6
grep -Fq "bash scripts/check_play_secrets.sh >> \"\$GITHUB_OUTPUT\"" "$workflow" || \
  fail "Workflow ruft das Guard-Skript nicht"
grep -Fq "id: play_secrets" "$workflow" || fail "Guard-Step hat keine id play_secrets"
[[ $(grep -c "steps.play_secrets.outputs.ready == 'true'" "$workflow") -ge 2 ]] || \
  fail "Decode/Build-Steps sind nicht an ready gekoppelt"

# P7
grep -q "docs/distribution.md" "$guard" || fail "Guard nennt den Doku-Pfad nicht"

echo "✅ [play-guard-test] Guard-Skript und Workflow-Verdrahtung vertragstreu (P1–P7)."
