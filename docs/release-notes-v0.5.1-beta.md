# 🚀 Release-Notes v0.5.1-beta

| | |
|---|---|
| **Version** | `0.5.1-beta` (versionCode `5012`, deterministisch aus dem Tag) |
| **Tag** | `v0.5.1-beta` |
| **Branch** | `develop` |
| **Veröffentlicht** | über `fastlane release_beta` → CI baut signiert und publiziert als GitHub-Release (Latest) |
| **Signiert mit** | Release-Key (SHA-256 `b31b8119…`, siehe [RELEASE.md](../RELEASE.md) → Referenz-Fingerprint) |
| **Installation** | Obtainium (Latest, kein Pre-Release-Flag nötig) · direkt über die GitHub-Release-Seite |

---

## 🎉 Das Highlight: Sentry-Privacy-Härtung — die letzte datenschutzrelevante Änderung vor dem Play-Upload

Seit `v0.5.0-beta` (17.08.2026) liegt der Fokus auf **Datenschutz und Absicherung**: Die Crash-Berichterstattung (Sentry) wurde so umgebaut, dass sie die Play-Policy „User Data“ sauber erfüllt — und der Streamer hat die volle Kontrolle über das, was beim Absturz verlassen darf. Diese Beta ist die **letzte Privacy-relevante Änderung, bevor Vivid in die Play Console geht** — genau das soll mit Testern validiert werden.

## ✨ Was neu ist

### 1. Keine Crash-Screenshots mehr

`io.sentry.attach-screenshot` ist **deaktiviert**: Bei einem Absturz wird kein Screenshot mehr mitgesendet. Damit können weder die Stream-Vorschau, noch der Chat oder sichtbare Zugangsdaten (Token, Client-ID) in einem Crash-Report landen.

### 2. Neuer Opt-out-Toggle „Fehlerberichte senden (Sentry)“

In den Einstellungen (Abschnitt **„Datenschutz & Fehlerberichte“**) kann die Crash-Berichterstattung **komplett abgeschaltet** werden (Default: **an**). Der Schalter wirkt **live pro Event** — wird er ausgeschaltet, verwirft die App jedes weitere Event (`beforeSend` → `null`), ohne Neustart.

### 3. Kein PII-Sampling

`sendDefaultPii` ist deaktiviert: **Keine IP-Adresse, kein Gerätename** wird mit den Crash-Daten erhoben. Was genau gesendet wird, ist in [PRIVACY.md](../PRIVACY.md) dokumentiert — abgestimmt auf die Data-Safety-Antworttabelle in RELEASE.md.

### 4. Der Nachweis ist im Build verankert (R8-Mapping-Check)

Damit die Opt-out-Logik nie stillschweigend vom Release-Build wegoptimiert wird, prüft jetzt das **Pre-Push-Gate + die Fastlane-CI** in **beiden Release-Kanälen** (APK `release` + AAB `playRelease`) per R8-Mapping, dass der Callback in `io.sentry.SentryClient` eingebettet ist. `PRE_PUSH_RELEASE=1` baut dafür `assembleRelease` **und** `bundlePlayRelease`.

### 5. Runtime-Dependency gepflegt

- **okhttp 5.3.2 → 5.4.0** (in der App verbaute HTTP-Bibliothek, u. a. Twitch-API/EventSub)
- Weitere Dependabot-Bumps (Actions checkout 7.0.1, cache 6.1.0, upload-artifact 7.0.1; mockito-core 5.23.0, junit-jupiter-engine 6.1.3)

---

## 🔧 Weitere Änderungen seit v0.5.0-beta

- **Play-Vorbereitung (Doku + Tooling):** [PRIVACY.md](../PRIVACY.md) + GitHub-Pages-Hosting (`thoser666.github.io/Vivid/privacy/`), IARC-Content-Rating-Vorlage, Data-Safety-Antworttabelle, Play-Console-Registrierungs-Guide, Store-Strategie (Play vs. F-Droid), `prepare_play_secrets.sh`, Play-Metadaten-Gate, Screengrab-Lane `capture_play_screenshots`
- **CI-Härtung:** Zwei-Kanal-Mapping-Check (Pre-Push + Fastlane-CI), Play-Metadaten-Selbsttest, sentry-optout-Checker-Selbsttest (9 Fälle) in android-ci.yml

## 🧪 Testschwerpunkte für Beta-Tester

1. **Opt-out-Toggle:** Einstellungen → „Fehlerberichte senden (Sentry)“ **aus** → Absturz provozieren → in der Sentry-Console erscheint **kein** Event (bzw. mit Toggle **an** erscheint es)
2. **Kein Screenshot-Leak:** Crash-Report (Toggle an) enthält **keinen** Screenshot-Attach
3. **Kein PII:** Crash-Report enthält keine IP-/Gerätename-Felder
4. **Regressionscheck:** Chat-Bot (Bot-Modus + KI-Modus), Overlay und Widgets funktionieren weiterhin — die Sentry-Änderung berührt nur die Crash-Erfassung

## ⚠️ Bekannte Einschränkungen

- **Cross-Track:** Nutzer von `v0.6.0-alpha` (versionCode 6001) müssen für `v0.5.1-beta` (5012) die App deinstallieren (Android-Downgrade-Block); wer bereits auf `v0.5.0-beta` ist, kann direkt updaten
- **Play-Upload** steht noch aus (P0–P2-Checkliste, Issue #116) — diese Beta wird weiterhin über GitHub verteilt

---

*Datenschutz: [PRIVACY.md](../PRIVACY.md) · Release-Pipeline: [RELEASE.md](../RELEASE.md) · Feature-Tracking: [PARITY.md](../PARITY.md)*
