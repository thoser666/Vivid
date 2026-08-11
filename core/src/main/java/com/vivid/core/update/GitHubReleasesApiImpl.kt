package com.vivid.core.update

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import javax.inject.Inject

class GitHubReleasesApiImpl @Inject constructor(
    private val client: HttpClient,
) : GitHubReleasesApi {

    private companion object {
        const val API_URL = "https://api.github.com/repos/thoser666/Vivid/releases"
    }

    override suspend fun getReleases(perPage: Int): List<GitHubRelease> {
        return client.get("$API_URL?per_page=$perPage").body()
    }
}
