package com.vivid.core.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import javax.inject.Inject

class GitHubReleasesApiImpl @Inject constructor(
    private val client: HttpClient,
    private val authToken: String? = null,
) : GitHubReleasesApi {

    private companion object {
        const val API_URL = "https://api.github.com/repos/thoser666/Vivid/releases"
    }

    override suspend fun getReleases(perPage: Int, page: Int): List<GitHubRelease> {
        return client.get("$API_URL?per_page=$perPage&page=$page") {
            // Authentifizierter Zugriff (z. B. GITHUB_TOKEN im CI-Live-Check):
            // deutlich höheres Rate-Limit als 60 Requests/h unauthentifiziert.
            if (!authToken.isNullOrEmpty()) {
                header(HttpHeaders.Authorization, "Bearer $authToken")
            }
        }.body()
    }
}
