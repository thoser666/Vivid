#!/usr/bin/env ruby
# frozen_string_literal: true

# Test für die versionCode-Ableitung aus app/build.gradle.kts.
#
# Der Test rechnet NICHT nach, sondern fragt den Build: die Task
# `:app:versionCodesProbe` gibt die Codes aus, die `derivedVersionCode`
# tatsächlich liefert; verglichen wird gegen `version_code_for` — dieselbe
# Formel wie fastlane/release_safety.rb, die die Tags bestimmt.
#
# Warum das so sein muss: bis 2026-11-23 verglich dieses Skript nur eine eigene
# Kopie der Formel mit sich selbst und blieb grün, während der Build für jede
# Stufe `+4` addierte. F-Droid baut einen Tag mit `assembleFossRelease` **ohne**
# `-PversionCode` — der Tag `v0.5.49-beta` wäre dort als 5494 gebaut worden,
# während die Metadata 5492 verlangt (fdroidserver: versionCode-Mismatch,
# Build abgelehnt). Ein Guard, der die Implementierung nicht ausführt, beweist
# für diesen Fall nichts.

require "fileutils"

# Referenz: identisch zu fastlane/release_safety.rb#version_code_for.
# Das fuehrende "v" entfernen hier die Aufrufer; der Gradle-Build macht es selbst
# (`removePrefix("v")`), deshalb normalisiert die Referenz ebenfalls — sonst
# vergleicht der Guard "v0.5.14" mit nil.
def version_code_for(version_name)
  name = version_name.sub(/^v/, "")
  m = name.match(/^(\d+)\.(\d+)(?:\.(\d+))?(?:[-.]?(alpha|beta|rc))?/)
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

PROBES = %w[
  0.5.13-beta 0.5.8-beta.1 1.0 0.5.13 v0.5.14 0.5.14-stable
  0.5.49-beta 0.5.16-rc 0.5.13-alpha
].freeze

output_path = "build/version-codes-probe.txt"
FileUtils.mkdir_p("build")
# Windows: cmd findet "gradlew.bat" ohne Pfadpraefix nicht ("konnte nicht gefunden
# werden" — genau so scheiterte der erste Lauf); daher explizit relativ aufrufen.
gradle = Gem.win_platform? ? '.\gradlew.bat' : './gradlew'
ran = system("#{gradle} -q :app:versionCodesProbe --console=plain > #{output_path} 2>&1")

unless ran
  puts(File.exist?(output_path) ? File.readlines(output_path).last(20).join : "(keine Ausgabe)")
  puts "FAIL: :app:versionCodesProbe ist nicht gelaufen — Guard fail-closed."
  exit 1
end

actual = {}
File.readlines(output_path).each do |line|
  m = line.strip.match(/\A(.+)=(\d+)\z/)
  actual[m[1]] = m[2].to_i if m
end

puts "=== Konsistenz-Check: derivedVersionCode (Gradle) vs. release_safety.rb ==="

failures = []
PROBES.each do |probe|
  expected = version_code_for(probe)
  got = actual[probe]
  if got.nil?
    failures << "#{probe}: nicht in der Gradle-Ausgabe"
    puts "FAIL: #{probe} fehlt in der Ausgabe von :app:versionCodesProbe"
  elsif got == expected
    puts "OK: #{probe} => #{got}"
  else
    failures << "#{probe}: Gradle #{got} != #{expected}"
    puts "FAIL: #{probe} => Gradle #{got}, erwartet #{expected}"
  end
end

# Der reale Arbeitsstand muss ebenfalls stimmen: sonst baut F-Droid den Tag mit
# einer anderen versionCode als die veröffentlichte APK.
version = File.read("VERSION").strip
expected_current = version_code_for(version)
got_current = actual["VERSION"]
if got_current.nil?
  failures << "VERSION (#{version}): nicht in der Gradle-Ausgabe"
  puts "FAIL: VERSION fehlt in der Ausgabe von :app:versionCodesProbe"
elsif got_current == expected_current
  puts "OK: VERSION #{version} => #{got_current}"
else
  failures << "VERSION #{version}: Gradle #{got_current} != #{expected_current}"
  puts "FAIL: VERSION #{version} => Gradle #{got_current}, erwartet #{expected_current}"
end

puts "\n=== Ergebnis ==="
if failures.empty?
  puts "Alle Version-Code-Checks bestanden (Gradle == release_safety.rb)."
  exit 0
end

puts "#{failures.size} Version-Code-Check(s) fehlgeschlagen: #{failures.join('; ')}"
exit 1
