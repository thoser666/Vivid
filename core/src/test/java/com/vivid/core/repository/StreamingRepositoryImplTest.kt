package com.vivid.core.repository

import com.vivid.core.network.VividApi
import com.vivid.core.network.obs.OBSWebSocketClient
import com.vivid.domain.model.LoginRequest
import com.vivid.domain.model.LoginResult
import com.vivid.domain.model.RegistrationRequest
import com.vivid.domain.model.RegistrationResult
import com.vivid.domain.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StreamingRepositoryImplTest {

    private val obsClient = mockk<OBSWebSocketClient>()
    private val vividApi = mockk<VividApi>()
    private val repository = StreamingRepositoryImpl(obsClient, vividApi)

    @Test
    fun `connectToObs delegates to the websocket client`() {
        every { obsClient.connect("secret", "192.168.0.10", 4455) } just runs

        repository.connectToObs("secret", "192.168.0.10", 4455)

        verify { obsClient.connect("secret", "192.168.0.10", 4455) }
    }

    @Test
    fun `disconnectFromObs delegates to the websocket client`() {
        every { obsClient.disconnect() } just runs

        repository.disconnectFromObs()

        verify { obsClient.disconnect() }
    }

    @Test
    fun `isConnectedToObs mirrors the client state`() {
        every { obsClient.isConnected } returns MutableStateFlow(true)

        assertTrue(repository.isConnectedToObs.value)
    }

    @Test
    fun `getObsScenes returns an empty list`() {
        assertEquals(emptyList<String>(), repository.getObsScenes())
    }

    @Test
    fun `login delegates to the api`() = runTest {
        val request = LoginRequest(username = "alice", email = "alice@example.com", password = "pw")
        val result = LoginResult.Success(User(id = 1, username = "alice", email = "alice@example.com"))
        coEvery { vividApi.login(request) } returns result

        assertEquals(result, repository.login(request))

        coVerify { vividApi.login(request) }
    }

    @Test
    fun `register delegates to the api`() = runTest {
        val request = RegistrationRequest(username = "bob", email = "bob@example.com", passwordHash = "hash")
        val result = RegistrationResult.Success
        coEvery { vividApi.register(request) } returns result

        assertEquals(result, repository.register(request))

        coVerify { vividApi.register(request) }
    }

    @Test
    fun `account operations delegate to the api`() = runTest {
        val user = User(id = 7, username = "carol", email = "carol@example.com")
        coEvery { vividApi.getAccount(7) } returns user
        coEvery { vividApi.updateAccount(7, user) } returns user
        coEvery { vividApi.deleteAccount(7) } just runs
        coEvery { vividApi.getFollowers(7) } returns listOf(user)
        coEvery { vividApi.getFollowing(7) } returns emptyList()
        coEvery { vividApi.followUser(7, 8) } just runs
        coEvery { vividApi.unfollowUser(7, 8) } just runs
        coEvery { vividApi.getStreamKey(7) } returns "stream-key-123"

        assertEquals(user, repository.getAccount(7))
        assertEquals(user, repository.updateAccount(7, user))
        repository.deleteAccount(7)
        assertEquals(listOf(user), repository.getFollowers(7))
        assertEquals(emptyList<User>(), repository.getFollowing(7))
        repository.followUser(7, 8)
        repository.unfollowUser(7, 8)
        assertEquals("stream-key-123", repository.getStreamKey(7))

        coVerify { vividApi.getAccount(7) }
        coVerify { vividApi.updateAccount(7, user) }
        coVerify { vividApi.deleteAccount(7) }
        coVerify { vividApi.getFollowers(7) }
        coVerify { vividApi.getFollowing(7) }
        coVerify { vividApi.followUser(7, 8) }
        coVerify { vividApi.unfollowUser(7, 8) }
        coVerify { vividApi.getStreamKey(7) }
    }
}
