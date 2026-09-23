#!/usr/bin/env bash
# Selbsttest: Sentry-Health-Guard (scripts/check_sentry_health.sh) — offline,
# kein Netz, kein echter POST (die Live-Probe ist opt-in und würde sonst ein
# Event pro Testlauf ins Dashboard schreiben).
#
#   HP1 Skript existiert, ausführbar, bash-Syntax ok, Doku-Pfad genannt
#   HP2 Ohne opt-in → SKIP, exit 0 (Live-Probe-Vertrag)
#   HP3 --print-envelope: 3 Zeilen; event_id 32-hex; length == Byte-Länge
#       des Payloads; Payload-JSON mit tags.probe=health-check
#   HP4 --print-dsn: KEY/HOST/PROJ aus dem echten Manifest, unabhängig
#       nachgeprüft
#   HP5 Fixture OK (200 ohne Rate-Limit-Header) → "OK:", exit 0
#   HP6 Fixture 200 + X-Sentry-Rate-Limits → "WARN:", exit 0
#   HP7 Fixture 403 → exit 1 mit "FEHLER:"
#   HP8 Header-Inhalte werden nicht ausgegeben (Injection-Probe im
#       Fixture-Header taucht nicht im Output auf)
#   HP9 Gate-Verdrahtung: test_sentry_health.sh im pre-push.sh
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "❌ [sentry-health-test] $1"; exit 1; }

guard=scripts/check_sentry_health.sh
manifest=app/src/main/AndroidManifest.xml
prepush=scripts/pre-push.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# HP1
[[ -s "$guard" ]] || fail "Guard-Skript fehlt oder ist leer"
[[ -x "$guard" ]] || fail "Guard-Skript ist nicht ausführbar"
bash -n "$guard" || fail "Guard-Skript: bash-Syntaxfehler"
grep -q "docs/sentry-stats.md" "$guard" || fail "Guard nennt den Doku-Pfad nicht"

# HP2
out=$(bash "$guard") || fail "HP2: ohne opt-in darf der Guard nicht scheitern"
grep -q "^SKIP:" <<<"$out" || fail "HP2: SKIP ohne --live erwartet"
grep -q -- "--live" <<<"$out" || fail "HP2: SKIP muss den opt-in-Hinweis nennen"

# HP3
envf="$tmp/env.txt"
bash "$guard" --print-envelope > "$envf" || fail "HP3: --print-envelope darf nicht scheitern"
[[ $(wc -l < "$envf") -eq 3 ]] || fail "HP3: Envelope muss 3 Zeilen haben"
env_payload=$(sed -n '3p' "$envf")
env_len=$(sed -n '2p' "$envf" | sed -n 's/.*"length":\([0-9]*\).*/\1/p')
env_hdr=$(sed -n '1p' "$envf")
env_len_payload=$(printf '%s' "$env_payload" | wc -c | tr -d ' ')
[ "$env_len" = "$env_len_payload" ] || fail "HP3: length ($env_len) != Payload-Bytes ($env_len_payload)"
env_hdr_event_id=$(printf '%s' "$env_hdr" | sed -n 's/.*"event_id":"\([0-9a-f]*\)".*/\1/p')
[[ ${#env_hdr_event_id} -eq 32 ]] || fail "HP3: event_id muss 32-hex sein"
printf '%s' "$env_payload" | grep -q '"probe":"health-check"' || fail "HP3: tags.probe=health-check erwartet"
printf '%s' "$env_payload" | grep -q '"environment":"health-probe"' || fail "HP3: environment=health-probe erwartet"

# HP4 (unabhängiges Nach-Parsen der Manifest-DSN)
guard_dsn=$(bash "$guard" --print-dsn) || fail "HP4: --print-dsn darf nicht scheitern"
ref=$(python - <<PY
import re
text = open("$manifest", encoding="utf-8").read()
dsn = re.search(r'android:value="([^"]+)"', re.search(r'<meta-data[^>]*io\.sentry\.dsn[^>]*/>', text).group(0)).group(1)
key = re.sub(r'^https://([0-9a-fA-F]+)@.*', r'\1', dsn)
host = re.sub(r'^https://[0-9a-fA-F]+@([^/]+)/.*', r'\1', dsn)
proj = re.sub(r'.*/([0-9]+)$', r'\1', dsn)
print(f"KEY={key} HOST={host} PROJ={proj}")
PY
)
[ "$guard_dsn" = "$ref" ] || fail "HP4: DSN-Parsing weicht ab ($guard_dsn vs $ref)"

# HP5
mkdir -p "$tmp/ok"; echo 200 > "$tmp/ok/status"; : > "$tmp/ok/headers"
out=$(SENTRY_HEALTH_FIXTURE="$tmp/ok" bash "$guard") || fail "HP5: OK-Fall darf nicht scheitern"
grep -q "^OK: Ingest lebt" <<<"$out" || fail "HP5: OK erwartet"
grep -q "keine Rate-Limit-Header" <<<"$out" || fail "HP5: keine-Header-Hinweis erwartet"

# HP6
mkdir -p "$tmp/rl"; echo 200 > "$tmp/rl/status"
printf 'X-Sentry-Rate-Limits: 40:event:read\n' > "$tmp/rl/headers"
out=$(SENTRY_HEALTH_FIXTURE="$tmp/rl" bash "$guard") || fail "HP6: Rate-Limit-Fall darf nicht scheitern"
grep -q "^WARN:" <<<"$out" || fail "HP6: WARN erwartet"
grep -q "X-Sentry-Rate-Limits" <<<"$out" || fail "HP6: Header-Name erwartet"

# HP7
mkdir -p "$tmp/f3"; echo 403 > "$tmp/f3/status"; : > "$tmp/f3/headers"
if SENTRY_HEALTH_FIXTURE="$tmp/f3" bash "$guard" >/dev/null 2>&1; then
  fail "HP7: 403 muss exit 1 (FEHLER) sein"
fi
out=$(SENTRY_HEALTH_FIXTURE="$tmp/f3" bash "$guard" || true)
grep -q "^FEHLER: HTTP 403" <<<"$out" || fail "HP7: FEHLER-Meldung erwartet"

# HP8 (Injection-Probe: Header-Inhalt darf nicht im Output erscheinen)
mkdir -p "$tmp/inj"; echo 200 > "$tmp/inj/status"
printf 'X-Sentry-Rate-Limits: %s\n' '$(rm -rf /tmp/vivid-injection-canary)' > "$tmp/inj/headers"
out=$(SENTRY_HEALTH_FIXTURE="$tmp/inj" bash "$guard") || fail "HP8: Injection-Fall darf nicht scheitern"
grep -q "^WARN:" <<<"$out" || fail "HP8: WARN erwartet"
grep -q "injection-canary" <<<"$out" && fail "HP8: Header-Inhalt darf nicht ausgegeben werden"
test ! -e "/tmp/vivid-injection-canary" || fail "HP8: Header-Inhalt darf nie ausgeführt werden"

# HP9
grep -Fq "bash scripts/test_sentry_health.sh" "$prepush" || \
  fail "HP9: Selbsttest ist nicht im pre-push.sh verdrahtet"

echo "✅ [sentry-health-test] Sentry-Health-Guard vertragstreu (HP1–HP9)."
