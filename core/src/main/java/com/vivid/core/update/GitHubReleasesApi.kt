package com.vivid.core.update

interface GitHubReleasesApi {

    /**
     * Holt die Releases des Vivid-Repos (neueste zuerst), ohne Drafts zu filtern —
     * das macht der [UpdateChecker].
     *
     * Für den Live-Konsistenztest lassen sich mit [page] alle Seiten durchlaufen
     * (GitHub liefert maximal [perPage] pro Seite).
     */
    suspend fun getReleases(perPage: Int = 10, page: Int = 1): List<GitHubRelease>
}
