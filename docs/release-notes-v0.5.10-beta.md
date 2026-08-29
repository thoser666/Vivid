# 🚀 Release-Notes v0.5.10-beta

| | |
|---|---|
| **Version** | `0.5.10-beta` (versionCode `5020`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.10-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md#referenz-fingerprint-release-key)) |

## Was ist neu in v0.5.10-beta

### 🎭 Third-Party-Emotes: Settings-UI

Drei individuelle Toggle-Schalter für die Third-Party-Emote-Quellen im Chat-Overlay:

| Quelle | Beschreibung |
|--------|--------------|
| **BetterTTV (BTTV)** | Community-Emotes von BetterTTV |
| **FrankerFaceZ (FFZ)** | Community-Emotes von FrankerFaceZ |
| **7TV** | Community-Emotes von 7TV |

**Features:**
- Jede Quelle kann einzeln aktiviert/deaktiviert werden
- Einstellungen werden via DataStore persistiert
- Bei Quellen-Änderungen wird der Cache geleert und die Emotes neu geladen
- Vollständige i18n-Unterstützung (de/en/fr)

**Speicherort:** Einstellungen → Overlays & Widgets → Third-Party-Emotes

### 🔧 Technische Änderungen

| Komponente | Änderung |
|------------|----------|
| **AppSettings** | 3 neue Boolean-Felder: `emotesBttvEnabled`, `emotesFfzEnabled`, `emotes7tvEnabled` |
| **SettingsRepository** | DataStore-Keys + `updateEmoteSettings()` |
| **SettingsViewModel** | 3 Toggle-Handler + Save-Integration |
| **SettingsOverlaysScreen** | Neue Sektion „Third-Party-Emotes" mit 3 Switches |
| **ChatOverlayViewModel** | Settings → `ThirdPartyEmoteService.setActiveSources()` + Cache-Invalidierung |
| **i18n** | `overlays_emotes_*` (de/en/fr) |
| **Tests** | `SettingsViewModelTest` +2 (Toggle-UI-State, Save-Persistenz) |

## Änderungen im Detail

| Datei | Änderung |
|-------|----------|
| `domain/.../AppSettings.kt` | 3 neue Boolean-Felder für Emote-Quellen |
| `core/.../SettingsRepository.kt` | DataStore-Keys + `updateEmoteSettings()` |
| `feature-settings/.../SettingsViewModel.kt` | 3 Toggle-Handler + Save-Integration |
| `feature-settings/.../SettingsOverlaysScreen.kt` | Neue Sektion „Third-Party-Emotes" |
| `feature-chat/.../ChatOverlayViewModel.kt` | Settings → Service Verdrahtung |
| `feature-settings/src/main/res/values*/strings.xml` | i18n-Strings (de/en/fr) |
| `feature-settings/src/test/.../SettingsViewModelTest.kt` | 2 neue Tests |
| `PARITY.md` | Zeile „Emotes" aktualisiert + Aktualisierungslog |

## Bekannte Einschränkungen

- Emote-Picker und Emote-Sticker sind noch nicht implementiert (offene Tasks in PARITY.md)
- Third-Party-Emotes werden nur im Chat-Overlay angezeigt, nicht im Bot

## Testvorgehen

1. **Unit-Tests:** `./gradlew testDebugUnitTest` — alle grün
2. **Lint:** `./gradlew lintDebug` — warningsAsErrors grün
3. **I18n-Check:** `bash scripts/check_i18n.sh` — alle 3 Sprachen vollständig
4. **Pre-push-guard:** `bash scripts/pre-push.sh` — alle Checks grün

## Nächste Schritte

- Emote-Picker für schnellen Zugriff auf beliebte Emotes
- Emote-Sticker für erweiterte Ausdrucksmöglichkeiten
- Play-Upload vorbereiten (P0-P2-Checkliste in RELEASE.md)
