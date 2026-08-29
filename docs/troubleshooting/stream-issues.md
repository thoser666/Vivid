# 🔧 Troubleshooting: Stream-Probleme

> Häufige Probleme beim Streamen und deren Lösungen.

## Stream bricht ab

### Ursache: Instabile Internetverbindung

**Lösung:**
1. WLAN statt LTE verwenden
2. In der Nähe des Routers positionieren
3. Bitrate in den Settings reduzieren (z.B. 2000 kbps)
4. RTMPS deaktivieren ( tls kann bei schlechtem Netz instabil sein)

### Ursache: Stream-Key ungültig

**Lösung:**
1. Stream-Key in der [Twitch Dashboard](https://dashboard.twitch.tv/settings/stream) prüfen
2. Key copypasten (keine Leerzeichen am Anfang/Ende)
3. Bei Bedarf neuen Key generieren

## Kein Bild/Sound

### Ursache: Kamera-Berechtigung fehlt

**Lösung:**
1. Android-Einstellungen → Apps → Vivid → Berechtigungen
2. **Kamera** erlauben
3. **Mikrofon** erlauben
4. App neu starten

### Ursache: Audio-Quelle nicht ausgewählt

**Lösung:**
1. Prüfe, ob das Mikrofon aktiv ist
2. In den Android-Einstellungen die Audio-Quelle prüfen

## Hohe Latenz

### Ursache: Server-Problem

**Lösung:**
1. Ingest-URL prüfen (nicht den Stream-Key mit der URL verwechseln)
2. Näheren Twitch-Server auswählen
3. In OBS: **Einstellungen → Stream → Server** ändern

## Audio-Probleme

### Ursache: Kein Sound im Stream

**Lösung:**
1. Mikrofon-Berechtigung prüfen
2. In den Android-Einstellungen: **Vivid → Audio** erlauben
3. App neu starten

### Ursache: Echo im Stream

**Lösung:**
1. Kopfhörer verwenden (kein Speaker-Feedback)
2. Lautstärke des Handys reduzieren

## Further Reading

- [FAQ: Häufige Probleme](../faq/common-issues.md)
- [User Guide](../user-guide.md)
