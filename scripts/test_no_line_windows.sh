#!/usr/bin/env bash
# Meta-Guard: verbietet ZEILEN- UND KONTEXTFENSTER in den Guard-/Testskripten.
#
# Hintergrund (drei Vorfälle, alle dieselbe Ursache):
#   * #249  `grep -A35` auf den Emulator-Gate-Block: ein laengerer Kommentar
#          schob den geprueften Block aus dem Fenster, der Check wurde rot, obwohl
#          sich am Getesteten nichts geaendert hatte.
#   * #258  `head -30 | grep 'permissions: {}'` in test_workflow_security.sh:
#          `permissions: {}` rutschte auf Zeile 32, der Workflow war korrekt,
#          der Check wurde rot. Ein Kommentar in derselben Datei dokumentierte
#          schon vorher, dass das -A9-Fenster einmal wegen eines gewachsenen
#          if:-Blocks weggesessen hatte.
#   * #258  `head -5` auf die set-euo-pipefail-Zeile in pre-push.sh: gleiche
#          Klasse, gleiche Ursache.
#
# Die Diagnose lautet immer gleich: ein Struktur- oder Semantik-Fakt wurde als
# Zeilenabstand ausgedrueckt. Sobald Kommentare, Leerzeilen oder neue Keys
# dazwischenrutschen, meldet der Check einen Fehler, den es nicht gibt — oder
# (schlimmer) laesst einen echten Fehler durch. Beides ist ein Guard, der
# nicht das prueft, was er behauptet.
#
# REGEL: Struktur- und Semantik-Fakten werden per YAML-Parsing, per awk ueber
# Schluesselwoerter oder per Anker-Vergleich geprueft, nie ueber Abstand.
#
# Bewusst ERLAUBT sind:
#   1) `head -1` — "erster Treffer" bzw. "erste Zeilennummer" als
#      Datenbegrenzung einer Extraktion. Kein Assertionsfenster: die Aussage
#      ist "die erste Fundstelle", unabhaengig davon, wie viel danach steht.
#   2) Zeilennummern-Vergleiche `... | head -1 | xargs test {} -lt $(...)`:
#      das prueft eine REIHENFOLGE (Schritt A vor Schritt B), keinen Abstand.
#      Einfuegungen zwischen den Schritten aendern die Aussage nicht.
#   3) Eintraege in der Allowlist: dort IST der Abstand die Aussage. Jeder
#      Eintrag traegt eine Begruendung und wird auf Existenz geprueft, damit
#      die Allowlist nicht verwaisen kann.
#
# Kommentarzeilen werden NICHT gemeldet: ein Kommentar, der ein frueher
# vorhandenes Muster nennt ("wird per YAML geparst statt per `grep -A35`"),
# dokumentiert die Reparatur und ist kein Fund.
#
# Aufruf: bash scripts/test_no_line_windows.sh
set -euo pipefail
cd "$(dirname "$0")/.."

self="scripts/test_no_line_windows.sh"
PASS=0; FAIL=0
ok()  { PASS=$((PASS + 1)); }
fail(){ printf '❌ FAIL: %s\n' "$1" >&2; FAIL=$((FAIL + 1)); }

ALLOW=$(mktemp); PROBE=$(mktemp -d)
trap 'rm -f "$ALLOW"; rm -rf "$PROBE"' EXIT
allow() { printf '%s|%s|%s\n' "$1" "$2" "$3" >> "$ALLOW"; }
allow "scripts/test_build_retry.sh" "-B4" \
  "Abstand IST die Aussage: der Retry-Wrapper muss unmittelbar ueber dem Task stehen — gewolltes Naeheverhalten, kein Struktur-Fakt."
allow "scripts/test_pip_drift_semantics.sh" "-A2" \
  "Abstand IST die Aussage: fail() muss im unmittelbar folgenden Zweig des PIP_DRIFT_STRICT-Arms liegen."
allowlisted() { grep -qF -- "$1|$2|" "$ALLOW"; }

# ── Die eine Entscheidung, die alles gemeinsam benutzt ────────────────────
# Scanner UND Gegenproben (W6) rufen diese Funktion auf. Eine nachgebaute
# Parallelimplementierung in der Gegenprobe wuerde genau die Art von
# Scheinabdeckung erzeugen, die dieser Guard hier verhindern soll.
#
# Die EREs liegen in Variablen statt inline in `[[ =~ ]]`: eine Quoteklammer
# im Regex (z. B. ['\"]) bricht dort die Parsing-Schicht des Gards, nicht nur
# den Vergleich — der Guard waere dann nicht "zu locker", sondern gar nicht
# lauffaehig, ohne dass es auffaellt.
RE_AB='grep[[:space:]]+-[AB][0-9]+'
RE_HEAD='head[[:space:]]+-[0-9]+'
RE_SED="sed -n ['\"]"  # nur der Praefix; die Ziffern prueft der Aufrufer
RE_NR='NR[[:space:]]*(==|>=|<=)[[:space:]]*[0-9]+'
RE_HEAD_FEEDS_GREP='head[[:space:]]+-[0-9]+[^|]*\|.*grep'

classify() { # $1=Datei $2=Zeile-rest -> gibt das Fenster-Token aus, sonst ''
  local f="$1" rest="$2" tok n
  if [[ "$rest" =~ $RE_AB ]]; then
    tok="${BASH_REMATCH[0]##* }"
  elif [[ "$rest" =~ $RE_HEAD ]]; then
    n="${BASH_REMATCH[0]##*-}"
    [[ "$n" == "1" ]] && return 0        # Ersttreffer: erlaubt
    # head -N speist nur dann eine Assertion, wenn danach ein grep folgt
    [[ "$rest" =~ $RE_HEAD_FEEDS_GREP ]] || return 0
    tok="-$n"
  elif [[ "$rest" == *sed* && "$rest" =~ [0-9]+,[0-9]+p ]]; then
    tok="sed N,Mp"
  elif [[ "$rest" =~ $RE_NR ]]; then
    tok="awk NR"
  else
    return 0
  fi
  allowlisted "$f" "$tok" && return 0
  printf '%s\n' "$tok"
}

# Zeilenliste: grep -n, dann am ERSTEN Doppelpunkt splitten. `read -r a b c`
# mit IFS=: waere hier falsch — bei zwei Feldern landet die ganze Zeile in b und
# c bleibt leer; der Scan saehe nie eine Fundstelle und meldete grundlos gruen.
scan() { # $1=Datei $2=ERE
  local line
  while IFS= read -r line; do
    [[ -n "$line" ]] || continue
    [[ "$line" =~ ^([0-9]+):([[:space:]]*#|//) ]] && continue   # Kommentar
    printf '%s\t%s\n' "${line%%:*}" "${line#*:}"
  done < <(grep -nE "$2" "$1" || true)
}

mapfile -t SCRIPTS < <(ls scripts/*.sh | sort)

# ── W1..W4: die vier Fensterklassen ───────────────────────────────────────
scan_rule() { # $1=ERE $2=Beschreibung
  local f ln rest tok
  for f in "${SCRIPTS[@]}"; do
    [[ "$f" == "$self" ]] && continue
    while IFS=$'\t' read -r ln rest; do
      [[ -n "$ln" ]] || continue
      tok=$(classify "$f" "$rest")
      if [[ -n "$tok" ]]; then fail "$f:$ln: $2-Fenster '$tok' — ${rest# }"
      else ok; fi
    done < <(scan "$f" "$1")
  done
}
scan_rule "$RE_AB"        'Kontext'
scan_rule "$RE_HEAD"      'head'
scan_rule "${RE_SED}[0-9]+,[0-9]+p"  'sed-Zeilennummer'
scan_rule "$RE_NR"        'awk-NR'

# ── W5: Die Allowlist darf nicht verwaisen ─────────────────────────────────
while IFS='|' read -r f pat why; do
  [[ -n "$f" ]] || continue
  if [[ ! -f "$f" ]]; then fail "Allowlist verwaist: $f existiert nicht mehr"; continue; fi
  if grep -qF -- "$pat" "$f"; then ok
  else fail "Allowlist verwaist: $f enthaelt '$pat' nicht mehr ($why)"; fi
done < "$ALLOW"

# ── W6: Gegenproben gegen dieselbe classify-Funktion ──────────────────────
probe() { # $1=Datei $2=erwartetes Token
  local line ln rest
  while IFS= read -r line; do
    ln="${line%%:*}"; rest="${line#*:}"
    [[ "$(classify "probe.sh" "$rest")" == "$2" ]] && return 0
  done < <(grep -nE "$RE_AB|$RE_HEAD|sed |NR" "$1" || true)
  return 1
}
printf 'x=$(grep -A9 -F jobs: f.yml | grep -Fq x)\n' > "$PROBE/a"
probe "$PROBE/a" "-A9" && ok || fail "W6a: -A/-B-Scanner erkennt eine eingebaute Verletzung nicht"
printf 'x=$(head -30 f | grep -Fq p)\n' > "$PROBE/b"
probe "$PROBE/b" "-30" && ok || fail "W6b: head-Scanner erkennt eine eingebaute Verletzung nicht"
printf 'x=$(sed -n "31,40p" f)\n' > "$PROBE/c"
probe "$PROBE/c" "sed N,Mp" && ok || fail "W6c: sed-Scanner erkennt eine eingebaute Verletzung nicht"
printf 'awk "NR==42" f\n' > "$PROBE/d"
probe "$PROBE/d" "awk NR" && ok || fail "W6d: awk-Scanner erkennt eine eingebaute Verletzung nicht"
# Negativkontrollen: was erlaubt ist, darf NICHT gemeldet werden.
printf 'grep -n x f | cut -d: -f1 | head -1 | xargs -I{} test {} -lt 9\n' > "$PROBE/e"
probe "$PROBE/e" "" && ok || fail "W6e: geordnete Ersttreffer-Extraktion wird faelschlich gemeldet"
printf 'n=$(grep -n x f | head -1 | grep -o "[0-9]*")\n' > "$PROBE/f"
probe "$PROBE/f" "" && ok || fail "W6f: head -1 als Ersttreffer wird faelschlich gemeldet"
printf 'x=$(head -5 kws)\n' > "$PROBE/g"
probe "$PROBE/g" "" && ok || fail "W6g: head -N als reine Datenbegrenzung wird faelschlich gemeldet"
# Kommentarzeilen duerfen Muster nennen (dokumentieren eine Reparatur).
printf '# frueher per `grep -A35` geprueft, jetzt YAML\n' > "$PROBE/h"
[[ -z "$(scan "$PROBE/h" "$RE_AB")" ]] && ok || fail "W6h: Kommentarzeile wird faelschlich gemeldet"

# ── W7: Der YAML-Helper darf bei Tippfehlern nicht wie "bestanden" aussehen ──
# Jeder Aufrufer vergleicht gegen einen EXAKTEN Wert ("issues=write"). Käme der
# Helper bei falschem Datei- oder Job-Namen auf denselben Wert, wäre ein
# Tippfehler still ein grüner Check. Deshalb: unbekannt == "-", immer.
( source scripts/lib_workflow_yaml.sh
  [[ "$(wf_job_permissions .github/workflows/gibtsnicht.yml job)" == "-" ]] || { echo "T7a" >&2; exit 1; }
  [[ "$(wf_job_permissions .github/workflows/security-snyk.yml gibtsnicht)" == "-" ]] || { echo "T7b" >&2; exit 1; }
  [[ "$(wf_step_with .github/workflows/distribution-stable.yml publish-stable gibtsnicht api-level)" == "-" ]] || { echo "T7c" >&2; exit 1; }
  [[ "$(wf_job_permissions .github/workflows/security-snyk.yml snyk-test)" == "contents=read;security-events=write" ]] || { echo "T7d" >&2; exit 1; }
) && ok || fail "W7: lib_workflow_yaml.sh liefert bei unbekanntem Pfad/Job/Key nicht '-' (Tippfehler wuerden als bestandener Check durchgehen)"

printf '✅ [no-line-windows] %d Pass, %d Fail (Allowlist: %d bewusste Ausnahmen)\n' \
  "$PASS" "$FAIL" "$(wc -l < "$ALLOW")"
[[ "$FAIL" -eq 0 ]]
