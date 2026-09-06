# Vivid v0.5.13-beta\r
\r
**Veröffentlicht:** 6. September 2026\r
\r
Patch-Beta in der laufenden Minor-Linie (kein Roadmap-Bucket abgeschlossen — `v0.6.0` bleibt dem Streaming-Erweiterungs-Bucket reserviert, siehe [RELEASE.md](../RELEASE.md)).\r
\r
## ✨ Neue Features\r
\r
### Twitch-Integration\r
- **Verschlüsselte Token-Persistenz:** Twitch-OAuth-Token werden jetzt AES/GCM-verschlüsselt über den Android-Keystore persistiert statt im Klartext-DataStore ([a3d2001])\r
- **Automatischer Token-Refresh:** Abgelaufene Tokens werden transparent erneuert; 401-Antworten lösen einen Refresh-Retry aus ([a3d2001])\r
\r
## 🔒 Security\r
- **Token-at-Rest-Verschlüsselung:** Neuer `TokenCipher`-Contract (`AesGcmTokenCipher` + `AndroidKeystoreTokenCipher`) — Key-Handling komplett im Android-Keystore, keine Klartext-Token mehr auf dem Gerät ([a3d2001])\r
- **CodeQL-Action-Pins vereinheitlicht:** Alle `github/codeql-action`-Steps (init/analyze/upload-sarif) pinnen dieselbe v4.37.8-Commit-SHA — behebt die GitHub-Warnung zu inkonsistenten Action-Versionen ([e2f0788])\r
\r
## 🛠️ CI & Infrastruktur\r
- **Bot-PR-Vollautomatik:** Bot-Branches werden per `AUTOMATION_TOKEN` (User-Credential statt `GITHUB_TOKEN`) gepusht — Pflicht-Checks laufen auf Bot-PRs automatisch; PR-Erstellung via REST statt GraphQL; Classic-PAT statt Fine-grained ([95dc1dd], [4356da2])\r
- **Orphan-Rollback:** Scheitert der PR-Create in einem Bot-Workflow, wird der Bot-Branch automatisch gelöscht — keine verwaisten Branches mehr ([2ddfcee])\r
- **Changelog-Mirror-Rebase:** Bot-PRs werden vor dem PR-Create gegen ein weitergelaufenes develop gerebt; bei Konflikt wird der Changelog neu generiert — keine `CONFLICTING`-Bot-PRs mehr ([f050a90])\r
- **Verify-Reproducibility-Fix:** Nightly-Verify lädt das flavor-korrekte `app-standard-release.apk` statt `app-release.apk` ([719cd07])\r
- **Emulator-Matrix:** Instrumentierte UI-Tests laufen auf beiden Runner-Architekturen (ubuntu-x86_64 mit KVM, macos-arm64 experimentell) ([2a48cb1])\r
\r
## 📊 Quality & Testing\r
- **Robolectric-Compose-Coverage-Runde 2:** 27 neue UI-Tests für Settings-Screens und Widgets; Gesamt-Coverage **47,2 % → 69,3 %** (LINE, Kover-Merge aller 9 Module) ([009972a])\r
- **Nebenfund behoben:** Doppelter `verticalScroll` im SettingsCameraScreen (Production-Bug unter Infinity-Constraints) ([009972a])\r
\r
## 📊 Statistik\r
- **Commits seit v0.5.12-beta:** 30\r
- **Coverage:** 47,2 % → 69,3 % (LINE, Kover-Merge)\r
- **Badges:** OpenSSF Best Practices **Passing** ✅ (Silver-Anlauf läuft)\r
\r
## 🔗 Links\r
- [GitHub Release](https://github.com/thoser666/Vivid/releases/tag/v0.5.13-beta)\r
- [Vollständiger Vergleich](https://github.com/thoser666/Vivid/compare/v0.5.12-beta...v0.5.13-beta)\r
\r
---\r
\r
*Hinweis: Die meisten dieser Änderungen sind Infrastruktur-/Security-Arbeit — das sichtbarste Nutzer-Feature (verschlüsselte Twitch-Token) wirkt transparent im Hintergrund. Das nächste Feature-Beta ist `v0.6.0-beta` (Streaming-Erweiterung: RIST/WHIP/RTMP/4K/SRTLA, Roadmap-reserviert).*\r
