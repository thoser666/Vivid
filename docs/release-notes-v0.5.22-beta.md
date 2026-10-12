# Release Notes: Vivid v0.5.22-beta

**Release Date:** 2026-10-11
**Version:** 0.5.22-beta (versionCode 5222)
**Channel:** Beta

> Reserviert für `v0.6.0` bleibt das Streaming-Erweiterungs-Bucket (RIST/WHIP/RTMP).

## Changes since v0.5.21-beta
- **Pomodoro In-App-Timer** (`6ed8bc9`) — Settings-Kategorie + Timer-Screen
- **Sentry EOFException filter** (`0c83392`) — `java.io.EOFException` als erwarteter Transport-Abbruch
- **CI/Emulator-Gate** (`e9a3c11`, `522038bc`) — `verify` liest AGP-elements, Fail-Loud, Staging-Skripte (`set -eu` statt `pipefail`)
- Dependency bumps (roborazzi, Kotlinx-Kover, Guava 33.7.2-jre, Bouncy Castle 1.86)
- `VERSION` auf `0.5.22-beta` gehoben (vorher `0.5.20-beta` — stale gegen Tag `v0.5.21-beta`)
