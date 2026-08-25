#!/usr/bin/env bash
# Selbsttest für den Roadmap-Reservierungs-Guard (fastlane/release_safety.rb).
#
# Beweist (RELEASE.md → Roadmap → Nummerierung):
#   1) reserved_roadmap_reason("v0.6.0-beta", …) liefert einen Grund, SOLANGE
#      das Streaming-Erweiterungs-Bucket (RIST/WHIP/RTMP-Pull/4K-HEVC/SRTLA)
#      in PARITY.md nicht vollständig ✅ ist — die Lane lehnt den Tag ab.
#   2) Derselbe Tag ist erlaubt (nil), sobald alle Bucket-Zeilen ✅ sind.
#   3) Nicht-reservierte Tags (v0.5.3-beta) und andere Stufen (v0.6.0-alpha)
#      sind nie blockiert.
#   4) Fehlende PARITY-Datei → konservativ blockiert (Guard greift).
#   5) Gegen die echte PARITY.md: aktuell ist das Bucket offen → Grund.
#   6) Strukturell: BEIDE Lanes (release_alpha + release_beta) rufen den Guard
#      auf — gleiche Quelle, kein Auseinanderlaufen (Muster wie Safety 2–4).
#
# Wichtig (Windows/MSYS): Argumente mit Single-Quotes werden beim Spawn von
# ruby.exe verschluckt — die Werte werden als reine ARGV-Argumente übergeben.
# Läuft offline, kein fastlane nötig.
set -euo pipefail

cd "$(dirname "$0")/.."

fail() {
  echo "❌ FAIL: $1"
  exit 1
}

release_safety_rb="$(cd fastlane && pwd)/release_safety.rb"
[[ -f "$release_safety_rb" ]] || fail "fastlane/release_safety.rb fehlt"

tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# Fixture 1: Streaming-Bucket noch offen (📋) — wie aktuell in PARITY.md.
cat > "$tmp/open.md" <<'EOF'
| Moblin-Feature | Status | Modul | Notizen |
|----------------|--------|-------|---------|
| RIST | 📋 | `core` | Stack-Entscheidung offen |
| WHIP (WebRTC) | 📋 | `core` | WebRTC-Stack |
| RTMP-Pull / Ingest (Server-Modus) | 📋 | `core` | Community-Request |
| 4K/60fps, H.264/H.265 (HEVC) | 📋 | `feature-streaming` | Encoder-Presets |
| SRTLA Multi-Network Bonding | 📋 | `core` | SRTLA-Protokoll |
| RTMP | ✅ | `feature-streaming` | vorhanden (nicht Teil des Buckets) |
EOF

# Fixture 2: Streaming-Bucket vollständig implementiert (✅).
cat > "$tmp/done.md" <<'EOF'
| Moblin-Feature | Status | Modul | Notizen |
|----------------|--------|-------|---------|
| RIST | ✅ | `core` | fertig |
| WHIP (WebRTC) | ✅ | `core` | fertig |
| RTMP-Pull / Ingest (Server-Modus) | ✅ | `core` | fertig |
| 4K/60fps, H.264/H.265 (HEVC) | ✅ | `feature-streaming` | fertig |
| SRTLA Multi-Network Bonding | ✅ | `core` | fertig |
| RTMP | ✅ | `feature-streaming` | vorhanden |
EOF

# call <funktion> <args…> — ruft die Funktion mit ARGV auf, gibt "nil" oder
# den inspect-Wert aus (Grund-String enthält kein Zeilenumbruch).
call() {
  ruby -e 'require ARGV[0]
           fn = ARGV[1]
           case fn
           when "reserved_roadmap_reason" then puts reserved_roadmap_reason(ARGV[2], ARGV[3] || "PARITY.md").inspect
           end' \
    "$release_safety_rb" "$@"
}

expect_reason() { # <desc> <funktion> <args…> — muss einen Grund liefern
  local desc="$1"
  shift
  local actual
  actual="$(call "$@")"
  if [[ "$actual" == "nil" ]]; then
    fail "$desc: erwartet Reservierungs-Grund, war nil"
  fi
}

expect_nil() { # <desc> <funktion> <args…> — muss nil liefern
  local desc="$1"
  shift
  local actual
  actual="$(call "$@")"
  if [[ "$actual" != "nil" ]]; then
    fail "$desc: erwartet nil, war $actual"
  fi
}

# 1) Reservierter Tag + offenes Bucket → Grund (Lane lehnt ab).
expect_reason "v0.6.0-beta bei offenem Bucket" reserved_roadmap_reason "v0.6.0-beta" "$tmp/open.md"

# 2) Reservierter Tag + implementiertes Bucket → nil (erlaubt).
expect_nil "v0.6.0-beta bei implementiertem Bucket" reserved_roadmap_reason "v0.6.0-beta" "$tmp/done.md"

# 3) Nicht-reservierte Tags / andere Stufen → nie blockiert.
expect_nil "v0.5.3-beta (nicht reserviert)" reserved_roadmap_reason "v0.5.3-beta" "$tmp/open.md"
expect_nil "v0.6.0-alpha (Reservierung nur beta)" reserved_roadmap_reason "v0.6.0-alpha" "$tmp/open.md"

# 4) Fehlende PARITY-Datei → konservativ Grund.
expect_reason "fehlende PARITY.md → konservativ blockiert" reserved_roadmap_reason "v0.6.0-beta" "$tmp/gibt-es-nicht.md"

# 5) Echte Repo-PARITY.md: Streaming-Bucket ist aktuell offen → Grund.
expect_reason "echte PARITY.md (Bucket noch offen)" reserved_roadmap_reason "v0.6.0-beta"

# 6) Strukturell: BEIDE Lanes rufen den Guard auf (gleiche Quelle).
# Achtung: awk-Ausgabe IMMER erst in eine Variable erfassen und DANN grep'en —
# ein direktes `awk | grep -q` bricht unter pipefail intermittierend ab, weil
# grep -q nach dem ersten Treffer die Pipe schließt und awk beim Weiterschreiben
# SIGPIPE bekommt (Exit 141).
for lane in release_alpha release_beta; do
  local_body="$(awk "/lane :$lane/,/^  end/" fastlane/Fastfile)"
  grep -q "reserved_roadmap_reason(" <<< "$local_body" \
    || fail "$lane ruft reserved_roadmap_reason nicht auf (Safety 5)"
done

# 7) Syntax beider Ruby-Dateien.
ruby -c fastlane/Fastfile >/dev/null || fail "fastlane/Fastfile ist syntaktisch kaputt"
ruby -c "$release_safety_rb" >/dev/null || fail "fastlane/release_safety.rb ist syntaktisch kaputt"

echo "✅ Roadmap-Reservierung: 7 Fälle grün (v0.6.0-beta abgelehnt solange Bucket offen), beide Lanes rufen den Guard, Syntax OK."
