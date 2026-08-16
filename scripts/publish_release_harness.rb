# Harness: fuehrt die ECHTE publish_release-Lane aus fastlane/Fastfile aus
# (Text wird direkt aus der Datei extrahiert, kein Code dupliziert), mit
# minimalen fastlane-Stubs und einem Mock-gh-Transport. Beruehrt KEINE echten
# Releases. Aufruf: ruby scripts/publish_release_harness.rb
#   MOCK_GH_STATE_DIR — Verzeichnis mit releases.json (Mock-Zustand)
#   MOCK_GH_APK       — Pfad zur Dummy-APK (File.exist?-Check der Lane)
require "json"

# Befehls-Log: alle tatsaechlich ausgeführten sh-/Backtick-Kommandos, damit der
# Test beweisen kann, welche Kommandos die Lane WIRKLICH ausgeführt hat
# (z. B. "nie eine Tag-Löschung").
$CMD_LOG = []

module SharedValues
  GRADLE_APK_OUTPUT_PATH = :apk_path
  GRADLE_MAPPING_TXT_OUTPUT_PATH = :mapping
  GRADLE_OUTPUT_JSON_OUTPUT_PATH = :metadata
end

class UI
  def self.header(m);      puts "== #{m}"; end
  def self.message(m);     puts "   [msg] #{m}"; end
  def self.important(m);   puts "   [!!] #{m}"; end
  def self.error(m);       puts "   [ERR] #{m}"; end
  def self.success(m);     puts "   [OK] #{m}"; end
  def self.user_error!(m); raise "USER_ERROR: #{m}"; end
end

MOCK_GH = File.expand_path("gh_mock_release.sh", __dir__)

def sh(*args)
  puts "   [sh] #{args.join(' ')}"
  $CMD_LOG << args.join(" ")
  cmd = args[0] == "gh" ? ["bash", MOCK_GH] + args[1..] : args
  ok = system(*cmd)
  raise "sh failed: #{cmd.join(' ')}" unless ok
end

# Backtick-Override: `gh ...`-Aufrufe der Lane zum Mock umleiten. Das
# nachgestellte "2>/dev/null" entfernen, damit cmd.exe es nicht missdeutet.
def `(cmd)
  c = cmd.strip.sub(/^gh\b/, "bash #{MOCK_GH}").sub(/\s*2>\/dev\/null\s*$/, "")
  puts "   [bt] #{c}"
  $CMD_LOG << c
  IO.popen(c.split, &:read).to_s
end

def lane_context; {}; end

def lane(_name, &block)
  $LANE_BLOCK = block
end

source = File.read(File.expand_path("fastlane/Fastfile", Dir.pwd))
start = source.index("lane :publish_release do |options|")
raise "publish_release-Lane nicht gefunden — Fastfile geändert?" unless start
finish = source.index('  desc "Build a release AAB', start)
raise "Lane-Ende nicht gefunden — Fastfile geändert?" unless finish
lane_src = source[start...finish].sub(/\r?\n[ \t]*end[ \t]*\r?\n?\z/m, "")
# Statischer Guard (läuft bei JEDEM Szenario): Der stabile Zweig der Lane
# (if stable ... else) darf keinerlei Tag-Löschung enthalten — der v*-Tag ist
# Versionsmarker und bleibt bewusst erhalten (kein :refs/tags/-Push, kein
# git tag -d). Der Nightly-Zweig (else) enthält legitimerweise :refs/tags/ —
# deshalb wird nur der stabile Abschnitt geprüft.
stable_start = lane_src.index("    if stable")
stable_end = lane_src.index("    else", stable_start) if stable_start
raise "Lane-Struktur unerwartet - if stable/else nicht gefunden" unless stable_start && stable_end
stable_branch = lane_src[stable_start...stable_end]
forbidden = [":refs/tags/", "tag -d", '"tag", "-d"', "delete-tag"]
hits = forbidden.select { |f| stable_branch.include?(f) }
raise "STABLE-GUARD: stabiler Zweig enthält Tag-Löschung: #{hits.join(', ')}" unless hits.empty?

eval(lane_src, TOPLEVEL_BINDING)

options = { tag: "v9.9.9-test", apk: ENV["MOCK_GH_APK"] || "dummy.apk" }
begin
  $LANE_BLOCK.call(options)
  puts "LANE_OK"
ensure
  # Kommando-Log immer ausgeben (auch bei Fehler/Rollback) - der Test prüft
  # damit z. B. das Fehlen von Tag-Lösch-Kommandos.
  $CMD_LOG.each { |c| puts "[cmdlog] #{c}" }
end
