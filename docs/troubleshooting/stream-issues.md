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

## Encoder-Profil und Bitrate prüfen

Das angezeigte aktive Profil ist die erfolgreich vorbereitete Kombination aus
gewählter Kamera und Encoder. Reguläre Camera2-Ausgabegrößen, AE-FPS-Bereiche
und Mindest-Framedauer begrenzen die Auswahl zusätzlich zu den Encoder-Fähigkeiten.
Mit Auto-Fallback kann beispielsweise FHD60 auf FHD30 zurückfallen; ohne Fallback
wird eine nicht unterstützte Kombination vor dem Streamstart abgelehnt.
Separate High-Speed-Kamera-Modi sind in diesem Pfad nicht implementiert.
Die gemessenen Encoder-FPS sind eine Beobachtung, keine Garantie der Sensor-FPS.

Bitraten in Einstellungen, Ziel-Statistik und adaptiver Regelung sind in kbit/s.
Die Diagnose zeigt den ausgewählten Encoder, seinen tatsächlichen Bitratenmodus
und die Zielbitrate. Unterstützt dieser Encoder kein CBR, kann er VBR verwenden;
eine feste Zielbitrate garantiert dann keinen konstanten Upload. Adaptive Bitrate
ändert den Zielwert unabhängig vom angezeigten Codec-Modus.

Das konfigurierte Keyframe-Intervall beträgt zwei Sekunden. Die App beobachtet
die ausgegebenen Keyframes und fordert bei überfälligen Frames ein Sync-Frame an.
Bei geringer Eingangs-FPS kann die Hardware trotzdem größere Abstände erzeugen;
insbesondere der 14-FPS-Gerätetest hat noch keine verlässlich bestandene Kadenz.

Lokaler Button, Web-Remote und Chat verwenden dieselben gespeicherten
Stream-Einstellungen und denselben Foreground-Service-Start. Nach Stop wird der
geteilte Encoder beendet, sobald kein Stream-Ziel mehr aktiv ist. Nach einem
Verbindungsfehler bleibt der Fehler sichtbar; sobald der Empfänger wieder
verfügbar ist, kann der Retry-Button ohne App-Neustart verwendet werden.

## Further Reading

- [FAQ: Häufige Probleme](../faq/common-issues.md)
- [User Guide](../user-guide.md)
