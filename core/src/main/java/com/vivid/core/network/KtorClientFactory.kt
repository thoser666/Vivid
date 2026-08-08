package com.vivid.core.network

import com.vivid.core.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object KtorClientFactory {

    fun create(): HttpClient {
        return HttpClient(CIO) {
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
