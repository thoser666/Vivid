#!/usr/bin/env bash
# Selbsttest: Emulator-Gate-Retry (scripts/emulator_gate_retry.sh).
#
# Verträge:
#   E1  Klassifikation gegen echte Log-Fixtures (aus CI-Run 36025777687 rot /
#       36021918352 grün extrahiert)
#   E2  transient → Erfolg im 2. Versuch: Exit 0, Backoff 10 s, Notice
#   E3  transient dauerhaft: Exit-Code erhalten, Backoff 10 s + 20 s,
#       endgültig-Fehlermeldung
#   E4  unklassifiziert: SOFORT scheitern (fail-closed, kein Sleep)
#   E5  deterministisch: SOFORT scheitern (Vorrang, kein Sleep)
#   E6  Output-Transparenz: Ausgabe aller Versuche erscheint (nichts maskiert)
#   E7  Muster-Matrix: jedes Muster zwischen den MARKER-Kommentaren matcht
#       mind. eine zugeordnete Fixture-Datei; Boot-Noise-Fixture matcht KEIN
#       Muster (der Grün-Run-Beweis — Kernaussage der Klassifikation)
#   E8  Vorrang deterministisch > transient bei gemischtem Output (1 Versuch)
#   E9  Ohne Argumente: sofortiges Scheitern (Exit 64)
#   E10 MAX_ATTEMPTS=1: transient → kein Sleep, genau 1 Versuch
#
# Läuft im Pre-Push-Gate (pre-push.sh) — plain bash, keine Abhängigkeiten.
set -uo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
WRAPPER="$SCRIPT_DIR/emulator_gate_retry.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT
cd "$TMP" || exit 1

failures=0
check() { # check <label> <cond-exit-code>
  if [ "$2" -eq 0 ]; then echo "  ✅ $1"; else echo "  ❌ $1"; failures=$((failures + 1)); fi
}

# ── Fixtures (echte Log-Signaturen) ─────────────────────────────────────────
# F1: Roter Stable-Publish (Run 36025777687) — Tail des Gate-Steps
cat > f1_failing_tests.txt <<'EOF'
> Task :app:connectedStandardDebugAndroidTest FAILED
* What went wrong:
Execution failed for task ':app:connectedStandardDebugAndroidTest' (registered by plugin 'com.android.internal.application').
> There were failing tests. See the report at: file:///home/runner/work/Vivid/Vivid/app/build/reports/androidTests/connected/debug/flavors/standard/index.html
BUILD FAILED in 6m 49s
EOF
# F2: Task-FAILED-Zeile (ohne "There were failing tests") — Gradle-Tail-Variante
cat > f2_task_failed.txt <<'EOF'
> Task :app:connectedFossDebugAndroidTest FAILED
FAILURE: Build failed with an exception.
* What went wrong:
Execution failed for task ':app:connectedFossDebugAndroidTest'.
BUILD FAILED in 4m 12s
EOF
# F3: GRÜNER Tag-Run (Run 36021918352) — Boot-Noise, der auch bei Erfolg vorkommt
cat > f3_green_noise.txt <<'EOF'
INFO         | Storing crashdata in: /tmp/android-runner/emu-crash-37.1.11.db, detection is enabled for process: 2708
ERROR        | Unable to connect to adb daemon on port: 5037
WARNING      | Failed to process .ini file /home/runner/.android/emu-update-last-check.ini for reading.
WARNING      | Failed to load snapshot 'default_boot'
ERROR        | Failed to find ColorBuffer: 41
WARNING      | Netsim Wifi dns:///localhost:39987 is gone due to Stream removed (CANCELLED)
EOF
# F4: Deterministischer Compile-Fehler der Test-Quellen
cat > f4_compile_error.txt <<'EOF'
e: file:///home/runner/work/Vivid/Vivid/app/src/androidTest/java/com/vivid/irlbroadcaster/FooTest.kt:12:1 unresolved reference: bar
> Task :app:compileStandardDebugAndroidTestKotlin FAILED
FAILURE: Build failed with an exception.
* What went wrong: Compilation error. See log for more details
error: cannot find symbol
  symbol:   variable missingHelper
EOF
# F5: Dependency-Auflösung (Netzwerk-Klasse, wie build_retry.rb)
cat > f5_dependency.txt <<'EOF'
FAILURE: Build failed with an exception.
* What went wrong:
Could not resolve all files for configuration ':standardDebugAndroidTestRuntimeClasspath'
> Could not download androidx.test.espresso:espresso-core-3.7.0.pom
EOF
# F6: Emulator-Boot-Klasse (Vorfall 06.09.2026)
cat > f6_boot.txt <<'EOF'
qemu-system-aarch64: failed to initialize HVF: HV_UNSUPPORTED
Emulator: Process exited with code 1
EOF
# F7: Geräteverlust mid-run
cat > f7_device_lost.txt <<'EOF'
com.example.Test: Error: Device is offline
adb: failed to get feature 'shell'
error: device offline
error: device 'emulator-5554' not found
EOF

# ── Muster-Extraktion zwischen den MARKER-Kommentaren (Parse-Guard in E7a) ──
extract_patterns() { # $1 = Arrayname (DETERMINISTIC_PATTERNS|TRANSIENT_PATTERNS)
  sed -n '/<<PATTERNS-BEGIN>>/,/<<PATTERNS-END>>/p' "$WRAPPER" \
    | sed -n "/^$1=(/,/^)/p" \
    | grep -E "^  '" | sed "s/^  '//; s/'.*$//"
}

echo "▶ [test_emulator_gate_retry] Szenarien E1–E10 gegen scripts/emulator_gate_retry.sh"

# ── E1: Klassifikation über echte Runs (Funktion via Subshell-Sourcing) ────
classify_of() { # classify_of <fixture> — ruft die ECHTE Logik via --classify-Modus
  bash "$WRAPPER" --classify "$1"
}
check "E1.1 roter Run-Tail (There were failing tests) → transient"   $([ "$(classify_of f1_failing_tests.txt)" = transient ] && echo 0 || echo 1)
check "E1.2 roter Run-Tail (Task FAILED ohne Summary) → transient"   $([ "$(classify_of f2_task_failed.txt)" = transient ] && echo 0 || echo 1)
check "E1.3 GRÜN-Run-Boot-Noise → NICHT transient (Kernbeweis)"      $([ "$(classify_of f3_green_noise.txt)" != transient ] && echo 0 || echo 1)
check "E1.4 Compile-Fehler → deterministic"                          $([ "$(classify_of f4_compile_error.txt)" = deterministic ] && echo 0 || echo 1)
check "E1.5 Dependency-Auflösung → transient"                        $([ "$(classify_of f5_dependency.txt)" = transient ] && echo 0 || echo 1)
check "E1.6 Boot-Fehler → transient"                                 $([ "$(classify_of f6_boot.txt)" = transient ] && echo 0 || echo 1)
check "E1.7 Geräteverlust → transient"                               $([ "$(classify_of f7_device_lost.txt)" = transient ] && echo 0 || echo 1)

# Sleep-Stub: loggt das Delay in $STUB_LOG (zur LAUFZEIT aufgelöst — ein
# gemeinsamer Stub für alle Blöcke, keinerlei „gebackene" Pfad-Falle).
cat > "$TMP/stub_sleep" <<'EOF'
#!/usr/bin/env bash
echo "$1" >> "$STUB_LOG"
EOF
chmod +x "$TMP/stub_sleep"

# ── E2: transient → Erfolg im 2. Versuch ────────────────────────────────────
SLEEPS_FILE="$TMP/sleeps_e2"; : > "$SLEEPS_FILE"
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'if [ -e /tmp/egr_e2_done ]; then echo "ALL TESTS PASSED"; exit 0; else touch /tmp/egr_e2_done; echo "There were failing tests. See the report"; exit 3; fi' 2>&1)
rc=$?
rm -f /tmp/egr_e2_done
check "E2.1 Exit 0 nach Retry"                        $([ $rc -eq 0 ] && echo 0 || echo 1)
check "E2.2 genau 1 Sleep mit Backoff 10"             $([ "$(cat "$SLEEPS_FILE")" = "10" ] && echo 0 || echo 1)
check "E2.3 Notice meldet Versuch 2/3"                $(echo "$out" | grep -q "bestanden im Versuch 2/3" && echo 0 || echo 1)

# ── E3: transient dauerhaft → Exit-Code erhalten, Backoff 10+20 ─────────────
SLEEPS_FILE="$TMP/sleeps_e3"; : > "$SLEEPS_FILE"
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'echo "There were failing tests"; exit 3' 2>&1)
rc=$?
check "E3.1 Exit-Code des Befehls erhalten (3)"       $([ $rc -eq 3 ] && echo 0 || echo 1)
check "E3.2 Backoff 10 s + 20 s (linear)"             $([ "$(cat "$SLEEPS_FILE" | tr '\n' ' ' | xargs)" = "10 20" ] && echo 0 || echo 1)
check "E3.3 endgültig-Fehlermeldung"                  $(echo "$out" | grep -q "endgültig fehlgeschlagen nach 3 Versuchen" && echo 0 || echo 1)

# ── E4: unklassifiziert → sofort fail-closed ────────────────────────────────
SLEEPS_FILE="$TMP/sleeps_e4"; : > "$SLEEPS_FILE"
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'echo "Boom: total Unexpected"; exit 7' 2>&1)
rc=$?
check "E4.1 kein Retry (Exit-Code 7 sofort)"          $([ $rc -eq 7 ] && echo 0 || echo 1)
check "E4.2 kein Sleep"                               $([ ! -s "$SLEEPS_FILE" ] && echo 0 || echo 1)
check "E4.3 fail-closed-Meldung mit Klasse"           $(echo "$out" | grep -q "Klasse: unclassified" && echo 0 || echo 1)

# ── E5: deterministisch → sofort fail-closed ────────────────────────────────
SLEEPS_FILE="$TMP/sleeps_e5"; : > "$SLEEPS_FILE"
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'echo "e: file:///x/FooTest.kt:12:1 unresolved reference: bar"; echo "Compilation error"; exit 2' 2>&1)
rc=$?
check "E5.1 kein Retry (Exit-Code 2 sofort)"          $([ $rc -eq 2 ] && echo 0 || echo 1)
check "E5.2 kein Sleep"                               $([ ! -s "$SLEEPS_FILE" ] && echo 0 || echo 1)
check "E5.3 Klasse: deterministic gemeldet"           $(echo "$out" | grep -q "Klasse: deterministic" && echo 0 || echo 1)

# ── E6: Output-Transparenz ──────────────────────────────────────────────────
rm -f /tmp/egr_e6_done
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$TMP/sleeps_e6" bash "$WRAPPER" bash -c 'if [ -e /tmp/egr_e6_done ]; then echo "SUCCESSTEXT-ALPHA"; exit 0; else touch /tmp/egr_e6_done; echo "FAILTEXT-BETA There were failing tests"; exit 1; fi' 2>&1)
rc=$?
rm -f /tmp/egr_e6_done
check "E6.1 fehlgeschlagener Versuch sichtbar"        $(echo "$out" | grep -q "FAILTEXT-BETA" && echo 0 || echo 1)
check "E6.2 erfolgreicher Versuch sichtbar"           $(echo "$out" | grep -q "SUCCESSTEXT-ALPHA" && echo 0 || echo 1)

# ── E7: Muster-Matrix ───────────────────────────────────────────────────────
bad=0; total=0
while IFS= read -r pat; do
  [ -z "$pat" ] && continue
  total=$((total + 1))
  m=0
  for f in f1_failing_tests.txt f2_task_failed.txt f4_compile_error.txt f5_dependency.txt f6_boot.txt f7_device_lost.txt; do
    grep -qE "$pat" "$f" && m=1
  done
  [ $m -eq 0 ] && { echo "    ❌ Muster ohne Fixture-Treffer: $pat"; bad=$((bad + 1)); }
done < <(extract_patterns TRANSIENT_PATTERNS; extract_patterns DETERMINISTIC_PATTERNS)
check "E7.1 jedes Muster hat einen Fixture-Treffer ($total Muster)" $([ $bad -eq 0 ] && [ "$total" -ge 8 ] && echo 0 || echo 1)
noise=0
while IFS= read -r pat; do
  [ -z "$pat" ] && continue
  grep -qE "$pat" f3_green_noise.txt && { echo "    ❌ Boot-Noise würde matchen: $pat"; noise=$((noise + 1)); }
done < <(extract_patterns TRANSIENT_PATTERNS; extract_patterns DETERMINISTIC_PATTERNS)
check "E7.2 Grün-Run-Noise matcht KEIN Muster"        $([ $noise -eq 0 ] && echo 0 || echo 1)
check "E7.3 Marker-Kommentare intakt (Parse-Guard)"   $(grep -q "<<PATTERNS-BEGIN>>" "$WRAPPER" && grep -q "<<PATTERNS-END>>" "$WRAPPER" && echo 0 || echo 1)

# ── E8: Vorrang deterministisch > transient ─────────────────────────────────
SLEEPS_FILE="$TMP/sleeps_e8"; : > "$SLEEPS_FILE"
out=$(SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'echo "There were failing tests"; echo "e: file:///x/FooTest.kt:9:1 error"; exit 5' 2>&1)
rc=$?
check "E8.1 gemischter Output: sofort fail (1 Versuch)" $([ $rc -eq 5 ] && [ ! -s "$SLEEPS_FILE" ] && echo 0 || echo 1)
check "E8.2 als deterministic klassifiziert"          $(echo "$out" | grep -q "Klasse: deterministic" && echo 0 || echo 1)

# ── E9: ohne Argumente ──────────────────────────────────────────────────────
bash "$WRAPPER" >/dev/null 2>&1; rc=$?
check "E9.1 Exit 64 ohne Argumente"                   $([ $rc -eq 64 ] && echo 0 || echo 1)

# ── E10: MAX_ATTEMPTS=1 ─────────────────────────────────────────────────────
SLEEPS_FILE="$TMP/sleeps_e10"; : > "$SLEEPS_FILE"
out=$(MAX_ATTEMPTS=1 SLEEP_CMD="$TMP/stub_sleep" STUB_LOG="$SLEEPS_FILE" bash "$WRAPPER" bash -c 'echo "There were failing tests"; exit 3' 2>&1)
rc=$?
check "E10.1 genau 1 Versuch, kein Sleep"             $([ $rc -eq 3 ] && [ ! -s "$SLEEPS_FILE" ] && echo 0 || echo 1)
check "E10.2 endgültig nach 1 Versuch"                $(echo "$out" | grep -q "endgültig fehlgeschlagen nach 1 Versuchen" && echo 0 || echo 1)

echo
if [ "$failures" -eq 0 ]; then
  echo "✅ [test_emulator_gate_retry] Alle Checks bestanden."
  exit 0
else
  echo "❌ [test_emulator_gate_retry] $failures Check(s) fehlgeschlagen."
  exit 1
fi
