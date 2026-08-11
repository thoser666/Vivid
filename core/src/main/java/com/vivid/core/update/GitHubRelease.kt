package com.vivid.core.update

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Teilmenge der GitHub-Releases-API-Antwort (`GET /repos/{owner}/{repo}/releases`).
 * `ignoreUnknownKeys = true` im Json-Config von [com.vivid.core.network.KtorClientFactory]
 * ignoriert alle weiteren Felder (assets, author, …).
 */
@Serializable
data class GitHubRelease(
    @SerialName("tag_name") val tagName: String = "",
    val name: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String = "",
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    @SerialName("published_at") val publishedAt: String? = null,
)
