#!/usr/bin/env bash
# Selbsttest: Sentry-Stats-Guard (scripts/check_sentry_stats.sh) — offline
# über Fixtures, kein Netz, kein echtes Token.
#
#   S1 Skript existiert, ausführbar, bash-Syntax ok, Doku-Pfad genannt
#   S2 Explizit ohne Token → SKIP, exit 0, nennt die Anleitung
#   S3 Fixture OK-Fall (accepted>0, dropped=0) → "OK: N", exit 0, keine WARN
#   S4 Fixture dropped>0 → "WARN:", exit 0 (informational, kein Gate-Block)
#   S5 Fixture leer (0/0) → "OK: 0 Events" (ruhig), exit 0
#   S6 Fixture mit unbekanntem API-Format → exit 1 (fail-closed)
#   S7 Kein Token-Leak: gesetzter Fake-Token erscheint nie im Output
#   S8 403-Fixture → SKIP mit Scopes-Hinweis (project:read/event:read)
#   S9 Token-Quellen-Vertrag (--print-token-source nennt nur die QUELLE,
#      nie den Token): env > stats.token > auth.token > none
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "❌ [sentry-stats-test] $1"; exit 1; }

guard=scripts/check_sentry_stats.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# S1
[[ -s "$guard" ]] || fail "Guard-Skript fehlt oder ist leer"
[[ -x "$guard" ]] || fail "Guard-Skript ist nicht ausführbar"
bash -n "$guard" || fail "Guard-Skript: bash-Syntaxfehler"
grep -q "docs/sentry-stats.md" "$guard" || fail "Guard nennt den Doku-Pfad nicht"

# S2
out=$(SENTRY_STATS_TOKEN= bash "$guard" 2>/dev/null) || \
  fail "S2: Guard darf ohne Token nicht scheitern (exit 0 Vertrag)"
grep -q "^SKIP:" <<<"$out" || fail "S2: SKIP erwartet"
grep -q "docs/sentry-stats.md" <<<"$out" || fail "S2: Anleitung-Pfad erwartet"

# S3
mkdir -p "$tmp/ok"
echo 200 > "$tmp/ok/status"
echo '{"id": "4509837327990784", "slug": "vivid", "firstEvent": "2026-08-01T00:00:00Z"}' > "$tmp/ok/project.json"
echo '{"org": [{"totals": {"accepted": 12, "dropped": 0}}]}' > "$tmp/ok/events.json"
out_s3=$(SENTRY_STATS_FIXTURE="$tmp/ok" SENTRY_STATS_TOKEN=sntrys_FAKE_LEAK_TOKEN bash "$guard") || \
  fail "S3: OK-Fall darf nicht scheitern"
grep -q "^OK: 12" <<<"$out_s3" || fail "S3: OK mit 12 Events erwartet"
grep -q "WARN" <<<"$out_s3" && fail "S3: keine WARN erwartet"

# S4
mkdir -p "$tmp/dropped"
echo 200 > "$tmp/dropped/status"
echo '{"id": "1"}' > "$tmp/dropped/project.json"
echo '{"org": [{"totals": {"accepted": 5, "dropped": 3, "rateLimited": 2}}]}' > "$tmp/dropped/events.json"
out=$(SENTRY_STATS_FIXTURE="$tmp/dropped" bash "$guard") || \
  fail "S4: dropped-Fall darf nicht scheitern (informational)"
grep -q "^WARN:" <<<"$out" || fail "S4: WARN erwartet"
grep -q "5 Event(s) in 30d angenommen, 5 verworfen" <<<"$out" || \
  fail "S4: dropped+rateLimited müssen summiert werden (erwartet 5)"

# S5
mkdir -p "$tmp/empty"
echo 200 > "$tmp/empty/status"
echo '{"id": "1"}' > "$tmp/empty/project.json"
echo '{"org": [{"totals": {"accepted": 0, "dropped": 0}}]}' > "$tmp/empty/events.json"
out=$(SENTRY_STATS_FIXTURE="$tmp/empty" bash "$guard") || \
  fail "S5: ruhiger Projekt-Fall darf nicht scheitern"
grep -q "^OK: 0 Events" <<<"$out" || fail "S5: OK (ruhig) erwartet"

# S6
mkdir -p "$tmp/malformed"
echo 200 > "$tmp/malformed/status"
echo '{"id": "1"}' > "$tmp/malformed/project.json"
echo '{"something": "else"}' > "$tmp/malformed/events.json"
if SENTRY_STATS_FIXTURE="$tmp/malformed" bash "$guard" >/dev/null 2>&1; then
  fail "S6: unbekanntes API-Format muss fail-closed (exit 1) sein"
fi

# S7 (Fake-Token aus S3 darf nirgends auftauchen)
grep -q "sntrys_FAKE_LEAK_TOKEN" <<<"$out_s3" && fail "S7: Token-Leak im OK-Fall"
grep -q "sntrys_FAKE_LEAK_TOKEN" <<<"$out" && fail "S7: Token-Leak"

# S8
mkdir -p "$tmp/forbidden"
echo 403 > "$tmp/forbidden/status"
echo '{}' > "$tmp/forbidden/project.json"
echo '{}' > "$tmp/forbidden/events.json"
out=$(SENTRY_STATS_FIXTURE="$tmp/forbidden" bash "$guard") || \
  fail "S8: 403 darf nicht scheitern (SKIP-Vertrag)"
grep -q "project:read" <<<"$out" || fail "S8: Scopes-Hinweis erwartet"

# S9 Token-Quellen-Vertrag (nur Quellen-Namen, kein Token im Output)
src=$(SENTRY_STATS_TOKEN=x bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=env$" <<<"$src" || fail "S9: env-Quelle erwartet"

mkdir -p "$tmp/props"
printf 'stats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=stats.token$" <<<"$src" || fail "S9: stats.token hat Vorrang vor auth.token"
grep -q "sntrys" <<<"$src" && fail "S9: Token darf nie im Source-Report stehen"

printf 'auth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=auth.token$" <<<"$src" || fail "S9: auth.token-Fallback erwartet"

src=$(SENTRY_PROPERTIES_FILE="$tmp/props/fehlt.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=none$" <<<"$src" || fail "S9: none bei fehlender Datei erwartet"

echo "✅ [sentry-stats-test] Sentry-Stats-Guard vertragstreu (S1–S9)."
