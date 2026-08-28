# 🚀 Release-Notes v0.5.9-beta

| | |
|---|---|
| **Version** | `0.5.9-beta` (versionCode `5019`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.9-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](RELEASE.md#-referenz-fingerprint-release-key)) |

## Was ist neu in v0.5.9-beta

### ☕ CI-JDK-Upgrade auf Version 25 (LTS)

Alle CI-Workflows nutzen jetzt **JDK 25** (neuestes LTS) statt JDK 17:

| Komponente | Vorher | Nachher |
|------------|--------|---------|
| CI (GitHub Actions) | JDK 17 | **JDK 25** |
| Pre-push-guard | JDK 21 (Fallback) | **JDK 25 (bevorzugt)** |
| README.md | JDK 17 (minimum) | **JDK 25 (empfohlen)** |

**Betroffene Workflows:**
- `android-ci.yml`
- `android_fastlane.yml`
- `release-pipeline.yml`
- `security-codeql.yml`
- `security-snyk.yml`

**Kompatibilität verifiziert:**
- Gradle 9.7.0: ✅ unterstützt JDK 25 (seit 9.1.0)
- AGP 9.3.2: ✅ kompatibel mit JDK 25
- Kotlin 2.2.20: ✅ kompatibel mit JDK 25

### 🔧 Pre-push-guard: Auto-Erkennung

Der Pre-push-guard erkennt automatisch das verfügbare JDK:
1. **JDK 25** (bevorzugt, neuestes LTS)
2. **JDK 21** (Fallback)
3. **JDK 17** (Minimum)

### 📝 Dokumentation

- README.md: JDK 25 als empfohlene Version dokumentiert
- RELEASE.md: Neue Sektion "CI-JDK-Upgrade auf Version 25 (LTS)"
- Reproducible Builds: JDK/OS-Verifikation auf temurin 25/Ubuntu vs. Oracle 25/Windows aktualisiert

## Änderungen im Detail

| Datei | Änderung |
|-------|----------|
| `.github/workflows/android-ci.yml` | `java-version: '25'` |
| `.github/workflows/android_fastlane.yml` | `java-version: '25'` |
| `.github/workflows/release-pipeline.yml` | `java-version: '25'` |
| `.github/workflows/security-codeql.yml` | `java-version: '25'` |
| `.github/workflows/security-snyk.yml` | `java-version: '25'` |
| `scripts/pre-push.sh` | JDK 25 als bevorzugte Version in Auto-Erkennung |
| `README.md` | JDK 25 als empfohlene Version dokumentiert |
| `RELEASE.md` | Neue Sektion + aktualisierte Referenzen |

## Bekannte Einschränkungen

- Keine bekannten Einschränkungen mit JDK 25
- Alle Tests und Lints laufen grün

## Testvorgehen

1. **Pre-push-guard:** `bash scripts/pre-push.sh` — zeigt `JAVA_HOME gesetzt: .../jdk-25`
2. **CI:** Alle Workflows laufen mit JDK 25 (Setup-Java-Action)
3. **Unit-Tests:** `./gradlew testDebugUnitTest` — alle grün
4. **Lint:** `./gradlew lintDebug` — warningsAsErrors grün

## Nächste Schritte

- Play-Upload vorbereiten (P0-P2-Checkliste in RELEASE.md)
- Echte Screenshots für Play-Listing aufnehmen
- F-Droid-Hauptrepo einreichen (Post-Beta)
