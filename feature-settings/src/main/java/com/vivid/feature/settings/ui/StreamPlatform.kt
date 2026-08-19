package com.vivid.feature.settings.ui

/**
 * Plattform-Vorlagen für die Stream-Einstellungen.
 *
 * [ingestUrl] ist die Basis-RTMP-Ingest-URL ohne Stream-Key. Beim Anwenden
 * einer Vorlage wird [ingestUrl] als Stream-URL gesetzt und TLS aktiviert,
 * sodass die Konvertierung `rtmp://` → `rtmps://` in buildStreamUrl greift.
 *
 * - Twitch:  rtmps://live.twitch.tv/app (offiziell unterstützt)
 * - YouTube: rtmps://a.rtmp.youtube.com/live2 (offiziell unterstützt)
 * - Kick:    rtmps://live.kick.com/app (nicht offiziell dokumentiert, wird
 *            aber von gängigen Tools wie Restreamer/Stunnel genutzt; falls
 *            der Server TLS ablehnt, kann der Nutzer den Toggle deaktivieren)
 * - Custom:  leert die URL — beliebige RTMP(S)/SRT-Ingest-Ziele (z. B. Owncast)
 *            eintragen; der TLS-Toggle bleibt unangetastet.
 */
enum class StreamPlatform(
    val label: String,
    val ingestUrl: String,
) {
    Twitch("Twitch", "rtmp://live.twitch.tv/app"),
    YouTube("YouTube", "rtmp://a.rtmp.youtube.com/live2"),
    Kick("Kick", "rtmp://live.kick.com/app"),

    /**
     * Eigene/benutzerdefinierte Ziel-URL: [ingestUrl] ist leer, damit eine
     * beliebige RTMP(S)/SRT-Ingest-URL eingetragen werden kann (z. B. Owncast
     * oder ein eigener Server). Anders als die Vorlagen wird der TLS-Toggle
     * NICHT verändert — er bleibt, wie der Nutzer ihn gesetzt hat.
     */
    Custom("Benutzerdefiniert", ""),
}

/**
 * Hinweistext unter dem Stream-URL-Feld: Das Feld akzeptiert beliebige
 * RTMP(S)-/SRT-Ingest-URLs (z. B. Owncast oder einen eigenen Server) — die
 * Plattform-Vorlagen oben sind nur Komfort-Presets. Als Konstante testbar
 * und zentral gepflegt (siehe StreamPlatformTest).
 */
const val STREAM_URL_HINT =
    "Freie Ingest-URL: akzeptiert beliebige RTMP(S)- oder SRT-Ziele, z. B. Owncast oder einen eigenen Server. Die Plattform-Vorlagen oben sind nur Presets."
