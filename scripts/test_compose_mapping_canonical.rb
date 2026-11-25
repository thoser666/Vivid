#!/usr/bin/env ruby
# frozen_string_literal: true

# Guard: die Build-seitige Kanonisierung des Compose-Group-Mapping-Anhangs ist
# wirklich ordnungsunabhaengig (#458).
#
# Hintergrund: AGP haengt `intermediates/compose_mapping/<variant>/
# compose-mapping.txt` an `mapping.txt` an. Die REIHENFOLGE dieses Anhangs war
# nicht stabil (Nightly 17./18./20.11.2026: 79 von 3.322 Zeilen permutiert,
# Menge identisch, R8-Teil byte-identisch). Weil AGP `# pg_map_hash` ueber den
# Gesamtinhalt bildet und Sentry `io.sentry.ProguardUuids` daraus ableitet,
# kippten damit das APK und `assets/sentry-debug-meta.properties` — obwohl der
# obfuskierte Code identisch war. Der Build sortiert den Anhang deshalb selbst
# (`app/build.gradle.kts`, `doLast` der `merge*ComposeMapping`-Task).
#
# Warum eine Gradle-Task und keine Kopie: der Guard muss die ECHTE
# Implementierung ausfuehren. Eine nachgebaute Sortierung im Test wuerde nur
# sich selbst beweisen — dieselbe Fehlerklasse ist im Repo belegt (bis
# 2026-11-23 pruefte `test_version_fallback.rb` eine Kopie der Formel, waehrend
# der Build fuer jede Stufe +4 rechnete).
#
# Geprueft (P1-P8):
#   P1 die Probe ist gelaufen und hat alle vier Dateien geschrieben
#   P2 Permutationsinvarianz — der eigentliche Fix: zwei Permutationen derselben
#      Eintraege ergeben dieselben Bytes
#   P3 Idempotenz — zweimal kanonisiert == einmal kanonisiert (der doLast laeuft
#      nachweislich mehrfach: der Merge sieht seine eigene Ausgabe)
#   P4 Verlustfreiheit — gleiche Zeilen-Multimenge und gleiche Zeilenzahl wie die
#      Eingabe (Sortieren darf keinen Eintrag schlucken oder zusammenfassen)
#   P5 Struktur — nicht eingerueckte Blockkoepfe bleiben an ihrer Position, die
#      Leerzeile zwischen den Bloecken bleibt Leerzeile
#   P6 Byte-Ordnung — jeder eingerueckte Lauf ist aufsteigend nach UTF-8-BYTES
#      sortiert (= `LC_ALL=C sort`), genau die Ordnung, die
#      `scripts/compare_reproducibility.sh` beim Vergleich voraussetzt. Eine
#      Sortierung nach UTF-16-Code-Einheiten (`String.compareTo`) waere hier
#      rot: U+FF21 liegt in UTF-16 hinter U+1F600, in UTF-8 davor.
#   P7 Gegenprobe — die Fixture war vor der Kanonisierung NICHT sortiert; sonst
#      waere P2 auch ganz ohne Sortierung gruen und wuerde nichts beweisen
#   P8 Dateiform — abschliessendes Newline und Anzahl der Leerzeilen bleiben

require "fileutils"

# Modul-Build-Verzeichnis: die Task liegt im Modul :app, `layout.buildDirectory`
# ist dort app/build (nicht das Root-build/).
PROBE_DIR = File.join("app", "build", "compose-mapping-probe")
LOG_PATH = File.join("build", "compose-mapping-probe.log")

FileUtils.mkdir_p("build")
# Vorher loeschen: so beweist ein gruener Lauf, dass DIESER Lauf die Dateien
# erzeugt hat, und nicht ein UP-TO-DATE-Ergebnis von gestern.
FileUtils.rm_rf(PROBE_DIR)
# Windows: cmd findet "gradlew.bat" ohne Pfadpraefix nicht — daher explizit
# relativ aufrufen (gleiche Stelle wie in test_version_fallback.rb).
gradle = Gem.win_platform? ? '.\\gradlew.bat' : './gradlew'
ran = system("#{gradle} -q :app:composeMappingProbe --console=plain > #{LOG_PATH} 2>&1")
unless ran
  puts(File.exist?(LOG_PATH) ? File.readlines(LOG_PATH).last(20).join : "(keine Ausgabe)")
  puts "FAIL: :app:composeMappingProbe ist nicht gelaufen — Guard fail-closed."
  exit 1
end

# Die Task schreibt UTF-8; binread + force_encoding verhindert, dass die
# Plattform-Kodierung des Test-Ruby die Bytes umdeutet.
def read_probe(name)
  path = File.join(PROBE_DIR, name)
  return nil unless File.file?(path)

  File.binread(path).force_encoding(Encoding::UTF_8)
end

puts "=== Compose-Mapping-Kanonisierung: Permutationsinvarianz (Gradle == LC_ALL=C) ==="

failures = []
input = read_probe("input.txt")
perm_a = read_probe("perm-a.txt")
perm_b = read_probe("perm-b.txt")
perm_a2 = read_probe("perm-a-twice.txt")

if [input, perm_a, perm_b, perm_a2].any?(&:nil?)
  puts "FAIL: P1 die Probe hat nicht alle vier Dateien geschrieben (#{PROBE_DIR}) — Log: #{LOG_PATH}"
  failures << "P1"
else
  puts "OK: P1 :app:composeMappingProbe hat input/perm-a/perm-b/perm-a-twice geschrieben"
end

if failures.empty?
  # trailing empty line muss mitgezaehlt werden (split ohne -1 schluckt sie).
  lines_of = ->(text) { text.split("\n", -1) }
  # Zusammenhaengende Laeufe eingerueckter Eintraege (die Einheit, die der
  # Kanonisierer sortiert).
  runs_of = lambda do |lines|
    runs = []
    current = []
    lines.each do |line|
      if !line.empty? && line.start_with?(" ")
        current << line
      else
        runs << current unless current.empty?
        current = []
      end
    end
    runs << current unless current.empty?
    runs
  end
  # Aufsteigend nach Bytes — `x.b` liefert eine ASCII-8BIT-Kopie, deren
  # String#<=> memcmp ist (kein Locale, keine UTF-16-Code-Einheiten).
  sorted_run = ->(run) { run.each_cons(2).all? { |x, y| (x.b <=> y.b) <= 0 } }

  input_lines = lines_of.call(input)
  a_lines = lines_of.call(perm_a)
  b_lines = lines_of.call(perm_b)
  a_runs = runs_of.call(a_lines)

  if perm_a == perm_b
    puts "OK: P2 Permutationsinvarianz — beide Permutationen ergeben identische Bytes"
  else
    puts "FAIL: P2 Permutationsinvarianz verletzt — die Kanonisierung haengt von der " \
         "Eingabereihenfolge ab (#{perm_a.bytesize} B vs. #{perm_b.bytesize} B)"
    failures << "P2"
  end

  if perm_a2 == perm_a
    puts "OK: P3 idempotent — zweimal kanonisiert == einmal kanonisiert"
  else
    puts "FAIL: P3 nicht idempotent — ein zweiter doLast-Lauf wuerde die Datei erneut veraendern"
    failures << "P3"
  end

  if input_lines.tally == a_lines.tally
    puts "OK: P4 verlustfrei — #{input_lines.size} Zeilen, gleiche Multimenge wie die Eingabe"
  else
    lost = input_lines.tally.keys - a_lines.tally.keys
    added = a_lines.tally.keys - input_lines.tally.keys
    puts "FAIL: P4 Zeilen-Multimenge veraendert (verloren: #{lost.size}, hinzugefuegt: #{added.size})"
    failures << "P4"
  end

  headers_of = lambda do |ls|
    ls.each_with_index.select { |line, _i| !line.empty? && !line.start_with?(" ") }.map { |line, i| [i, line] }
  end
  if headers_of.call(input_lines) == headers_of.call(a_lines)
    puts "OK: P5 Blockkoepfe und Leerzeilen an unveraenderter Position"
  else
    puts "FAIL: P5 Blockkoepfe verschoben oder Leerzeilen umsortiert"
    failures << "P5"
  end

  bad_runs = a_runs.reject { |run| sorted_run.call(run) }
  if a_runs.empty?
    puts "FAIL: P6 die Probe enthaelt keinen eingerueckten Lauf — der Check waere leer"
    failures << "P6"
  elsif bad_runs.empty?
    puts "OK: P6 alle #{a_runs.size} Laeufe in UTF-8-Byte-Reihenfolge sortiert (LC_ALL=C)"
  else
    puts "FAIL: P6 #{bad_runs.size} Lauf/Laeufe NICHT in Byte-Reihenfolge sortiert — " \
         "der Vergleich in compare_reproducibility.sh (`LC_ALL=C sort`) wuerde " \
         "abweichen. Erste Abweichung: #{bad_runs.first.first(3).inspect}"
    failures << "P6"
  end

  input_unsorted = runs_of.call(input_lines).any? { |run| !sorted_run.call(run) }
  if input_unsorted
    puts "OK: P7 Gegenprobe — die Fixture war vorher unsortiert (P2 kann nicht trivial gruen sein)"
  else
    puts "FAIL: P7 die Fixture ist bereits sortiert — die Probe beweist nichts, Fixture anpassen"
    failures << "P7"
  end

  blank_input = input_lines.count("")
  blank_out = a_lines.count("")
  if perm_a.end_with?("\n") == input.end_with?("\n") && blank_input == blank_out &&
     input_lines.size == a_lines.size
    puts "OK: P8 Dateiform bleibt (abschliessendes Newline, #{blank_out} Leerzeile(n), gleiche Zeilenzahl)"
  else
    puts "FAIL: P8 Dateiform veraendert (Zeilen #{input_lines.size} -> #{a_lines.size}, " \
         "Leerzeilen #{blank_input} -> #{blank_out})"
    failures << "P8"
  end
end

puts "\n=== Ergebnis ==="
if failures.empty?
  puts "Alle Kanonisierungs-Checks bestanden (Permutationsinvarianz, Idempotenz, Byte-Ordnung)."
  exit 0
end

puts "#{failures.size} Check(s) fehlgeschlagen: #{failures.join(', ')}"
exit 1
