package com.vivid.core.repository

import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
import kotlinx.coroutines.flow.StateFlow

interface StreamingRepository {

    val isConnectedToObs: StateFlow<Boolean>

    // OBS-Steuerung (PARITY Row 71: Snapshot / Audio-Levels / Audio-Sync / Mute / Screen-black)
    val obsInputs: StateFlow<List<String>>
    val obsMuteStates: StateFlow<Map<String, Boolean>>
    val obsAudioLevels: StateFlow<Map<String, Float>>
    val obsSyncOffsets: StateFlow<Map<String, Long>>
    val obsScenes: StateFlow<List<String>>
    val obsCurrentProgramScene: StateFlow<String?>
    val obsSnapshot: StateFlow<ByteArray?>

    fun connectToObs(password: String, ip: String, port: Int, useTls: Boolean = false)

    fun disconnectFromObs()

    fun obsRefreshInputs()

    fun obsRefreshScenes()

    fun obsRefreshProgramScene()

    fun obsToggleMute(inputName: String)

    fun obsRefreshSyncOffset(inputName: String)

    fun obsSetSyncOffset(inputName: String, syncOffsetNs: Long)

    fun obsSetProgramScene(sceneName: String)

    fun obsCreateScene(sceneName: String)

    fun obsCreateBlackoutInput(sceneName: String, inputName: String)

    fun obsTakeScreenshot(sourceName: String, width: Int? = null, height: Int? = null)

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
