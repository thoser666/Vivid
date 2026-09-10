# frozen_string_literal: true

require "digest"

# SHA-256-Prüfsummen für Release-Artefakte (✓ wöchentliche Distribution: Stable-
# GitHub-Releases tragen beide Flavors (standard + foss) und eine SHA256SUMS,
# damit Nutzer und Tools die APKs verifizieren können).
#
# Pure Ruby (Digest::SHA256) — bewusst OHNE externes sha256sum-Kommando:
#  - läuft unter Windows (cmd.exe) UND Linux-CI identisch
#  - ist als Modul ohne fastlane-/Android-Abhängigkeiten deterministisch testbar
#
# Ausgabeformat = GNU coreutils sha256sum (zwei Leerzeichen vor dem Namen):
#   <64-hex-digest>  <dateiname>
# Damit können Nutzer die Datei direkt mit `sha256sum -c SHA256SUMS.txt`
# verifizieren; Zeilen sind nach Dateinamen sortiert (deterministische Reihenfolge).
module Sha256sums
  FILE_NAME = "SHA256SUMS.txt"

  module_function

  # Liefert den Inhalt der SHA256SUMS-Datei für die angegebenen Pfade
  # (sortiert nach Basisnamen, GNU-Format, mit abschließendem Newline).
  def generate(paths)
    paths
      .filter { |p| File.file?(p) }
      .map { |p| [File.basename(p), Digest::SHA256.file(p).hexdigest] }
      .sort_by(&:first)
      .map { |name, hex| "#{hex}  #{name}" }
      .join("\n") + "\n"
  end

  # Schreibt die SHA256SUMS-Datei in das Verzeichnis dir zurück und liefert
  # den absoluten Pfad zur erzeugten Datei (für gh release create).
  # binwrite: GNU-Glbus-Format erfordert exakt LF, kein \r\n (Windows-Ruby
  # würde in Textmode übersetzen und sha256sum -c mit \\r-Namen brechen).
  def write(paths, dir)
    out = File.join(dir, FILE_NAME)
    File.binwrite(out, generate(paths))
    out
  end
end