#!/usr/bin/env bash
# Selbsttest fuer scripts/check_sentry_mapping_upload.sh (Vivid).
#
# Offline und deterministisch: jede Probe laeuft gegen eine mutierte Kopie der
# echten Workflow-/Gradle-Datei in einem temporaeren Verzeichnis (Env-Overrides
# RELEASE_PIPELINE_OVERRIDE / DISTRIBUTION_STABLE_OVERRIDE / APP_GRADLE_OVERRIDE).
# Der Guard darf nie gegen den echten Baum "gruen" sein und dabei nichts
# geprueft haben — deshalb gibt es zu jeder Mutation eine Positivkontrolle
# (unveraenderte Kopie bleibt gruen).
#
# Fixtures werden im Universal-Newline-Modus gelesen (CRLF -> LF), geschrieben
# mit os.linesep: die Workflows sind CRLF, die Anker unten sind LF.
#
# Proben:
#   T0  echter Baum -> gruen (M1–M5)
#   T1  Token-Zeile im Play-Step entfernt            -> rot (M2)
#   T2  Token auf ein fremdes Secret umgebogen       -> rot (M2)
#   T3  autoUploadProguardMapping abgeschaltet       -> rot (M1)
#   T4  Fail-closed-Block entfernt                   -> rot (M2)
#   T5  SENTRY_ORG im Play-Step entfernt             -> rot (M2)
#   T6  Step-Name im Play-Job vertippt               -> rot (M2, kein stiller Skip)
#   T7  unveraenderte Kopie (Negativkontrolle)       -> gruen
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT="$(pwd)"
GUARD="$ROOT/scripts/check_sentry_mapping_upload.sh"
REL="$ROOT/.github/workflows/release-pipeline.yml"
DIST="$ROOT/.github/workflows/distribution-stable.yml"
GRADLE="$ROOT/app/build.gradle.kts"

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

PASS=0
FAIL=0
ok() { echo "  OK   $*"; PASS=$((PASS + 1)); }
no() { echo "  FAIL $*"; FAIL=$((FAIL + 1)); }

# Anker als $'…': darin werden $ und {{ }} NICHT expandiert. Der Play-Step ist
# der einzige, der VERSION: github.event.inputs.version traegt — die Token-Zeile
# allein kommt in drei Jobs vor und waere kein eindeutiger Anker.
PLAY_TOKEN_OK=$'          SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_AUTH_TOKEN }}\n          SENTRY_ORG: privat-jb\n          SENTRY_PROJECT: vivid\n          VERSION: ${{ github.event.inputs.version }}'
PLAY_TOKEN_T1=$'          SENTRY_MAPPING_UNUSED: x\n          SENTRY_ORG: privat-jb\n          SENTRY_PROJECT: vivid\n          VERSION: ${{ github.event.inputs.version }}'
PLAY_TOKEN_T2=$'          SENTRY_AUTH_TOKEN: ${{ secrets.SENTRY_TOKEN }}\n          SENTRY_ORG: privat-jb\n          SENTRY_PROJECT: vivid\n          VERSION: ${{ github.event.inputs.version }}'
PLAY_ORG_OK=$'          SENTRY_ORG: privat-jb\n          SENTRY_PROJECT: vivid\n          VERSION: ${{ github.event.inputs.version }}'
PLAY_NO_ORG=$'          SENTRY_PROJECT: vivid\n          VERSION: ${{ github.event.inputs.version }}'
FAIL_LOUD_OK=$'          if [ -z "${SENTRY_AUTH_TOKEN:-}" ]; then\n            echo "::error::SENTRY_AUTH_TOKEN fehlt — der Play-Upload würde ohne ProGuard-Mapping ausgeliefert (Sentry-Frames bleiben obfuskiert). Abbruch."\n            exit 1\n          fi'
FAIL_LOUD_T4=$'          if [ -z "${SENTRY_MAPPING_UNUSED:-}" ]; then\n            echo "notice: kein Mapping-Token" >&2\n          fi'

OUT=""
run_guard() { # $1=rel $2=dist $3=gradle -> Exit-Code; Ausgabe in $TMP/guard.out
  # Bewusst ueber eine Datei statt ueber eine Variable: der Aufruf steht in einer
  # Command-Substitution, eine dort gesetzte Variable ginge im Subshell verloren
  # (erster Anlauf: OUT war beim Aufrufer immer leer, jede Rot-Probe erschien ohne
  # Hinweistext und T4 sah faelschlich wie "gruen" aus).
  local code=0
  RELEASE_PIPELINE_OVERRIDE="$1" DISTRIBUTION_STABLE_OVERRIDE="$2" APP_GRADLE_OVERRIDE="$3" \
    bash "$GUARD" >"$TMP/guard.out" 2>&1 || code=$?
  printf '%s' "$code"
}

expect_red() { # $1 = Probe, $2 = erwarteter Text, $3.. = Pfade
  local label="$1" needle="$2"; shift 2
  local code; code="$(run_guard "$1" "$2" "$3")"; OUT="$(cat "$TMP/guard.out")"
  if [[ "$code" == "0" ]]; then
    no "$label: Guard blieb gruen, obwohl der Nachweis fehlt."
  elif ! grep -qF "$needle" <<<"$OUT"; then
    no "$label: rot, aber ohne den erwarteten Hinweis '$needle'."
    echo "$OUT" | sed 's/^/       /' | head -4
  else
    ok "$label: rot mit '$needle'."
  fi
}

expect_green() { # $1 = Probe, $2.. = Pfade
  local label="$1"; shift
  local code; code="$(run_guard "$1" "$2" "$3")"; OUT="$(cat "$TMP/guard.out")"
  if [[ "$code" == "0" ]]; then
    ok "$label: gruen."
  else
    no "$label: unerwartet rot."
    echo "$OUT" | sed 's/^/       /' | head -6
  fi
}

# mutate <quelle> <ziel> <alt> <neu> — ersetzt genau ein Vorkommen und bricht ab,
# wenn der Anker nicht exakt einmal vorkommt. So kann eine umbenannte YAML-Zeile
# den Test nicht stillschweigend wirkungslos machen.
mutate() {
  python -X utf8 - "$1" "$2" "$3" "$4" <<'PY'
import io, os, sys
src, dst, old, new = sys.argv[1:5]
text = io.open(src, encoding='utf-8').read()
count = text.count(old)
if count != 1:
    sys.exit("Anker kommt %dx vor (erwartet: 1): %r" % (count, old[:60]))
io.open(dst, 'w', encoding='utf-8', newline='').write(text.replace(old, new).replace('\n', os.linesep))
PY
}

echo "[sentry-mapping-test] Proben T0-T7 gegen $GUARD"

expect_green "T0 echter Baum" "$REL" "$DIST" "$GRADLE"

mutate "$REL" "$TMP/rel_t1.yml" "$PLAY_TOKEN_OK" "$PLAY_TOKEN_T1"
expect_red "T1 Play-Step ohne Token" "M2 (Play-Beta-Kanal): SENTRY_AUTH_TOKEN fehlt" "$TMP/rel_t1.yml" "$DIST" "$GRADLE"

mutate "$REL" "$TMP/rel_t2.yml" "$PLAY_TOKEN_OK" "$PLAY_TOKEN_T2"
expect_red "T2 fremde Secret-Referenz" "SENTRY_AUTH_TOKEN fehlt oder ist keine Secret-Referenz" "$TMP/rel_t2.yml" "$DIST" "$GRADLE"

python -X utf8 - "$GRADLE" "$TMP/gradle_t3.kts" <<'PY'
import io, os, sys
text = io.open(sys.argv[1], encoding='utf-8').read()
old = 'autoUploadProguardMapping.set(!System.getenv("SENTRY_AUTH_TOKEN").isNullOrBlank())'
if text.count(old) != 1:
    sys.exit("Anker autoUploadProguardMapping nicht eindeutig")
out = text.replace(old, 'autoUploadProguardMapping.set(false)')
io.open(sys.argv[2], 'w', encoding='utf-8', newline='').write(out.replace('\n', os.linesep))
PY
expect_red "T3 Upload-Bedingung abgeschaltet" "M1:" "$REL" "$DIST" "$TMP/gradle_t3.kts"

mutate "$REL" "$TMP/rel_t4.yml" "$FAIL_LOUD_OK" "$FAIL_LOUD_T4"
expect_red "T4 kein lauter Abbruch" "prueft SENTRY_AUTH_TOKEN nicht selbst" "$TMP/rel_t4.yml" "$DIST" "$GRADLE"

mutate "$REL" "$TMP/rel_t5.yml" "$PLAY_ORG_OK" "$PLAY_NO_ORG"
expect_red "T5 SENTRY_ORG fehlt" "SENTRY_ORG fehlt" "$TMP/rel_t5.yml" "$DIST" "$GRADLE"

mutate "$REL" "$TMP/rel_t6.yml" "      - name: Build AAB and upload to Google Play" "      - name: Build AAB and upload to Play"
expect_red "T6 Step-Name vertippt" "M2" "$TMP/rel_t6.yml" "$DIST" "$GRADLE"

cp "$REL" "$TMP/rel_t7.yml"
expect_green "T7 Negativkontrolle (unveraenderte Kopie)" "$TMP/rel_t7.yml" "$DIST" "$GRADLE"

echo ""
if [[ $FAIL -gt 0 ]]; then
  echo "FAIL [sentry-mapping-test] $FAIL von $((PASS + FAIL)) Proben fehlgeschlagen."
  exit 1
fi
echo "OK [sentry-mapping-test] alle $PASS Proben gruen (T0-T7)."
