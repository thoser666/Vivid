# 🎯 Vivid Vision & Feature-Kriterien

> Diese Datei ist die **Autoritätsquelle für Feature-Entscheidungen**: Neue Features — ob aus Moblin-Updates oder aus Community-Wünschen — werden hieran gemessen. Der wöchentliche [Moblin-Feature-Check](../.github/workflows/check-moblin-features.yml) und der [Community-Request-Triage](../.github/workflows/community-requests.yml) zitieren diese Kriterien in ihren Reports; jede neue PARITY.md-Zeile sollte einen ausgefüllten Check davon haben.

## Vision

**Vivid ist die Android-Version der Open-Source-IRL-Streaming-App [Moblin](https://moblin.github.io)** — professionelles IRL-Streaming mit dem Smartphone, komplett mit Chat-Overlay, KI-Chat-Bot, Widgets, Szenen, Replays und manueller Kamerasteuerung. Ziel ist **volle Feature-Parität mit Moblin**, soweit auf Android technisch sinnvoll umsetzbar, plus durchdachte Vivid-Extras (KI-Chat-Bot, In-App-Logs & Diagnose).

**Kernversprechen:**

- 🎥 **Professionelles IRL-Streaming** — Multi-Kamera, stabiles RTMP/RTMPS, zuverlässig in Bewegung und auf Akku
- 🤖 **Automatisierung** — KI-Chat-Bot und Owner-Befehle: der Streamer streamt, statt die App zu bedienen
- 🔓 **Open Source & FOSS-first** — der `foss`-Flavor ist komplett quelloffen (F-Droid); Supply-Chain-Transparenz via [OpenSSF Scorecard](../SECURITY.md) und [CII Best Practices](cii-best-practices-badge.md)
- 🌍 **Lokalisiert** — Deutsch, Englisch, Französisch (de/en/fr)
- 📱 **Android-idiomatisch** — Material 3, System-APIs (Kamera, Akku, Sensoren), SAF statt Speicher-Root, Accessibility

## Feature-Kriterien (der 5-Fragen-Check)

Ein Feature kommt auf die Roadmap, wenn es **überwiegend** zutrifft:

| # | Frage | Warum |
|---|-------|-------|
| 1 | **Streamer-Nutzen:** Macht es IRL-Streams direkt besser — Bild, Audio, Chat-Interaktion, Zuverlässigkeit? | Nice-to-have-Deko allein reicht nicht; Vivid ist ein Werkzeug |
| 2 | **Mobile-Realität:** Ist es auf einem Smartphone (Akku, Mobilfunk, einhändig) sinnvoll nutzbar? | Kein Desktop-Feature in Mobile-Verkleidung |
| 3 | **Parität/Ökosystem:** Schließt es eine Lücke zu Moblin oder stärkt das Twitch-Streaming-Ökosystem? | Parität ist das Projektziel; Twitch ist das primäre Ziel |
| 4 | **Wartbarkeit:** Kann ein kleines Team es bauen und halten — keine proprietären Abhängigkeiten, kein Geräte-Zoo? | Nachhaltigkeit schlägt Feature-Anzahl |
| 5 | **FOSS-Kompatibilität:** Läuft es im `foss`-Flavor ohne Closed-Source-Pflicht? (Optionals wie Sentry bleiben Flavor-gekapselt.) | F-Droid-Verfügbarkeit ist ein Kernversprechen |

## Einstufungen

| Ergebnis | Bedeutung |
|----------|-----------|
| **✅ Aufnehmen** | 4–5 Kriterien erfüllt → PARITY.md-Zeile (📋) + passender [Roadmap-Milestone](../PARITY.md) |
| **🚧 Zurückstellen** | 2–3 Kriterien erfüllt → PARITY-Zeile mit Begründung, kein Milestone |
| **❌ Ablehnen** | ≤1 Kriterium oder Vision-Widerspruch (Desktop-Workflow, Closed-Platform-Lock-in, reine Deko) → dokumentierte Absage im Report/Issue |

## Prozess

1. **Moblin-Updates:** Der [wöchentliche Moblin-Check](../.github/workflows/check-moblin-features.yml) meldet neue Moblin-Features als Issue — jedes Finding enthält einen Antwortbogen (Vision-Check, Feature-Familie, Ziel-Milestone), der beim Aufnehmen in PARITY.md auszufüllen ist.
2. **Community-Wünsche:** Der [wöchentliche Community-Triage](../.github/workflows/community-requests.yml) heftet an jedes offene `enhancement`-Issue genau einmal einen Vorschlags-Kommentar mit denselben Kriterien.
3. **Entscheidung** trifft der Maintainer manuell — die Automatisierung liefert Struktur und Vollständigkeit, keine Automatik-Merges in die Roadmap.
