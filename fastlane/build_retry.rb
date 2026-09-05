# frozen_string_literal: true

# Retry-Härtung für release-grade Gradle-Builds (assembleRelease /
# bundleStandardPlayRelease) gegen TRANSIENTE Fehler.
#
# Motivation (05.09.2026, Run 33952343595): Der Beta-Release v0.5.12-beta
# scheiterte im Task :app:uploadSentryProguardMappingsStandardRelease an einem
# transienten SSL-Fehler ("SSL connect error (Recv failure: Connection reset
# by peer)") — NACH 5:48 Minuten Buildzeit, ohne Release. Der Sentry-Upload
# läuft als Gradle-Task im Build (Auto-Upload der ProGuard-Mappings) und ist
# damit die einzige Netzwerk-abhängige Stufe innerhalb der sonst
# deterministischen Release-Builds. Ein Retry mit Backoff baut in dem Fall
# erneut — dank Gradle-Build-Cache kostet der zweite Versuch nur den
# fehlgeschlagenen Task, nicht die vollen Build-Minuten.
#
# bewusst ENG gefasst: Alles andere (Kompilierfehler, Testfehler, fehlende
# Keystores, falsche Signatur-Parameter) ist deterministisch und scheitert
# weiterhin beim ersten Versuch — kein Maskieren echter Bugs, keine
# verbrannten CI-Minuten durch wahllose Retries.
module BuildRetry
  # Muster, die auf transiente (Netzwerk-/API-)Fehler hindeuten und einen
  # Retry rechtfertigen. Erweiterbar — neue Muster nur mit Beleg
  # (fehlgeschlagener CI-Log) aufnehmen.
  TRANSIENT_PATTERNS = [
    /SSL connect error/i,              # sentry-cli/okhttp SSL-Handshake-Fehler (Vorfall 05.09.2026)
    /Connection reset by peer/i,       # TCP-Abbruch mid-transfer
    /Connection timed out|connect timed out/i,
    /Could not resolve host|UnknownHost/i, # DNS-Hüpfer des Runners
    /Failed to connect/i,
    /uploadSentryProguardMappings/i    # Sentry-Mapping-Upload-Task ist inhärent netzwerkabhängig;
                                       # nicht-netzwerkige Ursachen (z. B. ungültiger Token) scheitern
                                       # auch nach 3 Versuchen → Run bleibt rot, nichts wird maskiert
  ].freeze

  MAX_ATTEMPTS = 3
  BASE_DELAY_SECONDS = 10 # linearer Backoff: 10 s nach Versuch 1, 20 s nach Versuch 2

  module_function

  # True, wenn die Fehlermeldung auf einen transienten Fehler hindeutet.
  # Erwartet die Fehlermeldung (inkl. Build-Output-Tail, wie fastlane sie in
  # der Exception führt — "Exit status ... was N instead of 0" + letzte ~500
  # Zeichen des Outputs; die relevante Task-/Fehlerzeile steht am Build-Ende
  # und ist damit im Tail enthalten).
  def transient_error?(message)
    msg = message.to_s
    TRANSIENT_PATTERNS.any? { |pattern| pattern.match?(msg) }
  end

  # Führt den Block aus und wiederholt ihn bei transienten Fehlern
  # (MAX_ATTEMPTS Versuche, linearer Backoff). Deterministische Fehler
  # werden unverzüglich weitergereicht.
  def with_gradle_retry(description)
    attempts = 0
    begin
      attempts += 1
      yield
    rescue StandardError => e
      raise unless transient_error?(e.message)

      if attempts < MAX_ATTEMPTS
        wait = BASE_DELAY_SECONDS * attempts
        UI.important("#{description} fehlgeschlagen (Versuch #{attempts}/#{MAX_ATTEMPTS}, vermutlich transient) — retry in #{wait}s: #{e.message.to_s.lines.first&.strip}")
        delay(wait)
        retry
      end
      UI.error("#{description} endgültig fehlgeschlagen nach #{attempts} Versuchen (transienter Fehler blieb bestehen)")
      raise
    end
  end

  # Ausgelagert, damit der Selbsttest das Warten stubben kann (kein echtes
  # Sleep in der CI).
  def delay(seconds)
    sleep(seconds)
  end
end
