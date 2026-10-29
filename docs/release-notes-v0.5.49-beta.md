# Release Notes: Vivid v0.5.49-beta

**Release Date:** 2026-10-29
**Version:** 0.5.49-beta (versionCode 5494, nach `major*1_000_000 + minor*1_000 + patch*10 + 4`)
**Channel:** Beta (GitHub Releases → „Latest" → Obtainium → eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `VERSION` stand seit dem 12.10.2026 auf `0.5.23-beta`, während die Beta-Spur im Feld bereits `0.5.48-beta+5484` trug (Sentry-Events vom 26.10., `com.vivid@0.5.48-beta+5484`). Der Release-Workflow nimmt `version`/`version_code` als manuelle Inputs — die Datei wurde deshalb nicht mitgezogen und lief der ausgelieferten Linie hinterher. Dieser Schnitt zieht sie nach: **0.5.49-beta = versionCode 5494 > 5484**, also ein gültiges Update über die zuletzt beobachtete Beta. `0.6.0` bleibt weiterhin dem offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, 4K/HEVC, Game-Controller, Streamer-Browser) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** 43 Commits an Funktions-, Test- und Doku-Arbeit seit `v0.5.23-beta` (Tag 25.10.; plus die zwei Release-Commits selbst), darunter ein **neuer Szenen-Kompositor** (#330), der **experimentelle 60-FPS-Modus** (#322) und **zwei Fixes an Sentry-Pfaden** — ein Fatal (#324) und ein High-Severity-Error (#350). Alle vier waren bis jetzt ausschließlich auf `develop` bzw. in Nightlies erreichbar.

## 🎉 Highlights

### 🎬 Szenen-Kompositor: versionierte Diagramme, ein Encoder für RTMP + MP4 (#330)
- **Szenen sind jetzt ein versioniertes Diagramm** (`SceneComposition` mit Quellen, Layern, Transform und `CUT`/`FADE`-Übergang) statt einer festen Quellenauswahl; mehrere Layer dürfen sich denselben Producer teilen.
- **Ein Encoder für alle Ziele:** GLES/FBO-Composition rendert Quellen zu einem Bild, das sowohl an die RTMP-Ziele als auch an den MP4-Muxer geht — kein zweiter Aufnahmepfad mehr.
- **Encoder-freie Quellen:** Camera2, UVC und MediaProjection liefern direkt in die Szene (ohne parallelen Encoder), inklusive Kamera-Konkurrenz-Behandlung und Surface-Aufräumen.
- **Compose-Editor** mit Canvas, Gesten (Verschieben/Skalieren/Drehen), Undo/Redo und Quellen-Picker (`SceneEditorScreen`/`SceneEditorCanvas`).
- **Steuerung folgt der aktiven Kamera:** Die Fassade `ActiveCameraControls` hängt an der Kamera der Szenen-Quelle — auch wenn die Kamera erst **nach** dem Start der Szenen-Session dazukommt, und sie fällt beim Entfernen der Quelle auf neutrale Antworten zurück. Beide Richtungen sind als Engine-Regressionstest abgesichert.
- Gelandet über PR [#355](https://github.com/thoser666/Vivid/pull/355) (Squash `f2dcf962`) aus dem Fork-Beitrag [#330](https://github.com/thoser666/Vivid/issues/330); die Konflikte wurden zugunsten der #324-Guard-Nähte aufgelöst.

### 🚀 60-FPS-Kameramodus (experimentell, opt-in, #322)
- Neuer Einstellungs-Abschnitt **Kamera-Modus**: „Standard" (Default) und „60 FPS — experimentell"; Umschalten ist während Vorbereitung, Aufnahme und Stream gesperrt, und die Aktivierung prüft Kamera, Auflösung, Android-Version und Hardware-Codecs vorab. Der angewandte Modus und die gemessene Encoder-FPS stehen in den Settings.
- Pfad: Constrained-High-Speed-Camera2 → AVC-Encoder → Hardware-Decoder → bestehende GL-Pipeline inkl. Filter und Overlays → Aufnahme/Stream. Fehlgeschlagene Vorbereitung fällt sichtbar auf den Standardpfad zurück, ein Laufzeitfehler stoppt mit Hinweis.
- **Orientierungs-Fix:** Das Kamerabild wird beim Streamstart nicht mehr rotiert oder gestreckt (Decoder-Orientierung nach `RootEncoder`-GL-Vorbereitung, Reverse-Landscape-Korrektur vor den Filtern).
- Weniger Buffering im Bridge, Aufnahme-Wechsel ohne AAC-Neustart, kompakte lokale Preview-Labels (Modus, Bitrate, Uptime) statt des großen Diagnose-Panels, Bestätigung für den Stop-Button.

### 🛡️ Zwei Sentry-Pfade geschlossen
- **#324 / VIVID-3W (Fatal):** `CameraCaptureSessionImpl.checkNotClosed` warf uncaught, wenn ein Control-Dispatch die von `stopIdlePreview()` gerade geschlossene Session traf. Der neue `CameraSessionGuard` ordnet `dispatch` gegen `beginClose`/`endClose` (der Monitor wird bewusst nicht über `closeCamera()` gehalten, um keinen Deadlock mit dem Camera-Handler-Thread zu bauen) und schluckt **genau** die „Session has been closed"-`IllegalStateException`; jede andere läuft weiter.
- **#350 / VIVID-46 (High):** `Idle camera: Configuration failed` feuerte weiter, obwohl die zugehörige Vorschau längst ersetzt war — der abgeschnittene Configure-Lauf einer verworfenen Instanz meldete sich ohne Identitätsprüfung weiter und konnte den Kamera-Zustand der laufenden Session vergiften. Die Callbacks prüfen jetzt ihre Instanz, und ein Configure-Fehler der aktuellen Vorschau löst **genau einen** `reOpenCamera` aus (Budget pro erzeugter Vorschau, Ausführung außerhalb des Camera-Callback-Threads). Der Reopen läuft durch `onCameraOpened` und stellt Kamera-Zustand und Steuerung wieder her.
- **Sentry-Build-Identität:** `io.sentry.environment` trägt jetzt `development` (Debug/CI) bzw. `production` (Store) — Feld- und Test-Builds sind auf Sentry-Ebene unterscheidbar.

## 🧪 Tests & Guardrails
- Neue Regressionstests für die Szenen-Steuerung (kamerafreie Session **und** später hinzukommende Kamera-Quelle), für den Identitätscheck der Idle-Preview und für den einmaligen Reopen — alle mutationsgeprüft (Fix bzw. Verdrahtung zurückgebaut → genau die betroffenen Fälle rot).
- `docs/sentry-issues.md` §5 hält fest, dass **Timber-Log-Signaturen eigene Sentry-Gruppen** sind: gleicher Titel oder gleiche Log-Zeile belegen kein Duplikat, verschiedene SIDs werden als Geschwister verlinkt statt geschlossen (sonst unterdrückt der Watchdog weitere Meldungen derselben Gruppe).
- Kontributions-Vertrag (#330-Ankündigung) im Selbsttest festgeschrieben; `CONTRIBUTORS.md` um den gemergten #322-Beitrag ergänzt.

## 🔒 Wartung
- `roborazzi` 1.77, `ruby/setup-ruby` 1.334, fdroidserver-Requirements regeneriert (PyPI-Drift).
- Gate-Härtung: CodeQL-Action-Familie bleibt auf einem gemeinsamen SHA mit Dependabot-Gruppe, Snyk-Policy-Guard und Workflow-Security-Selbsttest unverändert grün.

## ⚠️ Bekannte offene Fehler (keine Regressionen)
- [#325](https://github.com/thoser666/Vivid/issues/325) `CameraOpenException: Camera no detected` — Feldbefund (Pixel 8 Pro, `0.5.21-beta+5212`), **unbehoben**. Die Release-Identität entlastet den Befund nicht, deshalb bleibt er offen.
- [#336](https://github.com/thoser666/Vivid/issues/336) `Idle camera: Create capture session failed: … CAMERA_DISCONNECTED` (VIVID-43) — **unbehoben**. Der neue Identitätscheck aus #350 beseitigt nur die Fehler verworfener Vorschauen, nicht die Kamera-Trennung selbst.
- [#350](https://github.com/thoser666/Vivid/issues/350) bleibt offen, bis die Sentry-Gruppe VIVID-46 nach diesem Build nachweislich still ist — ein vorab geschlossenes Issue würde weitere Rückläufe unsichtbar machen.
- **F-Droid-Metadaten:** Der `Builds`-Block in `fdroid/metadata/com.vivid.foss.yml` endet bei `0.5.20-beta` (5202); beim nächsten Repo-Build auf diesen Tag nachziehen.

## 📥 Installation & Update

| Kanal | Bezug |
|-------|-------|
| **GitHub Releases** | „Latest" (diese Version) — FOSS-/Standard-APK + `SHA256SUMS.txt` |
| **Obtainium** | zieht automatisch das neueste GitHub-Release |
| **Eigenes F-Droid-Repo** | wöchentlicher Build aus den Stable-Releases (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** von `0.5.48-beta` (5484) direkt installierbar (5494 > 5484); nightly → beta ✅; beta → nightly wäre ein Downgrade (vorher deinstallieren).
- **FOSS-Hinweis:** Alle Änderungen sind flavor-unabhängig und wirken identisch in beiden Builds.

## 🔗 Referenzen

- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Features & Fixes: [#330](https://github.com/thoser666/Vivid/issues/330) · [#322](https://github.com/thoser666/Vivid/pull/322) · [#324](https://github.com/thoser666/Vivid/issues/324) · [#350](https://github.com/thoser666/Vivid/issues/350)
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
