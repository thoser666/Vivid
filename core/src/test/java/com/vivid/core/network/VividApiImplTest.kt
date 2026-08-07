package com.vivid.core.network

import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
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
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.IOException

class VividApiImplTest {

    private val baseUrl = "http://10.0.2.2:8080"

    private fun clientWith(engine: MockEngine): HttpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json { ignoreUnknownKeys = true })
        }
    }

    private fun jsonHeaders() = headersOf(HttpHeaders.ContentType, "application/json")

    @Test
    fun `login returns success with the parsed user`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/login", request.url.toString())
            respond(
                content = """{"id":1,"username":"alice","email":"alice@example.com"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val api = VividApiImpl(clientWith(engine))

        val result = api.login(LoginRequest(username = "alice", email = "alice@example.com", password = "pw"))

        assertTrue(result is LoginResult.Success)
        assertEquals(User(id = 1, username = "alice", email = "alice@example.com"), (result as LoginResult.Success).user)
    }

    @Test
    fun `login returns error when the request fails`() = runTest {
        val engine = MockEngine { throw IOException("network down") }
        val api = VividApiImpl(clientWith(engine))

        val result = api.login(LoginRequest(username = "alice", email = "a@b.de", password = "pw"))

        assertTrue(result is LoginResult.Error)
    }

    @Test
    fun `register returns success on an empty ok response`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/register", request.url.toString())
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = VividApiImpl(clientWith(engine))

        val result = api.register(
            RegistrationRequest(username = "bob", email = "bob@example.com", passwordHash = "hash"),
        )

        assertEquals(RegistrationResult.Success, result)
    }

    @Test
    fun `register returns error when the request fails`() = runTest {
        val engine = MockEngine { throw IOException("network down") }
        val api = VividApiImpl(clientWith(engine))

        val result = api.register(
            RegistrationRequest(username = "bob", email = "bob@example.com", passwordHash = "hash"),
        )

        assertTrue(result is RegistrationResult.Error)
    }

    @Test
    fun `getAccount hits the users endpoint and parses the user`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/users/7", request.url.toString())
            respond(
                content = """{"id":7,"username":"carol","email":"carol@example.com"}""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val api = VividApiImpl(clientWith(engine))

        val user = api.getAccount(7)

        assertEquals(User(id = 7, username = "carol", email = "carol@example.com"), user)
    }

    @Test
    fun `getFollowers parses a list of users`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/users/7/followers", request.url.toString())
            respond(
                content = """[{"id":1,"username":"alice","email":"a@b.de"},{"id":2,"username":"bob","email":"b@c.de"}]""",
                status = HttpStatusCode.OK,
                headers = jsonHeaders(),
            )
        }
        val api = VividApiImpl(clientWith(engine))

        val followers = api.getFollowers(7)

        assertEquals(2, followers.size)
        assertEquals("alice", followers[0].username)
    }

    @Test
    fun `followUser posts to the follow endpoint`() = runTest {
        val urls = mutableListOf<String>()
        val engine = MockEngine { request ->
            urls += request.url.toString()
            respond(content = "", status = HttpStatusCode.OK)
        }
        val api = VividApiImpl(clientWith(engine))

        api.followUser(7, 8)

        assertEquals("$baseUrl/users/7/follow/8", urls.single())
    }

    @Test
    fun `getStreamKey returns the raw body as string`() = runTest {
        val engine = MockEngine { request ->
            assertEquals("$baseUrl/users/7/stream-key", request.url.toString())
            respond(
                content = "stream-key-123",
                status = HttpStatusCode.OK,
                headers = headersOf(HttpHeaders.ContentType, "text/plain"),
            )
        }
        val api = VividApiImpl(clientWith(engine))

        val streamKey = api.getStreamKey(7)

        assertEquals("stream-key-123", streamKey)
    }

    @Test
    fun `getAccount propagates network failures`() = runTest {
        val engine = MockEngine { throw IOException("network down") }
        val api = VividApiImpl(clientWith(engine))

        var thrown: Throwable? = null
        try {
            api.getAccount(7)
        } catch (e: Exception) {
            thrown = e
        }

        assertTrue(thrown is IOException)
    }
}
