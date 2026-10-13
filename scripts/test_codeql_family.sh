#!/usr/bin/env bash
# Selbsttest: CodeQL-Familien-Bump (offline) — Mutationsnachweis fuer K1.4
# UND Gruppen-Vertrag der Dependabot-Konfiguration.
#
# Hintergrund (Vorfall 12.10.2026, Dependabot-PRs #290/#293): Dependabot
# loest github/codeql-action je Aktions-Typ eigenstoend auf — init, analyze
# und upload-sarif sind drei getrennte „Dependencies" (vier Pin-Stellen in
# drei Workflows). Der K1.4-Vertrag (Same-Release ueber ALLE Pin-Stellen,
# test_codeql_kotlin.sh) laesst dadurch EINZEL-PRs strukturell nie gruen
# werden: beide PRs waren nach gruener CI auf BLOCKED, weil jeweils nur eine
# von vier Stellen gehoben war. Familien-Bump und Dependabot-Gruppe sind
# deshalb zwei Haelften derselben Loesung — dieser Selbsttest deckt beide ab.
#
# Szenarien:
#   S0  Fixtures lassen sich auf den Ausgangsstand bringen (4 Pin-Stellen)
#   S1  Repofassung: test_codeql_kotlin.sh gruen (K1.4 erfuellt)
#   S2  Mutation (genau PR #293): NUR init v4.38.2 -> v4.38.3 -> K1.4 ROT
#   S3  Negativkontrolle: alle vier Stellen auf v4.38.3 -> wieder GRUEN
#   S4  dependabot.yml: die github-actions-Gruppe deckt JEDE im Repo
#       benutzte codeql-action-Dependency (exakte Namen, kein Wildcard)
#   S5  Mutation: Gruppen-Block entfernt -> S4-Pruefung faellt aus (rot)
# Exit 0 = gruen.
# Offline-Testbarkeit: grep/YAML-Parsing auf committed Dateien + Temp-Fixtures,
# kein Netzwerk- und kein GitHub-Zugriff.
set -euo pipefail
cd "$(dirname "$0")/.."

source scripts/lib_workflow_yaml.sh

echo "▶ [test_codeql_family] Szenarien S0–S5"

# Tag-Commits via `git ls-remote https://github.com/github/codeql-action
# 'refs/tags/v4.38.x*'` (annotated Tag -> ^{}-Objekt), dieselbe Konvention wie
# der bisherige Pin. Bewusst KONSTANTEN: S2/S3 bauen den Vorfallzustand nach.
OLD_SHA=2892aa5e19bbd11bc0cff5427e3b750a04d9e3c2 # v4.38.2 (Tag-Commit)
NEW_SHA=24c54180a607b1449ed407dd24f251e4e9147c8d # v4.38.3 (Tag-Commit)
OLD_VER=4.38.2
NEW_VER=4.38.3

PY="$(wf_python)" || { echo "❌ [test_codeql_family] kein Python+PyYAML (wf_python) — Selbsttest nicht ausfuehrbar."; exit 1; }

FAILED=0
pass() { echo "  ✅ $1"; }
fail() { echo "  ❌ $1"; FAILED=1; }

WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# Frisches Fixture-Root: Workflows + Versionskatalog (K1-K6 lesen beide).
fixture() { # $1=Name
  local d="$WORK/$1"
  mkdir -p "$d/.github/workflows" "$d/gradle"
  cp .github/workflows/*.yml "$d/.github/workflows/"
  cp gradle/libs.versions.toml "$d/gradle/"
  printf '%s' "$d"
}

# Einzelne Pin-Stelle umschreiben. Rückgabe: Anzahl getroffener Stellen — der
# Zähler IST der Mutations-Nachweis: 0 Treffer hiesse, die Mutation hat nicht
# gegriffen und der nachfolgende „rot"-Erwartungswert wäre ein Scheinbefund.
bump_pin() { # $1=Root $2=Aktionsname $3=SHA $4=Version
  "$PY" - "$1" "$2" "$3" "$4" <<'PYEOF'
import pathlib, re, sys
root, action, sha, ver = sys.argv[1:5]
pat = re.compile(
    r'(uses:[ \t]*github/codeql-action/%s@)[0-9a-f]{40}([ \t]*#[ \t]*v)[0-9.]+'
    % re.escape(action))
hits = 0
for f in sorted(pathlib.Path(root, '.github', 'workflows').glob('*.yml')):
    with open(f, encoding='utf-8', newline='') as fh:
        text = fh.read()
    text, n = pat.subn(lambda m: m.group(1) + sha + m.group(2) + ver, text)
    if n:
        with open(f, 'w', encoding='utf-8', newline='') as fh:
            fh.write(text)
        hits += n
print(hits)
PYEOF
}

bump_family() { # $1=Root $2=SHA $3=Version -> Summe ueber die drei Dependencies
  local d="$1" sha="$2" ver="$3" n=0 a
  for a in init analyze upload-sarif; do
    n=$((n + $(bump_pin "$d" "$a" "$sha" "$ver")))
  done
  printf '%s' "$n"
}

# ── S0: Die vier Pin-Stellen sind auffindbar und beschreibbar ──────────────
FIX_A="$(fixture s0a)"
FIX_B="$(fixture s0b)"
n0a="$(bump_family "$FIX_A" "$OLD_SHA" "$OLD_VER")"
n0b="$(bump_family "$FIX_B" "$OLD_SHA" "$OLD_VER")"
if [ "$n0a" = "4" ] && [ "$n0b" = "4" ]; then
  pass "S0 Vier Pin-Stellen gefunden (init + analyze + 2× upload-sarif)"
else
  fail "S0 Nur $n0a/$n0b statt 4 Pin-Stellen getroffen — Mutation wuerde ohne Wirkung bleiben"
fi

# ── S1: Repofassung ist gruen (K1.4: alle Stellen auf demselben Release) ──
if out="$(bash scripts/test_codeql_kotlin.sh 2>&1)"; then
  pass "S1 Repofassung: test_codeql_kotlin.sh gruen (K1–K6)"
else
  printf '%s\n' "$out"
  fail "S1 Repofassung: test_codeql_kotlin.sh rot"
fi

# ── S2: Geteilte Familie (Vorfall #293) muss K1.4 rot melden ──────────────
split_hits="$(bump_pin "$FIX_A" init "$NEW_SHA" "$NEW_VER")"
if [ "$split_hits" = "1" ]; then
  pass "S2.0 Mutation greift (nur init auf v$NEW_VER)"
else
  fail "S2.0 Mutation traf $split_hits statt 1 Stelle — die Erwartung Rot waere dann wertlos"
fi
if out="$(CODEQL_TEST_ROOT="$FIX_A" bash scripts/test_codeql_kotlin.sh 2>&1)"; then
  fail "S2 Geteilte Familie wurde NICHT rot gemeldet (K1.4 deckt den Vorfall #290/#293 nicht mehr ab)"
elif printf '%s' "$out" | grep -q "K1.4"; then
  pass "S2 Einzel-PR-Stand (nur init gebumpt) → K1.4 rot, Exit != 0"
else
  printf '%s\n' "$out"
  fail "S2 Rot, aber ohne K1.4-Meldung — die falsche Stelle ist fehlgeschlagen"
fi

# ── S3: Negativkontrolle — einheitliche Familie bleibt gruen ───────────────
uni_hits="$(bump_family "$FIX_B" "$NEW_SHA" "$NEW_VER")"
if [ "$uni_hits" = "4" ]; then
  pass "S3.0 Mutation greift (alle vier Stellen auf v$NEW_VER)"
else
  fail "S3.0 Mutation traf $uni_hits statt 4 Stellen"
fi
if out="$(CODEQL_TEST_ROOT="$FIX_B" bash scripts/test_codeql_kotlin.sh 2>&1)"; then
  pass "S3 Negativkontrolle: alle vier Stellen auf v$NEW_VER → gruen"
else
  printf '%s\n' "$out"
  fail "S3 Einheitliche Familie wurde rot gemeldet (Guard zu streng)"
fi

# ── S4/S5: Dependabot-Gruppe haelt die Familie zusammen ────────────────────
# Prueft: fuer JEDE im Repo benutzte codeql-action-Dependency steht ihr
# exakter Name als Gruppen-Pattern in der github-actions-Sektion. Genau das
# ist der Vertrag, den Einzel-PRs verletzt haben (#290/#293). Der Name muss
# wortgleich vorkommen — ein Wildcard-Pattern (Hausregel: exakte Paketnamen)
# erfuellt die Suche nach "github/codeql-action/<aktion>" nicht und faellt rot.
family_group_covered() { # $1=Pfad zur dependabot.yml
  local patterns a names
  patterns="$(wf_dependabot_group_patterns "$1" github-actions)"
  [ "$patterns" = "-" ] && return 1
  names="$(grep -rhoE 'github/codeql-action/[a-z-]+' .github/workflows/*.yml | sort -u)"
  [ -n "$names" ] || return 1
  for a in $names; do
    printf '%s' "$patterns" | grep -Fq "\"$a\"" || return 1
  done
  return 0
}

if family_group_covered .github/dependabot.yml; then
  pass "S4 Dependabot-Gruppe deckt jede genutzte codeql-action-Dependency ($(grep -rhoE 'github/codeql-action/[a-z-]+' .github/workflows/*.yml | sort -u | tr '\n' ' '))"
else
  fail "S4 .github/dependabot.yml gruppiert die codeql-action-Familie nicht vollstaendig — der naechste Bump faellt wieder in Einzel-PRs auseinander"
fi

NOGRP="$WORK/dependabot-ohne-gruppen.yml"
"$PY" - .github/dependabot.yml "$NOGRP" <<'PYEOF'
import sys
import yaml
with open(sys.argv[1], encoding='utf-8') as fh:
    doc = yaml.safe_load(fh)
for upd in doc.get('updates') or []:
    if upd.get('package-ecosystem') == 'github-actions':
        upd.pop('groups', None)
with open(sys.argv[2], 'w', encoding='utf-8', newline='\n') as fh:
    yaml.safe_dump(doc, fh, allow_unicode=True, sort_keys=False)
PYEOF
if family_group_covered "$NOGRP"; then
  fail "S5 Pruefung ist gruen, obwohl der Gruppen-Block fehlt — Scheingruen (faellt der Datei-Zugriff still aus?)"
else
  pass "S5 Mutation: Gruppen-Block entfernt → Pruefung faellt aus (rot)"
fi

echo ""
if [ "$FAILED" -eq 0 ]; then
  echo "✅ [test-codeql-family] Alle Szenarien bestanden — Familien-Bump und Dependabot-Gruppe greifen zusammen."
  exit 0
fi
echo "❌ [test-codeql-family] Mindestens ein Szenario fehlgeschlagen — siehe oben."
exit 1
