package com.vivid.core.network

import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject

class VividApiImpl @Inject constructor(
    private val client: HttpClient,
) : VividApi {

    // HIER IST DIE WICHTIGE ÄNDERUNG: Alle URL-Strings an einem Ort
    private companion object {
        private const val BASE_URL = "http://10.0.2.2:8080"

        // Endpunkte als Konstanten definieren
        private const val ENDPOINT_LOGIN = "/login"
        private const val ENDPOINT_REGISTER = "/register"
        private const val ENDPOINT_USERS = "/users"
    }

    override suspend fun login(loginRequest: LoginRequest): LoginResult {
        return try {
            val response: User = client.post("$BASE_URL$ENDPOINT_LOGIN") {
                contentType(ContentType.Application.Json)
                setBody(loginRequest)
            }.body()
            LoginResult.Success(response)
        } catch (e: Exception) {
            LoginResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun register(registrationRequest: RegistrationRequest): RegistrationResult {
        return try {
            client.post("$BASE_URL$ENDPOINT_REGISTER") {
                contentType(ContentType.Application.Json)
                setBody(registrationRequest)
            }.body<Unit>() // Korrektur: Wir erwarten hier keinen User-Body zurück
            RegistrationResult.Success
        } catch (e: Exception) {
            RegistrationResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun getAccount(userId: Int): User {
        return client.get("$BASE_URL$ENDPOINT_USERS/$userId").body()
    }

    override suspend fun updateAccount(userId: Int, user: User): User {
        return client.put("$BASE_URL$ENDPOINT_USERS/$userId") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }.body()
    }

    override suspend fun deleteAccount(userId: Int) {
        client.delete("$BASE_URL$ENDPOINT_USERS/$userId")
    }

    override suspend fun getFollowers(userId: Int): List<User> {
        return client.get("$BASE_URL$ENDPOINT_USERS/$userId/followers").body()
    }

    override suspend fun getFollowing(userId: Int): List<User> {
        return client.get("$BASE_URL$ENDPOINT_USERS/$userId/following").body()
    }

    override suspend fun followUser(userId: Int, followId: Int) {
        client.post("$BASE_URL$ENDPOINT_USERS/$userId/follow/$followId")
    }

    override suspend fun unfollowUser(userId: Int, unfollowId: Int) {
        client.post("$BASE_URL$ENDPOINT_USERS/$userId/unfollow/$unfollowId")
    }

    override suspend fun getStreamKey(userId: Int): String {
        return client.get("$BASE_URL$ENDPOINT_USERS/$userId/stream-key").body()
    }
}
