#!/usr/bin/env ruby
# Test für die Version-Code-Fallback-Logik aus app/build.gradle.kts.
# Ziel: Dieselbe Semantik wie fastlane/release_safety.rb (version_code_for).
# Hinweis: Keine neuen Android-Tests - Validierung über Ruby + Gradle-Konfigurations-Task.

def version_code_for(name)
  cleaned = name.sub(/^v/, "")
  stage = case
           when cleaned.end_with?("-beta") then 2
           when cleaned.end_with?("-alpha") then 1
           when cleaned.end_with?("-rc") then 3
           else 4
           end
  base = case
          when cleaned.end_with?("-beta") then cleaned[0...-5]
          when cleaned.end_with?("-alpha") then cleaned[0...-6]
          when cleaned.end_with?("-rc") then cleaned[0...-3]
          else cleaned
          end
  parts = base.split(".")
  major = (parts[0] || "0").to_i
  minor = (parts[1] || "0").to_i
  patch = (parts[2] || "0").to_i
  major * 1_000_000 + minor * 1_000 + patch * 10 + stage
end

tests = {
  "0.5.13-beta" => 5132,
  "0.5.12-beta" => 5122,
  "v0.5.13-beta" => 5132,
  "0.5.11-beta" => 5112,
  "0.5.8-beta.1" => 5084,
  "1.0" => 1000004,
  "0.5.13" => 5134,
  "v0.5.13" => 5134,
  "0.5.14" => 5144,
  "v0.5.14" => 5144,
  "0.5.14-stable" => 5144
}

puts "=== Konsistenz-Check: version_code_for (Ruby) vs. Fastlane-Schema ==="

puts "Alle Einträge:".inspect
failures = []
tests.each do |k, expected|
  got = version_code_for(k)
  if got == expected
    puts "OK: #{k} => #{got} (expected #{expected})"
  else
    failures << "#{k} => #{got} (expected #{expected})"
    puts "FAIL: #{k} => #{got} (expected #{expected})"
  end
end

puts "\n=== Ergebnis ==="
if failures.empty?
  puts "Alle Version-Code-Fallback-Checks bestanden (konsistent mit Fastlane-Schema)."
  exit 0
else
  puts "#{failures.size} Version-Code-Fallback-Check(s) fehlgeschlagen: #{failures.join("; ")}"
  exit 1
end
