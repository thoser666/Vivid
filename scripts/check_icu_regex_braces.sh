#!/usr/bin/env bash
# ICU-Regex-Klammer-Guard (Vivid):
#
# Hintergrund (Sentry-Befund 24.09.2026, TEXT-INFO-WIDGET-REGEX-ICU):
# Androids ICU-Regex-Engine (libcore/ojluni, java.util.regex auf dem Gerät)
# ist strenger als die JVM-Engine, auf der alle Unit-Tests laufen: Ein
# unmaskiertes schließendes `}` am Pattern-Ende wirft dort eine
# PatternSyntaxException im <clinit> — der ViewModel-Konstruktionspfad stirbt
# mit ExceptionInInitializerError und die App crasht beim Widget-Rendern,
# obwohl sämtliche JVM-Tests grün sind (JVM akzeptiert bare `}`).
#
# Dieser Guard auditier ALLE Regex-Literale im Produktionscode statisch:
#   - Regex("…")- und Regex("""…""")-Literale (auch mehrzeilig),
#   - maskierte Quantifier {n}/{n,}/{n,m} bleiben erlaubt,
#   - maskierte Klammern \{ und \} sind die Pflicht-Form.
# Roter Befund = bare `{`/`}` in einem Literal, das die ICU-Engine
# rejecten kann — Genauigkeit bewusst vor Vollständigkeit (matcht die
# Semantik des Sentry-Befunds; dokumentierte Einschränkungen unten).
#
# Bekannte bewusste Einschränkungen (False Positives sind die Preis der
# Absicherung, False Negatives soweit wie möglich ausgeschlossen):
#   - Der Scanner matcht Literale nur im Kotlin-Kontext `Regex(`; `.toRegex()`
#     und Pattern.compile werden derzeit nicht gescannt.
#   - Charakterklassen-Bereiche wie `[a-z{]` können theoretisch falsch
#     positiv sein — im Codebestand existieren sie nicht.
#   - Zusätzlich musss mindestens ein Registry-Befund dokumentiert sein
#     (Vertrag wie bei der CrashAdvisoryRegistry: keine spekulativen Einträge).
#
# Exit-Codes: 0 = sauber, 1 = Verstoß gefunden, 2 = Setup-Fehler.
set -euo pipefail
cd "$(dirname "$0")/.."

FAIL=0

# ── 1) Statischer Scan: bare Klammern in Regex-Literalen ────────────────────
# PYTHONIOENCODING + reconfigure: Windows-Konsolen laufen auf cp1252 — ohne
# UTF-8-Erzwingung stirbt das ✅/❌-Output selbst bei sauberem Scan
# (UnicodeEncodeError → fälschlich exit 1, Vorfall im Erstrun 24.09.2026).
PYTHONIOENCODING=utf-8 python - <<'PY' || FAIL=1
import re, subprocess, sys
try:
    sys.stdout.reconfigure(encoding="utf-8")
except Exception:
    pass  # ältere Pythons ohne reconfigure — env var trägt dann

files = subprocess.run(
    ["git", "grep", "-l", "Regex(", "--", "*.kt"],
    capture_output=True, text=True,
).stdout.split()

violations = []
for f in files:
    if "/test/" in f or "/androidTest/" in f or f.endswith("Test.kt"):
        continue  # Testcode läuft auf der JVM, nicht auf dem Gerät
    src = open(f, encoding="utf-8").read()
    # Regex-Literale: Regex("…") (eine Zeile) und Regex("""…""") (auch mehrzeilig)
    for m in re.finditer(r'Regex\(\s*("""(?:(?!""").)*"""|"(?:\\.|[^"\\])*")', src, re.S):
        raw = m.group(1)
        if raw.startswith('"""'):
            pat = raw[3:-3]
        else:
            pat = raw[1:-1]
        # Kotlin-Escapes auflösen (\\\\ -> Placeholder, \\" -> ") und zurück
        real = pat.replace("\\\\", "\x00").replace('\\"', '"').replace("\x00", "\\")
        # Maskierte Quantifier {n}/{n,}/{n,m} sind ICU-legal — raus damit
        stripped = re.sub(r"(?<!\\)\{\d+(,\d*)?\}", "", real)
        for i, ch in enumerate(stripped):
            if ch in "{}" and (i == 0 or stripped[i - 1] != "\\"):
                line = src[: m.start()].count("\n") + 1
                violations.append(
                    f"  {f}:{line}: bare '{ch}' in Regex-Literal "
                    f"(Pattern-Anfang: {real[:50]!r}…)"
                )

if violations:
    print("❌ [icu-regex] Bare-Klammern in Regex-Literalen — Android-ICU rejectet diese Patterns:")
    for v in violations:
        print(v)
    print("   Fix: Klammern maskieren (\\{ bzw. \\}) — siehe scripts/check_icu_regex_braces.sh (Sentry TEXT-INFO-WIDGET-REGEX-ICU).")
    sys.exit(1)
print("✅ [icu-regex] Keine bare Klammern in Regex-Literalen (ICU-sicher).")
PY

# ── 2) CrashAdvisoryRegistry: alle Events müssen dokumentiert sein ─────────
if ! git grep -q "TEXT-INFO-WIDGET-REGEX-ICU" -- core/src/main/java/com/vivid/core/startup/CrashAdvisory.kt; then
  echo "❌ [icu-regex] Registry-Eintrag TEXT-INFO-WIDGET-REGEX-ICU fehlt in CrashAdvisory.kt"
  echo "   (Der Befund ist real identifiziert (Sentry); ohne Eintrag bleibt die"
  echo "    Advisory-Leiste im Log-Viewer für betroffene Nutzer stumm.)"
  FAIL=1
else
  echo "✅ [icu-regex] CrashAdvisory-Registry-Eintrag TEXT-INFO-WIDGET-REGEX-ICU vorhanden."
fi

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi
echo "✅ [icu-regex] Alle Checks bestanden."
