package com.vivid.core.update

interface GitHubReleasesApi {

    /**
     * Holt die neuesten Releases des Vivid-Repos (neueste zuerst), ohne Drafts zu filtern —
     * das macht der [UpdateChecker].
     */
    suspend fun getReleases(perPage: Int = 10): List<GitHubRelease>
}
