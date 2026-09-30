#!/usr/bin/env bash
# Selbsttest für die Drift-Semantik des pip-pinning-Guards (offline, Sandbox).
# Vertrag (Vorfall 30.09.2026 — Same-Day-PyPI-Drift machte den Push-CI rot):
#   S1  Standard-Lauf: Drift ist ADVISORY (exit 0, Warn-Text)
#   S2  PIP_DRIFT_STRICT=1: Drift ist FAIL-CLOSED (exit != 0)
#   S3  Integritätsverträge bleiben in BEIDEN Modi strikt; STRICT ohne Drift
#       bleibt grün (Strict heißt nicht „immer rot“)
# Der echte Live-PyPI-Vergleich ist bewusst NICHT Teil des Tests — getestet
# wird die Modus-Semantik gegen einen Stub-Generator (STUB_CHECK_RC = Exit von
# `--check`), nicht den aktuellen Netzwerk-Zustand. Die Sandbox ist ein
# Repo-Slice: echter Guard + echte Workflow-/Closure-Dateien (Integrität
# real grün), nur der Generator ist gestubbt.
set -euo pipefail
cd "$(dirname "$0")/.."

PASS=0; FAIL=0
fail() { echo "❌ FAIL: $1"; FAIL=$((FAIL+1)); }
ok()   { PASS=$((PASS+1)); }

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# ── S0: Vertragsanker im Guard-Text ──
grep -Fq 'PIP_DRIFT_STRICT:-0' scripts/test_pip_pinning.sh \
  && ok || fail "S0a: Drift-Ast muss an PIP_DRIFT_STRICT (Default 0 = advisory) gebunden sein"
grep -A2 'PIP_DRIFT_STRICT:-0' scripts/test_pip_pinning.sh | grep -Fq 'fail' \
  && ok || fail "S0b: STRICT-Modus muss fail() rufen"
grep -Fq 'advisory, nicht push-blockierend' scripts/test_pip_pinning.sh \
  && ok || fail "S0c: Advisory-Warnung fehlt (Drift-Ast muss warnen, nicht blockieren)"

# ── Sandbox aufbauen ──
sb="$tmp/sb"
mkdir -p "$sb/scripts" "$sb/.github/workflows" "$sb/.github/requirements"
cp scripts/test_pip_pinning.sh "$sb/scripts/"
cp .github/workflows/deploy-pages.yml .github/workflows/deploy-fdroid.yml "$sb/.github/workflows/"
cp .github/requirements/fdroidserver-requirements.txt "$sb/.github/requirements/"
cat > "$sb/scripts/gen_fdroid_requirements.py" <<'PYEOF'
import os, sys
if len(sys.argv) > 1 and sys.argv[1] == "--check":
    sys.exit(int(os.environ.get("STUB_CHECK_RC", "1")))
sys.exit(0)
PYEOF

run_sandbox() { # $1 = zusätzliche ENV (KEY=VAL oder leer)
  ( cd "$sb" && env -u JAVA_HOME ${1:+$1} bash scripts/test_pip_pinning.sh ) > "$tmp/sbx.log" 2>&1
  echo $?
}

# S1: Drift (Stub rc=1), STRICT aus → exit 0 mit Advisory-Warnung
rc=$(run_sandbox "")
if [[ "$rc" == "0" ]] && grep -q "advisory, nicht push-blockierend" "$tmp/sbx.log"; then
  ok; else fail "S1: Standard-Modus muss Drift advisory behandeln (rc=$rc)"; fi

# S2: Drift + STRICT=1 → exit != 0 mit fail-Meldung
rc=$(run_sandbox "STUB_CHECK_RC=1 PIP_DRIFT_STRICT=1")
if [[ "$rc" != "0" ]] && grep -q "nicht mehr reproduzierbar" "$tmp/sbx.log"; then
  ok; else fail "S2: STRICT-Modus muss bei Drift fail-closed sein (rc=$rc)"; fi

# S3a: STRICT ohne Drift bleibt grün
rc=$(run_sandbox "STUB_CHECK_RC=0 PIP_DRIFT_STRICT=1")
[[ "$rc" == "0" ]] && ok || fail "S3a: STRICT ohne Drift muss grün sein (rc=$rc)"

# S3b: Integrität bleibt auch im Advisory-Modus strikt — Closure ohne Hashes failt
printf 'unpinned==1.0.0\n' > "$sb/.github/requirements/fdroidserver-requirements.txt"
rc=$(run_sandbox "STUB_CHECK_RC=0")
if [[ "$rc" != "0" ]] && grep -q "formal invalid" "$tmp/sbx.log"; then
  ok; else fail "S3b: Integritätsverstoß (fehlende Hashes) muss auch advisory-strikt failen (rc=$rc)"; fi

echo "✅ [pip-drift-semantics-test] $PASS Pass, $FAIL Fail"
[[ "$FAIL" -eq 0 ]]
