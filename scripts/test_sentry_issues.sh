#!/usr/bin/env bash
# Selbsttest: Sentry-Issues-Watchdog (scripts/check_sentry_issues.sh) — offline
# über Fixtures, kein Netz, kein echtes Token. Vertrag (docs/sentry-issues.md):
# Neue/unresolved Sentry-Reports werden als GitHub-Issue (Marker
# `<!-- vivid-sentry-issue: <id> -->` zur Dedup) mit bug_report-Struktur *
# Sentry-Zusammenfassung geöffnet; die native Sentry-Integration bleibt die
# Echtzeit-Route, dieser Guard ist Watchdog/Failover dazu.
#
#   I1  Guard existiert, ausführbar, Syntax ok; nennt Doku-Pfad, Marker und
#       den Sentry-API-Vertrag (issues/?query=is:unresolved)
#   I2  Ohne Token (und ohne Fixture) → SKIP, exit 0, nennt die Anleitung
#   I3  Fixture: 2 neue + 1 bereits erfasste + 1 resolved → 2 erstellt
#       (ghstatus 201) → "OK: 2 Issue(s) neu"
#   I4  Alle unresolved bereits erfasst → "OK: 0 neue", exit 0
#   I5  Fixture issues.json ist keine Liste → exit 1 (fail-closed)
#   I6  Fixture gh_getstatus 403 → SKIP (GitHub-Token/rechte-Hinweis)
#   I7  Fake-Token erscheint nie im Output
#   I8  Fixture getstatus 403 → SKIP (project:read-Hinweis)
#   I9  --dry-run: "OK (dry-run): 2" OHNE Erstellung (ghstatus 422 unbenutzt)
#   I10 Payload-Vertrag (inspiziert issue_1.json via SENTRY_ISSUES_KEEP):
#       Marker, "## Beschreibung", "**Level: <level>", Permalink, Titel-Prefix
#   I11 PR-Einträge im GitHub-Bestand zählen NICHT als Dedup (Marker in PR
#       macht das Sentry-Issue trotzdem zum Kandidaten)
#   I12 Token-Quellen-Vertrag (--print-token-source, nur QUELLE):
#       env > issues.token > resolve.token > stats.token > auth.token > none
#   I13 Fixture ghstatus 422 → FEHLER (exit 1)
#   I14 Fixture ghstatus 401 → SKIP (GitHub-Token-Hinweis)
#   I15 Workflow-/Gate-Verdrahtung: automation-sentry-issues.yml (Cron,
#       permissions issues:write, SENTRY_ISSUES_TOKEN-Secret, AUTOMATION_TOKEN
#       als Erstellungs-Credential — GITHUB_TOKEN-Events triggern keine
#       Workflow-Runs, ohne PAT bliebe der Triage-Autolabel aus, SHA-Pin),
#       pre-push.sh und android-ci.yml führen den Selbsttest aus
#   I16 Windows-Coding-Sicherheitsnetz: beide Python-Heredocs nutzen
#       `python -X utf8`
set -euo pipefail
cd "$(dirname "$0")/.."
fail() { echo "❌ [sentry-issues-test] $1"; exit 1; }

guard=scripts/check_sentry_issues.sh
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# I1
[[ -s "$guard" ]] || fail "Guard-Skript fehlt oder ist leer"
[[ -x "$guard" ]] || fail "Guard-Skript ist nicht ausführbar"
bash -n "$guard" || fail "Guard-Skript: bash-Syntaxfehler"
grep -q "docs/sentry-issues.md" "$guard" || fail "Guard nennt den Doku-Pfad nicht"
grep -q "vivid-sentry-issue" "$guard" || fail "Guard kennt den Dedup-Marker nicht"
grep -q "issues/?query=is:unresolved" "$guard" || fail "Guard nutzt die Sentry-API (issues/?query=is:unresolved)"

# I2
out=$(SENTRY_ISSUES_TOKEN= bash "$guard" 2>/dev/null) || \
  fail "I2: Guard darf ohne Token nicht scheitern (exit 0 Vertrag)"
grep -q "^SKIP:" <<<"$out" || fail "I2: SKIP erwartet"
grep -q "docs/sentry-issues.md" <<<"$out" || fail "I2: Anleitung-Pfad erwartet"

# Fixtures
mk_new2() {
  local dir="$1"
  mkdir -p "$dir"
  cat > "$dir/issues.json" <<'JSON'
[
  {"id": "100", "shortId": "VIVID-21", "title": "NullPointerException in StreamLayout", "status": "unresolved",
   "level": "error", "count": 3, "userCount": 2,
   "firstSeen": "2026-09-25T10:00:00Z", "lastSeen": "2026-09-25T10:00:00Z",
   "permalink": "https://sentry.io/organizations/privat-jb/issues/100/?project=1",
   "metadata": {"type": "NullPointerException", "value": "tried to access field", "function": "render", "filename": "StreamLayout.kt"},
   "tags": [{"key": "os", "value": "Android"}, {"key": "device", "value": "Pixel 8"}, {"key": "release", "value": "0.5.19-beta"}]},
  {"id": "200", "shortId": "VIVID-22", "title": "ANR in SettingsScreen", "status": "unresolved",
   "level": "fatal", "count": 1, "userCount": 1,
   "firstSeen": "2026-09-26T07:00:00Z", "lastSeen": "2026-09-26T07:00:00Z",
   "metadata": {"type": "ANR", "value": "Input dispatching timed out"},
   "tags": []},
  {"id": "300", "shortId": "VIVID-23", "title": "Schon erfasst", "status": "unresolved",
   "level": "warning", "count": 9, "userCount": 5,
   "firstSeen": "2026-09-20T00:00:00Z", "lastSeen": "2026-09-26T09:00:00Z", "metadata": {}, "tags": []},
  {"id": "400", "shortId": "VIVID-24", "title": "Alt und resolved", "status": "resolved",
   "level": "error", "count": 2, "userCount": 1,
   "metadata": {}, "tags": []}
]
JSON
  cat > "$dir/ghissues.json" <<'JSON'
[
  {"number": 42, "pull_request": false, "title": "irgendwas", "body": "<!-- vivid-sentry-issue: 300 -->\nschon gemeldet"}
]
JSON
}

# I3
mk_new2 "$tmp/new2"
echo 201 > "$tmp/new2/ghstatus"
out_r3=$(SENTRY_ISSUES_FIXTURE="$tmp/new2" SENTRY_ISSUES_TOKEN=sntrys_FAKE_LEAK \
  bash "$guard") || fail "I3: Watchdog-Lauf darf nicht scheitern"
grep -q "^OK: 2 Issue(s) neu als GitHub-Issue erstellt" <<<"$out_r3" || \
  fail "I3: 2 erstellt erwartet"
grep -q "Sentry: 4 unresolved geprüft | bereits erfasst: 1 | neu: 2" <<<"$out_r3" || \
  fail "I3: Zähler (4 geprüft / 1 erfasst / 2 neu) erwartet"
grep -q "CANDIDATE: 100 → NullPointerException in StreamLayout" <<<"$out_r3" || \
  fail "I3: Kandidat 100 erwartet"

# I4
mkdir -p "$tmp/allreported"
cat > "$tmp/allreported/issues.json" <<'JSON'
[{"id": "300", "title": "Schon erfasst", "status": "unresolved", "level": "warning", "metadata": {}, "tags": []}]
JSON
cat > "$tmp/allreported/ghissues.json" <<'JSON'
[{"number": 1, "pull_request": false, "body": "<!-- vivid-sentry-issue: 300 -->\nja"}]
JSON
echo 201 > "$tmp/allreported/ghstatus"
out_r4=$(SENTRY_ISSUES_FIXTURE="$tmp/allreported" bash "$guard") || \
  fail "I4: alles-erfasst darf nicht scheitern"
grep -q "^OK: 0 neue Sentry-Issues" <<<"$out_r4" || fail "I4: OK 0 neue erwartet"

# I5
mkdir -p "$tmp/malformed"
echo 200 > "$tmp/malformed/gh_getstatus"
echo '{"keine": "liste"}' > "$tmp/malformed/issues.json"
echo '[]' > "$tmp/malformed/ghissues.json"
if SENTRY_ISSUES_FIXTURE="$tmp/malformed" bash "$guard" >/dev/null 2>&1; then
  fail "I5: issues.json ohne Liste muss fail-closed (exit 1) sein"
fi

# I6
mkdir -p "$tmp/ghforbidden"
echo 403 > "$tmp/ghforbidden/gh_getstatus"
echo '[]' > "$tmp/ghforbidden/issues.json"
out_r6=$(SENTRY_ISSUES_FIXTURE="$tmp/ghforbidden" bash "$guard") || \
  fail "I6: gh_getstatus 403 darf nicht scheitern (SKIP-Vertrag)"
grep -q "GITHUB_ISSUES_TOKEN" <<<"$out_r6" || fail "I6: GitHub-Token-Hinweis erwartet"

# I7 (Fake-Token aus I3 darf nirgends auftauchen)
grep -q "sntrys_FAKE_LEAK" <<<"$out_r3" && fail "I7: Token-Leak im Watchdog-Lauf"

# I8
mkdir -p "$tmp/sentryforbidden"
echo 403 > "$tmp/sentryforbidden/getstatus"
echo '[]' > "$tmp/sentryforbidden/issues.json"
out_r8=$(SENTRY_ISSUES_FIXTURE="$tmp/sentryforbidden" bash "$guard") || \
  fail "I8: getstatus 403 darf nicht scheitern (SKIP-Vertrag)"
grep -q "project:read" <<<"$out_r8" || fail "I8: project:read-Hinweis erwartet"

# I9 --dry-run (keine Erstellung — ghstatus 422 würde live einen FEHLER provozieren)
mk_new2 "$tmp/dryrun"
echo 422 > "$tmp/dryrun/ghstatus"
out_r9=$(SENTRY_ISSUES_FIXTURE="$tmp/dryrun" bash "$guard" --dry-run) || \
  fail "I9: dry-run darf nicht scheitern"
grep -q "^OK (dry-run): 2 Issue(s) würden neu" <<<"$out_r9" || fail "I9: dry-run-Verdict erwartet"
grep -q "422" <<<"$out_r9" && fail "I9: dry-run darf ghstatus 422 nicht auswerten"

# I10 Payload-Vertrag (Payloads via KEEP + Workdir inspizierbar)
payloaddir="$tmp/payloads"
mkdir -p "$payloaddir"
mk_new2 "$tmp/i10"
out_r10=$(SENTRY_ISSUES_FIXTURE="$tmp/i10" SENTRY_ISSUES_WORKDIR="$payloaddir" \
  SENTRY_ISSUES_KEEP=1 bash "$guard" --dry-run) || fail "I10: Payload-Lauf darf nicht scheitern"
[[ -s "$payloaddir/issue_1.json" ]] || fail "I10: issue_1.json wurde nicht geschrieben"
p=$(python -X utf8 -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['body'])" "$payloaddir/issue_1.json")
grep -q "<!-- vivid-sentry-issue: 100 -->" <<<"$p" || fail "I10: Marker fehlt im Body"
grep -q "## Beschreibung / Description" <<<"$p" || fail "I10: Überschrift fehlt"
grep -q "\*\*Level:\*\* error" <<<"$p" || fail "I10: Level-Zeile fehlt (Triage-Vertrag)"
grep -q "https://sentry.io/organizations/privat-jb/issues/100" <<<"$p" || fail "I10: Permalink fehlt"
grep -q "\*\*Top-Frame:\*\* StreamLayout.kt:render" <<<"$p" || fail "I10: Top-Frame fehlt"
grep -q "Events:\*\* 3 (2 Nutzer betroffen)" <<<"$p" || fail "I10: Event-Count fehlt"
t1=$(python -X utf8 -c "import json,sys;print(json.load(open(sys.argv[1],encoding='utf-8'))['title'])" "$payloaddir/issue_1.json")
grep -q "^\[Sentry\] NullPointerException in StreamLayout$" <<<"$t1" || fail "I10: Titel-Prefix fehlt"

# I11 PR-Einträge zählen nicht als Dedup
mkdir -p "$tmp/prignore"
cat > "$tmp/prignore/issues.json" <<'JSON'
[{"id": "500", "title": "Nur im PR erfasst", "status": "unresolved", "level": "error", "metadata": {}, "tags": []}]
JSON
cat > "$tmp/prignore/ghissues.json" <<'JSON'
[{"number": 7, "pull_request": true, "title": "Ein PR", "body": "<!-- vivid-sentry-issue: 500 -->\nzählt nicht"}]
JSON
echo 201 > "$tmp/prignore/ghstatus"
out_r11=$(SENTRY_ISSUES_FIXTURE="$tmp/prignore" bash "$guard") || \
  fail "I11: PR-ignore-Fall darf nicht scheitern"
grep -q "^OK: 1 Issue(s) neu als GitHub-Issue erstellt" <<<"$out_r11" || \
  fail "I11: Marker in PR darf nicht deduplizieren (1 erstellt erwartet)"

# I12 Token-Quellen-Vertrag (nur Quellen-Namen, nie Tokens)
src=$(SENTRY_ISSUES_TOKEN=x bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=env$" <<<"$src" || fail "I12: env-Quelle erwartet"

mkdir -p "$tmp/props"
printf 'issues.token=sntrys_III\nresolve.token=sntrys_RRR\nstats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=issues.token$" <<<"$src" || fail "I12: issues.token hat Vorrang"
grep -q "sntrys" <<<"$src" && fail "I12: Token darf nie im Source-Report stehen"

printf 'resolve.token=sntrys_RRR\nstats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=resolve.token$" <<<"$src" || fail "I12: resolve.token-Fallback erwartet"

printf 'stats.token=sntrys_AAA\nauth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=stats.token$" <<<"$src" || fail "I12: stats.token-Fallback erwartet"

printf 'auth.token=sntrys_BBB\n' > "$tmp/props/sentry.properties"
src=$(SENTRY_PROPERTIES_FILE="$tmp/props/sentry.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=auth.token$" <<<"$src" || fail "I12: auth.token-Fallback erwartet"

src=$(SENTRY_PROPERTIES_FILE="$tmp/props/keine.properties" bash "$guard" --print-token-source)
grep -q "^TOKEN_SOURCE=none$" <<<"$src" || fail "I12: none bei fehlender Datei erwartet"

# I13 ghstatus 422 → FEHLER (exit 1)
mk_new2 "$tmp/create422"
echo 422 > "$tmp/create422/ghstatus"
if SENTRY_ISSUES_FIXTURE="$tmp/create422" bash "$guard" >/dev/null 2>&1; then
  fail "I13: ghstatus 422 muss FEHLER (exit 1) sein"
fi

# I14 ghstatus 401 → SKIP (GitHub-Token-Hinweis)
mk_new2 "$tmp/create401"
echo 401 > "$tmp/create401/ghstatus"
out_r14=$(SENTRY_ISSUES_FIXTURE="$tmp/create401" bash "$guard") || \
  fail "I14: ghstatus 401 darf nicht scheitern (SKIP-Vertrag)"
grep -q "GITHUB_ISSUES_TOKEN" <<<"$out_r14" || fail "I14: GitHub-Token-Hinweis erwartet"

# I15 Workflow-/Gate-Verdrahtung
WF=.github/workflows/automation-sentry-issues.yml
[[ -s "$WF" ]] || fail "I15: Workflow fehlt: $WF"
grep -q "cron:" "$WF" || fail "I15: stündlicher Cron fehlt"
grep -q "permissions: {}" "$WF" || fail "I15: top-level permissions: {} fehlt"
grep -q "issues: write" "$WF" || fail "I15: minimales Job-Permissions-Modell fehlt"
grep -q "SENTRY_ISSUES_TOKEN" "$WF" || fail "I15: Secret SENTRY_ISSUES_TOKEN nicht verdrahtet"
grep -q 'GITHUB_ISSUES_TOKEN: \${{ secrets.AUTOMATION_TOKEN }}' "$WF" || \
  fail "I15: Erstellung muss über AUTOMATION_TOKEN (PAT) laufen — GITHUB_TOKEN-Events triggern keine Workflows, Triage bliebe aus"
grep -q 'GITHUB_ISSUES_TOKEN: \${{ secrets.GITHUB_TOKEN }}' "$WF" && \
  fail "I15: GITHUB_TOKEN darf die Issues nicht erzeugen (Triage feuert nie — Trigger-Suppression)"
grep -q "actions/checkout@" "$WF" || fail "I15: Checkout-Pin fehlt"
grep -q "check_sentry_issues.sh" "$WF" || fail "I15: Guard nicht im Workflow aufgerufen"
grep -q "test_sentry_issues.sh" scripts/pre-push.sh || fail "I15: Selbsttest nicht im Pre-Push-Gate"
grep -q "Test sentry issues watchdog guard" .github/workflows/android-ci.yml || fail "I15: Step 'Test sentry issues watchdog guard' in CI fehlt"

# I16 Windows-Coding-Sicherheitsnetz: beide Python-Heredocs mit -X utf8
utf8_calls=$(grep -c "python -X utf8 - " "$guard" || true)
[ "$utf8_calls" -ge 2 ] || fail "I16: Python-Heredocs müssen 'python -X utf8' nutzen (gefunden: $utf8_calls)"
if grep -E "python - (2>/dev/null )?<<'PY'" "$guard" >/dev/null; then
  fail "I16: es existiert noch ein Heredoc ohne -X utf8 (cp1252-Crash-Risiko)"
fi

echo "✅ [sentry-issues-test] Sentry-Issues-Guard vertragstreu (I1–I16)."