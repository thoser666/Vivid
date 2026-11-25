#!/usr/bin/env ruby
# frozen_string_literal: true

# Selbsttest fuer fastlane/fdroid_metadata.rb (Builds-Block-Pflege).
#
# Arbeitet ausschliesslich auf Kopien in einem Temp-Verzeichnis — die echte
# fdroid/metadata/com.vivid.foss.yml wird nie angefasst.
#
# Warum das noetig ist: der Block stand bis 2026-11-23 auf 0.5.20-beta, weil ihn
# niemand automatisch schrieb (die im yml-Kommentar genannte Lane `release_live`
# existierte nicht). Ein Updater, den kein Test anfasst, wuerde denselben Drift
# nur schneller erzeugen.

require "fileutils"
require "tmpdir"
require "yaml"

ROOT = File.expand_path("..", __dir__)
require File.join(ROOT, "fastlane/fdroid_metadata")
require File.join(ROOT, "fastlane/release_safety")

SOURCE = File.join(ROOT, FdroidMetadata::RELATIVE_PATH)
fails = []
ok = lambda do |label, cond|
  puts(cond ? "  ✅ #{label}" : "  ❌ #{label}")
  fails << label unless cond
end

Dir.mktmpdir do |tmp|
  path = File.join(tmp, "com.vivid.foss.yml")
  original = File.read(SOURCE)
  File.binwrite(path, original)

  puts "▶ [test_fdroid_metadata_update] Szenarien T1–T7"

  # T1: bestehender Tag ist ein No-op (Idempotenz auf dem echten Stand).
  current_tag = "v#{YAML.safe_load(original, aliases: true)['CurrentVersion']}"
  res = FdroidMetadata.update_text(original, tag: current_tag)
  ok.call("T1 vorhandener Tag ändert nichts (#{current_tag})", res[:changed] == false && res[:text] == original)

  # T2: neuer Tag landet oben, Code stimmt, CurrentVersion wird nachgezogen.
  upd = FdroidMetadata.update_text(original, tag: "v0.5.50-beta")
  parsed = YAML.safe_load(upd[:text], aliases: true)
  first = Array(parsed["Builds"]).first
  ok.call("T2 neuer Eintrag v0.5.50-beta oben (5502)",
          upd[:changed] && first["versionName"] == "0.5.50-beta" && first["versionCode"] == 5502 &&
          first["commit"] == "v0.5.50-beta")
  ok.call("T2 CurrentVersion/CurrentVersionCode nachgezogen",
          parsed["CurrentVersion"] == "0.5.50-beta" && parsed["CurrentVersionCode"] == 5502)
  ok.call("T2 versionCode == release_safety.rb", first["versionCode"] == version_code_for("0.5.50-beta"))
  # Die Lane meldet den Code des EINGEFUEGTEN Tags (result[:entry]), nicht den des
  # obersten Eintrags — beim Nachziehen aelterer Tags sind die verschieden.
  ok.call("T2 result[:entry] ist der eingefügte Eintrag",
          upd[:entry]["versionName"] == "0.5.50-beta" && upd[:entry]["versionCode"] == 5502)

  # T3: Obergrenze greift (MAX_BUILDS), aeltester Eintrag faellt raus.
  many = original
  3.times { |i| many = FdroidMetadata.update_text(many, tag: "v0.5.#{40 - i}-beta")[:text] }
  capped = YAML.safe_load(many, aliases: true)["Builds"]
  ok.call("T3 Builds-Block auf #{FdroidMetadata::MAX_BUILDS} Einträge begrenzt",
          capped.size == FdroidMetadata::MAX_BUILDS)
  ok.call("T3 ältester Eintrag entfernt (0.5.23-beta weg)",
          capped.none? { |e| e["versionName"] == "0.5.23-beta" })

  # T4: Kopfkommentare und Description bleiben erhalten.
  before_head = original.split("\nBuilds:").first.sub(/^CurrentVersion:.*$/m, "")
  after_head = upd[:text].split("\nBuilds:").first.sub(/^CurrentVersion:.*$/m, "")
  ok.call("T4 Kommentare/Description unverändert", before_head == after_head)
  ok.call("T4 Kopfkommentar mit F-Droid-Hinweis erhalten",
          upd[:text].include?("# F-Droid / IzzyOnDroid Metadata für den FOSS-Build von Vivid"))

  # T5: jeder Eintrag vollständig, Codes streng fallend.
  entries = YAML.safe_load(upd[:text], aliases: true)["Builds"]
  ok.call("T5 alle Einträge vollständig",
          entries.all? { |e| FdroidMetadata.complete?(e) })
  ok.call("T5 versionCodes streng fallend",
          entries.map { |e| e["versionCode"] }.each_cons(2).all? { |a, b| a > b })

  # T6: zweimal derselbe Tag → identischer Inhalt (kein Doppel-Eintrag).
  twice = FdroidMetadata.update_text(upd[:text], tag: "v0.5.50-beta")
  ok.call("T6 zweiter Lauf desselben Tags ist ein No-op", twice[:changed] == false)

  # T7: fail-closed bei Tags, die nicht eingereicht werden dürfen.
  %w[nightly-20260101-120000 0.5.50-beta v0.5 vabc].each do |bad|
    raised = begin
      FdroidMetadata.update_text(original, tag: bad)
      false
    rescue ArgumentError
      true
    end
    ok.call("T7 #{bad} wird abgelehnt", raised)
  end

  # T8: die echte Datei bleibt unberührt (Guard schreibt nie ins Repo).
  # Zeilenenden ignorieren: der Worktree kann CRLF tragen (core.autocrlf), der
  # Blob im Index LF — geprüft wird, dass der Guard nichts IN DIE DATEI schreibt.
  # Byte-Arrays vergleichen: binread liefert ASCII-8BIT, File.read UTF-8 —
  # String#== waere bei Umlauten allein wegen der Encoding-Labels false.
  ok.call("T8 echte Metadata unverändert",
          File.binread(SOURCE).delete("\r").bytes == original.delete("\r").bytes)
end

# T9: die Verdrahtung der Lane selbst. `record_fdroid_metadata!` laeuft mit
# cwd = fastlane/ (Fastfile-Verzeichnis), ein relatives Pathspec landet daher in
# fastlane/fdroid/... — im ersten echten Lane-Lauf (v0.5.50-beta, 2026-11-25)
# brach `git add` mit 128 ab, nachdem der Updater die Datei korrekt geschrieben
# hatte: die Aenderung lag uncommittet im Baum, ein Tag wurde nie gesetzt. Ein
# Test des Updaters allein sieht das nicht, deshalb wird der Helfer-Rumpf aus
# dem Fastfile gelesen und jeder git-Aufruf auf `-C` geprueft.
fastfile = File.read(File.join(ROOT, "fastlane/Fastfile"))
body = fastfile[/^def record_fdroid_metadata!.*?^end$/m].to_s
ok.call("T9 Helfer record_fdroid_metadata! im Fastfile gefunden", !body.empty?)
git_calls = body.lines.map(&:strip).select { |line| line.include?('sh("git"') }
ok.call("T9 git-Aufrufe im Helfer vorhanden (add/commit/push)",
        %w[add commit push].all? { |sub| git_calls.any? { |line| line.include?("\"#{sub}\"") } })
ok.call("T9 jeder git-Aufruf nennt -C <root> (cwd ist fastlane/)",
        !git_calls.empty? && git_calls.all? { |line| line.include?('sh("git", "-C", root') })
ok.call("T9 kein relatives Pathspec mehr ohne -C",
        git_calls.none? { |line| line.include?("RELATIVE_PATH") && !line.include?('"-C"') })

puts "\n=== Ergebnis ==="
if fails.empty?
  puts "Alle Builds-Block-Checks bestanden."
  exit 0
end
puts "#{fails.size} Check(s) fehlgeschlagen: #{fails.join('; ')}"
exit 1
