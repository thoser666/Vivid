# 🎥 Tutorial: OBS Studio Integration

> So steuerst du OBS Studio aus Vivid heraus.

## Voraussetzungen

- OBS Studio mit WebSocket-Plugin installiert
- Vivid im selben WLAN wie der OBS-PC

## Schritt 1: OBS WebSocket aktivieren

1. Öffne OBS Studio
2. **Tools** → **WebSocket Server Settings**
3. **Enable WebSocket server** aktivieren
4. **Server Port** notieren (Standard: 4455)
5. **Server Password** setzen (empfohlen)

## Schritt 2: Verbindung in Vivid einrichten

1. **Einstellungen** → **Streaming & OBS**
2. **OBS-Einstellungen** Bereich
3. **OBS Host** eintragen (IP-Adresse des PCs, z.B. `192.168.1.100`)
4. **OBS Port** eintragen (Standard: 4455)
5. **OBS Passwort** eintragen
6. **Sichere Verbindung (wss://)** aktivieren (empfohlen)

## Schritt 3: Verbindung testen

1. Tippe auf **Verbinden**
2. Vivid prüft die Verbindung zu OBS
3. Bei Erfolg: grüner Status-Indikator

## Was du steuern kannst

| Funktion | Beschreibung |
|----------|--------------|
| **Szenen wechseln** | Wechsle zwischen OBS-Szenen aus Vivid |
| **Quellen ein/ausblenden** | Schalte OBS-Quellen per Tap |
| **Audio-Mixer** | Lautstärke anpassen |

## Fehlerbehebung

| Problem | Lösung |
|---------|--------|
| Verbindung fehlgeschlagen | Prüfe IP-Adresse und Port |
| Passwort falsch | Passwort in OBS und Vivid muss übereinstimmen |
| Kein Zugriff | Firewall muss Port 4455 erlauben |

## Weitere Informationen

- [OBS WebSocket Doku](https://github.com/obsproject/obs-websocket)
- [User Guide: OBS steuern](../user-guide.md#9-obs-studio-steuern)
