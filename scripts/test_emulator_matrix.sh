#!/usr/bin/env bash
# Regressionstest: Emulator-Test-Job (release-pipeline.yml, emulator-tests)
# ========================================================================
# Hintergrund (Vorfall 06.09.2026, Run 34009482168): Der Job lief allein auf
# macos-latest (arm64) und crashte beim Emulator-Boot mit
# `qemu-system-aarch64: failed to initialize HVF: HV_UNSUPPORTED` — ohne einen
# einzigen Testfehler. Die früheren Linux-Läufe scheiterten am Boot-Timeout,
# weil auf GitHub-hosted Linux-Runnern zwar KVM für Android-Workloads
# bereitgestellt wird (Changelog 02.04.2024), ohne udev-perms-Step der Emulator
# aber auf Software-Acceleration (-accel off) zurückfällt.
#
# Geprüfte Szenarien:
#   T1  Matrix enthält beide Architekturen (ubuntu-x86_64, macos-arm64)
#   T2  x86_64-Leg: authority-fähig (kein continue-on-error)
#   T3  arm64-Leg: experimentell (continue-on-error: true)
#   T4  KVM-udev-Step vorhanden und Linux-gepunktet
#   T5  KVM-Verifikations-Step vorhanden (harter Fail bei fehlendem /dev/kvm)
#   T6  arch ist parametrisiert (kein hartkodierter arch-Wert im Step)
#   T7  emulator-boot-timeout explizit gesetzt
#   T8  fail-fast: false — beide Legs werden immer gemeldet
#   T9  Artefakt-Uploads matrix-spezifisch (keine Namenskollision)
#   T10 Emulator-Action bleibt SHA-gepinnt
#   T11 Job läuft bei workflow_dispatch ODER v*-Tag-Push (Release-Gate, 24.09.2026)
#   T12 release-pipeline.yml bleibt valides YAML + 4-Legs-API-Staffelung
#       (34/35 Pflicht, 36 experimentell — SDK-Abdeckungs-Analyse 25.09.2026,
#        Beobachter-Leg 01.10.2026 von 37 auf 36 gezogen, #249: `platforms;
#        android-37` existiert im Stable-SDK-Kanal des Runners nicht)
#   T16 Emulator-Gate darf KEINen API-Level referenzieren, den der Stable-SDK-
#       Kanal des Runners nicht kennt — der android-emulator-runner bricht
#       sonst im Provisionierungs-Step ab ("Failed to find package") und die
#       Leg ist dauerhaft tot (blinder Rot-Status ohne Testaussage).
#   T13 Stable-Distribution: Emulator-Gate vor dem Publish-Step verdrahtet
#   T14 Emulator-Tests decken BEIDE Flavors ab (standard + foss)
#   T15 #249 Emulator-Grant-Setup: Runtime-Permissions werden VOR den
#       connected*-Tasks gegranted (Setup-Skript installiert die Debug-Test-
#       APKs beider Flavors vorconnected) — Gegenstück zur app-seitigen
#       Entfernung des CAMERA-Auto-Requests (StreamingScreen.kt)

set -euo pipefail
cd "$(dirname "$0")/.."

FAIL=0
check() {
  local desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}
check_absent() {
  local desc="$1"; shift
  if ! "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    FAIL=$((FAIL + 1))
  fi
}

WORKFLOW=.github/workflows/release-pipeline.yml
JOB_START=$(grep -n '^  emulator-tests:' "$WORKFLOW" | cut -d: -f1)
JOB_END=$(awk -v s="$JOB_START" 'NR>s && /^  [a-z-]+:/{print NR; exit}' "$WORKFLOW")
JOB=$(sed -n "${JOB_START},${JOB_END}p" "$WORKFLOW")

echo "== T1: Matrix beide Architekturen =="
check "T1.1 ubuntu-x86_64-Leg" grep -q 'name: ubuntu-x86_64' <<<"$JOB"
check "T1.2 macos-arm64-Leg" grep -q 'name: macos-arm64' <<<"$JOB"
check "T1.3 x86_64-arch" grep -q 'arch: x86_64' <<<"$JOB"
check "T1.4 arm64-v8a-arch" grep -q 'arch: arm64-v8a' <<<"$JOB"
check "T1.5 os-Zuordnung ubuntu" grep -q 'os: ubuntu-latest' <<<"$JOB"
check "T1.6 os-Zuordnung macos" grep -q 'os: macos-latest' <<<"$JOB"
check "T1.7 runs-on parametrisiert" grep -q 'runs-on: ${{ matrix.os }}' <<<"$JOB"

echo "== T2/T3: experimental-Flag =="
check "T2.1 x86_64 nicht experimentell" grep -q 'experimental: false' <<<"$JOB"
check "T3.1 arm64 experimentell" grep -q 'experimental: true' <<<"$JOB"
check "T3.2 continue-on-error aus Matrix" grep -q 'continue-on-error: ${{ matrix.experimental }}' <<<"$JOB"

echo "== T4: KVM-udev-Step =="
check "T4.1 udev-Regel-Step vorhanden" grep -q 'Enable KVM group perms' <<<"$JOB"
check "T4.2 udev-Regel-Inhalt" grep -q '99-kvm4all.rules' <<<"$JOB"
check "T4.3 Step auf Linux gepunktet" grep -q "if: runner.os == 'Linux'" <<<"$JOB"

echo "== T5: KVM-Verifikation =="
check "T5.1 Verifikations-Step vorhanden" grep -q 'Verify KVM acceleration available' <<<"$JOB"
check "T5.2 harter Fail bei fehlendem /dev/kvm" grep -q '/dev/kvm fehlt' <<<"$JOB"
check "T5.3 harter Fail bei nicht beschreibbarem /dev/kvm" grep -q 'nicht beschreibbar' <<<"$JOB"

echo "== T6: arch parametrisiert =="
check "T6.1 arch aus Matrix" grep -q 'arch: ${{ matrix.arch }}' <<<"$JOB"
ARM_COUNT=$(grep -c 'arch: arm64-v8a' <<<"$JOB" || true)
X86_COUNT=$(grep -c 'arch: x86_64' <<<"$JOB" || true)
PARAM_COUNT=$(grep -cF 'arch: ${{ matrix.arch }}' <<<"$JOB" || true)
check "T6.2 arch nur in der Matrix hartkodiert (1× arm64)" test "$ARM_COUNT" -eq 1
# 3× x86_64: drei ubuntu-Legs (API 34/35/37) — API-Staffelung 25.09.2026
check "T6.3 arch nur in der Matrix hartkodiert (3× x86_64, API-Staffelung)" test "$X86_COUNT" -eq 3
check "T6.4 Step nutzt Matrix-arch (genau 1×)" test "$PARAM_COUNT" -eq 1

echo "== T7: Boot-Timeout =="
check "T7.1 emulator-boot-timeout gesetzt" grep -q 'emulator-boot-timeout: 900' <<<"$JOB"

echo "== T8: fail-fast =="
check "T8.1 fail-fast: false" grep -q 'fail-fast: false' <<<"$JOB"

echo "== T9: Artefakte matrix-spezifisch =="
check "T9.1 Artefakt-Name mit matrix.name" \
  grep -q 'name: instrumented-test-results-${{ matrix.name }}' <<<"$JOB"
check_absent "T9.2 kein generischer Artefakt-Name mehr" \
  grep -q 'name: instrumented-test-results$' <<<"$JOB"

echo "== T10: Action-Pin =="
check "T10.1 emulator-runner SHA-gepinnt" \
  grep -q 'ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d' <<<"$JOB"

echo "== T11: Release-Gate-Trigger =="
check "T11.1 if: workflow_dispatch" grep -q "if: github.event_name == 'workflow_dispatch'" <<<"$JOB"
check "T11.2 if: auch bei v*-Tag-Pushen (Release-Gate)" \
  grep -q "github.event_name == 'push' && startsWith(github.ref, 'refs/tags/v')" <<<"$JOB"

echo "== T13: Stable-Distribution Emulator-Gate =="
# export: die bash -c-Kinder unten brauchen die Variable (sonst leer →
# vakuum-grüne Checks — das schlagt T13.5 zu Recht an).
export DIST=.github/workflows/distribution-stable.yml
check "T13.1 Emulator-Gate-Step vorhanden" grep -q "Run instrumented tests on emulator (release gate)" "$DIST"
check "T13.2 Gate-Step hängt am TAG-Guard (env.TAG != '')" \
  grep -q "Run instrumented tests on emulator (release gate)" "$DIST"
check "T13.3 Gate läuft VOR dem Publish-Step" bash -c 'grep -n "Run instrumented tests on emulator (release gate)" "$DIST" | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "Build and publish stable release" "$DIST" | cut -d: -f1)'
check "T13.4 Gate nutzt denselben SHA-gepinnten Emulator-Runner" \
  grep -q "ReactiveCircus/android-emulator-runner@a421e43855164a8197daf9d8d40fe71c6996bb0d" "$DIST"
# Der Gate-Script-Block wird per YAML geparst statt per `grep -A35` aus dem
# Rohtext gefischt: eine Zeilenzahl-Fenster-Suche bricht still, sobald Kommentare
# im Script-Block wachsen (Vorfall 02.10.2026 — der `set -euo pipefail`-Block
# verschob connectedStandardDebugAndroidTest aus dem -A35-Fenster und ließ
# T13.5/T13.6/T14.3 ohne Änderung der Gate-Logik rot werden).
gate_script() {
  python3 -c "
import yaml, io
with io.open('$DIST', encoding='utf-8') as f:
    d = yaml.safe_load(f)
steps = d['jobs']['publish-stable']['steps']
gate = [s for s in steps if s.get('name') == 'Run instrumented tests on emulator (release gate)']
assert len(gate) == 1, gate
print(gate[0]['with']['script'])
"
}
# `|| true`: das Skript läuft mit `set -euo pipefail` — ein YAML-Parse-Fehler
# würde die Auswertung kommentarlos abbrechen und alle Checks als "nicht
# fehlgeschlagen" erscheinen lassen (stiller Grüner). Der leere Wert macht die
# betroffenen Checks stattdessen rot.
GATE_SCRIPT=$(gate_script || true)

check "T13.5 Gate-Step testet flavor-explicit" \
  grep -q "connectedStandardDebugAndroidTest" <<<"$GATE_SCRIPT"
check "T13.6 Gate-Task läuft über Retry-Wrapper (BuildRetry-Hausmuster)" \
  grep -q "scripts/emulator_gate_retry.sh" <<<"$GATE_SCRIPT"

echo "== T14: Beide Flavors im Emulator-Gate =="
# Kein end-Anker: der Step-Name steht im Arbeitsbaum (Windows/Git-for-Windows)
# ggf. mit CRLF-Zeilenende — ein hartes $ würde dann nie matchen (T14.1/T14.2
# wären je nach Zeilenende inkonsistent). In release-pipeline.yml gibt es genau
# einen Step mit diesem Namen → Ankerlos reicht.
EMU_JOB=$(grep -A30 'name: Run instrumented tests on emulator' "$WORKFLOW" || true)
check "T14.1 release-pipeline emulator-tests deckt standard ab" \
  grep -q "connectedStandardDebugAndroidTest" <<<"$EMU_JOB"
check "T14.2 release-pipeline emulator-tests deckt foss ab" \
  grep -q "connectedFossDebugAndroidTest" <<<"$EMU_JOB"
check "T14.3 distribution-stable Gate deckt foss ab" \
  grep -q "connectedFossDebugAndroidTest" <<<"$GATE_SCRIPT"

echo "== T15: #249 Emulator-Grant-Setup (Runtime-Permissions für UI-Tests) =="
# Vorfall Tag-Run 36849526447: Der CAMERA-Auto-Request des Streaming-Screens
# öffnete beim Betreten des Start-Screens den Systemdialog ÜBER der MainActivity
# → "No compose hierarchies found" in 11 UI-Tests. Die Emulator-Legs granten
# die Permissions deshalb VOR den connected*-Tasks (Setup-Skript, installiert
# die Debug-Test-APKs vorconnected), die App fordert camera-seitig nur noch im
# Go-Live-Flow an. `pm grant` scheitert an nicht installierten Packages —
# deshalb baut jeder Workflow die APKs in einem eigenen Step VOR dem Gate.
check "T15.1 Setup-Skript vorhanden" test -f scripts/emulator_test_setup.sh
check "T15.2 Setup installiert standard-debug-APK" grep -q 'apk/standard/debug/app-standard-debug' scripts/emulator_test_setup.sh
check "T15.3 Setup installiert foss-debug-APK" grep -q 'apk/foss/debug/app-foss-debug' scripts/emulator_test_setup.sh
check "T15.4 Setup grantet an das standard-Package" grep -q 'com\.vivid\.debug' scripts/emulator_test_setup.sh
check "T15.5 Setup grantet an das foss-Package" grep -q 'com\.vivid\.foss\.debug' scripts/emulator_test_setup.sh
check "T15.6 Setup grantet Location (TextInfoWidget)" grep -q 'ACCESS_FINE_LOCATION' scripts/emulator_test_setup.sh
check "T15.7 POST_NOTIFICATIONS-API-Guard (>= 33)" grep -q 'ge 33' scripts/emulator_test_setup.sh
check "T15.8 Setup fail-loud bei fehlendem APK" grep -q 'erwartetes APK fehlt' scripts/emulator_test_setup.sh
check "T15.9 release-pipeline: Grant-Setup vor den connected-Tests" \
  bash -c 'grep -n "emulator_test_setup.sh" .github/workflows/release-pipeline.yml | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "connectedStandardDebugAndroidTest" .github/workflows/release-pipeline.yml | cut -d: -f1 | head -1)'
check "T15.10 release-pipeline: Debug-APKs werden vor dem Emulator gebaut" \
  bash -c 'grep -n "Build debug APKs for emulator tests" .github/workflows/release-pipeline.yml | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "Run instrumented tests on emulator" .github/workflows/release-pipeline.yml | cut -d: -f1 | head -1)'
check "T15.11 distribution-stable: Grant-Setup vor dem Retry-Wrapper" \
  bash -c 'grep -n "emulator_test_setup.sh 34" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "emulator_gate_retry.sh" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1)'
check "T15.12 distribution-stable: Assemble-Step vor dem Gate-Step" \
  bash -c 'grep -n "Build debug APKs for emulator gate" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "Run instrumented tests on emulator (release gate)" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1)'
# fail-loud im Emulator-Script-Block: der android-emulator-runner führt das
# script-Snippet OHNE `set -e` aus. Ohne die Zeile wird ein fehlgeschlagenes
# Setup-Skript stillschweigend übersprungen — das Gate testet dann ohne Grants
# und fällt mit "No compose hierarchies found" durch, also als Testfehler
# getarnt. Genau diese Verschleierung machte #249 in der ersten Runde schwer
# diagnostizierbar.
check "T15.13 release-pipeline Gate-Script: set -euo pipefail im Script-Block" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
steps = d['jobs']['emulator-tests']['steps']
emu = [s for s in steps if str(s.get('uses','')).startswith('ReactiveCircus/android-emulator-runner')]
assert len(emu) == 1, emu
lines = [l for l in emu[0]['with']['script'].strip().splitlines() if l.strip() and not l.strip().startswith('#')]
assert lines[0].strip() == 'set -euo pipefail', lines[0]
"
check "T15.14 distribution-stable Gate-Script: set -euo pipefail im Script-Block" python3 -c "
import yaml, io
with io.open('.github/workflows/distribution-stable.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
steps = d['jobs']['publish-stable']['steps']
gate = [s for s in steps if str(s.get('uses','')).startswith('ReactiveCircus/android-emulator-runner')]
assert len(gate) == 1, gate
lines = [l for l in gate[0]['with']['script'].strip().splitlines() if l.strip() and not l.strip().startswith('#')]
assert lines[0].strip() == 'set -euo pipefail', lines[0]
"
check "T15.15 Setup-Skript läuft VOR dem Retry-Wrapper (Setupfehler nicht als Retry)" \
  bash -c 'grep -n "emulator_test_setup.sh" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1 | xargs -I{} test {} -lt $(grep -n "emulator_gate_retry.sh" .github/workflows/distribution-stable.yml | cut -d: -f1 | head -1)'
check "T15.16 Begruendung fuer set -e im Script-Block dokumentiert" \
  grep -q "fail-loud" <<<"$GATE_SCRIPT"

echo "== T12: Workflow-YAML valide + API-Staffelung =="
check "T12.1 release-pipeline.yml parst als YAML" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
j = d['jobs']['emulator-tests']
inc = j['strategy']['matrix']['include']
assert len(inc) == 4, inc
names = {i['name'] for i in inc}
assert names == {'ubuntu-x86_64-api34', 'ubuntu-x86_64-api35', 'ubuntu-x86_64-api36', 'macos-arm64'}, names
by = {i['name']: i for i in inc}
# Pflicht-Legs: API 34 (FGS-Regression) + API 35 (Edge-to-Edge) — roter Tag-Run = kein Release
assert by['ubuntu-x86_64-api34']['experimental'] is False, by['ubuntu-x86_64-api34']
assert by['ubuntu-x86_64-api35']['experimental'] is False, by['ubuntu-x86_64-api35']
assert by['ubuntu-x86_64-api34']['api-level'] == 34 and by['ubuntu-x86_64-api35']['api-level'] == 35
# Beobachter-Legs (continue-on-error): API 36 (neueste stabile Plattform) + macos-arm64
assert by['ubuntu-x86_64-api36']['experimental'] is True and by['ubuntu-x86_64-api36']['api-level'] == 36
assert by['macos-arm64']['experimental'] is True
"
check "T12.2 api-level kommt aus der Matrix (nicht hartkodiert)" \
  grep -q 'api-level: \${{ matrix.api-level }}' .github/workflows/release-pipeline.yml
check "T12.3 Stable-Publish-Gate bleibt bewusst auf API 34" \
  bash -c 'grep -A8 "Run instrumented tests on emulator (release gate)" .github/workflows/distribution-stable.yml | grep -q "api-level: 34"'

echo "== T16: Provisionierbare API-Level (Vorbedingung des Emulator-Runners) =="
# Vorfall #249 (Run 36849526447): Die Beobachter-Leg api37 referenzierte ein
# Plattform-Paket, das der Stable-SDK-Kanal des GitHub-Runners NICHT führt:
#   sdkmanager --install 'build-tools;37.0.0' platform-tools 'platforms;android-37'
#   Warning: Failed to find package 'platforms;android-37'
# Der android-emulator-runner bricht daraufhin im Provisionierungs-Step ab
# ("Terminate Emulator") — die Leg war dauerhaft tot und lieferte dauerhaft
# Rot ohne jede Testaussage (continue-on-error maskiert es als "nur Beobachter").
#
# Der Contract, den T16 festschreibt: API-Level im Emulator-Gate MUSS im
# Stable-SDK-Kanal verfügbar sein. compileSdk/targetSdk (37) sind davon nicht
# betroffen — Kompilieren und Emulator-Provisionierung sind verschiedene Dinge.
check "T16.1 Matrix-Level liegen im provisionierbaren Bereich (<= 36)" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
inc = d['jobs']['emulator-tests']['strategy']['matrix']['include']
levels = sorted({i['api-level'] for i in inc})
# 'platforms;android-37' fehlt im Stable-Kanal des Runners (Vorfall 01.10.2026).
MAX_PROVISIONABLE = 36
bad = [l for l in levels if l > MAX_PROVISIONABLE]
assert not bad, ('nicht provisionierbare API-Level(s) in der Matrix: %s — Leg waere '
                 'dauerhaft tot (sdkmanager: Failed to find package)' % bad)
"
check "T16.2 distribution-stable-Gate nutzt einen provisionierbaren Level" python3 -c "
import yaml, io
with io.open('.github/workflows/distribution-stable.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
steps = d['jobs']['publish-stable']['steps']
gate = [s for s in steps if s.get('name') == 'Run instrumented tests on emulator (release gate)']
assert len(gate) == 1, gate
lvl = gate[0]['with']['api-level']
assert lvl <= 36, ('Stable-Publish-Gate referenziert platforms;android-%d, das der '
                   'Stable-SDK-Kanal nicht fuehrt — Gate waere dauerhaft rot' % lvl)
"
check_absent "T16.3 keine api37-Leg mehr im Workflow" \
  grep -q 'ubuntu-x86_64-api37' "$WORKFLOW"
check_absent "T16.4 keine 37er-Emulator-Provisionierung" \
  grep -qE 'api-level:\s*(37|38)\s*$' "$WORKFLOW"
check "T16.5 Begruendung fuer den 37er-Ausschluss ist dokumentiert" \
  grep -q 'Failed to find package' "$WORKFLOW"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_emulator_matrix.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
