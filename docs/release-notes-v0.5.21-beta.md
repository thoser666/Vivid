# Release Notes: Vivid v0.5.21-beta

**Release Date:** 2026-10-11
**Version:** 0.5.21-beta (versionCode 5212, deterministisch aus dem Tag)
**Channel:** Beta (GitHub Releases → „Latest" → Obtainium → eigenes F-Droid-Repo)

> **Hinweis zur Versionsnummer:** `v0.6.0` bleibt dem noch offenen **Streaming-Erweiterungs-Bucket** (RIST, WHIP, RTMP-Pull, SRTLA, Game-Controller, Streamer-Browser, Landscape) reserviert — dieser Cut ist ein Patch-Beta der laufenden `0.5.x`-Linie gemäß [RELEASE.md](../RELEASE.md) → Nummerierung.
>
> **Warum dieser Cut:** Seit dem letzten Beta-Schnitt (`v0.5.20-beta`, 02.10.2026) sind drei nutzer-sichtbare Änderungen gelandet — ein neuer **In-App-Pomodoro-Timer**, eine **Überarbeitung des Live-Stream-Screens** samt Kamera-Steuerung und eine Reihe von **Streaming-Robustheits-Fixes** (Bitraten-Einheiten, einheitlicher Start, Encoder-Erholung). Reine Doku-/CI-/Guard-Arbeit rechtfertigt keinen Cut (siehe RELEASE.md → Beta-Strategie); die drei genannten Änderungen tun es.

## 🎉 Highlights

### 🍅 In-App-Pomodoro-Timer (neu)
- Neue Einstellungs-Kategorie **Pomodoro**: Der Fokus-Timer läuft direkt in der App — ein Schalter (`Enabled`) plus konfigurierbare **Arbeits- und Pausendauer**.
- Ein Countdown-Screen wechselt zwischen **Work-** und **Break-Phase**, läuft **im Loop** weiter und **vibriert** beim Phasenwechsel (neue `VIBRATE`-Berechtigung). Die Dauern werden auf sinnvolle Bereiche begrenzt (Arbeit 1–120 min, Pause 1–60 min).
- Bewusst **in der App** statt als Stream-Overlay: kein Eingriff in den Streaming-Pfad, unabhängig nutzbar. Die Paritäts-Zeile „Pomodoro Timer Widget" wurde entsprechend ehrlich neu zugeschnitten ([PARITY.md](../PARITY.md)).
- Strings in **de/en/fr/ru**.

### 💥 Live-Stream-Screen: aufgeräumt, Kamera-Steuerung korrigiert (#253)
- Überlappende Controls ersetzt: App-Bar → **Controls-Menü** plus separates **Settings-Zahnrad** (Quellen Camera/Screen/Video gruppiert, Record direkt nach dem Trenner, danach Torch, Stabilisierung, OBS, Fokus, Filter, Boost, LUT, Color-Spaces, Belichtung, Weißabgleich); **Scenes** wandern in ein per Wisch schließbares Bottom-Sheet.
- Lokale **MP4-Aufnahme** funktioniert jetzt **ohne Stream-URL** bzw. ohne laufende Übertragung; der Menü-Zustand spiegelt den Aufnahmestatus; Kamera-Controls überleben Vorschau-/Aufnahme-Übergänge; die Live-Kamerabilder kehren nach dem Stop zurück.
- Kamera-Commands gehen an den aktuell offenen Camera2-Manager (einschließlich Idle-Preview); Torch, Stabilisierung, Fokus, Auto-Exposition und Auto-Weißabgleich werden im **angewandten Gerätezustand** verifiziert, nicht nur im UI-State. Die Idle-Vorschau rendert über **GL**, sodass Filter, Boost, LUTs und Color-Space-Tonwertkurven schon vor dem Stream sichtbar sind (LUT-Indexierung korrigiert, trilineare Interpolation).
- Der **#249-Permission-Vertrag** bleibt gewahrt: Der Screen-Eintritt startet die Idle-Vorschau nur bei bereits erteilter CAMERA-Berechtigung und löst keinen Systemdialog aus; Anfragen feuern ausschließlich aus User-Gesten.
- Verifiziert auf Samsung SM-G991B (Aufnahme ohne Stream, Aufnahmezustand, Frame-Delivery nach Stop, Kamera-Controls).

### 💥 Streaming-Robustheit: Bitraten-Einheiten, einheitlicher Start, Encoder-Erholung (#276)
- **Bitraten-Einheiten** werden an der **RootEncoder-Grenze** korrekt umgerechnet; das Streaming-Profil wird auf die Fähigkeiten der gewählten Kamera abgestimmt.
- Die **Keyframe-Kadenz** (Zwei-Sekunden-Rhythmus) wird überwacht und **überfällige IDR-Frames** werden angefordert; der **Bitraten-Modus** des gewählten Encoders wird verifiziert, adaptive Ziel-Werte werden diagnostiziert.
- **Stream-Neustart und Erholung** nach einem Verbindungsabbruch funktionieren; die Startvorbereitung (gespeicherte Einstellungen) wird für lokalen und Remote-Start geteilt; die Vorschau läuft im **Vollbild**, der Bildschirm bleibt wach.
- Zugehörige CI-/Datums-Guards und Selbsttests wurden portabel gemacht (Teil desselben PRs).

## 🔒 Wartung

- **Bouncy Castle 1.85 → 1.86** (#280/#281): behebt den Snyk-Rotstand und hebt die PR-Blockade auf.
- **Kotlinx-Kover** (Test-Coverage-Tooling) als Dependency-Bump nachgezogen — kein Nutzer-Impact.

## 📥 Installation & Update

| Kanal | Bezug |
|-------|-------|
| **GitHub Releases** | „Latest" (diese Version) — FOSS-/Standard-APK + `SHA256SUMS.txt` |
| **Obtainium** | zieht automatisch das neueste GitHub-Release |
| **Eigenes F-Droid-Repo** | wöchentlicher Build aus den Stable-Releases (Mo 04:00 UTC bzw. Dispatch) |
| **F-Droid Hauptrepo** | FOSS-Metadaten gepflegt (`fdroid/metadata/com.vivid.foss.yml`); Einreichung folgt |

- **Prüfsummen:** `SHA256SUMS.txt` im Release; Signatur-Verifikation per `cosign verify-blob --bundle SHA256SUMS.txt.bundle SHA256SUMS.txt`.
- **Update-Pfad:** nightly → beta ✅ installierbar; beta → nightly wäre ein Downgrade (vorher deinstallieren). Direkt von `v0.5.20-beta` installierbar (versionCode 5212 > 5202).
- **FOSS-Hinweis:** Alle Änderungen sind flavor-unabhängig und wirken identisch in beiden Builds.

## 🔗 Referenzen

- Release-Strategie: [RELEASE.md](../RELEASE.md) → Beta-Strategie
- Streaming-Fixes: PR [#253](https://github.com/thoser666/Vivid/pull/253) · [#276](https://github.com/thoser666/Vivid/pull/276)
- Parität: [PARITY.md](../PARITY.md) (ständiges Protokoll aller Änderungen)
