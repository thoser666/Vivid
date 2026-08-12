package com.vivid.core.remote

/** Status des Streams, wie ihn die Web-Remote-Control über LAN ausliefert. */
enum class RemoteStreamStatus {
    IDLE,
    PREPARING,
    STREAMING,
    FAILED,
}
