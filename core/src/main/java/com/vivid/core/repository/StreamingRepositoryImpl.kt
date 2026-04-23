package com.vivid.core.repository

import com.vivid.core.network.VividApi
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject // <-- DIESEN IMPORT HINZUFÜGEN

class StreamingRepositoryImpl @Inject constructor( // <-- DIESE ANNOTATION HINZUFÜGEN
    private val obsWebSocketClient: OBSWebSocketClient,
    private val vividApi: VividApi,
) : StreamingRepository {

    override val isConnectedToObs: StateFlow<Boolean>
        get() = obsWebSocketClient.isConnected

    override fun connectToObs(password: String, ip: String, port: Int) {
        obsWebSocketClient.connect(password, ip, port)
    }

    override fun disconnectFromObs() {
        obsWebSocketClient.disconnect()
    }

    override fun getObsScenes(): List<String> {
        // This should be implemented to fetch scenes from OBS
        return emptyList()
    }

    override suspend fun login(loginRequest: LoginRequest): LoginResult {
        return vividApi.login(loginRequest)
    }

    override suspend fun register(registrationRequest: RegistrationRequest): RegistrationResult {
        return vividApi.register(registrationRequest)
    }

    override suspend fun getAccount(userId: Int): User {
        return vividApi.getAccount(userId)
    }

    override suspend fun updateAccount(userId: Int, user: User): User {
        return vividApi.updateAccount(userId, user)
    }

    override suspend fun deleteAccount(userId: Int) {
        vividApi.deleteAccount(userId)
    }

    override suspend fun getFollowers(userId: Int): List<User> {
        return vividApi.getFollowers(userId)
    }

    override suspend fun getFollowing(userId: Int): List<User> {
        return vividApi.getFollowing(userId)
    }

    override suspend fun followUser(userId: Int, followId: Int) {
        vividApi.followUser(userId, followId)
    }

    override suspend fun unfollowUser(userId: Int, unfollowId: Int) {
        vividApi.unfollowUser(userId, unfollowId)
    }

    override suspend fun getStreamKey(userId: Int): String {
        return vividApi.getStreamKey(userId)
    }
}
