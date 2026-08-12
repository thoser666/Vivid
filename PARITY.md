# 🎯 Moblin Feature Parity Tracker

Das **Endziel** von Vivid ist es, **zumindest funktionsgleich zu [Moblin](https://github.com/eerimoq/moblin)** zu sein — jedes Moblin-Feature soll in Vivid, adaptiert an die Android-Plattform, funktionieren.

Dieses Dokument ist die Arbeitsliste hinter dem [Parity-Status in der README](README.md#-parity-status): Es verfolgt **jedes** Moblin-Feature (35 anwendbare) mit Implementierungsstand, verantwortlichem Modul und offenen Tasks.

## Status-Legende

| Symbol | Bedeutung |
|--------|-----------|
| ✅ | Implementiert & getestet |
| 🚧 | In Arbeit (Teile vorhanden) |
| 📋 | Geplant (Roadmap) |
| — | Nicht zutreffend auf Android |

> **Stand:** 2026-08-12 · Aktualisierung: Foreground-Service für Hintergrund-Streaming implementiert
>
> **Pflege:** Nach jedem Feature-Commit den Status in der jeweiligen Zeile aktualisieren und das Datum oben anpassen.
>
> 🚦 **Nächster Meilenstein:** Wenn die ✅-Spalte in der Tabelle ≥17 erreicht → Zeit für [Beta](RELEASE.md#beta--nächster-meilenstein). Siehe [RELEASE.md](RELEASE.md) für alle Stage Gates.

---

## 📊 Übersicht

| Kategorie | ✅ | 🚧 | 📋 | Summe |
|-----------|----|----|----|-------|
| Streaming & Protokolle | 4 | 0 | 4 | 8 |
| Netzwerk-Bonding | 0 | 0 | 1 | 1 |
| OBS-Steuerung | 2 | 0 | 2 | 4 |
| Chat & Moderation | 0 | 0 | 3 | 3 |
| Overlays & Widgets | 0 | 0 | 5 | 5 |
| Kamera & Video | 0 | 1 | 3 | 4 |
| Audio | 0 | 0 | 3 | 3 |
| Remote & Companion | 1 | 0 | 2 | 3 |
| Plattform & Grundlagen | 5 | 1 | 0 | 6 |
| **Gesamt** | **12** | **2** | **23** | **37**† |

† Inkl. 1 n/a-Zeile (Apple-Watch-Companion); anwendbare Features: **36**.

---

## 📡 Streaming & Protokolle

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| RTMP | ✅ | `feature-streaming`, `core` | Single-Ingest-Stream über konfigurierte URL/Key |
| SRT | ✅ | `feature-streaming` | SRT über `RootEncoder` 2.6.4; Stream-URL aus Settings |
| RTMPS (TLS) | ✅ | `feature-streaming` | TLS-Ingest via RootEncoder 2.6.4 verifiziert (Bytecode: `tlsEnabled = scheme.endsWith("s")`, Port 443 default, TLSv1.1/1.2); `buildStreamUrl` schreibt `rtmp://` → `rtmps://` und normalisiert Port 1935 → 443; Beweis-Test am echten UrlParser (`RootEncoderRtmpsSupportTest`) |
| Hintergrund-Streaming (Foreground-Service) | ✅ | `app` (`StreamingService`), `feature-streaming` (`StreamingServiceLauncher`, `StreamingViewModel`) | Stream läuft weiter, wenn die App im Hintergrund ist (Home-Taste/Bildschirm aus): Foreground-Service mit `microphone|camera`-Type, persistenter Notification (Status-Aktualisierung, Stop-Aktion), PARTIAL_WAKE_LOCK; Runtime-Permissions (Kamera/Mikro/Notif.) werden beim Go-Live angefordert. **GL-freier Encoderpfad:** `RtmpCamera2` wird über den **Context-Konstruktor** erzeugt (interne `GlStreamInterface`-Pipeline mit eigenem EGL-Context + ForceRenderer, verifiziert an RootEncoder-2.6.4-Bytecode + Maintainer-Doku); die Kamera-Vorschau hängt nur als Surface an (`attachPreview`/`detachPreview`). Dadurch läuft der Stream auch bei **Activity-Zerstörung** (Recents-Wischen, Rotation) weiter — die Vorschau kommt beim nächsten Öffnen automatisch zurück |
| Multi-Streaming (RTMP(S) an mehrere Ziele) | 📋 | `feature-streaming` | Parallele Publisher verwalten; UI für mehrere Ziele |
| RIST | 📋 | `core` | Stack-Entscheidung: `librist`-JNI oder SRT-basiert |
| WHIP (WebRTC) | 📋 | `core` | WebRTC-Stack (z. B. `io.github.webrtc-sdk`); WHIP-Client + Sender |
| 4K/60fps, H.264/H.265 (HEVC) | 📋 | `feature-streaming` | Encoder-Presets (CameraX/MediaCodec); HEVC-Fallback-Kette |

## 🔗 Netzwerk-Bonding

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| SRTLA Multi-Network Bonding (WiFi + Mobilfunk, Failover, adaptive Bitrate) | 📋 | `core` | SRTLA-Protokoll (Link-Split/Reassembly) portieren; Statistik pro Verbindung; Gewichtung |

## 🎛️ OBS-Steuerung

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| OBS WebSocket-Connect (ws:// LAN / wss:// Remote, Passwort-Auth) | ✅ | `core` (`OBSWebSocketClient`), `feature-obs-control` | Scheme über `obsUseTls`-Setting; Auth-Handshake getestet. **Offene Tasks (Troubleshooting):** Verbindungsfehler-Ursache im UI anzeigen (OBS nicht erreichbar, Port/Firewall, Netzwerk); Auth-Fehler (falsches/leeres Passwort) als eigener UI-Zustand + Passwort-Reset-Hinweis; ws/wss-Fehlkonfiguration gezielt melden — Doku: [README-FAQ](README.md#-faq--häufige-probleme) |
| Szenen wechseln, Recording/Stream-Start/-Stop | ✅ | `feature-obs-control` | Request-Batch + Typen vorhanden |
| Snapshot / Audio-Levels / Audio-Sync auslesen | 📋 | `feature-obs-control` | Weitere Request-Typen ergänzen |
| OBS-Konfiguration per QR-Code importieren | 📋 | `core` | `ObsQrCodeData`-Modell existiert; QR-Scan + Connect-Flow verdrahten |

## 💬 Chat & Moderation

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Plattform-Chat (Twitch, Kick, YouTube, SOOP) | 📋 | `feature-chat` | Modul ist Platzhalter; IRC/WebSocket-Clients + Nachrichten-Stream aufbauen |
| Emotes (BTTV, FFZ, 7TV) | 📋 | `feature-chat` | Emote-API-Clients + Rendering |
| Moderation (Ban, Timeout, Delete), Chat-Bot, TTS | 📋 | `feature-chat` | ModActions + Bot-Framework |

## 🎨 Overlays & Widgets

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Chat-Overlay & Event-Alerts (Follow/Sub/Raid) | 📋 | `feature-widgets` | Modul ist Platzhalter; Alert-Layer + Trigger-API |
| Text-/Info-Widgets (Zeit, Wetter, Geschwindigkeit, GPS) | 📋 | `feature-widgets` | Sensor-/Geo-Daten-Bindungen |
| Karten-Widget | 📋 | `feature-widgets` | Karten-Provider wählen (Maps SDK / Tile-Overlay) |
| Browser-Widget (CSS + JS-API) | 📋 | `feature-widgets` | WebView-Layer + `postMessage`-Bridge |
| Scoreboards (Padel, Golf, Volleyball) | 📋 | `feature-widgets` | Datenmodelle + Renderer |

## 📹 Kamera & Video

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Tap-to-Focus, Pinch-Zoom, Stabilisierung | 🚧 | `feature-streaming` | CameraX-Controls teils vorhanden (`CameraScreen`); UI-Completion offen |
| Color-Spaces (sRGB, P3, Log) + 3D-LUTs | 📋 | `feature-streaming` | Shader-Pipeline in `OpenGlView` ausbauen |
| Video-Effekte (Graustufen, Letterbox, Sepia, Rauschfilter) | 📋 | `feature-streaming` | OpenGL-Effektkette |
| Externes Zubehör (DJI Osmo, GoPro, Gimbal, UVC) | 📋 | `feature-streaming` | BLE/USB-Integrationen einzeln bewerten |

## 🎙️ Audio

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Mic-Verwaltung + Auto-Switch bei externem Mic | 📋 | `feature-streaming` | AudioCapture-Quellen steuern. **Offene Tasks (Troubleshooting):** `RECORD_AUDIO`-Berechtigung vor Stream-Start prüfen + klare Meldung; Mic-Exklusivität erkennen (Android gibt das Mikrofon nur an eine App gleichzeitig) und hinweisen; Bluetooth-Mikrofon-Routing behandeln — Doku: [README-FAQ](README.md#-faq--häufige-probleme) |
| Level-Meter, Muting, Sync-Offset | 📋 | `feature-streaming` | DSP/UI. **Offen:** Fehlgeschlagenes `prepareAudio()`/`prepareVideo()` („Failed to prepare audio/video“) im UI anzeigen statt nur internem State |
| Talk Back (Monitor-Feed) | 📋 | `feature-streaming` | Rückkanal-Routing |

## 🕹️ Remote & Companion

| Moblin-Feature | Status | Modul | Offene Tasks / Notizen |
|----------------|--------|-------|------------------------|
| Web-Remote-Control (LAN-Server, Status/Preview) | ✅ | `core` (`RemoteControlServer`), `feature-streaming` (`StreamingEngineStreamControl`), `app` (`VividApplication`) | Ktor-Server auf Port 8080 mit Token-Auth; `GET /status` (öffentlich), `POST /start|stop` (Bearer-Token aus Settings); Token wird in DataStore persistiert und in den Settings angezeigt. **Offen:** Video-Preview im Browser, Toggle zum Aktivieren/Deaktivieren |
| Game-Controller-Support (Zoom, Szenen, Torch) | 📋 | `core` | Android-GameController-API |
| Deep Linking / Konfig-Import (`moblin://`, `.moblinSettings`) | 📋 | `app` | URL-Scheme + Import-Parser |
| Apple-Watch-Companion | — | — | Nicht zutreffend auf Android; Wear-OS-Pendant separat bewerten |

## 🧱 Plattform & Grundlagen

| Feature | Status | Modul | Offene Tasks / Notizen |
|---------|--------|-------|------------------------|
| Persistierte Stream-Einstellungen (URL/Key) | ✅ | `feature-streaming`, `feature-settings`, `core` (`SettingsRepository`) | `streamUrl`/`streamKey` via DataStore; in `startStream()` verdrahtet |
| Go-Live-Selbst-Check (Stream-Config-Validierung) | ✅ | `feature-streaming` (`StreamConfigValidator`), UI (`StreamingScreen`) | Validierung von URL/Scheme/Host/Key vor dem Start; ERROR blockiert, WARNING zeigt Hinweis; Re-Validierung bei Screen-Resume |
| Persistierte OBS-Einstellungen (Host, Port, Passwort, TLS) | ✅ | `core`, `feature-obs-control`, `feature-settings` | DataStore-Key `obs_use_tls` etc.; beide Settings-Screens |
| Auth-/API-Basis (Login, Register) | ✅ | `core` (`VividApi`), `domain` | Ktor-Client mit MockEngine-Tests |
| In-App-Version & Update-Check (About-Screen) | ✅ | `app` (`AboutScreen`), `feature-settings` (Update-Badge), `core` (`UpdateChecker`) | Zeigt installierte Version (versionName/versionCode) in Settings + About, Update-Badge direkt auf dem Settings-Screen, manueller Check in About; folgt den Cross-Track-Regeln aus RELEASE.md (kein Downgrade); Basis für den Obtainium-Update-Test |
| I18n (lokalisierte Strings) | 🚧 | `feature-*` | `strings.xml`-Grundgerüst vorhanden; Übersetzungen laufen über |

---

## 🔄 Aktualisierungslog

| Datum | Commit | Änderung |
|-------|--------|----------|
| 2026-08-12 | — | Foreground-Service für Hintergrund-Streaming (Notification, WakeLock, Runtime-Permissions) implementiert |
| 2026-08-12 | — | RTMPS (TLS-Ingest) verifiziert: RootEncoder 2.6.4 kann nativ TLS; Port-Normalisierung 1935→443 + Beweis-Test |
| 2026-08-12 | — | Go-Live-Selbst-Check (Stream-Config-Validierung, blockierende Fehler + Warnungen) ergänzt |
| 2026-08-11 | — | Web-Remote-Control (LAN-Server mit Token-Auth, Status/Start/Stop) ergänzt |
| 2026-08-11 | — | About-Screen (In-App-Version + Update-Check gegen GitHub-Releases) ergänzt |
| 2026-08-11 | — | Offene Tasks für OBS- & Audio-Troubleshooting ergänzt (README-FAQ) |
| 2026-08-08 | — | Erstversion erstellt (Stand nach `4d8362e`) |

---

*Siehe auch: [README – Parity Status](README.md#-parity-status) · [Moblin (Original)](https://github.com/eerimoq/moblin)*
