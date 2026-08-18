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
