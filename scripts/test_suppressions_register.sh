#!/usr/bin/env bash
# Selbsttest: Suppressions-Register-Guard (scripts/check_suppressions_register.sh,
# komplett offline — die Live-Gegenprobe G7 wird per Env deaktiviert).
#
# Prüft pro Fixture ein isoliertes Temp-Repo (Guard wird dorthin kopiert):
#   T1: gültiges Register + .snyk + Scorecard     → RC 0
#   T2: abgelaufene Prüffrist im Register          → RC 1, "abgelaufen" (G2)
#   T3: Prüffrist-Block fehlt                      → RC 1, "Kein Prüffrist-Block" (G1)
#   T4: Snyk-Ignore ohne Registereintrag           → RC 1, "ohne Registereintrag" (G3)
#   T5: Registereintrag ohne .snyk-Ignore          → RC 1, "ohne Entsprechung" (G3)
#   T6: NOSONAR ohne Begründung                    → RC 1, "ohne Begründung" (G5)
#   T7: NOSONAR als Prosa in Backticks             → RC 0 (keine Suppression)
#   T8: Registerdatei fehlt                        → RC 1, "Registerdatei fehlt" (G1)
#   T9: Scorecard nicht im Register dokumentiert   → RC 1, "nicht im Register" (G6)
#
# Exit 0 = alle Tests bestanden, Exit 1 = mindestens ein Test fehlgeschlagen.
set -u

GUARD="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/check_suppressions_register.sh"
PASS=0
FAIL=0

VALID_REGISTER='// review-dates-data
2027-03-11 Halbjährlicher Review: Dismissals, NOSONAR, Scorecard
-->

## 1. Snyk-Policy

| ID | Paket |
|---|---|
| SNYK-RUBY-RUBYZIP-19666145 | rubyzip 2.4.1 (via fastlane) |'

VALID_SNYK='# Snyk-Policy
version: v1.25.1
ignore:
  SNYK-RUBY-RUBYZIP-19666145:
    - '"'"'*'"'"':
        reason: >-
          rubyzip 2.4.1: Directory Traversal, Fix unerreichbar (fastlane pinnt
          < 3.0.0), Risiko bewusst akzeptiert, pipeline-seitig ohne
          Angreifer-kontrollierte Zip-Pfade.
        expires: '"'"'2026-12-10T00:00:00.000Z'"'"'
patch: {}'

setup_repo() {
    local dir="$1"
    mkdir -p "$dir/scripts" "$dir/docs" "$dir/.github"
    cp "$GUARD" "$dir/scripts/"
    # Register mit Block-Marker:
    {
        echo '# Register'
        echo '<!-- review-dates-data'
        printf '%s\n' "$VALID_REGISTER"
        echo
        echo '.github/scorecard.yml Annotations: dokumentiert.'
    } > "$dir/docs/security-suppressions.md"
    printf '%s\n' "$VALID_SNYK" > "$dir/.snyk"
    printf 'annotations:\n  - checks: [binary-artifacts]\n' > "$dir/.github/scorecard.yml"
}

run_guard() {
    (cd "$1" && SUPPRESSIONS_GUARD_NO_NETWORK=1 bash scripts/check_suppressions_register.sh 2>&1)
}

check() { # $1=T-Nummer  $2=Beschreibung  $3=erwartet-RC  $4=Pattern  $5..=Optional: RC-Override
    local t="$1" desc="$2" expect_rc="$3" pattern="$4" out rc
    out="$(run_guard "$TMP/t$t")"
    rc=$?
    if [ "$rc" -eq "$expect_rc" ] && echo "$out" | grep -q "$pattern"; then
        echo "PASS T$t: $desc"
        PASS=$((PASS + 1))
    else
        echo "FAIL T$t: $desc (rc=$rc, erwartet=$expect_rc, pattern='$pattern')"
        echo "$out" | sed 's/^/    | /'
        FAIL=$((FAIL + 1))
    fi
}

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

# --- Fixtures -----------------------------------------------------------------
for n in 1 2 3 4 5 6 7 8 9; do setup_repo "$TMP/t$n"; done

# T2: Prüffrist in der Vergangenheit
sed -i 's/2027-03-11 Halbjährlicher/2020-01-01 Halbjährlicher/' "$TMP/t2/docs/security-suppressions.md"

# T3: Block-Marker entfernen
sed -i 's/<!-- review-dates-data//' "$TMP/t3/docs/security-suppressions.md"

# T4: .snyk ohne Registereintrag (zweite ID nur in der Policy)
python - "$TMP/t4/.snyk" <<'PYEOF'
import sys
p = sys.argv[1]
s = open(p).read()
s = s.replace("patch: {}", "  SNYK-JAVA-SOMEPKG-9999999:\n    - '*':\n        reason: >-\n          Nur-Test-Eintrag, Fix unerreichbar im Oekosystem, Risiko bewusst\n          akzeptiert und pipeline-seitig ohne Angreifer-Kontrollpfad.\n        expires: '2027-01-01T00:00:00.000Z'\npatch: {}")
open(p, "w").write(s)
PYEOF

# T5: Register ohne .snyk-Entsprechung (ID-Zeile im Register ergänzen)
sed -i 's#| SNYK-RUBY-RUBYZIP-19666145 | rubyzip 2.4.1 (via fastlane) |#| SNYK-RUBY-RUBYZIP-19666145 | rubyzip 2.4.1 (via fastlane) |\n| SNYK-JAVA-OTHERPKG-8888888 | java-pkg |#' "$TMP/t5/docs/security-suppressions.md"

# T6: unbegründeter NOSONAR im Code
mkdir -p "$TMP/t6/feature-x"
echo 'val url = "rtmp://x" // NOSONAR' > "$TMP/t6/feature-x/S.kt"

# T7: NOSONAR nur als Prosa-Erwähnung
mkdir -p "$TMP/t7/feature-x"
echo ' * Deshalb `// NOSONAR` später — siehe Register.' > "$TMP/t7/feature-x/S.kt"

# T8: Registerdatei löschen
rm "$TMP/t8/docs/security-suppressions.md"

# T9: Scorecard-Referenz aus dem Register entfernen
sed -i '/scorecard.yml Annotations/d' "$TMP/t9/docs/security-suppressions.md"
sed -i '/scorecard.yml/d' "$TMP/t9/docs/security-suppressions.md" 2>/dev/null || true

# --- Tests --------------------------------------------------------------------
check 1 "gültiges Setup → RC 0"          0 "konsistent"
check 2 "abgelaufene Prüffrist (G2)"     1 "abgelaufen"
check 3 "Prüffrist-Block fehlt (G1)"     1 "Kein Prüffrist-Block"
check 4 "Snyk-Ignore ohne Register (G3)" 1 "ohne Registereintrag"
check 5 "Register ohne Snyk-Ignore (G3)" 1 "ohne Entsprechung"
check 6 "NOSONAR ohne Begründung (G5)"   1 "ohne Begründung"
check 7 "NOSONAR-Prosa erlaubt (G5)"     0 "konsistent"
check 8 "Registerdatei fehlt (G1)"       1 "Registerdatei fehlt"
check 9 "Scorecard nicht dokumentiert (G6)" 1 "nicht im Register"

# T10: G7 mit 403-Antwort (Stub-gh ohne Berechtigung) -> neutral, RC 0
mkdir -p "$TMP/t10/scripts" "$TMP/t10/docs" "$TMP/t10/.github" "$TMP/t10/bin"
cp "$GUARD" "$TMP/t10/scripts/"
{
        echo '# Register'
        echo '<!-- review-dates-data'
        printf '%s
' "$VALID_REGISTER"
        echo
        echo '.github/scorecard.yml Annotations: dokumentiert.'
        echo 'Dismissed: #8, #461 (Code Scanning).'
} > "$TMP/t10/docs/security-suppressions.md"
printf '%s
' "$VALID_SNYK" > "$TMP/t10/.snyk"
printf 'annotations:
  - checks: [binary-artifacts]
' > "$TMP/t10/.github/scorecard.yml"
cat > "$TMP/t10/bin/gh" <<'STUBGH'
#!/usr/bin/env bash
# Simuliert fehlende Dependabot-Alert-Berechtigung (GITHUB_TOKEN-Basis)
if [[ "$*" == *dependabot/alerts* ]]; then
    echo '{"message":"Resource not accessible by integration","status":"403"}'
    exit 1
fi
if [[ "$*" == *code-scanning/alerts* ]]; then
    echo '8'
    echo '461'
    exit 0
fi
exit 0
STUBGH
chmod +x "$TMP/t10/bin/gh"
out_t10="$(cd "$TMP/t10" && PATH="$TMP/t10/bin:$PATH" bash scripts/check_suppressions_register.sh 2>&1)"
rc_t10=$?
if [ "$rc_t10" -eq 0 ] && echo "$out_t10" | grep -q 'neutral'; then
    echo 'PASS T10: G7 mit 403 (Dependabot) laeuft neutral weiter (RC 0)'
    PASS=$((PASS + 1))
else
    echo "FAIL T10: 403-Fall nicht neutral (rc=$rc_t10)"
    echo "$out_t10" | sed 's/^/    | /'
    FAIL=$((FAIL + 1))
fi

echo
echo "$PASS PASS, $FAIL FAIL"
[ "$FAIL" -eq 0 ] || exit 1
