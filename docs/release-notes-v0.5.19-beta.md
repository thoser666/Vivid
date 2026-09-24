# Release Notes: Vivid v0.5.19-beta

**Release Date:** 2026-09-24
**Version:** 0.5.19-beta (versionCode 5192, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest“ · Obtainium · eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut (Hotfix):** Der **erste durch Sentry identifizierte Produktions-Crash** (`TEXT-INFO-WIDGET-REGEX-ICU`) steckte seit `v0.5.14` in jedem Release — inklusive der gestrigen v0.5.18-beta. Der Fix ist minimal, verhaltensidentisch und im Pre-Push-Gate gegen die ganze Crash-Klasse abgesichert; deshalb der schnelle Hotfix statt eines Wartens auf den nächsten regulären Cut. Seit `v0.5.18-beta` sind 25 Commits zusammengekommen (davon der Großteil Ops-/Automatisierung, die Nutzer nichts spüren).

## 🎉 Highlights

### 💥 Text-Info-Widget-Crash behoben (Sentry-Befund, seit v0.5.14 im Feld)
- Androids **ICU-Regex-Engine ist strenger als die JVM**: Das Geo-Pattern `{road}/{city}/{country}` des Text-Info-Widgets enthielt ein unmaskiertes schließendes `}` — auf betroffenen Geräten warf `Pattern.compile` beim Laden des ViewModels eine `PatternSyntaxException` (`ExceptionInInitializerError` über die Hilt-Factory) und crashte beim Rendern des Streaming-Overlays. JDK-basierte Unit-Tests sahen den Fehler prinzipbedingt **nie**.
- **Beide** betroffenen Patterns maskiert (`TextInfoWidgetViewModel` + `WidgetVariableResolver` — ohne den zweiten Fix wäre der `<clinit>` eine Zeile später erneut gestorben); verhaltensidentisch, belegt durch Regressionstests.
- **CrashAdvisory-Eintrag** `TEXT-INFO-WIDGET-REGEX-ICU` (versionCode 5144–5182, Kill-Switch: Text-Widget deaktivieren): Betroffene Nutzer auf v0.5.14–v0.5.18 sehen die rote 💥-Advisory mit dem Workaround — und nach diesem Update schließt sich die Range sauber (ab 5192 ist der Crash behoben).
- **Neuer statischer ICU-Regex-Guard** im Pre-Push-Gate (JVM-Tests können diese Crash-Klasse nie fangen): Das gesamte Repo wird jetzt gegen unmaskierte Regex-Klammern geprüft, Selbsttest F1–F6 mit dem Original-Crash-Pattern als Fixture.

## 🔒 Sicherheit & Wartung

- **Emulator-Gate vor jedem Release** (Neu in dieser Pipeline): Die instrumentierte UI-Suite (`connectedStandardDebugAndroidTest` auf API-34-Emulator) läuft jetzt beim **Tag-Push** (Pflicht-Leg ubuntu-x86_64, zusätzlich macos-arm64 experimentell) und direkt **vor dem Stable-Publish** (`release_github`-Dispatch) — getestet wird exakt das Commit, das veröffentlicht wird.
- **Sentry-Report → automatisches Issue mit Dringlichkeit:** Sentry-Issues landen per GitHub-Integration als GitHub-Issue und werden sofort mit `sentry` + `severity:critical/high/medium/low` gelabelt (Crash-Signale im Stacktrace erzwingen mindestens `high`); `critical` erhält zusätzlich das `crash`-Label samt Verweis auf den CrashAdvisoryRegistry-Prozess im Issue-Body.
- **Wöchentlicher Sentry-Ops-Review-Workflow** (Mo 06:30 UTC): Stats-Guard mit Lese-Token + Health-Probe als sanktionierter Cron-Nutzer, Issue-Automation bei WARN/FEHLER mit Dedup/Auto-Close; Marker- und Severity-Parsing mehrfach im Feld nachgeschärft (Konfig-SKIP-Muster, markdown-tolerantes Severity-Parsing).
- fdroidserver-Closure automatisch gegen PyPI-Drift nachgeführt (etabliertes Verfahren).

## 📥 Installation & Update

| Kanal | Wie |
|---|---|
| **GitHub Releases** | Neuestes Release („Latest“) — `app-standard-release.apk` oder `app-foss-release.apk` |
| **Obtainium** | Zieht das Latest-Release automatisch |
| **Eigenes F-Droid-Repo** | Wöchentlich aus den Stable-Releases gebaut (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren). Der Hotfix ist ab v0.5.14 direkt installierbar (versionCode 5192 > 5182).
- **FOSS-Hinweis:** Der FOSS-Build enthält keinen Sentry — der ICU-Regex-Fix ist flavor-unabhängig und wirkt identisch in beiden Builds.

## 🔗 Referenzen

- Sentry-Ops: [docs/sentry-stats.md](sentry-stats.md) · Alerts→Issues: [docs/sentry-alerts.md](sentry-alerts.md)
- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
