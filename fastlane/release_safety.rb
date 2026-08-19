# frozen_string_literal: true

# Geteilte Release-Safety-Logik für release_alpha/release_beta.
#
# Hintergrund: release_beta hatte diese Checks, release_alpha nicht — genau diese
# Divergenz ließ die Alpha-Lane Downgrades ungeprüft durchgehen. Die Logik lebt
# hier als pure, testbare Funktionen; beide Lanes nutzen DIESELBE Quelle, damit
# sie nie wieder auseinanderlaufen (siehe RELEASE.md → Beta-/Alpha-Strategie).
#
# Die Shell-/UI-Aufrufe (git tag, UI.user_error!) bleiben bewusst in den Lanes —
# hier steht nur die reine Entscheidungslogik.

# Deterministischer versionCode aus einem Version-Tag (vX.Y.Z[-alpha|beta|rc]).
# Schema: major*1_000_000 + minor*1_000 + patch*10 + Stufe
# Stufe: alpha=1, beta=2, rc=3, stable=4 → wächst mit jeder Veröffentlichung.
# Bewusst NICHT von GITHUB_RUN_NUMBER abgeleitet: gleicher Tag ⇒ gleicher versionCode
# ⇒ gleiche APK (reproducible builds). Liefert nil bei unpassendem Format (Fallback).
def version_code_for(version_name)
  m = version_name.match(/^(\d+)\.(\d+)(?:\.(\d+))?(?:[-.]?(alpha|beta|rc))?/)
  return nil unless m
  major = m[1].to_i
  minor = m[2].to_i
  patch = (m[3] || "0").to_i
  stage = case m[4]
          when "alpha" then 1
          when "beta"  then 2
          when "rc"    then 3
          else 4
          end
  major * 1_000_000 + minor * 1_000 + patch * 10 + stage
end

# Höchster versionCode aller Tags einer Stufe (z. B. aller "-beta"-Tags).
# Pure Funktion über eine Tag-Liste — die Lanes liefern `git tag -l 'v*'`.
def highest_stage_code(tag_names, stage)
  tag_names
    .map { |t| t.strip.sub(/^v/, "") }
    .select { |n| n.match?(/-#{Regexp.escape(stage)}$/) }
    .map { |n| version_code_for(n) }
    .compact
    .max
end

# Höchster versionCode aller Tags AUSSER dem übergebenen Tag (Quer-Track-Vergleich).
# Pure Funktion über eine Tag-Liste.
def highest_other_code(tag_names, tag)
  self_code = version_code_for(tag.strip.sub(/^v/, ""))
  tag_names
    .map { |t| version_code_for(t.strip.sub(/^v/, "")) }
    .compact
    .reject { |c| c == self_code }
    .max
end

# Roadmap-Reservierung (RELEASE.md → Roadmap → Nummerierung): bestimmte
# Versionsnummern sind fest für ein noch NICHT implementiertes Feature-Bucket
# reserviert und dürfen nicht belegt werden — sonst kollidiert die Nummerierung,
# wenn das Bucket später kommt (dokumentierter Fallstrick, RELEASE.md §Beta-Strategie).
#
# Aktuell: `v0.6.0-beta` gehört dem Streaming-Erweiterungs-Bucket (RIST, WHIP,
# RTMP-Pull/Ingest, 4K/HEVC, SRTLA-Bonding, OBS-Snapshot, Game-Controller). Solange
# dieses Bucket in PARITY.md nicht vollständig implementiert (✅) ist, lehnt die
# Lane den Tag ab.
#
# Schlüssel: voller Stufen-Tag OHNE "v" (z. B. "0.6.0-beta").
# keywords: Schlüsselwörter der PARITY.md-Feature-Zeilen des Buckets (nur Spalte 1
#           — der Feature-Name — wird gematcht, damit Prosa-Erwähnungen nicht zählen).
ROADMAP_RESERVATIONS = {
  "0.6.0-beta" => {
    label: "v0.6.0-beta ist für das Streaming-Erweiterungs-Bucket reserviert (RIST/WHIP/RTMP-Pull/4K-HEVC/SRTLA in PARITY.md noch offen)",
    keywords: ["RIST", "WHIP", "RTMP-Pull", "4K/60fps", "SRTLA"],
  },
}.freeze

# Liefert den Reservierungs-Grund (String), wenn [tag] reserviert UND das Bucket
# in [parity_file] nicht implementiert ist; sonst nil. Pure Funktion (nur Datei-
# lesen, keine fastlane-Abhängigkeiten) — testbar mit Fixture-Dateien.
def reserved_roadmap_reason(tag, parity_file = "PARITY.md")
  reservation = ROADMAP_RESERVATIONS[tag.to_s.sub(/^v/, "")]
  return nil unless reservation
  return nil if roadmap_bucket_implemented?(parity_file, reservation[:keywords])
  reservation[:label]
end

# Alle Feature-Zeilen, deren NAME (Spalte 1) eines der Keywords enthält, müssen
# Status ✅ haben — dann gilt das Bucket als implementiert. Konservativ:
# fehlende Datei oder keine Trefferzeilen ⇒ NICHT implementiert (Guard greift).
def roadmap_bucket_implemented?(parity_file, keywords)
  rows = File.read(parity_file).lines.select do |line|
    next false unless line.start_with?("|")
    name_cell = line.split("|")[1].to_s
    keywords.any? { |kw| name_cell.include?(kw) }
  end
  return false if rows.empty?
  rows.all? { |line| line.match?(/^\| [^|]* \| ✅/) }
rescue Errno::ENOENT
  false
end
