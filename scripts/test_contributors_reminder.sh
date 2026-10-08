#!/usr/bin/env bash
# Selbsttest fuer scripts/contributors_reminder.sh + den zugehoerigen Workflow
# (Offline, keine Netzwerk-Calls): beweist die Vertraege, die den Reminder
# sicher machen — State-Fenster/Marker, Fork-Erkennung inkl. geloeschter
# Forks, atomares State-Schreiben, Zeilenstatus-Parser (auch CRLF) und
# kein Bot-PR-Mechanik. Vertragsliste siehe CONTRIBUTING.md.
set -euo pipefail
cd "$(dirname "$0")/.."

script="scripts/contributors_reminder.sh"
workflow=".github/workflows/automation-contributors-reminder.yml"

PASS=0; FAIL=0
fail() { echo "❌ FAIL: $1"; FAIL=$((FAIL+1)); }
ok()   { PASS=$((PASS+1)); }

# ── C1: Script existiert und ist syntaktisch valide ──
if [[ -s "$script" ]] && bash -n "$script" 2>/dev/null; then ok; else fail "C1: $script fehlt, leer oder Syntaxfehler"; fi

# ── C2: Dedup-/State-Anker vorhanden ──
grep -Fq '<!-- contributors-reminder -->' "$script" && ok || fail "C2a: Issue-Marker fehlt (Dedup bricht)"
grep -Fq 'contributors-state:' "$script" && ok || fail "C2b: State-Token fehlt (Fenster bricht)"

# ── C3: State wird atomar im Issue-Body mitgeschrieben (ein API-Call) ──
grep -Fq 'body=@$BODY_FILE' "$script" && ok || fail "C3: Body muss per -F body=@-Datei atomar uebergehen"

# ── C4: Fork-Erkennung inkl. geloeschter Forks ──
grep -Fq 'head.repo == null' "$script" && ok || fail "C4a: geloeschte Forks (head.repo == null) werden nicht erkannt"
grep -Fq 'head.repo.fork == true' "$script" && ok || fail "C4b: aktive Forks werden nicht erkannt"

# ── C5: Keine Bot-PR-Mechanik (Vertrag bewusst nicht anwendbar) ──
if grep -Eq 'gh pr create|git push' "$script"; then fail "C5: Reminder darf keine Branches pushen / keine PRs erzeugen"; else ok; fi

# ── C6: ISO-Zeitstempel werden validiert, bevor sie ins --jq eingebettet werden ──
grep -Fq '^[0-9T:.Z-]+$' "$script" && ok || fail "C6: Zeitstempel-Validierung gegen Daten-in-Code fehlt"

# ── C7: Pipe-Zeichen in PR-Titeln werden escaped (Tabellen-Vertrag) ──
grep -Fq '${title//|/\\|}' "$script" && ok || fail "C7: Titel-Pipes werden nicht escaped"

# ── C8: Workflow — Default-Deny, least privilege, SHA-Pin, Trigger ──
[[ -s "$workflow" ]] || fail "C8a: Workflow fehlt"
# Top-Level-Default-Deny: `permissions: {}` muss auf Spalte 0 stehen und vor
# `jobs:` kommen. Bewusst ohne head-Fenster — ein Zeilenfenster bricht, sobald
# der Header-Kommentar waechst (Vorfall #258: genau das hat C8b rot gemacht,
# als die Begruendung fuer pull-requests: write eingefuegt wurde).
awk '/^jobs:/{exit} /^permissions:[[:space:]]*\{\}[[:space:]]*$/{f=1} END{exit !f}' "$workflow" \
  && ok || fail "C8b: Top-Level-Permissions muessen leer sein (permissions: {} vor jobs:)"
grep -Eq 'issues:[[:space:]]*write' "$workflow" && ok || fail "C8ca: Job-Permission issues: write fehlt"
# C8c (Nachtrag #258): `issues: write` allein reicht NICHT — der Dank-Kommentar
# geht an einen Pull Request, und GitHub akzeptiert dort issues: write ODER
# pull_requests: write (X-Accepted-GitHub-Permissions nennt beide). Mit nur
# issues: write starb der Lauf mit 403, nachdem das Reminder-Issue schon
# geschrieben war. Der alte Test fixierte diesen Fehler, indem er genau eine
# Berechtigung verlangte.
grep -Eq 'pull-requests:[[:space:]]*write' "$workflow" \
  && ok || fail "C8c: Job-Permission pull-requests: write fehlt (Dank-Kommentar an einen PR wird sonst mit 403 abgelehnt)"
grep -Eq 'actions/checkout@[0-9a-f]{40}' "$workflow" && ok || fail "C8d: Checkout muss auf 40-Zeichen-SHA gepinnt sein"
grep -Fq "github.actor != 'dependabot[bot]'" "$workflow" && ok || fail "C8e: Dependabot-Guard fehlt"
grep -Eq 'branches:[[:space:]]*$|branches: \[develop\]' "$workflow" && ok || fail "C8f: push-Trigger auf develop fehlt"
grep -Fq 'workflow_dispatch' "$workflow" && ok || fail "C8g: manueller Trigger fehlt"

# ── C10: --jq liefert Zeilen-Stream (kein Array-Literal) — Regression zum
# Smoke-Vorfall: "[[...] | .[]]" bleibt ein Array-Literal, gh druckt den
# Block als EINE JSON-Zeile und das IFS-Parsing erzeugt Müll-Befunde.
if grep -Fq -- '--jq ".[] | select(.merged_at' "$script"; then ok; else fail "C10a: Pull-Sammlung muss als Zeilen-Stream ge-jqt werden (.[] | …)"; fi
if grep -Fq '[[.[]' "$script"; then fail "C10b: Array-Klammer um den @tsv-Stream erzeugt eine Müll-JSON-Zeile"; else ok; fi

# ── C11: Öffentlicher Dank-/Ankündigungs-Kommentar am PR (Marker + Editierbarkeit) ──
grep -Fq 'contributors-credit' "$script" && ok || fail "C11a: Credit-Marker fehlt (Dank-Kommentar-Vertrag)"
grep -Fq 'credit_comment_for' "$script" && ok || fail "C11b: existierende Credit-Kommentare müssen vor dem Posten gesucht werden (Idempotenz)"
grep -Fq 'issues/comments/' "$script" && ok || fail "C11c: PATCH-Editierbarkeit fehlt (Ankündigung kann nicht zum Dank aktualisiert werden)"

# ── C12: Geschlossener Regelkreis — pending-Tracking im State-Kommentar ──
grep -Fq 'pending:' "$script" && ok || fail "C12a: pending-Liste fehlt im State-Token (Nachzieh-PRs werden sonst vergessen)"
grep -Fq 'diesen Kommentar automatisch zum Dank' "$script" \
  && ok || fail "C12b: Ankündigung muss das automatische Dank-Update versprechen (geschlossener Regelkreis)"

# ── C13: Autor-Erwähnung kommt aus der API (.user.login), nie als Code interpoliert ──
grep -Fq '.user.login' "$script" && ok || fail "C13a: Autor muss aus der Pulls-API kommen (.user.login)"
if grep -Eq 'user\\.login.*\|\|.*sh|\\$\(.*user\\.login' "$script"; then
  fail "C13b: user.login darf nicht in Shell-Kommandos interpoliert werden"
else
  ok
fi

# ── C14: Beide Modi mit @-Mention am Autor ──
grep -Fq '@$author' "$script" && ok || fail "C14: Dank/Ankündigung muss den Autor explizit erwähnen (@$author)"

# ── C9: status_for-Parser funktional testen (echter Code, Fixtures inkl. CRLF) ──
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT
fn=$(sed -n '/^status_for() {/,/^}/p' "$script")
[[ -n "$fn" ]] || fail "C9a: status_for kann nicht extrahiert werden"
printf '# Contributors\r\n\r\n| Person | Beitrag | Referenz | Status |\r\n|---|---|---|---|\r\n| smka | Sprachauswahl | #213 | offen |\r\n| smka | Kamera-Vorschau | #230 | umgesetzt |\r\n' > "$tmp/CONTRIBUTORS.md"
eval "$fn"
CONTRIBUTORS_FILE="$tmp/CONTRIBUTORS.md"
[[ "$(status_for 999)" == "fehlt" ]]            && ok || fail "C9b: fehlender Eintrag muss 'fehlt' melden (bekam: $(status_for 999))"
[[ "$(status_for 213)" == "offen" ]]            && ok || fail "C9c: 'offen' nach Merge muss erkannt werden (bekam: $(status_for 213))"
[[ "$(status_for 230)" == "umgesetzt" ]]      && ok || fail "C9d: gepflegter Eintrag (CRLF) muss 'umgesetzt' lesen (bekam: $(status_for 230))"
printf '| X | Y | #301 | offen |\n| X | Y | #301 | umgesetzt |\n' > "$tmp/dup.md"
CONTRIBUTORS_FILE="$tmp/dup.md"
[[ "$(status_for 301)" == "umgesetzt" ]]      && ok || fail "C9e: bei mehreren Zeilen muss die letzte gewinnen (bekam: $(status_for 301))"
printf '| X | Y | #401 | offen |\n' > "$tmp/bad.md"
CONTRIBUTORS_FILE="$tmp/bad.md"
[[ "$(status_for 401)" == "offen" ]]            && ok || fail "C9f: LF-Datei offen-Zeile (bekam: $(status_for 401))"

# ── C15: latest_state_for liest BEIDE State-Quellen (Body + Kommentare) ──
# Vorfall #256/#258: Der Marker wird beim Anlegen einer Reminder-Issue in den
# Body geschrieben, gelesen wurde aber ausschliesslich aus den Kommentaren.
# Dadurch startete der naechste Lauf mit leerer pending-Liste — der Loop wurde
# blind, ohne dass ein Lauf rot wurde. Der Test postet einen Marker, der NUR im
# Body liegt, und verlangt, dass er trotzdem gefunden wird.
sfn=$(sed -n '/^latest_state_for() {/,/^}/p' "$script")
[[ -n "$sfn" ]] || fail "C15a: latest_state_for kann nicht extrahiert werden"
# Bindungen, die die Funktion aus dem Laufzeitkontext des Skripts erwartet.
REPO="${REPO:-thoser666/Vivid}"
eval "$sfn"

STATE_TOKEN='contributors-state:'
MARKER='<!-- contributors-reminder -->'
api() { # Fixture-API: $1 ist der Pfad (Issue bzw. Kommentare), $2 das --jq-Flag
  case "$1" in
    *"/issues/999/comments"*)  printf '%s' "$COMMENTS_FIX" ;;
    *"/issues/999")           printf '%s' "$BODY_FIX" ;;
    *) return 0 ;;
  esac
}

BODY_FIX="## Reminder

$MARKER

<!-- contributors-state: 2026-10-02T10:28:02Z pending:253 -->"
COMMENTS_FIX=""
[[ "$(latest_state_for 999)" == *"pending:253"* ]] \
  && ok || fail "C15b: State-Marker NUR im Issue-Body wird nicht gelesen — der Loop verliert nach jeder neu eroeffneten Reminder-Issue den pending-Stand (bekam: '$(latest_state_for 999)')"

# Kommentar-Stand hat Vorrang vor dem Body (er ist chronologisch juenger; der
# Body wird nie nachgefuehrt).
COMMENTS_FIX='[{"body":"<!-- contributors-state: 2026-10-03T09:00:00Z pending:301 -->"}]'
out=$(latest_state_for 999)
if [[ "$out" == *"pending:301"* && "$out" != *"pending:253"* ]]; then ok
else fail "C15c: Kommentar-Stand muss den Body-Stand ueberstimmen (bekam: '$out')"; fi

# Ohne Marker in beiden Quellen: leer, nicht der rohe Body.
BODY_FIX="nur Fliesstext"; COMMENTS_FIX=""
[[ -z "$(latest_state_for 999)" ]] && ok || fail "C15d: ohne Marker muss latest_state_for leer liefern (bekam: '$(latest_state_for 999)')"

# Body vorhanden, Kommentar-API liefert nichts (u. a. 403/Netzfehler) → der Body
# muss als Rueckfall greifen, sonst waere der State verloren.
BODY_FIX="<!-- contributors-state: 2026-10-02T10:28:02Z pending:253 -->"
COMMENTS_FIX=""
[[ "$(latest_state_for 999)" == *"pending:253"* ]] \
  && ok || fail "C15e: Body muss greifen, wenn die Kommentar-Quelle leer ist"

# Eine Antwort, die NICHT leer ist, aber den Marker nicht enthaelt (ungefilterter
# API-Rumpf, andere jq-Version), darf den Body-State nicht verdecken — sonst waere
# der Loop wieder blind. Gefunden im End-to-End-Lauf, als der Mock fuer die
# Kommentar-Quelle "[]" zurueckgab.
# Reihenfolge wichtig: dieser Fall braucht noch die Fixture-api() von oben,
# C15f redefiniert sie weiter unten dauerhaft.
BODY_FIX="<!-- contributors-state: 2026-10-02T10:28:02Z pending:253 -->"
COMMENTS_FIX='[]'
[[ "$(latest_state_for 999)" == *"pending:253"* ]] \
  && ok || fail "C15g: eine Marker-fremde Antwort darf den Body-State nicht ueberstimmen (bekam: '$(latest_state_for 999)')"

# Ein fehlgeschlagener Kommentar-Read darf den Body-State nicht verschlucken.
api() { case "$1" in *"/issues/999/comments"*) return 1 ;; *) printf '%s' "$BODY_FIX" ;; esac; }
[[ "$(latest_state_for 999)" == *"pending:253"* ]] \
  && ok || fail "C15f: fehlgeschlagener Kommentar-Read darf den Body-State nicht verschlucken"

# ── C16: Beim Anlegen einer neuen Reminder-Issue wird der State zusaetzlich als
# Kommentar gespiegelt — sonst steht er dauerhaft nur im Body, der nie
# nachgefuehrt wird (die Asymmetrie aus #256).
body_block=$(sed -n '/ACTION="Reminder-Issue #\$REMINDER_NUM erstellt"/,/^  fi$/p' "$script")
if grep -Fq 'api -X POST "repos/$REPO/issues/$REMINDER_NUM/comments"' <<< "$body_block"; then ok
else fail "C16: State muss beim Erstellen der Reminder-Issue zusaetzlich als Kommentar gespiegelt werden (Body/Comment-Asymmetrie)"; fi

# ── C17/C18: End-to-End gegen die echte #256-Situation (gemocktes gh) ──
# Die Einzelpruefungen C15/C16 halten die Vertragsstellen fest, aber nicht den
# Zusammenspiel-Fehler, den der erste End-to-End-Lauf fand: der pending-Pfad
# holt den PR per @tsv (TAB-getrennt) und reicht ihn an process_candidate, das
# '|'-getrennt erwartet — die GANZE Zeile landete in $num und damit eine
# unlesbare PR-Nummer im naechsten State-Marker. Der Fehler war maskiert,
# weil Defekt B die pending-Liste immer leer hielt, dieser Pfad also toter
# Code war; erst nach dem Fix von B wurde er ausgefuehrt.
#
# Der Mock ist absichtlich bash (keine Python-Abhaengigkeit im Selbsttest) und
# gibt das bereits per --jq gefilterte Ergebnis zurueck, so wie das echte gh.
cat > "$tmp/gh" <<'MOCK'
#!/usr/bin/env bash
# Minimaler gh-Mock fuer den Contributors-Reminder-End-to-End-Lauf.
# Faellt #256 nach: Reminder-Issue mit State-Marker NUR im Body (pending:253),
# PR #253 als Fork-Merge 14 s VOR dem Fensterbeginn, kein Credit-Kommentar.
LOG="${MOCK_LOG:?}"
arg=(); seen=0; skip=0
for x in "$@"; do
  if [[ $seen -eq 0 ]]; then [[ "$x" == "api" ]] && seen=1; continue; fi
  if [[ $skip -eq 1 ]]; then skip=0; continue; fi
  if [[ "$x" == "-X" ]]; then skip=1; continue; fi
  [[ "$x" == -* ]] && continue
  arg=("$x"); break
done
path="${arg[0]:-}"
all="$*"
bodyfile=""
for x in "$@"; do [[ "$x" == body=@* ]] && bodyfile="${x#body=@}"; done
log() { printf '%s\n' "$1" >> "$LOG"; }
case "$path" in
  */issues?state=all*|*/issues?state=open*)
    # jq: jüngste Issue mit Marker -> .number
    printf '256\n' ;;
  */issues/256)
    if [[ "$all" == *".created_at"* ]]; then printf '2026-10-02T10:28:02Z\n'
    else log "READ-BODY-256"
      printf '## Reminder\n\n<!-- contributors-reminder -->\n\n<!-- contributors-state: 2026-10-02T10:28:02Z pending:253 -->\n'
    fi ;;
  */issues/256/comments?*)
    log "READ-COMMENTS-256" ;;   # jq liefert hier NICHTS (kein State-Kommentar)
  */pulls?state=closed*)
    if [[ "$all" == *"last | .updated_at"* ]]; then printf '2026-10-02T12:00:00Z\n'; fi ;;
  */pulls/253)
    printf '253\t2026-10-02T10:27:48Z\thttps://github.com/thoser666/Vivid/pull/253\t51faba7bdeadbeef\tfeat\tsmka\n' ;;
  */issues/253/comments)
    if [[ "$all" == *"POST"* ]]; then log "POST-THANKS-253"
    else log "CREDIT-LOOKUP-253"; fi ;;   # jq: kein Kommentar gefunden -> nichts
  */issues/*/comments)
    log "POST-COMMENT"; [[ -n "$bodyfile" && -f "$bodyfile" ]] && cat "$bodyfile" >> "$LOG" ;;
  *) : ;;
esac
exit 0
MOCK
chmod +x "$tmp/gh"
# Sicherheitsnetz: Falls der Mock ausfaellt, darf der Lauf NICHT die echte API
# treffen. Leeres HOME + leere Token = gh ist unauthentifiziert; Lese-Calls auf
# ein oeffentliches Repo funktionieren dann weiterhin, schreibende Calls
# scheitern mit 401, statt im echten Repo zu landen.
mkdir -p "$tmp/home"
run_e2e() { # $1 = Logdatei, $2 = optionale Skriptkopie
  rm -f "$1"
  PATH="$tmp:$PATH" MOCK_LOG="$1" GITHUB_REPOSITORY=thoser666/Vivid \
    CONTRIBUTORS_FILE="$tmp/CONTRIBUTORS.md" \
    HOME="$tmp/home" GH_TOKEN="" GITHUB_TOKEN="" \
    bash "${2:-$script}" >/dev/null 2>&1
}
e2e_log="$tmp/e2e.log"
run_e2e "$e2e_log"
if [[ -f "$e2e_log" ]]; then
  if grep -q "READ-BODY-256" "$e2e_log"; then ok
  else fail "C17a: der Body der Reminder-Issue wurde nicht als State-Quelle gelesen"; fi
  if grep -q "POST-THANKS-253" "$e2e_log"; then ok
  else fail "C17b: der Dank-Kommentar an PR #253 wurde nicht abgesetzt — der Regelkreis bleibt offen"; fi
  # Der neue State-Marker muss eine saubere pending-Liste fuehren: hoechstens
  # Ziffern und Kommata bis zum Leerzeichen. `[^ ]*` greift bewusst ueber den
  # Tab hinweg — ein auf 'pending:[0-9,]*' verkuerztes Muster wuerde die
  # unlesbare TSV-Zeule als 'pending:253' ausgeben und den Defekt durchwinken.
  # (grep in einer Zuweisung braucht || true: unter `set -e` killt ein
  # Treffer-miss RC 1 das ganze Skript, bevor die Zusammenfassung laeuft.)
  state_raw=$(grep -o 'pending:[^ ]*' "$e2e_log" 2>/dev/null | tail -1 || true)
  if [[ "$state_raw" =~ ^pending:[0-9,]*$ ]]; then ok
  else fail "C18: der fortgeschriebene State traegt eine unlesbare pending-Liste (erwartet 'pending:[0-9,]*', bekam: '${state_raw:-<keiner>}')"; fi
else
  fail "C17c: End-to-End-Lauf hat den Mock gar nicht erreicht"
fi

# C18 mutationsgeprueft: nimmt man das TSV->Pipe-Umstellen zurueck, muss der
# End-to-End-Lauf die unlesbare pending-Liste melden (der Mock laeuft mit
# einer Kopie des Skripts, das Original bleibt unberuehrt).
cp "$script" "$tmp/rem_mut.sh"
perl -pi -e 's/^(\s*)process_candidate "\$\(printf .*"\$row".*$/${1}process_candidate "\$row"/' "$tmp/rem_mut.sh"
if grep -qF 'process_candidate "$row"' "$tmp/rem_mut.sh"; then
  mut_log="$tmp/e2e_mut.log"; rm -f "$mut_log"
  PATH="$tmp:$PATH" MOCK_LOG="$mut_log" GITHUB_REPOSITORY=thoser666/Vivid \
    CONTRIBUTORS_FILE="$tmp/CONTRIBUTORS.md" \
    HOME="$tmp/home" GH_TOKEN="" GITHUB_TOKEN="" \
    bash "$tmp/rem_mut.sh" >"$tmp/mutation-output.log" 2>&1 || true
  mut_state=$(grep -o 'pending:[^ >]*' "$mut_log" 2>/dev/null | tail -1 || true)
  # Ohne das TSV->Pipe-Umstellen landet die GANZE Zeile in $num; der neue
  # State-Marker traegt dann eine pending-Liste mit Tab und Zeitstempel.
  if [[ -z "$mut_state" ]]; then
    cat "$tmp/mutation-output.log"
    fail "C18: die Mutation hat keinen State-Marker erzeugt — fehlende Ausgabe ist kein Defekt-Nachweis"
  elif [[ "$mut_state" =~ ^pending:[0-9,]*$ ]]; then
    fail "C18: die Mutation ist nicht wirksam — der Test kann Defekt C nicht erkennen"
  else ok; fi
else
  fail "C18: Mutation konnte nicht angewendet werden (Zeile nicht gefunden)"
fi

# Negativkontrolle: eine reine Kommentar-Aenderung darf den E2E-Vertrag
# nicht veraendern; auch die Skriptkopie muss die Fixture wirklich erreichen.
cp "$script" "$tmp/rem_comment.sh"
printf '\n# Kommentar-Negativkontrolle\n' >> "$tmp/rem_comment.sh"
comment_log="$tmp/e2e_comment.log"
run_e2e "$comment_log" "$tmp/rem_comment.sh"
comment_state=$(grep -o 'pending:[^ ]*' "$comment_log" 2>/dev/null | tail -1 || true)
if [[ -n "$comment_state" && "$comment_state" =~ ^pending:[0-9,]*$ ]] &&
    grep -q 'READ-BODY-256' "$comment_log" && grep -q 'POST-THANKS-253' "$comment_log"; then ok
else fail "C18: Kommentar-Negativkontrolle muss einen sauberen State und den Credit-Call erzeugen"; fi

echo "✅ [contributors-reminder-test] $PASS Pass, $FAIL Fail"
[[ "$FAIL" -eq 0 ]]
