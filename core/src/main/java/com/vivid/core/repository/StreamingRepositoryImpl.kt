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

    override val obsInputs: StateFlow<List<String>>
        get() = obsWebSocketClient.inputs

    override val obsMuteStates: StateFlow<Map<String, Boolean>>
        get() = obsWebSocketClient.muteStates

    override val obsAudioLevels: StateFlow<Map<String, Float>>
        get() = obsWebSocketClient.audioLevels

    override val obsSyncOffsets: StateFlow<Map<String, Long>>
        get() = obsWebSocketClient.syncOffsets

    override val obsScenes: StateFlow<List<String>>
        get() = obsWebSocketClient.scenes

    override val obsCurrentProgramScene: StateFlow<String?>
        get() = obsWebSocketClient.currentProgramScene

    override val obsSnapshot: StateFlow<ByteArray?>
        get() = obsWebSocketClient.snapshot

    override fun connectToObs(password: String, ip: String, port: Int, useTls: Boolean) {
        obsWebSocketClient.connect(password, ip, port, useTls)
    }

    override fun disconnectFromObs() {
        obsWebSocketClient.disconnect()
    }

    override fun obsRefreshInputs() {
        obsWebSocketClient.refreshInputs()
    }

    override fun obsRefreshScenes() {
        obsWebSocketClient.refreshScenes()
    }

    override fun obsRefreshProgramScene() {
        obsWebSocketClient.refreshProgramScene()
    }

    override fun obsToggleMute(inputName: String) {
        obsWebSocketClient.toggleMute(inputName)
    }

    override fun obsRefreshSyncOffset(inputName: String) {
        obsWebSocketClient.refreshSyncOffset(inputName)
    }

    override fun obsSetSyncOffset(inputName: String, syncOffsetNs: Long) {
        obsWebSocketClient.setSyncOffset(inputName, syncOffsetNs)
    }

    override fun obsSetProgramScene(sceneName: String) {
        obsWebSocketClient.setProgramScene(sceneName)
    }

    override fun obsCreateScene(sceneName: String) {
        obsWebSocketClient.createScene(sceneName)
    }

    override fun obsCreateBlackoutInput(sceneName: String, inputName: String) {
        obsWebSocketClient.createBlackoutInput(sceneName, inputName)
    }

    override fun obsTakeScreenshot(sourceName: String, width: Int?, height: Int?) {
        obsWebSocketClient.takeScreenshot(sourceName, width, height)
    }

    override fun getObsScenes(): List<String> {
        return obsWebSocketClient.scenes.value
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
