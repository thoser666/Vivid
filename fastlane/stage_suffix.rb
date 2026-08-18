# frozen_string_literal: true

# Suffix-Guard für release_alpha/release_beta (siehe RELEASE.md → Beta-/Alpha-Strategie).
#
# Ein explizit übergebenes version: OHNE Stufen-Suffix würde einen STABILEN Tag
# erzeugen (z. B. version:0.5.1 → Tag "v0.5.1" statt "v0.5.1-beta"). Passiert am
# 18.08.2026: Der CI-Run wurde gecancelt und der Tag gelöscht, bevor etwas
# publiziert wurde — aber die Falle war strukturell offen.
#
# Diese Funktion ist die pure, testbare Entscheidung (keine fastlane-/
# Android-Abhängigkeiten). Die Lanes rufen sie nach der Tag-Normalisierung auf
# und brechen per UI.user_error! ab, wenn der Tag nicht zum Lane-Suffix passt.
#
# tag   – der Tag, ggf. ohne "v"-Präfix (wird normalisiert), z. B. "0.5.1-beta"
# stage – erwartetes Stufen-Suffix: "alpha", "beta" oder "rc"
#
# true  – Tag ist ein gültiger <stage>-Tag, z. B. "v0.5.1-beta" für stage "beta"
# false – Tag passt nicht (fehlendes Suffix, anderes Suffix oder Müll)
def stage_suffix_ok?(tag, stage)
  tag = "v#{tag}" unless tag.start_with?("v")
  tag.match?(/^v\d+\.\d+(?:\.\d+)?-#{Regexp.escape(stage)}$/)
end
