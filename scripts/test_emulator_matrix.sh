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
#   T11 Job bleibt workflow_dispatch-only
#   T12 release-pipeline.yml bleibt valides YAML

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
check "T6.3 arch nur in der Matrix hartkodiert (1× x86_64)" test "$X86_COUNT" -eq 1
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

echo "== T11: Dispatch-only =="
check "T11.1 if: workflow_dispatch" grep -q "if: github.event_name == 'workflow_dispatch'" <<<"$JOB"

echo "== T12: Workflow-YAML valide =="
check "T12.1 release-pipeline.yml parst als YAML" python3 -c "
import yaml, io
with io.open('.github/workflows/release-pipeline.yml', encoding='utf-8') as f:
    d = yaml.safe_load(f)
j = d['jobs']['emulator-tests']
inc = j['strategy']['matrix']['include']
assert len(inc) == 2, inc
assert {i['name'] for i in inc} == {'ubuntu-x86_64', 'macos-arm64'}, inc
"

echo
if [ "$FAIL" -eq 0 ]; then
  echo "✅ Alle Checks bestanden (test_emulator_matrix.sh)"
else
  echo "❌ $FAIL Check(s) fehlgeschlagen"
  exit 1
fi
