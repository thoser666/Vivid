package com.vivid.domain.model

import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelSerializationTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `login request round trips`() {
        val request = LoginRequest(username = "alice", email = "alice@example.com", password = "secret")

        val decoded = json.decodeFromString<LoginRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
    }

    @Test
    fun `registration request round trips`() {
        val request = RegistrationRequest(username = "bob", email = "bob@example.com", passwordHash = "hash")

        val decoded = json.decodeFromString<RegistrationRequest>(json.encodeToString(request))

        assertEquals(request, decoded)
    }

    @Test
    fun `user round trips`() {
        val user = User(id = 42, username = "carol", email = "carol@example.com")

        val decoded = json.decodeFromString<User>(json.encodeToString(user))

        assertEquals(user, decoded)
    }

    @Test
    fun `login request encodes the expected field names`() {
        val request = LoginRequest(username = "alice", email = "alice@example.com", password = "pw")

        val encoded = json.encodeToString(request)

        assertTrue(encoded.contains("\"username\":\"alice\""))
        assertTrue(encoded.contains("\"email\":\"alice@example.com\""))
        assertTrue(encoded.contains("\"password\":\"pw\""))
    }
}
