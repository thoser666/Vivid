# frozen_string_literal: true

require "yaml"
# version_code_for lebt in release_safety.rb (die eine Quelle fuer die Ableitung
# Tag → versionCode); ohne das require ist das Modul nicht eigenstaendig nutzbar.
require_relative "release_safety"

# Pflege des Builds-Blocks in fdroid/metadata/com.vivid.foss.yml.
#
# Hintergrund: Der Block wurde bis 2026-11-23 von Hand nachgezogen und stand
# deshalb noch auf 0.5.20-beta/5202, waehrend laengst 0.5.49-beta/5492
# veroeffentlicht war. Der Kommentar in der yml verwies dabei auf eine Lane
# `release_live`, die es nie gab — niemand hat den Block je automatisch
# geschrieben. Diese Datei ist die eine Quelle dafuer: die Release-Lanes rufen
# `record!` unmittelbar vor dem Tag-Push auf, `fastlane sync_fdroid_metadata`
# zieht einen bereits existierenden Tag nach.
#
# Bewusst textbasiert: die yml traegt Erklaer-Kommentare (Kopf, Description,
# UpdateCheckMode-Begruendung). Ein YAML-Dump wuerde sie verlieren, deshalb
# werden nur die beiden CurrentVersion-Zeilen und der Builds-Block ersetzt.
module FdroidMetadata
  RELATIVE_PATH = "fdroid/metadata/com.vivid.foss.yml"
  # Drei Eintraege reichen fuer IzzyOnDroid (Auto-Update nimmt den obersten) und
  # halten die Datei lesbar; aeltere Versionen sind im Git-Verlauf.
  MAX_BUILDS = 3
  # Nur echte Release-Tags: nightly-* traegt keine Stufe und wird nie
  # eingereicht (UpdateCheckMode: Tags).
  TAG_PATTERN = /\Av\d+\.\d+\.\d+(?:-(?:alpha|beta|rc))?\z/
  REQUIRED_KEYS = %w[versionName versionCode commit gradle output].freeze

  module_function

  # Eintrag fuer einen Tag. Wirft bei unpassendem Tag oder nicht ableitbarem
  # versionCode — fail-closed, damit kein Eintrag mit falschem Code entsteht
  # (fdroidserver verweigert den Build bei versionCode-Mismatch).
  def entry_for(tag, gradle_task: "assembleFossRelease", output: "app-foss-release.apk")
    raise ArgumentError, "kein Release-Tag: #{tag.inspect}" unless tag.to_s.match?(TAG_PATTERN)

    name = tag.to_s.sub(/^v/, "")
    code = version_code_for(name)
    raise ArgumentError, "versionCode nicht ableitbar aus #{tag.inspect}" if code.nil?

    {
      "versionName" => name,
      "versionCode" => code,
      "commit" => "v#{name}",
      "gradle" => [gradle_task],
      "output" => output,
    }
  end

  def block_text(entries)
    lines = ["Builds:"]
    entries.each do |entry|
      lines << "  - versionName: #{entry['versionName']}"
      lines << "    versionCode: #{entry['versionCode']}"
      lines << "    commit: #{entry['commit']}"
      lines << "    gradle:"
      Array(entry["gradle"]).each { |task| lines << "      - #{task}" }
      lines << "    output: #{entry['output']}"
    end
    "#{lines.join("\n")}\n"
  end

  # Neuer Dateiinhalt. Pure Funktion (kein Schreiben) — der Guard testet sie
  # gegen eine Kopie der echten Datei.
  def update_text(text, tag:, max: MAX_BUILDS)
    entry = entry_for(tag)
    parsed = YAML.safe_load(text, aliases: true)
    raise ArgumentError, "Metadata ist kein Mapping" unless parsed.is_a?(Hash)

    existing = Array(parsed["Builds"]).select { |e| e.is_a?(Hash) }
    entries = ([entry] + existing.reject { |e| e["versionCode"].to_i == entry["versionCode"] })
      .sort_by { |e| -e["versionCode"].to_i }
      .first(max)
    # fail-closed statt NoMethodError: max < 1 wuerde die Datei sonst in einen
    # Zustand ohne Builds-Eintrag schreiben (fdroidserver findet dann nichts).
    raise ArgumentError, "kein Builds-Eintrag (max muss >= 1 sein)" if entries.empty?

    newest = entries.first

    out = text.dup
    out = out.sub(/^CurrentVersion:.*$/, "CurrentVersion: '#{newest['versionName']}'")
    out = out.sub(/^CurrentVersionCode:.*$/, "CurrentVersionCode: #{newest['versionCode']}")

    # Builds steht als letzter Block in der Datei: alles ab "Builds:" bis zum
    # Dateiende (bzw. bis zur naechsten Spalte-0-Zeile) ersetzen.
    # Die letzte Zeile der Datei kann OHNE Schlusszeilenumbruch enden — der Block
    # muss sie mitnehmen, sonst bleibt "output: …" als Rest hinter dem neuen
    # Block stehen (Doppelzeile) und die Datei gilt als geaendert.
    m = out.match(/^Builds:\n(?:[ \t][^\n]*(?:\n|\z))*/)
    raise ArgumentError, "Builds-Block nicht gefunden" unless m

    out = out[0...m.begin(0)] + block_text(entries) + out[m.end(0)..]
    # Die Datei endet aktuell ohne Schlusszeilenumbruch — diese Konvention nicht
    # stillschweigend "reparieren", sonst meldet der Updater bei jedem Lauf eine
    # Aenderung und der Idempotenz-Check waere wertlos.
    out = out.sub(/\n\z/, "") unless text.end_with?("\n")
    # `entry` ist der EINTRAG DIESES TAGS, `entries.first` der neueste im Block.
    # Beim Nachziehen eines aelteren Tags sind die beiden verschieden — die Lane
    # meldete sonst den versionCode des obersten Eintrags (0.5.20-beta erschien
    # als "5492", Lauf 2026-11-25).
    { text: out, changed: out != text, entries: entries, entry: entry }
  end

  # Schreibt nur bei Aenderung (idempotent: derselbe Tag aendert nichts).
  def record!(root, tag, max: MAX_BUILDS)
    path = File.join(root, RELATIVE_PATH)
    original = File.read(path)
    result = update_text(original, tag: tag, max: max)
    File.binwrite(path, result[:text]) if result[:changed]
    result.merge(path: path)
  end

  # Vollstaendigkeit eines Eintrags (Guard-Nutzung).
  def complete?(entry)
    entry.is_a?(Hash) && REQUIRED_KEYS.all? { |k| entry.key?(k) && !entry[k].to_s.strip.empty? }
  end
end
