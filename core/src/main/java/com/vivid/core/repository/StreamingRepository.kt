package com.vivid.core.repository

import com.vivid.core.domain.model.*
import kotlinx.coroutines.flow.StateFlow

interface StreamingRepository {

    val isConnectedToObs: StateFlow<Boolean>

    fun connectToObs(password: String, ip: String, port: Int)

    fun disconnectFromObs()

    fun getObsScenes(): List<String>

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