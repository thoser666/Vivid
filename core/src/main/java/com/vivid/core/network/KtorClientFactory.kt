package com.vivid.core.network

import com.vivid.core.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClientFactory {

    fun create(): HttpClient {
        return HttpClient(CIO) {
            // WebSockets-Plugin (Pflicht vor httpClient.webSocket): traegt den
            // OBS-Steuerungs-Client. Der Transport laeuft bewusst ueber CIO-
            // Sockets statt ueber den von der Network-Security-Config
            // regulierten Java-HTTP-Stack (ws:// zu IP-Hosts wuerde sonst als
            // CLEARTEXT blockiert, Issue #226 / Sentry VIVID-M).
            install(WebSockets)
            install(ContentNegotiation) {
                json(
                    Json {
                        prettyPrint = true
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }
            // HTTP-Logging nur im Debug-Build und ohne Bodies (HEADERS):
            // Request-/Response-Bodies können Credentials enthalten (z. B. Login-Passwörter)
            // und dürfen niemals in Logs landen — auch nicht im Release-Build.
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    level = LogLevel.HEADERS
                }
            }
        }
    }
}
