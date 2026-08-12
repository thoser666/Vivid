package com.vivid.core.update

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException

class GitHubReleasesApiImplTest {

    private val baseUrl = "https://api.github.com/repos/thoser666/Vivid/releases"

    private fun clientWith(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `requests releases with per_page and page parameters and parses the list`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl?per_page=10&page=1", request.url.toString())
            respond(
                content = """
                    [{
                      "tag_name": "nightly-20260811-0428",
                      "name": "Vivid nightly (0.2.0-nightly.93)",
                      "html_url": "https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0428",
                      "body": "Nightly feature build — installable via Obtainium.",
                      "draft": false,
                      "prerelease": true,
                      "published_at": "2026-08-11T04:28:00Z"
                    }]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        val releases = api.getReleases()

        assertEquals(1, releases.size)
        assertEquals("nightly-20260811-0428", releases[0].tagName)
        assertEquals("Vivid nightly (0.2.0-nightly.93)", releases[0].name)
        assertEquals("https://github.com/thoser666/Vivid/releases/tag/nightly-20260811-0428", releases[0].htmlUrl)
        assertEquals("Nightly feature build — installable via Obtainium.", releases[0].body)
        assertEquals(true, releases[0].prerelease)
        assertEquals(false, releases[0].draft)
    }

    @Test
    fun `honours a custom per_page value`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl?per_page=3&page=1", request.url.toString())
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        val releases = api.getReleases(perPage = 3)

        assertEquals(0, releases.size)
    }

    @Test
    fun `honours the page parameter for pagination`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl?per_page=10&page=2", request.url.toString())
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        val releases = api.getReleases(page = 2)

        assertEquals(0, releases.size)
    }

    @Test
    fun `ignores unknown json fields like author and assets`() = runTest {
        val engine = MockEngine {
            respond(
                content = """
                    [{
                      "tag_name": "v0.2.0-alpha",
                      "name": "Vivid v0.2.0-alpha",
                      "html_url": "https://github.com/thoser666/Vivid/releases/tag/v0.2.0-alpha",
                      "draft": false,
                      "prerelease": false,
                      "published_at": "2026-08-10T10:00:00Z",
                      "author": {"login": "thoser666"},
                      "assets": [{"name": "app-release.apk", "size": 7014159}]
                    }]
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        val releases = api.getReleases()

        assertEquals(1, releases.size)
        assertEquals("v0.2.0-alpha", releases[0].tagName)
    }

    @Test
    fun `adds a bearer token header when an auth token is provided`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("Bearer test-token", request.headers[HttpHeaders.Authorization])
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val api = GitHubReleasesApiImpl(clientWith(engine), authToken = "test-token")

        api.getReleases()
    }

    @Test
    fun `omits the authorization header without a token`() = runTest {
        val engine = MockEngine { request ->
            assertEquals(null, request.headers[HttpHeaders.Authorization])
            respond(content = "[]", status = HttpStatusCode.OK, headers = jsonHeaders())
        }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        api.getReleases()
    }

    @Test
    fun `propagates network failures`() = runTest {
        val engine = MockEngine { throw IOException("network down") }
        val api = GitHubReleasesApiImpl(clientWith(engine))

        assertThrows<IOException> { api.getReleases() }
    }
}
