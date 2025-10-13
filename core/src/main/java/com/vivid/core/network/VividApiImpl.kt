package com.vivid.core.network

import com.vivid.core.domain.model.LoginRequest
import com.vivid.core.domain.model.LoginResult
import com.vivid.core.domain.model.RegistrationRequest
import com.vivid.core.domain.model.RegistrationResult
import com.vivid.core.domain.model.User
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import javax.inject.Inject // <-- WICHTIGER IMPORT

class VividApiImpl @Inject constructor( // <-- HIER ist die Änderung
    private val client: HttpClient,
) : VividApi {

    companion object {
        private const val BASE_URL = "http://10.0.2.2:8080"
    }

    override suspend fun login(loginRequest: LoginRequest): LoginResult {
        return try {
            val response: User = client.post("$BASE_URL/login") {
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
            val response: User = client.post("$BASE_URL/register") {
                contentType(ContentType.Application.Json)
                setBody(registrationRequest)
            }.body()
            RegistrationResult.Success
        } catch (e: Exception) {
            RegistrationResult.Error(e.message ?: "Unknown error")
        }
    }

    override suspend fun getAccount(userId: Int): User {
        return client.get("$BASE_URL/users/$userId").body()
    }

    override suspend fun updateAccount(userId: Int, user: User): User {
        return client.put("$BASE_URL/users/$userId") {
            contentType(ContentType.Application.Json)
            setBody(user)
        }.body()
    }

    override suspend fun deleteAccount(userId: Int) {
        client.delete("$BASE_URL/users/$userId")
    }

    override suspend fun getFollowers(userId: Int): List<User> {
        return client.get("$BASE_URL/users/$userId/followers").body()
    }

    override suspend fun getFollowing(userId: Int): List<User> {
        return client.get("$BASE_URL/users/$userId/following").body()
    }

    override suspend fun followUser(userId: Int, followId: Int) {
        client.post("$BASE_URL/users/$userId/follow/$followId")
    }

    override suspend fun unfollowUser(userId: Int, unfollowId: Int) {
        client.post("$BASE_URL/users/$userId/unfollow/$unfollowId")
    }

    override suspend fun getStreamKey(userId: Int): String {
        return client.get("$BASE_URL/users/$userId/stream-key").body()
    }
}
