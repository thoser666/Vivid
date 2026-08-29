# 💬 Tutorial: Chat-Overlay einrichten

> So zeigt Vivid den Twitch-Chat über deiner Streaming-Vorschau an.

## Was du brauchst

- Vivid installiert
- Twitch-Konto mit Bot-Zugangsdaten
- Stream-Key mit `user:read:chat` Scope

## Schritt 1: Bot-Zugangsdaten hinterlegen

1. **Einstellungen** → **Chat-Bot & KI**
2. **Bot-Login** eintragen (dein Twitch-Benutzername ohne @)
3. **Twitch-OAuth-Token** eintragen

> 💡 **Tipp:** Einen OAuth-Token erhältst du unter [twitchapps.com/tmi](https://twitchapps.com/tmi/)

## Schritt 2: Chat-Overlay aktivieren

1. **Einstellungen** → **Overlays & Widgets**
2. **Chat-Overlay** einschalten
3. **Twitch-Kanal** eintragen (dein Kanalname ohne #)

## Schritt 3: Overlay anpassen

Das Overlay zeigt automatisch:
- Die letzten Chat-Nachrichten
- Twitch-Badges (Broadcaster/Mod/Sub)
- Inline-Emotes (Twitch, BTTV, FFZ, 7TV)

## Fehlerbehebung

| Problem | Lösung |
|---------|--------|
| Kein Chat angezeigt | Prüfe, ob Bot-Login und OAuth-Token korrekt sind |
| Emotes fehlen | Third-Party-Emotes müssen in den Settings aktiviert sein |
| Overlay zu groß/klein | Die Größe wird automatisch an die Vorschau angepasst |

## Weitere Informationen

- [Vollständige Bot-Dokumentation](../ai-chat-bot.md)
- [User Guide: Chat-Overlay](../user-guide.md#7-chat-overlay)
