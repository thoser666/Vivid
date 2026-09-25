#!/usr/bin/env bash
# Selbsttest: Sentry-Resolve-Guard (scripts/check_sentry_resolve.sh) — offline
# über Fixtures, kein Netz, kein echtes Token. Vertrag (docs/sentry-stats.md,
# Abschnitt 6): Issues mit Issue-Tag `fix-release: <version>` werden beim
# Stable-Publish der Version als resolved/inNextRelease markiert.
#
#   R1 Script existiert, ausführbar, bash-Syntax ok, Doku-Pfad + fix-release-
#      Vertrag + inNextRelease + project:write-Hinweis genannt
#   R2 Ohne Token (und ohne Fixture) → SKIP, exit 0, nennt die Anleitung
#   R3 Fixture Match (2 Treffer, davon 1 mit 'v'-Präfix + 1 resolved endet
#      nicht) + Fixture-status 200 → "OK: 2 Issue(s)", IDs korrekt (dry ohne
#      Netz), kein Token-Leak
#   R4 Fixture ohne passenden Tag → "OK: 0 Issues mit Tag fix-release:", exit 0
#   R5 Fixture issues.json ist keine Liste → exit 1 (fail-closed)
#   R6 Fixture-status 403 → SKIP mit project:write-Hinweis (Schreibscopes fehlen)
#   R7 Fake-Token erscheint nie im Output
#   R8 Fixture-getstatus 403 → SKIP mit project:read-Hinweis (Lesen fehlt)
#   R9 Versions-Normalisierung: Tag '0.6.0-BETA' matcht --version v0.6.0-beta
#   R10 Token-Quellen-Vertrag (--print-token-source, nur QUELLE, nie Token):
#       env > resolve.token > stats.token > auth.token > none
#   R11 --dry-run: meldet "OK (dry-run): N" ohne PUT-Ausführung
#   R12 Windows-Coding-Sicherheitsnetz: beide Python-Heredocs des Guards
#       laufen mit `python -X utf8` — sonst crashen cp1252-Locales (Windows)
#       mit verschleiertem UnicodeEncodeError an —/→/ü in der Ausgabe
#       (stderr gefiltert; Regression vom 25.09.2026, entdeckt am
#       Pre-Push-Gate R3). Struktur-Check statt Fixture-Fall, damit er auf
#       jedem Host deterministisch greift.
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "❌ [sentry-resolve-test] $1"; exit 1; }

guard=scripts/check_sentry_resolve.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# R1
[[ -s "$guard" ]] || fail "Guard-Skript fehlt oder ist leer"
[[ -x "$guard" ]] || fail "Guard-Skript ist nicht ausführbar"
bash -n "$guard" || fail "Guard-Skript: bash-Syntaxfehler"
grep -q "docs/sentry-stats.md" "$guard" || fail "Guard nennt den Doku-Pfad nicht"
grep -q "fix-release" "$guard" || fail "Guard kennt den fix-release-Tag-Vertrag nicht"
grep -q "inNextRelease" "$guard" || fail "Guard resolvet mit inNextRelease (Bulk-Update)"
grep -q "project:write" "$guard" || fail "Guard nennt den project:write-Scope-Hinweis"
grep -q "issues/?id=" "$guard" || fail "Guard baut den Bulk-URL mit ?id= (Sentry-API Vertrag)"

# R2
out=$(SENTRY_RESOLVE_TOKEN= bash "$guard" --version v0.6.0-beta 2>/dev/null) || \
  fail "R2: Guard darf ohne Token nicht scheitern (exit 0 Vertrag)"
grep -q "^SKIP:" <<<"$out" || fail "R2: SKIP erwartet"
grep -q "docs/sentry-stats.md" <<<"$out" || fail "R2: Anleitung-Pfad erwartet"

# Fixtures für R3/R4/R5/R6/R8
mk_match_fixture() {
  local dir="$1"
  mkdir -p "$dir"
  cat > "$dir/issues.json" <<'JSON'
[
  {"id": "100", "shortId": "VIVID-12", "title": "Alter Fehler", "status": "unresolved",
   "tags": [{"key": "release", "value": "0.5.0"}, {"key": "fix-release", "value": "0.6.0-beta"}]},
  {"id": "200", "shortId": "VIVID-13", "title": "Zweiter Fehler", "status": "unresolved",
   "tags": [{"key": "fix-release", "value": "v0.6.0-beta"}]},
  {"id": "300", "shortId": "VIVID-14", "title": "Andere Version", "status": "unresolved",
   "tags": [{"key": "fix-release", "value": "0.5.0"}]},
  {"id": "400", "shortId": "VIVID-15", "title": "Kein Tag", "status": "unresolved", "tags": []},
  {"id": "500", "shortId": "VIVID-16", "title": "Schon erledigt", "status": "resolved",
   "tags": [{"key": "fix-release", "value": "0.6.0-beta"}]}
]
JSON
}

# R3
mk_match_fixture "$tmp/match"
echo 200 > "$tmp/match/status"
out_r3=$(SENTRY_RESOLVE_FIXTURE="$tmp/match" SENTRY_RESOLVE_TOKEN=sntrys_FAKE_LEAK_TOKEN \
  bash "$guard" --version v0.6.0-beta) || fail "R3: Match-Fall darf nicht scheitern"
grep -q "^OK: 2 Issue(s) als resolved (inNextRelease) markiert" <<<"$out_r3" || \
  fail "R3: 2 resolved erwartet (100 + 200, 500 ist schon resolved)"
grep -q "IDs: 100,200" <<<"$out_r3" || fail "R3: IDs 100,200 erwartet"
grep -q "Geprüft: 5 unresolved Issues" <<<"$out_r3" || fail "R3: 5 geprüfte Issues erwartet"

# R4
mkdir -p "$tmp/nomatch"
echo 200 > "$tmp/nomatch/status"
echo '[]' > "$tmp/nomatch/issues.json"
out_r4=$(SENTRY_RESOLVE_FIXTURE="$tmp/nomatch" bash "$guard" --version v0.6.0-beta) || \
  fail "R4: No-Match-Fall darf nicht scheitern"
grep -q "^OK: 0 Issues mit Tag fix-release:0.6.0-beta" <<<"$out_r4" || fail "R4: OK 0 erwartet"

# R5
mkdir -p "$tmp/malformed"
echo 200 > "$tmp/malformed/status"
echo '{"something": "else"}' > "$tmp/malformed/issues.json"
if SENTRY_RESOLVE_FIXTURE="$tmp/malformed" bash "$guard" --version v0.6.0-beta >/dev/null 2>&1; then
  fail "R5: issues.json ohne Liste muss fail-closed (exit 1) sein"
fi

# R6
mkdir -p "$tmp/writeforbidden"
echo 403 > "$tmp/writeforbidden/status"
cp "$tmp/match/issues.json" "$tmp/writeforbidden/issues.json"
out_r6=$(SENTRY_RESOLVE_FIXTURE="$tmp/writeforbidden" bash "$guard" --version v0.6.0-beta) || \
  fail "R6: 403 darf nicht scheitern (SKIP-Vertrag)"
grep -q "project:write" <<<"$out_r6" || fail "R6: project:write-Hinweis erwartet"

# R7 (Fake-Token aus R3 darf nirgends auftauchen)
grep -q "sntrys_FAKE_LEAK_TOKEN" <<<"$out_r3" && fail "R7: Token-Leak im Match-Fall"

# R8
mkdir -p "$tmp/readforbidden"
echo 403 > "$tmp/readforbidden/getstatus"
echo '[]' > "$tmp/readforbidden/issues.json"
out_r8=$(SENTRY_RESOLVE_FIXTURE="$tmp/readforbidden" bash "$guard" --version v0.6.0-beta) || \
  fail "R8: getstatus 403 darf nicht scheitern (SKIP-Vertrag)"
grep -q "project:read" <<<"$out_r8" || fail "R8: project:read-Hinweis erwartet"

# R9 Versions-Normalisierung (case-insensitiv, führendes v egal)
mkdir -p "$tmp/case"
echo 200 > "$tmp/case/status"
echo '[{"id":"99","title":"Upper","status":"unresolved","tags":[{"key":"fix-release","value":"0.6.0-BETA"}]}]' > "$tmp/case/issues.json"
out_r9=$(SENTRY_RESOLVE_FIXTURE="$tmp/case" bash "$guard" --version v0.6.0-beta) || \
  fail "R9: Normalisierungs-Fall darf nicht scheitern"
grep -q "^OK: 1 Issue(s) als resolved" <<<"$out_r9" || fail "R9: Tag 0.6.0-BETA muss v0.6.0-beta matchen"

# R10 Token-Quellen-Vertrag (nur Quellen-Namen, kein Token im Output)
src=$(SENTRY_RESOLVE_TOKEN=x bash "$guard" --version v0.6.0-beta --print-token-source)
grep -q "^TOKEN_SOURCE=env$" <<<"$src" || fail "R10: env-Quelle erwartet"

mkdir -p "$tmp/props"
printf 'resolve.token=sntrys_RRR\nstats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --version v0.6.0-beta --print-token-source)
grep -q "^TOKEN_SOURCE=resolve.token$" <<<"$src" || fail "R10: resolve.token hat Vorrang vor stats/auth.token"
grep -q "sntrys" <<<"$src" && fail "R10: Token darf nie im Source-Report stehen"

printf 'stats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --version v0.6.0-beta --print-token-source)
grep -q "^TOKEN_SOURCE=stats.token$" <<<"$src" || fail "R10: stats.token-Fallback erwartet"

printf 'auth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --version v0.6.0-beta --print-token-source)
grep -q "^TOKEN_SOURCE=auth.token$" <<<"$src" || fail "R10: auth.token-Fallback erwartet"

src=$(SENTRY_PROPERTIES_FILE="$tmp/props/fehlt.properties" bash "$guard" --version v0.6.0-beta --print-token-source)
grep -q "^TOKEN_SOURCE=none$" <<<"$src" || fail "R10: none bei fehlender Datei erwartet"

# R11 --dry-run (kein PUT — Fixture-status bleibt unbenutzt)
mkdir -p "$tmp/dryrun"
echo 403 > "$tmp/dryrun/status"   # würde live ein 403-SKIP provozieren — dry-run muss das vermeiden
cp "$tmp/match/issues.json" "$tmp/dryrun/issues.json"
out_r11=$(SENTRY_RESOLVE_FIXTURE="$tmp/dryrun" bash "$guard" --version v0.6.0-beta --dry-run) || \
  fail "R11: dry-run darf nicht scheitern"
grep -q "^OK (dry-run): 2 Issue(s) würden als resolved" <<<"$out_r11" || fail "R11: dry-run-Verdict erwartet"
grep -q "project:write" <<<"$out_r11" && fail "R11: dry-run darf den Fixture-status 403 nicht auswerten"

# R12 Windows-Coding-Sicherheitsnetz (strukturtreu, hostunabhängig): beide
# Python-Heredocs müssen mit `python -X utf8` laufen.
utf8_calls=$(grep -c "python -X utf8 - " "$guard" || true)
[ "$utf8_calls" -eq 2 ] || fail "R12: beide Python-Heredocs müssen 'python -X utf8' nutzen (gefunden: $utf8_calls)"
if grep -E "python - (2>/dev/null )?<<'PY'" "$guard" >/dev/null; then
  fail "R12: es existiert noch ein Heredoc ohne -X utf8 (cp1252-Crash-Risiko)"
fi

echo "✅ [sentry-resolve-test] Sentry-Resolve-Guard vertragstreu (R1–R12)."