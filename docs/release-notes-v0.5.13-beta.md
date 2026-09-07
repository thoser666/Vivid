# Vivid v0.5.13-beta

**Veröffentlicht:** 7. September 2026

M1-Abschluss als Patch-Beta: Das **Core Streaming Enhancement**-Milestone ist fertig und ausgeliefert. Diese Version dokumentiert die finalen Feinheiten rund um die M1-Features (Slideshow-Widget, Twitch-OAuth-Browser-Flow mit PKCE und optionale Start-Ads) sowie die Infrastruktur-Härtungen, die in der letzten Woche eingefallen sind (Bot-PR-Vollautomatik, Orphan-Rollback, Rebase-Härtung, CodeQL-Action-Versions-Pins, Sentry-Retry).

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, 4K/HEVC, SRTLA-Bonding, adaptive Bitrate etc.) reserviert. M1 ist deshalb als Patch-Beta in der laufenden `0.5.x`-Linie ausgeliefert – nicht als Feature-Beta `v0.6.0-beta`. Siehe [RELEASE.md](../RELEASE.md) → Roadmap → Nummerierung.

## ✨ Neue Features (M1-Abschluss)

### Slideshow-Widget (komplett)
- Stream-Overlay: Bildfolge mit einstellbarer Geschwindigkeit und Pause/Weiter
- Asset-Auswahl über SAF, wie bei den anderen Bild-Widgets
- Overlay-Positionierung wie im Grid-Overlay (Ecken, Größe, Deckkraft)

### Twitch-OAuth-Browser-Flow (PKCE, komplett)
- Authorization-Code-Flow mit Proof Key for Code Exchange
- Callback-Verifizierung: Custom-URI-Schema, `state` und PKCE-Verifier werden vor dem Code-Austausch geprüft
- Token-Austausch über HTTPS mit Twitch (keine Token-Leaks, keine Log-Ausgabe)

### Optionale Start-Ads (Twitch)
- Streamer kann Start-Ads im Twitch-Backend aktivieren/deaktivieren
- Status wird im App-UI gespiegelt, keine verdeckten Netzwerk-Aktivitäten

### Release- & Infra-Prozess (sichtbar für Tester)
- **Bot-PRs laufen jetzt vollautomatisch** (Push per `AUTOMATION_TOKEN` → REST-PR-Create → automatische Pflicht-Checks), validiert durch PRs #146–#150
- **Orphan-Rollback**: Scheitert der PR-Create, wird der Bot-Branch automatisch gelöscht (kein verwaister Branch mehr)
- **Rebase-Härtung im Changelog-Mirror**: Laufen zwei Changelog-Bot-PRs parallel, wird der neuere automatisch rebased, damit kein `CONFLICTING`-PR entsteht
- **CodeQL-Action pinnen**: `init`/`analyze`/`upload-sarif` nutzen jetzt einheitliche Action-Version (keine Versions-Warnung mehr im Workflow)
- **Sentry-Upload** gegen transiente Netzwerkfehler gehärtet (Retry mit Backoff)

## 🐛 Bugfixes & Hygiene
- CodeQL-Workflow-Warnung „not all `github/codeql-action` steps use the same version“ behoben (alle Steps auf dieselbe Pin-Position)
- Keine funktionalen App-Bugfixe in dieser Version – die Nutzer-Funktionen sind bereits in v0.5.12-beta enthalten; diese Version dient dem sauberen M1-Abschluss und der Prozess-Dokumentation.

## 📊 Statistik
- **Commits seit v0.5.12-beta:** 30+
- **Geänderte Dateien:** 20+ (inkl. Dokumentation, Workflows, Selbsttests)
- **Neue Unit-/Selbsttests:** Release-Safety- und Bot-PR-Selbsttests erweitert (Rebase, Orphan-Rollback, CodeQL-Pins)
- **PARITY-Status:** M1 (Core Streaming Enhancement) ✅ ausgeliefert

## 🔗 Links
- [GitHub Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.13-beta)
- [Vollständiger Vergleich](https://github.com/thoser666/Vivid/compare/v0.5.12-beta...v0.5.13-beta)

> **Nächster Schritt:** Nach M1-Abschluss ist der Fokus auf **M2 (Advanced Camera & Video)** gerichtet – Untertitel (Speech-to-Text), OBS Audio-Levels, 4K/60fps + HEVC sowie Replays (Record-to-Disk). Der Streaming-Erweiterungs-Bucket (v0.6.0) ist der nächste große Feature-Bucket nach M2.
