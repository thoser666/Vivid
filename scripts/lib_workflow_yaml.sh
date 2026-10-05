#!/usr/bin/env bash
# Gemeinsame YAML-Zugriffe auf Workflow-Dateien — die fensterfreie Alternative
# zu `grep -A N` / `head -N` als Struktur-Prüfung.
#
# Warum das existiert (Klasse "Zeilenfenster", vgl. #249 und #258): ein
# Struktur-Fakt wie "welche Berechtigungen hat Job X" oder "welchen api-level
# hat der Release-Gate-Step" ist eine Eigenschaft des YAML-Baums. Per Zeilen-
# abstand ausgedrückt bricht die Prüfung, sobald jemand eine Kommentarzeile,
# eine Leerzeile oder einen neuen Key einfügt — und meldet dann einen Fehler,
# den es nicht gibt. Zwei Instanzen dieser Klasse sind bereits zweimal
# passiert (ein `-A9`-Fenster musste einmal per Hand breiter gestellt werden,
# ein `head -30`-Fenster wurde beim Header-Wachstum rot).
#
# Alle Helfer geben bei "nicht gefunden" den Wert `-` zurück und schlagen nie
# fehl: der Aufrufer entscheidet, was das bedeutet. So kann ein Tippfehler im
# Pfad nicht stillschweigend wie ein bestandener Check aussehen.
#
# NUTZUNG:  source "$(dirname "$0")/lib_workflow_yaml.sh"
#           [[ "$(wf_job_permissions .github/workflows/x.yml job)" == "issues=write" ]] \
#             || fail "..."
#
# Die Assertions sind bewusst EXAKT (der komplette Permission-Satz, nicht
# "enthält issues: write"). Bei einem Least-Privilege-Guard ist eine zusätzlich
# vergebene Berechtigung genau das, was auffallen soll.

# Python-Interpreter robust bestimmen: `command -v` allein reicht nicht, die
# Windows-Store-Alias-Stubs (python3.exe) melden sich als vorhanden und brechen
# erst beim Aufruf mit Exit 49 ab. Deshalb wird der Kandidat ausgeführt.
WF_PY=""
wf_python() {
  if [[ -n "$WF_PY" ]]; then printf '%s' "$WF_PY"; return 0; fi
  local cand
  for cand in python3 python "py -3"; do
    if $cand -c 'import yaml' >/dev/null 2>&1; then WF_PY="$cand"; printf '%s' "$cand"; return 0; fi
  done
  return 1
}

_wf_query() { # $1=Datei, Rest = Python-Ausdruck ueber die Variable `d`
  local file="$1"; shift
  local py; py="$(wf_python)" || { echo "-"; return 0; }
  [[ -f "$file" ]] || { echo "-"; return 0; }
  "$py" - "$file" "$1" <<'PYEOF' 2>/dev/null || echo "-"
import sys, io, json
# stdout auf UTF-8 festnageln: unter Windows ist die Standard-Encoding cp1252,
# und ein extrahierter Wert mit ≠ (U+2260), — oder ü bringt print() sonst mit
# UnicodeEncodeError zum Abbruch. Der Abbruch landet im `|| echo "-"` und sieht
# dann wie "nicht gefunden" aus — bei [[ -z … ]]-Prüfungen also stillschweigend
# falsch grün. Beispiel: ein Workflow-Kommentar "RC≠0" im run-Snippet.
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass
try:
    import yaml
except ImportError:
    print("-"); raise SystemExit(0)
try:
    with io.open(sys.argv[1], encoding="utf-8") as fh:
        d = yaml.safe_load(fh)
except Exception:
    print("-"); raise SystemExit(0)
if d is None:
    d = {}
try:
    v = eval(sys.argv[2], {"d": d, "json": json, "sorted": sorted})
except Exception:
    v = None
if v is None or v == [] or v == {}:
    print("-")
elif isinstance(v, bool):
    print("true" if v else "false")
elif isinstance(v, (dict, list)):
    print(json.dumps(v, sort_keys=True, ensure_ascii=False))
else:
    print(v)
PYEOF
}

# wf_job_permissions <datei> <job> -> "a=read,b=write" (sortiert) oder "-"
wf_job_permissions() { _wf_query "$1" "';'.join('%s=%s' % (k, d['jobs']['$2']['permissions'][k]) for k in sorted(d['jobs']['$2'].get('permissions') or {}))"; }

# wf_job_if <datei> <job> -> if-Ausdruck oder "-"
wf_job_if() { _wf_query "$1" "d['jobs']['$2'].get('if')"; }

# wf_step_uses <datei> <job> <step-teilstück> -> uses-Pin des ersten passenden
# Steps oder "-". Bewusst über Name ODER uses gesucht, nicht über eine
# Zeilenposition.
wf_step_uses() { _wf_query "$1" "next((s.get('uses') for s in d['jobs']['$2'].get('steps', []) if '$3' in str(s.get('name','')) or '$3' in str(s.get('uses',''))), None)"; }

# wf_step_with <datei> <job> <step-teilstück> <key> -> with[key] oder "-"
wf_step_with() { _wf_query "$1" "next((s.get('with',{}).get('$4') for s in d['jobs']['$2'].get('steps', []) if '$3' in str(s.get('name','')) or '$3' in str(s.get('uses',''))), None)"; }

# wf_step_index <datei> <job> <step-name-teilstück> -> Index des Steps oder -1
# Für Reihenfolge-Aussagen („Wrapper-Validation vor dem ersten gradlew").
wf_step_index() { _wf_query "$1" "[i for i,s in enumerate(d['jobs']['$2'].get('steps', [])) if '$3' in str(s.get('name','')) or '$3' in str(s.get('uses',''))]"; }

# wf_step_if <datei> <job> <step-name-teilstück> -> if-Ausdruck des ersten
# passenden Steps oder "-" (kein if gesetzt, unbekannter Pfad/Job/Step).
# Wichtig für bedingungslos gemeinten Schritten: `$(wf_step_if …)` in einer
# [[ -z … ]]-Prüfung ist bei einer TIPPFEHLER-Funktionsname still leer und
# damit immer grün — dagegen schützt der Helper-Prüfer in
# test_workflow_security.sh (alle in Tests benutzten wf_* existieren).
wf_step_if() { _wf_query "$1" "next((s.get('if') for s in d['jobs']['$2'].get('steps', []) if '$3' in str(s.get('name','')) or '$3' in str(s.get('uses',''))), None)"; }

# wf_step_run <datei> <job> <step-name-teilstück> -> run-Snippet oder "-".
# Für Verhaltenstests: nur so kommt der echte Shell-Code aus dem Workflow in
# eine Sandbox (Stub für git/gh) und die Auswahl-Logik wird ausgeführt statt
# nur per grep angelesen. Der Wert ist mehrzeilig — Aufrufer müssen ihn
# quoten und nicht per Zeilenabstand zerlegen.
wf_step_run() { _wf_query "$1" "next((s.get('run') for s in d['jobs']['$2'].get('steps', []) if '$3' in str(s.get('name','')) or '$3' in str(s.get('uses',''))), None)"; }
