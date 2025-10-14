package com.vivid.core.network

import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User

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
