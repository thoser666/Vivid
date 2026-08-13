package com.vivid.feature.streaming

/**
 * Fokusmodus der Streaming-Kamera.
 *
 * Moblin bietet einen Fokus-Lock für Drive-/Train-/Boot-Streams, damit der
 * Autofokus nicht auf Regentropfen, Scheibenwischer oder Schmutz auf dem Glas
 * scharf stellt (Feature-Request [#377](https://github.com/eerimoq/moblin/issues/377)).
 */
enum class FocusMode {
    /** Kontinuierlicher Autofokus (Standard). */
    AUTO,

    /** Fokus auf Unendlich fixiert — verhindert Fokus-Hunting durch Nahobjekte. */
    LOCKED_INFINITY,
}
