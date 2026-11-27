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
#   R12 Windows-Coding-Sicherheitsnetz: alle Python-Heredocs des Guards
#       laufen mit `python -X utf8` — sonst crashen cp1252-Locales (Windows)
#       mit verschleiertem UnicodeEncodeError an —/→/ü in der Ausgabe
#       (stderr gefiltert; Regression vom 25.09.2026, entdeckt am
#       Pre-Push-Gate R3). Struktur-Check statt Fixture-Fall, damit er auf
#       jedem Host deterministisch greift.
#   R14 --ids: nur die genannten, noch offenen IDs werden resolvt; eine
#       bereits resolved ID wird nicht mitgeschickt, aber als HINWEIS genannt
#   R15 --ids ohne offenen Treffer -> "OK: 0 Issues aus --ids", exit 0
#   R16 Aufruf-Vertrag: --ids + --version (exklusiv), nicht-numerische ID und
#       fehlender Auswahl-Modus sind FEHLER (exit 1), nicht SKIP
#   R17 --ids + --dry-run: meldet die IDs, ohne PUT (Fixture-status bleibt
#       unbenutzt) und ohne inNextRelease-Wortlaut
#   R18 Header-Haertung: Token ohne CR/LF vor der Header-Nutzung (415-Befund)
#   R19 --ids + --in-next-release: der PUT-Body traegt statusDetails.inNextRelease
#       und das Verdict nennt es (Fix 1b74d05 erstmals in v0.5.51-beta, #429)
#   R20 Gegenprobe: --ids ohne Flag sendet weiter nur "resolved"
#   R21 --in-next-release ohne --ids ist ein Aufruffehler (exit 1), kein Fallback
#   R22 --ids + --in-next-release + --dry-run: Verdict nennt inNextRelease, kein PUT
#   R23 --ids auf ein SCHON resolved Issue (nicht in is:unresolved): mit Flag
#       wird es nachgeladen und auf inNextRelease hochgezogen (#429)
#   R24 Gegenprobe: bereits resolved/inNextRelease -> HINWEIS, kein PUT
#   R25 Plain-Vertrag: resolved Issue ohne Flag wird nicht angefasst
#   R26 Sentry-Status "resolvedInNextRelease" gilt als erledigt (kein PUT); sonst
#       meldet ein Dry-Run die ID nach erfolgreichem PUT weiter als offen
#   R13 VIVID_REPO_ROOT (#250): der Guard darf aus $RUNNER_TEMP aufgerufen
#      werden (Stable-Publish staget die Gate-Skripte dorthin, damit der
#      Arbeitsbaum nicht vom Ziel-Tag stammt). Ohne gesetzten Repo-Root muss
#      der Aufruf scheitern, mit gesetztem Repo-Root laufen — sonst waere der
#      Vertrag eine tote Zusicherung.
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
[ "$utf8_calls" -eq 3 ] || fail "R12: alle drei Python-Heredocs müssen 'python -X utf8' nutzen (gefunden: $utf8_calls)"
if grep -E "python - (2>/dev/null )?<<'PY'" "$guard" >/dev/null; then
  fail "R12: es existiert noch ein Heredoc ohne -X utf8 (cp1252-Crash-Risiko)"
fi

# R13 VIVID_REPO_ROOT (#250): der Stable-Publish legt die Gate-Skripte nach
# $RUNNER_TEMP, damit der Arbeitsbaum nicht vom Ziel-Tag stammt (sonst exit
# 127 bei alten Tags). Aus $RUNNER_TEMP zeigt $0/.. aber ins Leere.
#
# Der Nachweis muss den tatsächlichen cd beobachten, nicht nur "läuft/scheitert" —
# im Fixture-Modus kommt der Guard ohne jedes Repo aus. Beobachtbar wird der
# cd über einen RELATIVEN SENTRY_PROPERTIES_FILE: der Pfad wird nach dem cd
# aufgelöst, also verrät die Token-Quelle, in welchem Verzeichnis der Guard
# wirklich steht. Das Test-Repo wird selbst gebaut und liegt nur in $tmp —
# das echte Arbeitsverzeichnis bleibt unberührt.
alien="$tmp/alien-runner-temp/scripts"
mkdir -p "$alien" "$tmp/fakerepo/docs"
cp "$guard" "$alien/check_sentry_resolve.sh"
printf 'auth.token=sntrys_FAKE_REPO\n' > "$tmp/fakerepo/sentry.properties"
: > "$tmp/fakerepo/docs/sentry-stats.md"
# 1) Aus dem Alien-Verzeichnis OHNE VIVID_REPO_ROOT: cd zeigt auf
#    $tmp/alien-runner-temp, wo keine sentry.properties liegt → none.
without=$(cd "$tmp/alien-runner-temp" && SENTRY_PROPERTIES_FILE=sentry.properties \
  bash "$alien/check_sentry_resolve.sh" --version v0.6.0-beta --print-token-source 2>&1) \
  || fail "R13: --print-token-source darf nicht scheitern: $without"
grep -q "^TOKEN_SOURCE=none$" <<<"$without" \
  || fail "R13: ohne VIVID_REPO_ROOT muss der Guard ausserhalb des Repos stehen (war: $without)"
# 2) Mit VIVID_REPO_ROOT: identischer Aufruf, nur der Root gesetzt → der Guard
#    landet im Fake-Repo und findet die dortige sentry.properties.
with=$(cd "$tmp/alien-runner-temp" && VIVID_REPO_ROOT="$tmp/fakerepo" \
  SENTRY_PROPERTIES_FILE=sentry.properties \
  bash "$alien/check_sentry_resolve.sh" --version v0.6.0-beta --print-token-source 2>&1) \
  || fail "R13: VIVID_REPO_ROOT muss den Aufruf funktionsfaehig halten: $with"
grep -q "^TOKEN_SOURCE=auth.token$" <<<"$with" \
  || fail "R13: VIVID_REPO_ROOT hat den cd nicht ausgefuehrt (war: $with)"
# 3) Rueckwaertskompatibilitaet: der alte Aufrufsweg bleibt unveraendert.
grep -q 'cd "${VIVID_REPO_ROOT:-$(dirname "$0")/..}"' "$guard" \
  || fail "R13: \$0-Fallback muss erhalten bleiben (Rueckwaertskompatibilitaet)"

# R14 --ids resolvt genau die genannten offenen IDs (500 ist resolved)
out_r14=$(SENTRY_RESOLVE_FIXTURE="$tmp/match" bash "$guard" --ids 100,500) || \
  fail "R14: --ids darf nicht scheitern"
grep -q "^OK: 1 Issue(s) als resolved markiert (ohne inNextRelease) — IDs: 100" <<<"$out_r14" || \
  fail "R14: genau ID 100 erwartet (500 ist already resolved)"
grep -q "HINWEIS: 1 ID(s) nicht unter den unresolved Issues" <<<"$out_r14" || \
  fail "R14: HINWEIS auf die nicht-offene ID 500 erwartet"
grep -q "inNextRelease) markiert" <<<"$out_r14" && fail "R14: --ids darf nicht inNextRelease melden"

# R15 --ids ohne offenen Treffer
out_r15=$(SENTRY_RESOLVE_FIXTURE="$tmp/match" bash "$guard" --ids 999) || \
  fail "R15: --ids ohne Treffer darf nicht scheitern"
grep -q "^OK: 0 Issues aus --ids" <<<"$out_r15" || fail "R15: OK 0 aus --ids erwartet"

# R16 Aufruf-Vertrag (fail-closed)
if SENTRY_RESOLVE_FIXTURE="$tmp/match" bash "$guard" --ids 100 --version v0.6.0-beta >/dev/null 2>&1; then
  fail "R16: --ids und --version zusammen muessen exit 1 sein"
fi
if SENTRY_RESOLVE_FIXTURE="$tmp/match" bash "$guard" --ids 100,abc >/dev/null 2>&1; then
  fail "R16: nicht-numerische ID muss exit 1 sein"
fi
if bash "$guard" >/dev/null 2>&1; then
  fail "R16: ohne Auswahl-Modus muss exit 1 sein"
fi

# R17 --ids + --dry-run (Fixture-status 403 wird nicht ausgewertet)
out_r17=$(SENTRY_RESOLVE_FIXTURE="$tmp/dryrun" bash "$guard" --ids 100 --dry-run) || \
  fail "R17: dry-run mit --ids darf nicht scheitern"
grep -q "^OK (dry-run): 1 Issue(s) würden als resolved markiert (ohne inNextRelease) — IDs: 100" <<<"$out_r17" || \
  fail "R17: dry-run-Verdict fuer --ids erwartet"
grep -q "project:write" <<<"$out_r17" && fail "R17: dry-run darf den Fixture-status 403 nicht auswerten"

# R18 Header-Haertung: der Token wird vor jeder Header-Nutzung von CR/LF
# befreit. Ohne das sieht nginx/Sentry eine leere Content-Type (HTTP 415,
# "Unsupported media type \"\"") bzw. Python meldet "Invalid header value"
# (Dispatch-Laeufe 41303884737 und 41304202788 — Secret mit Zeilenumbruch).
grep -qF '"$token" | tr -d' "$guard" || \
  fail "R18: Token-Sanitisierung (tr -d CR/LF) fehlt vor der Header-Nutzung"
dirty=$(printf 'sntrys_TEST\n')
if [ "$(printf '%s' "$dirty" | tr -d '\r\n')" != "sntrys_TEST" ]; then
  fail "R18: Sanitisierungsmuster entfernt den Zeilenumbruch nicht"
fi

# R19 --ids + --in-next-release: die Zusage muss am gesendeten JSON haengen,
# nicht nur am Meldungstext (#429/VIVID-5Q, Fix 1b74d05 erstmals in v0.5.51-beta).
body_r19="$tmp/body-r19.json"
out_r19=$(SENTRY_RESOLVE_FIXTURE="$tmp/match" SENTRY_RESOLVE_BODY_OUT="$body_r19" \
  bash "$guard" --ids 100 --in-next-release) || fail "R19: --in-next-release darf nicht scheitern"
grep -q "^OK: 1 Issue(s) als resolved (inNextRelease) markiert — IDs: 100" <<<"$out_r19" || \
  fail "R19: inNextRelease-Verdict fuer --ids erwartet"
grep -q '"inNextRelease": true' "$body_r19" || fail "R19: PUT-Body ohne statusDetails.inNextRelease"
grep -q '"status": "resolved"' "$body_r19" || fail "R19: PUT-Body ohne status=resolved"

# R20 Gegenprobe: ohne Flag bleibt es beim blossen resolved (der bisherige
# Vertrag fuer "kein Defekt"-Entscheide darf nicht mitkippen).
body_r20="$tmp/body-r20.json"
out_r20=$(SENTRY_RESOLVE_FIXTURE="$tmp/match" SENTRY_RESOLVE_BODY_OUT="$body_r20" \
  bash "$guard" --ids 100) || fail "R20: Gegenprobe darf nicht scheitern"
grep -q '"inNextRelease"' "$body_r20" && fail "R20: ohne Flag darf der Body kein inNextRelease tragen"
grep -q "^OK: 1 Issue(s) als resolved markiert (ohne inNextRelease) — IDs: 100" <<<"$out_r20" || \
  fail "R20: Verdict ohne inNextRelease erwartet"

# R21 --in-next-release ohne --ids ist ein Aufruffehler, kein stiller Fallback.
# Geprueft wird der EIGENE Fehlertext: der generische "Auswahl fehlt"-Zweig
# liefert ebenfalls exit 1 und wuerde das Flag sonst stillschweigend durchlassen
# (Mutationsprobe B lief mit reiner Exit-Code-Pruefung gruen durch).
if out_r21=$(bash "$guard" --in-next-release 2>&1); then
  fail "R21: --in-next-release ohne --ids muss exit 1 sein"
fi
grep -q "in-next-release braucht --ids" <<<"$out_r21" || \
  fail "R21: eigener Aufruffehl-Text erwartet (generischer 'Auswahl fehlt'-Fehler laesst das Flag durch)"

# R22 dry-run mit Flag: Verdict nennt inNextRelease, Fixture-status bleibt unbenutzt.
out_r22=$(SENTRY_RESOLVE_FIXTURE="$tmp/dryrun" bash "$guard" --ids 100 --in-next-release --dry-run) || \
  fail "R22: dry-run mit --in-next-release darf nicht scheitern"
grep -q "^OK (dry-run): 1 Issue(s) würden als resolved (inNextRelease) markiert — IDs: 100" <<<"$out_r22" || \
  fail "R22: dry-run-Verdict mit inNextRelease erwartet"
grep -q "project:write" <<<"$out_r22" && fail "R22: dry-run darf den Fixture-status 403 nicht auswerten"

# R23 --ids erreicht auch ein schon resolved Issue: die is:unresolved-Abfrage
# blendet es aus, deshalb laedt der Guard genannte IDs einzeln nach (#429/
# VIVID-5Q war bereits plain resolved und soll inNextRelease tragen).
mkdir -p "$tmp/upgrade"
echo 200 > "$tmp/upgrade/status"
echo '[]' > "$tmp/upgrade/issues.json"
cat > "$tmp/upgrade/issue-100.json" <<'JSON'
{"id": "100", "shortId": "VIVID-5Q", "title": "Schon geschlossen", "status": "resolved",
 "statusDetails": {"inNextRelease": false}, "tags": []}
JSON
body_r23="$tmp/body-r23.json"
out_r23=$(SENTRY_RESOLVE_FIXTURE="$tmp/upgrade" SENTRY_RESOLVE_BODY_OUT="$body_r23" \
  bash "$guard" --ids 100 --in-next-release) || fail "R23: Upgrade eines resolved Issues darf nicht scheitern"
grep -q "^OK: 1 Issue(s) als resolved (inNextRelease) markiert — IDs: 100" <<<"$out_r23" || \
  fail "R23: resolved Issue muss mit --in-next-release nachgezogen werden"
grep -q '"inNextRelease": true' "$body_r23" || fail "R23: PUT-Body ohne statusDetails.inNextRelease"
grep -q "nicht unter den unresolved Issues" <<<"$out_r23" && fail "R23: nachgeladene ID darf nicht als fehlend gemeldet werden"

# R24 Gegenprobe: schon inNextRelease -> nichts zu tun, kein PUT.
mkdir -p "$tmp/already"
echo 200 > "$tmp/already/status"
echo '[]' > "$tmp/already/issues.json"
cat > "$tmp/already/issue-100.json" <<'JSON'
{"id": "100", "shortId": "VIVID-5Q", "title": "Schon geschlossen", "status": "resolved",
 "statusDetails": {"inNextRelease": true}, "tags": []}
JSON
body_r24="$tmp/body-r24.json"
out_r24=$(SENTRY_RESOLVE_FIXTURE="$tmp/already" SENTRY_RESOLVE_BODY_OUT="$body_r24" \
  bash "$guard" --ids 100 --in-next-release) || fail "R24: bereits inNextRelease darf nicht scheitern"
grep -q "^OK: 0 Issues aus --ids" <<<"$out_r24" || fail "R24: kein PUT erwartet"
grep -q "HINWEIS: 1 ID(s) sind bereits resolved/inNextRelease" <<<"$out_r24" || \
  fail "R24: HINWEIS auf bereits vorhandenes inNextRelease erwartet"
[ -f "$body_r24" ] && fail "R24: es darf kein PUT-Body entstehen"

# R25 Plain-Vertrag: ein resolved Issue ohne Flag bleibt unangetastet.
out_r25=$(SENTRY_RESOLVE_FIXTURE="$tmp/upgrade" SENTRY_RESOLVE_BODY_OUT="$tmp/body-r25.json" \
  bash "$guard" --ids 100) || fail "R25: Gegenprobe darf nicht scheitern"
grep -q "^OK: 0 Issues aus --ids" <<<"$out_r25" || fail "R25: ohne Flag darf ein resolved Issue nicht resolvt werden"
[ -f "$tmp/body-r25.json" ] && fail "R25: ohne Flag darf kein PUT-Body entstehen"

# R26 Sentry fuehrt "resolved in next release" als eigenen Status
# ("resolvedInNextRelease", Dispatch 45986689320 zeigte das nach dem PUT).
# Der Guard muss ihn als erledigt lesen — sonst bleibt die ID fuer immer
# "offen" und jeder Dry-Run wuerde erneut resolven wollen. Die Fixture laesst
# statusDetails absichtlich leer: nur der Status selbst darf entscheiden.
mkdir -p "$tmp/nextrel"
echo 200 > "$tmp/nextrel/status"
echo '[]' > "$tmp/nextrel/issues.json"
cat > "$tmp/nextrel/issue-100.json" <<'JSON'
{"id": "100", "shortId": "VIVID-5Q", "title": "Schon geschlossen", "status": "resolvedInNextRelease",
 "statusDetails": {}, "tags": []}
JSON
out_r26=$(SENTRY_RESOLVE_FIXTURE="$tmp/nextrel" SENTRY_RESOLVE_BODY_OUT="$tmp/body-r26.json" \
  bash "$guard" --ids 100 --in-next-release) || fail "R26: resolvedInNextRelease darf nicht scheitern"
grep -q "^OK: 0 Issues aus --ids" <<<"$out_r26" || fail "R26: kein PUT fuer resolvedInNextRelease erwartet"
grep -q "HINWEIS: 1 ID(s) sind bereits resolved/inNextRelease" <<<"$out_r26" || \
  fail "R26: HINWEIS auf resolvedInNextRelease erwartet"
grep -q "[resolvedInNextRelease]" <<<"$out_r26" || \
  fail "R26: HINWEIS muss den beobachteten Sentry-Status nennen"
[ -f "$tmp/body-r26.json" ] && fail "R26: es darf kein PUT-Body entstehen"

echo "✅ [sentry-resolve-test] Sentry-Resolve-Guard vertragstreu (R1–R26)."