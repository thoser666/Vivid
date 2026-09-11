#!/usr/bin/env bash
# Guard: Security-Suppressions-Register (docs/security-suppressions.md).
#
# Vivid suppressiert Security-Findings nur mit dokumentierter Begründung und
# Prüfbis-Datum. Dieses Guard erzwingt die Register-Hygiene:
#   G1  Registerdatei existiert und enthält den maschinenlesbaren
#       Prüffrist-Block (<!-- review-dates-data ... -->)
#   G2  Jede Prüffrist liegt in der Zukunft (abgelaufene Daten = Gate-Fail)
#   G3  SNYK-IDs in .snyk und Register stimmen überein (beide Richtungen)
#   G4  Jeder Ignore-Eintrag in .snyk hat reason + expires (Grundhygiene,
#       Detailprüfung übernimmt check_snyk_policy.sh)
#   G5  Jeder NOSONAR-Kommentar im Kotlin-Code trägt eine Begründung
#       ("NOSONAR: " im selben Kommentar). Prosa-Erwähnungen in
#       Backticks (`NOSONAR`) gelten nicht als Suppression.
#   G6  Die Scorecard-Annotationen (.github/scorecard.yml) sind im Register
#       dokumentiert
#   G7  (optional, Netzwerk) Jeder per GitHub-API gelistete dismissed
#       Code-Scanning-/Dependabot-Alert steht im Register — Abweichungen
#       schlagen an; bei Netzwerk-/Berechtigungsfehlern neutral.
#
# Exit 0 = Register konsistent, Exit 1 = Hygiene-Verstoß.
#
# Selbsttest: scripts/test_suppressions_register.sh
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REGISTER="$REPO_ROOT/docs/security-suppressions.md"
SNYK_POLICY="$REPO_ROOT/.snyk"
SCORECARD="$REPO_ROOT/.github/scorecard.yml"
ALLOW_NO_NETWORK="${SUPPRESSIONS_GUARD_NO_NETWORK:-0}"
TODAY="$(date +%Y-%m-%d)"

fail() { echo "❌ Suppressions-Register: $*" >&2; exit 1; }
warn() { echo "⚠️  Suppressions-Register (neutral): $*" >&2; }

[ -f "$REGISTER" ] || fail "Registerdatei fehlt: docs/security-suppressions.md (G1)"

# --- Prüffrist-Block extrahieren (G1/G2) -------------------------------------
DATES_BLOCK="$(sed -n '/<!-- review-dates-data/,/^-->/p' "$REGISTER" | grep -E '^20[0-9]{2}-[0-9]{2}-[0-9]{2} ' || true)"
[ -n "$DATES_BLOCK" ] || fail "Kein Prüffrist-Block (<!-- review-dates-data -->) im Register (G1)"

while IFS= read -r line; do
    due="$(echo "$line" | awk '{print $1}')"
    label="$(echo "$line" | cut -d' ' -f2-)"
    if [ "$(date -d "$due" +%s 2>/dev/null || echo 0)" -lt "$(date -d "$TODAY" +%s)" ]; then
        fail "Prüffrist abgelaufen ($due): $label — Register aktualisieren und Review durchführen (G2)"
    fi
done <<< "$DATES_BLOCK"
echo "✅ Prüffristen: alle $(echo "$DATES_BLOCK" | grep -c . | tr -d ' ') Daten in der Zukunft (G2)"

# --- SNYK-IDs abgleichen (G3/G4) ---------------------------------------------
if [ -f "$SNYK_POLICY" ]; then
    POLICY_IDS="$(grep -E '^  SNYK-[A-Z0-9-]+:' "$SNYK_POLICY" | sed -E 's/^  (SNYK-[A-Z0-9-]+):.*/\1/' | sort -u || true)"
    REGISTER_IDS="$(grep -oE 'SNYK-[A-Z]+-[A-Z0-9]+-[0-9]+' "$REGISTER" | sort -u || true)"
    MISSING_IN_REGISTER="$(comm -23 <(echo "$POLICY_IDS") <(echo "$REGISTER_IDS") || true)"
    STALE_IN_REGISTER="$(comm -13 <(echo "$POLICY_IDS") <(echo "$REGISTER_IDS") || true)"
    if [ -n "$MISSING_IN_REGISTER" ]; then
        fail "Snyk-Ignore(s) ohne Registereintrag: $MISSING_IN_REGISTER (G3)"
    fi
    if [ -n "$STALE_IN_REGISTER" ]; then
        fail "Registereintrag(e) ohne Entsprechung in .snyk (Suppressions entfernt, Register vergessen): $STALE_IN_REGISTER (G3)"
    fi
    # G4: jede Policy-ID braucht reason: und expires: in ihrem Block
    while IFS= read -r pid; do
        [ -n "$pid" ] || continue
        BLOCK="$(sed -n "/^  ${pid}:/,/^[A-Za-z]/p" "$SNYK_POLICY")"
        echo "$BLOCK" | grep -q "reason:" || fail "Snyk-Ignore $pid ohne reason (G4)"
        echo "$BLOCK" | grep -q "expires:" || fail "Snyk-Ignore $pid ohne expires (G4)"
    done <<< "$POLICY_IDS"
    COUNT="$(echo "$POLICY_IDS" | grep -c . || true)"
    echo "✅ Snyk-Register-Abgleich: ${COUNT} Ignore(s) konsistent (G3/G4)"
else
    warn ".snyk fehlt — Snyk-Abgleich übersprungen (G3/G4)"
fi

# --- NOSONAR-Hygiene (G5) ------------------------------------------------------
NOSONAR_HITS="$(grep -rn "NOSONAR" --include="*.kt" --include="*.java" "$REPO_ROOT" 2>/dev/null | grep -v "/build/" || true)"
# Prosa-Erwähnungen (Backticks) sind keine Suppressions:
NOSONAR_SUPPRESSIONS="$(echo "$NOSONAR_HITS" | grep -vE '`[^`]*NOSONAR' || true)"
UNDOCUMENTED="$(echo "$NOSONAR_SUPPRESSIONS" | grep -v "NOSONAR: " || true)"
if [ -n "$NOSONAR_SUPPRESSIONS" ] && [ -n "$UNDOCUMENTED" ]; then
    fail "NOSONAR ohne Begründung (Format: '// NOSONAR: …'; Prosa in Backticks schreiben) in: $UNDOCUMENTED (G5)"
fi
NCOUNT="$(echo "$NOSONAR_SUPPRESSIONS" | grep -c . || true)"
echo "✅ NOSONAR-Hygiene: ${NCOUNT} begründete Stelle(n) (G5)"

# --- Scorecard-Referenz (G6) ---------------------------------------------------
if [ -f "$SCORECARD" ]; then
    grep -q "scorecard.yml" "$REGISTER" || fail "Scorecard-Annotationen sind nicht im Register dokumentiert (G6)"
    grep -q "annotations:" "$SCORECARD" || fail ".github/scorecard.yml enthält keine annotations-Sektion — Register-Doku prüfen (G6)"
    echo "✅ Scorecard-Annotationen im Register referenziert (G6)"
else
    warn ".github/scorecard.yml fehlt — G6 übersprungen"
fi

# --- Live-Gegenprobe (G7, optional) -------------------------------------------
if [ "$ALLOW_NO_NETWORK" = "1" ]; then
    warn "Netzwerk-Gegenprobe per Env deaktiviert (G7)"
elif ! command -v gh >/dev/null 2>&1; then
    warn "gh CLI nicht verfügbar — Live-Gegenprobe übersprungen (G7)"
else
    CS_IDS="$(gh api "repos/thoser666/Vivid/code-scanning/alerts?state=dismissed&per_page=100" --jq '.[].number' 2>/dev/null || true)"
    DB_IDS="$(gh api "repos/thoser666/Vivid/dependabot/alerts?state=dismissed&per_page=100" --jq '.[].number' 2>/dev/null || true)"
    if [ -z "$CS_IDS" ] && [ -z "$DB_IDS" ]; then
        warn "GitHub-API nicht erreichbar/berechtigt — Live-Gegenprobe neutral übersprungen (G7)"
    else
        UNREGISTERED=""
        for id in $CS_IDS; do
            grep -qE "(^|[^0-9])#$id([^0-9]|$)" "$REGISTER" || UNREGISTERED="$UNREGISTERED CS#$id"
        done
        for id in $DB_IDS; do
            grep -qE "(^|[^0-9])#$id([^0-9]|$)" "$REGISTER" || UNREGISTERED="$UNREGISTERED DB#$id"
        done
        if [ -n "$UNREGISTERED" ]; then
            fail "Dismissed Alert(s) ohne Registereintrag:$UNREGISTERED — docs/security-suppressions.md ergänzen (G7)"
        fi
        echo "✅ Live-Gegenprobe: alle dismissed Alerts im Register (G7)"
    fi
fi

echo "✅ Suppressions-Register: konsistent"
