# ❓ FAQ: Häufige Probleme

> Antworten auf die häufigsten Fragen zu Vivid.

## Allgemein

### Was ist Vivid?

Vivid ist eine IRL-Streaming-App für Android, die auf Twitch, YouTube, Kick und andere Plattformen streamen kann. Sie bietet Chat-Overlay, KI-Bot und Kamera-Steuerung in einer App.

### Wo kann ich Vivid herunterladen?

Vivid ist auf der [Releases-Seite](https://github.com/thoser666/Vivid/releases) verfügbar. Derzeit noch nicht im Play Store.

### Welche Android-Version wird benötigt?

Android 7.0 (API 24) oder höher.

## Streaming

### Kann ich auf mehrere Plattformen gleichzeitig streamen?

Ja! Vivid unterstützt Multi-Streaming. Trage einfach eine zweite Stream-URL und einen zweiten Key in den Einstellungen ein.

### Wie ändere ich die Stream-Qualität?

Die Bitrate und Auflösung werden automatisch angepasst. Manuelle Einstellungen sind in den erweiterten Optionen verfügbar.

### Warum ist mein Stream ruckelig?

Häufige Ursachen:
- Instabile Internetverbindung
- Zu hohe Bitrate für das Netzwerk
- Overheating des Geräts

**Lösung:** Bitrate reduzieren, WLAN verwenden, Gerät abkühlen lassen.

## Chat & Bot

### Wie richte ich den KI-Chat-Bot ein?

Siehe [detaillierte Bot-Dokumentation](../ai-chat-bot.md).

### Welche Befehle unterstützt der Bot?

| Befehl | Beschreibung |
|--------|--------------|
| `!help` | Zeigt alle verfügbaren Befehle |
| `!uptime` | Zeigt die Stream-Dauer |
| `!tts` | Schaltet Text-to-Speech um |
| `!bot` | Zeigt Bot-Informationen |

### Funktioniert der Bot mit anderen Tools (z.B. Rivulet)?

Ja! Vivid kann neben anderen Bots laufen. Konfiguriere den **Koexistenz-Modus** in den Einstellungen.

## Technik

### Unterstützt Vivid SRT?

Ja! Vivid unterstützt RTMP, RTMPS und SRT als Streaming-Protokolle.

### Kann ich OBS aus Vivid steuern?

Ja! Vivid kann OBS über WebSocket steuern. Siehe [OBS-Integration](../tutorials/obs-integration.md).

### Wie aktiviere ich die Taschenlampe?

Tippe auf den 🔦-Button im Streaming-Screen oder nutze den Bot-Befehl `!torch`.

## Datenschutz

### Welche Daten erhebt Vivid?

Vivid erhebt nur die Daten, die für das Streaming benötigt werden. Siehe [PRIVACY.md](https://thoser666.github.io/Vivid/privacy/).

### Kann ich Fehlerberichte deaktivieren?

Ja! In den Einstellungen → **Remote & Datenschutz** → **Fehlerberichte senden (Sentry)** deaktivieren.

## Weitere Hilfe

- [Troubleshooting: Stream-Probleme](../troubleshooting/stream-issues.md)
- [User Guide](../user-guide.md)
- [GitHub Issues](https://github.com/thoser666/Vivid/issues)
