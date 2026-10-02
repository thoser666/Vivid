#!/usr/bin/env bash
# CONTRIBUTORS.md-Erinnerung + öffentlicher Dank: Fork-PR-Merges auf develop
# finden, die Pflege erinnern (Auto-Issue) und am gemergten PR oeffentlich
# danken (Vorfall 29.09.2026: #213 stand nach dem Merge weiter als `offen` in
# CONTRIBUTORS.md, #230 fehlte ganz — beides fiel erst durch eine
# Nutzer-Rueckfrage auf).
#
# Vertrag (siehe CONTRIBUTING.md, Abschnitt "Danksagung Dritter"):
#   - Trigger: jeder Push auf develop (+ workflow_dispatch) — ein Fork-Squash-
#     Merge erzeugt einen develop-Push, der Check laeuft also automatisch.
#   - State-Fenster: "seit dem letzten State-Marker". Er traegt
#     `<!-- contributors-state: <ISO> pending:<n1,n2,...> -->`: ISO =
#     Fensteruntergrenze, pending = PRs mit Nachzieh-Ankuendigung, deren Dank
#     auf den gepflegten Eintrag wartet. Der Marker landet an zwei Orten: im
#     Body (beim Anlegen einer neuen Issue, atomar im selben API-Call) und in
#     einem Kommentar (beim Nachziehen, Schritt 7). BEIDE werden gelesen
#     (latest_state_for), Kommentar-Stand hat Vorrang — vor #258 wurde nur
#     der Kommentar gelesen, wodurch der State einer frisch eroeffneten
#     Reminder-Issue verloren ging und der Loop dauerhaft blind wurde.
#   - Dank-Kommentar am gemergten PR (Marker `<!-- contributors-credit -->`,
#     idempotent): Eintrag `umgesetzt` → Danke + Verweis auf CONTRIBUTORS.md;
#     Eintrag fehlt/noch `offen` → Nachzieh-Ankuendigung (mit Verweis auf die
#     Reminder-Issue). Wird der Eintrag spaeter gepflegt, EDITIERT der naechste
#     Run die Ankuendigung zum Danke (PATCH auf den Kommentar) — der
#     Contributor sieht am PR, dass die Wuerdigung nachgezogen wurde.
#   - Dedup: existiert eine OFFENE Reminder-Issue, wird kommentiert, sonst
#     eine neue eroeffnet; geschlossene Issues liefern nur noch den State.
#   - Fork-Erkennung ueber die Pulls-API: `head.repo == null` (Fork nach dem
#     Merge geloescht) oder `head.repo.fork == true`. Same-repo-Branches
#     (Maintainer, Dependabot) sind damit ausgeschlossen.
#   - Bewusst KEIN Bot-PR (kein Branch-Push, kein PR-Create): der T1-T13-
#     Vertrag aus test_bot_pr_credentials.sh gilt hier nicht — `issues:
#     write` genuegt; GITHUB_TOKEN fliesst nur in Issue-/PR-Text (Daten,
#     kein Code).
#   - Die Pflege selbst bleibt Maintainer-Handarbeit; die Automatisierung
#     erinnert, dankt und aktualisiert. Fail-soft: nichts im Fenster und kein
#     pending = gruener No-Op.
#   - Nutzt nur `gh api` (mit gh-embedded gojq via --jq) — keine System-jq-
#     Abhaengigkeit, keine gh-Flags jenseits der Standard-API-Flags.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="${GITHUB_REPOSITORY:-thoser666/Vivid}"
MARKER='<!-- contributors-reminder -->'
STATE_TOKEN='contributors-state:'
CREDIT_MARKER='<!-- contributors-credit -->'
EPOCH='1970-01-01T00:00:00Z'
CONTRIBUTORS_URL="https://github.com/$REPO/blob/develop/CONTRIBUTORS.md"
CONTRIBUTORS_FILE="${CONTRIBUTORS_FILE:-CONTRIBUTORS.md}"
PER_PAGE=100
MAX_PAGES=10   # Sicherheitsnetz gegen Endlos-Blaettern (Repo-Skala: ~20 PRs/Tag)

command -v gh >/dev/null 2>&1 || { echo "::error::gh CLI nicht gefunden"; exit 1; }
[[ -s "$CONTRIBUTORS_FILE" ]] || { echo "::error::$CONTRIBUTORS_FILE fehlt oder ist leer"; exit 1; }

api() { gh api "$@"; }

# ── 1. Reminder-Issues + State (ISO-Fenster + pending-Liste) ──
# Jüngste Issue mit Marker = State-Quelle (auch geschlossen); jüngste OFFENE
# mit Marker = Kommentarziel.
LATEST_ANY=$(api "repos/$REPO/issues?state=all&sort=created&direction=desc&per_page=50" \
  --jq "[.[] | select(.pull_request == null and .body != null and (.body | contains(\"$MARKER\")))][0] // empty | .number" 2>/dev/null || true)
LATEST_OPEN=$(api "repos/$REPO/issues?state=open&sort=created&direction=desc&per_page=50" \
  --jq "[.[] | select(.pull_request == null and .body != null and (.body | contains(\"$MARKER\")))][0] // empty | .number" 2>/dev/null || true)

# Letzten State-Marker einer Reminder-Issue lesen. Quelle sind BEIDE Orte, an
# denen der Marker landet: der Issue-Body (Schritt 5 schreibt ihn beim Anlegen
# einer neuen Issue) und die Kommentare (Schritt 7 schreibt ihn per Kommentar).
# Vorfall #256: nur die Kommentare zu lesen hiess, dass der State einer frisch
# eroeffneten Reminder-Issue nie wieder gelesen wurde — der naechste Lauf
# startete mit leerer pending-Liste und der Loop wurde blind (der Merge lag
# 14 s vor dem zurueckfallenden Fensterbeginn). Kommentar-Stand hat Vorrang:
# er ist chronologisch juenger als der Body, der nie nachgefuehrt wird.
latest_state_for() { # $1 = Issue-Nummer -> roher Marker-Text (leer = keiner)
  local num="$1" from_body=""
  from_body=$(api "repos/$REPO/issues/$num" --jq '.body // empty' 2>/dev/null || true)
  local from_comments
  from_comments=$(api "repos/$REPO/issues/$num/comments?per_page=100" \
    --jq '([.[] | select(.body != null and (.body | contains("'"$STATE_TOKEN"'"))) ] | last | .body) // empty' 2>/dev/null || true)
  # Nur eine Quelle, die den Marker WIRKLICH enthaelt, gewinnt — nicht bloss
  # eine leere API-Antwort. Sonst wuerde z. B. ein ungefilterter Antwort-Rumpf
  # den Body-State verdecken und der Loop waere wieder blind.
  if [[ "$from_comments" == *"$STATE_TOKEN"* ]]; then printf '%s' "$from_comments"
  elif [[ "$from_body" == *"$STATE_TOKEN"* ]]; then printf '%s' "$from_body"
  fi
}

SINCE="$EPOCH"
PENDING_OLD=()
if [[ -n "$LATEST_ANY" ]]; then
  CREATED=$(api "repos/$REPO/issues/$LATEST_ANY" --jq .created_at)
  LAST_STATE_RAW=$(latest_state_for "$LATEST_ANY")
  CAND=$(printf '%s' "$LAST_STATE_RAW" | sed -n "s/.*$STATE_TOKEN \([0-9T:.Z-]*\).*/\1/p" | tail -1)
  # Nur validierte ISO-Zeitstempel werden eingebettet (kein Daten-in-Code-Vektor).
  [[ "$CAND" =~ ^[0-9T:.Z-]+$ ]] || CAND="$CREATED"
  if [[ "$CAND" > "$SINCE" ]]; then SINCE="$CAND"; fi
  # pending-Liste aus dem eigenen State-Marker (nur Ziffern/Kommata).
  PLINE=$(printf '%s' "$LAST_STATE_RAW" | sed -n "s/.*pending:\([0-9,]*\).*/\1/p" | tail -1)
  if [[ "$PLINE" =~ ^[0-9,]*$ ]]; then
    IFS=',' read -r -a PENDING_OLD <<< "$PLINE"
  fi
fi

# Einmaliger Backfill (workflow_dispatch mit Env BACKFILL_SINCE): besucht auch
# Merge-Fenster VOR dem State-Fenster, damit historische Fork-PRs ihre
# Dank-/Ankündigungs-Kommentare erhalten (z. B. #213/#230, Vorfall 29.09.).
# Nur validierte ISO-Zeitstempel; der reguläre State wird nicht ueberschrieben
# (State-Kommentar entsteht hier nur, wenn sich pending aendert).
if [[ -n "${BACKFILL_SINCE:-}" && "${BACKFILL_SINCE}" =~ ^[0-9T:.Z-]+$ ]]; then
  echo "::notice::Backfill-Modus: Fensteruntergrenze $BACKFILL_SINCE (statt $SINCE)"
  SINCE="$BACKFILL_SINCE"
fi

# ── 2. Fork-PR-Merges im Fenster einsammeln (sort=updated, Seitenschleife) ──
# Zeilen: nummer|merged_at|url|sha|title|author
MERGED_ROWS=()
PAGE=1
while :; do
  RESP=$(api "repos/$REPO/pulls?state=closed&sort=updated&direction=desc&per_page=$PER_PAGE&page=$PAGE" \
    --jq ".[] | select(.merged_at != null and .merged_at > \"$SINCE\" and ((.head.repo == null) or (.head.repo.fork == true))) | [.number, .merged_at, .html_url, (.merge_commit_sha // \"-\"), .title, (.user.login // \"-\")] | @tsv" 2>/dev/null || true)
  COUNT=$(printf '%s' "$RESP" | grep -c . || true)
  if [[ "$COUNT" -gt 0 ]]; then
    while IFS=$'\t' read -r num mat url sha title author; do
      [[ -z "$num" ]] && continue
      MERGED_ROWS+=("$num|$mat|$url|$sha|$title|$author")
    done <<< "$RESP"
  fi
  # Abbruch: aeltester Eintrag der Seite liegt vor dem State-Fenster
  # (sort=updated ⇒ alle weiteren Seiten sind noch aelter) oder die Seite
  # ist nicht voll (Ende der Liste) oder Seitenlimit erreicht.
  OLDEST_UPDATED=$(api "repos/$REPO/pulls?state=closed&sort=updated&direction=desc&per_page=$PER_PAGE&page=$PAGE" \
    --jq 'last | .updated_at // empty' 2>/dev/null || true)
  [[ -z "$OLDEST_UPDATED" ]] && break
  [[ "$OLDEST_UPDATED" < "$SINCE" ]] && break
  [[ "$COUNT" -lt "$PER_PAGE" ]] && break
  [[ "$PAGE" -ge "$MAX_PAGES" ]] && break
  PAGE=$((PAGE + 1))
done

# ── 3. Pflege-Status je Fork-Merge ermitteln ──
#  fehlt    : keine Zeile mit `|#<Nummer> |` in CONTRIBUTORS.md
#  offen    : Zeile vorhanden, Status aber noch `offen` (Merge schon durch)
#  umgesetzt: Zeile vorhanden und gepflegt (Zielzustand)
#  unlesbar : Zeile vorhanden, aber letzte Zelle nicht auswertbar → melden
status_for() { # $1 = PR-Nummer
  local row status
  row=$(grep -E "^[[:space:]]*[|].*[|] *#$1 *[|]" "$CONTRIBUTORS_FILE" | tail -1 || true)
  if [[ -z "$row" ]]; then
    echo "fehlt"
    return
  fi
  status=$(printf '%s' "$row" | tr -d '\r' | awk -F'|' \
    '{for (i = NF; i >= 1; i--) if ($i ~ /[^ \t]/) {gsub(/^[ \t]+|[ \t]+$/, "", $i); print $i; exit}}')
  [[ -z "$status" ]] && status="unlesbar"
  echo "$status"
}

# Existierenden Dank-Kommentar am PR finden (Marker) → "id|owner" oder leer.
credit_comment_for() { # $1 = PR-Nummer
  api "repos/$REPO/issues/$1/comments?per_page=100" \
    --jq "[.[] | select(.body != null and (.body | contains(\"$CREDIT_MARKER\")))][0] | [.id] | @tsv" 2>/dev/null || true
}

# ── 4. Kandidaten planen (Fenster ∪ pending) — Status/IDs vor allen Writes ──
CREDIT_TASKS=()  # "modus|nummer|author|merge_sha|merged_at|title|comment_id" (modus: thanks|promise)
PENDING_NEW=()
REPORT_ROWS=()
SUMMARY_ROWS=()

process_candidate() { # num|merged_at|url|sha|title|author
  local num mat url sha title author status cid
  IFS='|' read -r num mat url sha title author <<< "$1"
  status=$(status_for "$num")
  esc_title=${title//|/\\|}   # Pipe-Zeichen escapen, sonst bricht die Tabelle
  short_sha=${sha:0:7}
  SUMMARY_ROWS+=("| #$num | [PR öffnen]($url) | $mat | $status |")
  if [[ "$status" != "umgesetzt" ]]; then
    if [[ "$status" == "fehlt" ]]; then
      label="**fehlt** in CONTRIBUTORS.md"
    elif [[ "$status" == "offen" ]]; then
      label="**noch \`offen\`, aber bereits gemergt**"
    else
      label="**Statuszelle unlesbar** (\`$status\`)"
    fi
    REPORT_ROWS+=("- [ ] **#$num** — [$esc_title]($url) (Squash-Merge \`$short_sha\`, $mat) — Status: $label")
  fi
  cid=$(credit_comment_for "$num" | cut -f1)
  if [[ -n "$cid" ]]; then
    if [[ "$status" == "umgesetzt" ]]; then
      CREDIT_TASKS+=("thanks|$num|$author|$sha|$mat|$title|$cid")   # Ankuendigung → Danke (PATCH)
    else
      PENDING_NEW+=("$num")                                        # wartet weiter
    fi
  else
    if [[ "$status" == "umgesetzt" ]]; then
      CREDIT_TASKS+=("thanks|$num|$author|$sha|$mat|$title|-")     # direkt danken
    else
      CREDIT_TASKS+=("promise|$num|$author|$sha|$mat|$title|-")    # Ankuendigung
      PENDING_NEW+=("$num")
    fi
  fi
}

for entry in "${MERGED_ROWS[@]}"; do process_candidate "$entry"; done
for p in "${PENDING_OLD[@]}"; do
  [[ -z "$p" ]] && continue
  in_window=0
  for entry in "${MERGED_ROWS[@]}"; do
    [[ "$(printf '%s' "$entry" | cut -d'|' -f1)" == "$p" ]] && { in_window=1; break; }
  done
  if [[ "$in_window" == "0" ]]; then
    row=$(api "repos/$REPO/pulls/$p" --jq '[.number, (.merged_at // "-"), .html_url, (.merge_commit_sha // "-"), .title, (.user.login // "-")] | @tsv' 2>/dev/null || true)
    [[ -z "$row" ]] && continue   # PR gelöscht/gekappt → pending-Eintrag verfällt
    # @tsv liefert TAB-getrennt, process_candidate erwartet '|'-getrennt (so
    # baut der Merge-Sammler seine Eintraege). Ohne das Umstellen landet die
    # GANZE Zeile in $num — und damit eine unlesbare PR-Nummer in der
    # pending-Liste des naechsten State-Markers (Vorfall #258, im End-to-End-
    # Smoke gefunden: der pending-Pfad war vorher toter Code, weil Defekt B die
    # Liste immer leer hielt — der Fehler war maskiert).
    process_candidate "$(printf '%s' "$row" | tr '\t' '|')"
  fi
done

# ── 5. Reminder melden (Kommentar an offene Issue, sonst neue Issue) ──
NOW=$(date -u +%Y-%m-%dT%H:%M:%SZ)
PENDING_TXT=$(IFS=,; echo "${PENDING_NEW[*]:-}")
REMINDER_NUM="$LATEST_OPEN"
BODY_FILE=$(mktemp)
trap 'rm -f "$BODY_FILE"' EXIT

if [[ "${#REPORT_ROWS[@]}" -gt 0 ]]; then
  {
    echo "$MARKER"
    echo ""
    echo "## 🧾 Fork-PR-Merges ohne sauberen Beitragseintrag"
    echo ""
    echo "Fenster: Merge(s) seit **$SINCE** — erkannt durch \`.github/workflows/automation-contributors-reminder.yml\`."
    echo ""
    for row in "${REPORT_ROWS[@]}"; do echo "$row"; done
    echo ""
    echo "**Handlung (Maintainer, nach [CONTRIBUTING.md](CONTRIBUTING.md) „Danksagung Dritter“):**"
    echo ""
    echo "1. Zeile in \`CONTRIBUTORS.md\` ergänzen bzw. \`offen\` → \`umgesetzt\` flippen (Spalten: \`Person | Beitrag | Referenz | Status\`)."
    echo "2. Guard lokal prüfen: \`bash scripts/check_contributors.sh\`."
    echo "3. Commit nach Hausmuster (CONTRIBUTORS.md → PARITY-Append → Dashboard → Gate → Push), Issue danach schließen."
    echo ""
    echo "Die Pflege bleibt Maintainer-Handarbeit — dieser Reminder prüft nur das Nachziehen. Contributor sehen am PR eine Ankuendigung, die beim Nachziehen automatisch zum Dank aktualisiert wird."
    echo ""
    echo "<!-- $STATE_TOKEN $NOW pending:$PENDING_TXT -->"
  } > "$BODY_FILE"

  if [[ -n "$REMINDER_NUM" ]]; then
    api -X POST "repos/$REPO/issues/$REMINDER_NUM/comments" -F "body=@$BODY_FILE" >/dev/null
    ACTION="Kommentar an Reminder-Issue #$REMINDER_NUM"
  else
    TITLE="🧾 CONTRIBUTORS.md-Pflege: Fork-PR-Merges ohne Beitragseintrag"
    REMINDER_NUM=$(api "repos/$REPO/issues" -f title="$TITLE" -F "body=@$BODY_FILE" --jq .number)
    ACTION="Reminder-Issue #$REMINDER_NUM erstellt"
    # State zusaetzlich als Kommentar spiegeln: der Body einer neuen Issue wird
    # nie nachgefuehrt, waehrend Kommentare die juengere Quelle sind. Ohne den
    # Spiegel bleibt der State beim Body stehen — genau die Asymmetrie aus
    # #256/#258. latest_state_for liest beides, der Kommentar-Stand hat Vorrang.
    {
      echo "$MARKER"
      echo ""
      echo "<!-- $STATE_TOKEN $NOW pending:$PENDING_TXT -->"
    } > "$BODY_FILE"
    api -X POST "repos/$REPO/issues/$REMINDER_NUM/comments" -F "body=@$BODY_FILE" >/dev/null
  fi
fi

# ── 6. Dank-/Ankuendigungs-Kommentare am PR ausführen ──
write_credit_body() { # $1 = modus, Rest wie CREDIT_TASKS (ohne modus/comment_id)
  local modus="$1" num="$2" author="$3" sha="$4" mat="$5" title="$6"
  {
    echo "$CREDIT_MARKER"
    echo ""
    if [[ "$modus" == "thanks" ]]; then
      echo "🎉 Vielen Dank für deinen Beitrag, @$author!"
      echo ""
      echo "Dein PR wurde gemergt (Squash-Merge \`${sha:0:7}\`) und ist jetzt in [CONTRIBUTORS.md]($CONTRIBUTORS_URL) gewürdigt (Status \`umgesetzt\`)."
    else
      echo "🎉 Danke für deinen Beitrag, @$author!"
      echo ""
      echo "Dein PR wurde in develop gemergt (Squash-Merge \`${sha:0:7}\`). Die öffentliche Würdigung in [CONTRIBUTORS.md]($CONTRIBUTORS_URL) wird gerade nachgezogen${REMINDER_NUM:+ (Nachverfolgung: #$REMINDER_NUM)}."
      echo ""
      echo "Sobald der Eintrag \`umgesetzt\` ist, aktualisiert der [Contributors-Reminder](https://github.com/$REPO/blob/develop/.github/workflows/automation-contributors-reminder.yml) diesen Kommentar automatisch zum Dank."
    fi
    echo ""
    echo "*Automatisch gepostet — die Pflege bleibt Maintainer-Handarbeit.*"
  } > "$BODY_FILE"
}

CREDIT_THANKED=0; CREDIT_PROMISED=0; CREDIT_UPDATED=0
for task in "${CREDIT_TASKS[@]}"; do
  IFS='|' read -r modus num author sha mat title cid <<< "$task"
  write_credit_body "$modus" "$num" "$author" "$sha" "$mat" "$title"
  if [[ "$cid" != "-" ]]; then
    api -X PATCH "repos/$REPO/issues/comments/$cid" -F "body=@$BODY_FILE" >/dev/null
    CREDIT_UPDATED=$((CREDIT_UPDATED + 1))
  else
    api -X POST "repos/$REPO/issues/$num/comments" -F "body=@$BODY_FILE" >/dev/null
    if [[ "$modus" == "thanks" ]]; then CREDIT_THANKED=$((CREDIT_THANKED + 1)); else CREDIT_PROMISED=$((CREDIT_PROMISED + 1)); fi
  fi
done

# ── 7. State sichern, wenn pending sich ohne Bericht geändert hat ──
OLD_TXT=$(IFS=,; echo "${PENDING_OLD[*]:-}")
if [[ "${#REPORT_ROWS[@]}" -eq 0 && "$PENDING_TXT" != "$OLD_TXT" && -n "$LATEST_ANY" ]]; then
  {
    echo "$MARKER"
    echo ""
    echo "<!-- $STATE_TOKEN $NOW pending:$PENDING_TXT -->"
  } > "$BODY_FILE"
  api -X POST "repos/$REPO/issues/$LATEST_ANY/comments" -F "body=@$BODY_FILE" >/dev/null
fi

# ── 8. Run-Summary (immer) ──
# GITHUB_STEP_SUMMARY ist nur in der CI gesetzt — lokal/ohne Env wird nach
# /dev/null gespiegelt (Git-Bash kennt kein appendbares /dev/stdout).
SUMMARY_TARGET="${GITHUB_STEP_SUMMARY:-}"
ACTION2="kein Dank nötig"
if [[ $((CREDIT_THANKED + CREDIT_PROMISED + CREDIT_UPDATED)) -gt 0 ]]; then
  ACTION2="$CREDIT_THANKED gedankt, $CREDIT_PROMISED angekündigt, $CREDIT_UPDATED aktualisiert"
fi
{
  echo "## 🧾 Contributors-Reminder"
  echo ""
  if [[ "${#MERGED_ROWS[@]}" -eq 0 && "${#PENDING_OLD[@]}" -eq 0 ]]; then
    echo "Keine Fork-PR-Merges seit **$SINCE** — nichts zu tun."
  else
    echo "| PR | Merge | Zeitpunkt | Beitragseintrag |"
    echo "|----|-------|-----------|-----------------|"
    for row in "${SUMMARY_ROWS[@]}"; do echo "$row"; done
    echo ""
    echo "- Meldungen: **${#REPORT_ROWS[@]}** — ${ACTION:-keine}"
    echo "- Dank-Kommentare: $ACTION2 (pending: ${PENDING_TXT:-keine})"
  fi
} >> "${SUMMARY_TARGET:-/dev/null}"
echo "Contributors-Reminder: ${ACTION:-keine} (Fenster: $SINCE, Fork-Merges: ${#MERGED_ROWS[@]}, Meldungen: ${#REPORT_ROWS[@]}, Dank: $ACTION2, pending: ${PENDING_TXT:-keine})"

exit 0
