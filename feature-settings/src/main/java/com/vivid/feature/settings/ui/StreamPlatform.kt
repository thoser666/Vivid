package com.vivid.feature.settings.ui

import com.vivid.feature.settings.R
import androidx.annotation.StringRes

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
    @StringRes val labelRes: Int,
    val ingestUrl: String,
) {
    Twitch(R.string.platform_twitch, "rtmp://live.twitch.tv/app"),
    YouTube(R.string.platform_youtube, "rtmp://a.rtmp.youtube.com/live2"),
    Kick(R.string.platform_kick, "rtmp://live.kick.com/app"),

    /**
     * Eigene/benutzerdefinierte Ziel-URL: [ingestUrl] ist leer, damit eine
     * beliebige RTMP(S)/SRT-Ingest-URL eingetragen werden kann (z. B. Owncast
     * oder ein eigener Server). Anders als die Vorlagen wird der TLS-Toggle
     * NICHT verändert — er bleibt, wie der Nutzer ihn gesetzt hat.
     */
    Custom(R.string.platform_custom, ""),
}

/**
 * Ressourcen-ID des Hinweistexts unter dem Stream-URL-Feld: Das Feld
 * akzeptiert beliebige RTMP(S)-/SRT-Ingest-URLs (z. B. Owncast oder einen
 * eigenen Server) — die Plattform-Vorlagen oben sind nur Komfort-Presets.
 *
 * Als testbare Konstante und zentral gepflegt (siehe StreamPlatformTest).
 * Der Inhalt selbst lebt in `res/values/strings.xml` und `values-en`
 * (`stream_url_hint`) und wird zusätzlich vom CI-Guard `check_i18n.sh` auf
 * die Kernaussagen (RTMP, SRT, Owncast, Presets) in beiden Sprachen geprüft.
 */
val STREAM_URL_HINT_RES: Int = R.string.stream_url_hint
