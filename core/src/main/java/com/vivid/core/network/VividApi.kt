package com.vivid.core.network

import com.vivid.core.domain.model.LoginRequest
import com.vivid.core.domain.model.LoginResult
import com.vivid.core.domain.model.RegistrationRequest
import com.vivid.core.domain.model.RegistrationResult
import com.vivid.core.domain.model.User

interface VividApi {
    suspend fun login(loginRequest: LoginRequest): LoginResult
    suspend fun register(registrationRequest: RegistrationRequest): RegistrationResult
    suspend fun getAccount(userId: Int): User
    suspend fun updateAccount(userId: Int, user: User): User
    suspend fun deleteAccount(userId: Int)
    suspend fun getFollowers(userId: Int): List<User>
    suspend fun getFollowing(userId: Int): List<User>
    suspend fun followUser(userId: Int, followId: Int)
    suspend fun unfollowUser(userId: Int, unfollowId: Int)
    suspend fun getStreamKey(userId: Int): String
}
