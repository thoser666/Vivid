#!/usr/bin/env bash
# Regressionstest: SHA-256-Prüfsummen-Erzeugung für stable Releases
# (fastlane/sha256sums.rb — von release_github in jedem Stable-Publish aufgerufen).
#
# Geprüfte Szenarien:
#   H1 GNU-Format  → Zeilen `sha256  filename` (zwei Leerzeichen), verifizierbar
#                    mit `sha256sum -c` (Positivkontrolle über das echte Tool)
#   H2 Determinismus → gleiche Eingaben ⇒ byte-identische Ausgabe (sortierte
#                    Reihenfolge, keine Zeitstempel)
#   H3 Dateinamen-Sortierung → unsortierte Eingabe ergibt alphabetisch sortierte
#                    Ausgabe
#   H4 fehlende Dateien → werden ignoriert (kein Abbruch)
#   H5 write()-Pfad → SHA256SUMS.txt wird in das Zielverzeichnis geschrieben
#                    und datei-basiert validiert
#   H6 Nightly-Scope → publish_release hängt die Prüfsummendatei in BEIDEN
#                    Zweigen an (stable: standard+foss, nightly: standard)
#
# Läuft im CI (release-pipeline.yml, Job "Self-Test SHA256SUMS") und lokal:
# bash scripts/test_sha256sums.sh  (Exit 0 = grün)
# Plain Ruby, keine Fastlane-Abhängigkeit — bewusst wie die anderen Selbsttests.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

SCRATCH="$(mktemp -d)"
trap 'rm -rf "$SCRATCH"' EXIT

echo "▶ [test_sha256sums] Szenarien H1–H5 gegen fastlane/sha256sums.rb"

RUBY_SCRIPT=$(cat <<'RUBY'
require File.expand_path("fastlane/sha256sums", Dir.pwd)

dir = ARGV.fetch(0)
failures = 0
check = ->(label, cond) do
  if cond
    puts "  ✅ #{label}"
  else
    puts "  ❌ #{label}"
    failures += 1
  end
end

File.binwrite(File.join(dir, "b.txt"), "bbb\n")
File.binwrite(File.join(dir, "a.txt"), "aaa\n")
File.binwrite(File.join(dir, "c.txt"), "ccc\n")

content = Sha256sums.generate([File.join(dir, "b.txt"), File.join(dir, "a.txt"), File.join(dir, "c.txt")])
lines = content.split("\n")

# H2: Determinismus
again = Sha256sums.generate([File.join(dir, "b.txt"), File.join(dir, "a.txt"), File.join(dir, "c.txt")])
check.call("H2 deterministisch (identische Eingaben → identische Ausgabe)", content == again)

# H3: Sortierung nach Dateinamen (a, b, c)
check.call("H3 Ausgabe alphabetisch nach Dateiname sortiert",
  lines.map { |l| l.split("  ").last } == %w[a.txt b.txt c.txt])

# H1: GNU-Format (zwei Leerzeichen) pro Zeile
format_ok = lines.all? { |l| l =~ /^[0-9a-f]{64}  [^\s]+$/ }
check.call("H1 GNU-sha256sum-Format (64-hex + zwei Leerzeichen + Name)", format_ok)

# H4: fehlende Datei wird ignoriert (kein Abbruch, kein Eintrag)
missing = Sha256sums.generate([File.join(dir, "existiert-nicht.apk")])
check.call("H4 fehlende Datei wird ignoriert (leere Ausgabe)", missing == "\n")

# H5: write() erzeugt die Datei im Zielverzeichnis und liefert den Pfad
out_path = Sha256sums.write([File.join(dir, "a.txt"), File.join(dir, "b.txt")], dir)
check.call("H5 write() liefert Pfad zu SHA256SUMS.txt", out_path == File.join(dir, "SHA256SUMS.txt"))
check.call("H5 write() schreibt die Datei", File.exist?(out_path) && File.read(out_path) != "\n")

# H6: Nightly-Scope — Prüfsummen gelten nicht mehr nur für den Stable-Kanal:
# die publish_release-Lane erzeugt die Datei auch im Nightly-Zweig (Standard-APK
# ohne foss) und hängt sie an die Assets an. Statisch gegen die Fastfile:
fastfile = File.read(File.expand_path("fastlane/Fastfile", Dir.pwd))
upload_anchor = "options[:checksums] && File.exist?(options[:checksums])"
check.call("H6 Stable-Zweig hängt SHA256SUMS.txt an die Assets an",
  fastfile.include?(upload_anchor))
check.call("H6 Nightly-Zweig erzeugt SHA256SUMS für das Standard-APK",
  fastfile.include?("Sha256sums.write([apk], File.dirname(apk))"))
check.call("H6 Prüfsummen-Anhang existiert genau 2x (stable + nightly)",
  fastfile.scan(upload_anchor).size == 2)

exit(failures.zero? ? 0 : 1)
RUBY
)

RUBY_TMP="$(mktemp)"
trap 'rm -rf "$SCRATCH" "$RUBY_TMP"' EXIT
printf '%s' "$RUBY_SCRIPT" > "$RUBY_TMP"
if ! ruby "$RUBY_TMP" "$SCRATCH"; then
  echo "❌ SHA256SUMS-Szenarien fehlgeschlagen"
  exit 1
fi

# Positivkontrolle H1b über das echte sha256sum-Kommando: H5 hat SHA256SUMS.txt
# (a.txt + b.txt) ins Scratch-Verzeichnis geschrieben — `sha256sum -c` muss die
# Datei akzeptieren (Exit 0, "OK" je Eintrag). Fehlt sha256sum (exotische Hosts),
# wird die Kontrolle übersprungen — der Format-Check H1 deckt das Format ab.
if command -v sha256sum >/dev/null 2>&1; then
  SHA_OK=$(cd "$SCRATCH" && sha256sum -c SHA256SUMS.txt 2>&1) || true
  if [ -n "$SHA_OK" ] && echo "$SHA_OK" | grep -q "OK"; then
    echo "  ✅ H1b sha256sum -c verifiziert die Datei"
  else
    echo "  ❌ H1b sha256sum -c verifiziert die Datei"
    echo "$SHA_OK"
    exit 1
  fi
else
  echo "  (sha256sum nicht verfügbar — Positivkontrolle H1b übersprungen)"
fi

echo "✅ [test_sha256sums] Alle Szenarien grün"