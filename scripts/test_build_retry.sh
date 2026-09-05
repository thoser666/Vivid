#!/usr/bin/env bash
# Regressionstest: Retry-Härtung für release-grade Gradle-Builds
# (fastlane/build_retry.rb + Verdrahtung im Fastfile).
#
# Geprüfte Szenarien:
#   T1 transient, dann Erfolg   → Block läuft erneut, Ergebnis zurückgegeben
#   T2 deterministischer Fehler → KEIN Retry (sofortiger Raise, genau 1 Versuch)
#   T3 transient, dauerhaft     → Raise nach MAX_ATTEMPTS, Backoff-Delays 10s/20s
#   T4 Muster-Klassifikation    → jedes TRANSIENT_PATTERNS-Element matcht
#   T5 Verdrahtung (statisch)   → alle release-grade gradle-Aufrufe im Fastfile
#                                 sind gewrappt (assembleRelease ×2,
#                                 bundleStandardPlayRelease), debug/test/lint
#                                 bleiben bewusst ungewrappt
#   T6 message-lose Exception   → kein Crash in transient_error? (to_s-Fallback)
#
# Läuft im CI (release-pipeline.yml, Job "Self-Test Build-Retry (Hardening)")
# und lokal: bash scripts/test_build_retry.sh  (Exit 0 = grün)
# Plain Ruby, keine Fastlane-Abhängigkeit (UI wird gestubbt) — bewusst wie die
# anderen Selbsttests ohne Bundler lauffähig.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR/.." || exit 1

echo "▶ [test_build_retry] Szenarien T1–T6 gegen fastlane/build_retry.rb"

RUBY_SCRIPT=$(cat <<'RUBY'
require File.expand_path("fastlane/build_retry", Dir.pwd)

# UI-Stub (build_retry nutzt fastlane's UI — im Selbsttest ersetzt)
module UI
  @log = []
  class << self
    attr_reader :log
    def important(msg) = @log << "important: #{msg}"
    def error(msg)     = @log << "error: #{msg}"
    def message(msg)   = @log << "message: #{msg}"
  end
end

# Sleep-Stub: Delays aufzeichnen, nicht wirklich warten
DELAYS = []
BuildRetry.define_singleton_method(:delay) { |s| DELAYS << s }

TRANSIENT_MSG = "Exit status of command 'gradlew assembleRelease' was 1 instead of 0.\n" \
                "Execution failed for task ':app:uploadSentryProguardMappingsStandardRelease'\n" \
                "error: API request failed\n" \
                "1: [35] SSL connect error (Recv failure: Connection reset by peer)"

DETERMINISTIC_MSG = "Execution failed for task ':app:compileReleaseKotlin'\n" \
                    "e: error: unresolved reference: foo"

failures = 0
check = ->(label, cond) do
  if cond
    puts "  ✅ #{label}"
  else
    puts "  ❌ #{label}"
    failures += 1
  end
end

# T1: transient, dann Erfolg
attempts = 0
result = BuildRetry.with_gradle_retry("Test-Build") do
  attempts += 1
  raise TRANSIENT_MSG if attempts == 1
  "apk-built"
end
check.call("T1 retry führt Block erneut aus (2 Versuche)", attempts == 2)
check.call("T1 Ergebnis wird zurückgegeben", result == "apk-built")
check.call("T1 wichtige Warnung geloggt", UI.log.any? { |l| l.include?("Versuch 1/3") })

# T2: deterministischer Fehler — kein Retry
attempts = 0
t2_raised = false
begin
  BuildRetry.with_gradle_retry("Test-Build") do
    attempts += 1
    raise DETERMINISTIC_MSG
  end
rescue => e
  t2_raised = true
  t2_msg = e.message
end
check.call("T2 deterministischer Fehler: genau 1 Versuch", attempts == 1)
check.call("T2 deterministischer Fehler: Exception weitergereicht", t2_raised)
check.call("T2 deterministischer Fehler: Original-Nachricht erhalten", t2_msg.to_s.include?("unresolved reference"))

# T3: transient, dauerhaft → Raise nach MAX_ATTEMPTS, Backoff aufgezeichnet
attempts = 0
t3_raised = false
DELAYS.clear
begin
  BuildRetry.with_gradle_retry("Test-Build") do
    attempts += 1
    raise TRANSIENT_MSG
  end
rescue => e
  t3_raised = e.message.include?("SSL connect error")
end
check.call("T3 dauerhaft transient: MAX_ATTEMPTS Versuche", attempts == BuildRetry::MAX_ATTEMPTS)
check.call("T3 dauerhaft transient: Exception nach letztem Versuch", t3_raised)
check.call("T3 Backoff-Delays linear (10s, 20s)", DELAYS == [10, 20])
check.call("T3 finaler error-Eintrag geloggt", UI.log.any? { |l| l.start_with?("error:") && l.include?("endgültig fehlgeschlagen") })

# T4: jedes Muster klassifiziert als transient
sample_msgs = {
  /SSL connect error/i             => "error: [35] SSL connect error (Recv failure: Connection reset by peer)",
  /Connection reset by peer/i      => "TCP-Fehler: Connection reset by peer",
  /timed out/i                     => "java.net.SocketTimeoutException: connect timed out",
  /Could not resolve host/i        => "fatal: Could not resolve host: downloads.example.org",
  /Failed to connect/i             => "Failed to connect to downloads.example.org port 443",
  /uploadSentryProguardMappings/i  => "Execution failed for task ':app:uploadSentryProguardMappingsStandardRelease'"
}
mismatch = sample_msgs.reject { |re, msg| BuildRetry.transient_error?(msg) }
check.call("T4 alle Muster klassifizieren ihre Beispielmeldung als transient", mismatch.empty?)

# T6: message-lose Exception crasht nicht
no_msg_ok = false
begin
  BuildRetry.transient_error?(StandardError.new(nil))
  no_msg_ok = true
rescue => e
  puts "  (T6 raised: #{e.class}: #{e.message})"
end
check.call("T6 message-lose Exception wird sicher klassifiziert", no_msg_ok)

exit(failures.zero? ? 0 : 1)
RUBY
)

RUBY_TMP="$(mktemp)"
trap 'rm -f "$RUBY_TMP"' EXIT
printf '%s' "$RUBY_SCRIPT" > "$RUBY_TMP"
if ! ruby "$RUBY_TMP"; then
  echo "❌ Ruby-Szenarien fehlgeschlagen"
  exit 1
fi

echo "▶ [test_build_retry] Statische Verdrahtungs-Guards (T5)"

FAIL=0
guard() {
  local label="$1" cond="$2"
  if eval "$cond"; then
    echo "  ✅ $label"
  else
    echo "  ❌ $label"
    FAIL=1
  fi
}

# require im Fastfile vorhanden
guard "require_relative \"build_retry\" im Fastfile" \
  'grep -q "require_relative \"build_retry\"" fastlane/Fastfile'

# Alle release-grade Aufrufe gewrappt: assembleRelease (2×), bundleStandardPlayRelease (1×)
guard "assembleRelease-Aufrufe ×2 gewrappt" \
  'test "$(grep -c "BuildRetry.with_gradle_retry" fastlane/Fastfile)" -eq 3'
guard "assembleRelease ×2 + bundleStandardPlayRelease ×1 in Retry-Blöcken" \
  'test "$(grep -c "task: \"assembleRelease\"" fastlane/Fastfile)" -eq 2 && test "$(grep -c "task: \"bundleStandardPlayRelease\"" fastlane/Fastfile)" -eq 1'

# Bewusst NICHT gewrappt (deterministisch): test/lint/debug-Builds
guard "testDebugUnitTest ungewrappt" \
  '! grep -B4 "task: \"testDebugUnitTest\"" fastlane/Fastfile | grep -q "with_gradle_retry"'
guard "lintDebug ungewrappt" \
  '! grep -B4 "task: \"lintDebug\"" fastlane/Fastfile | grep -q "with_gradle_retry"'
guard "assembleDebug ungewrappt" \
  '! grep -B4 "task: \"assembleDebug\"" fastlane/Fastfile | grep -q "with_gradle_retry"'

# SENTINEL: bestehende Retry-Härtung der Release-Publizierung nicht beschädigt
guard "gh-release-Retry (publish_release) unverändert vorhanden" \
  'test "$(grep -c "gh release create fehlgeschlagen (Versuch" fastlane/Fastfile)" -eq 2'

if [ "$FAIL" -ne 0 ]; then
  echo "❌ Verdrahtungs-Guards fehlgeschlagen"
  exit 1
fi

echo "✅ [test_build_retry] Alle Szenarien grün"
