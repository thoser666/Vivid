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
        every { obsClient.connect("secret", "192.168.0.10", 4455, false) } just runs

        repository.connectToObs("secret", "192.168.0.10", 4455)

        verify { obsClient.connect("secret", "192.168.0.10", 4455, false) }
    }

    @Test
    fun `connectToObs forwards the tls flag to the websocket client`() {
        every { obsClient.connect("secret", "192.168.0.10", 4455, true) } just runs

        repository.connectToObs("secret", "192.168.0.10", 4455, useTls = true)

        verify { obsClient.connect("secret", "192.168.0.10", 4455, true) }
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
    fun `getObsScenes returns the current scene list from the client`() {
        every { obsClient.scenes } returns MutableStateFlow(listOf("Live", "Intro"))

        assertEquals(listOf("Live", "Intro"), repository.getObsScenes())
    }

    @Test
    fun `obs control flows mirror the client flows`() {
        every { obsClient.inputs } returns MutableStateFlow(listOf("Mic/Aux"))
        every { obsClient.muteStates } returns MutableStateFlow(mapOf("Mic/Aux" to true))
        every { obsClient.audioLevels } returns MutableStateFlow(mapOf("Mic/Aux" to -12.5f))
        every { obsClient.syncOffsets } returns MutableStateFlow(mapOf("Mic/Aux" to 50_000_000L))
        every { obsClient.scenes } returns MutableStateFlow(listOf("Live"))
        every { obsClient.currentProgramScene } returns MutableStateFlow("Live")
        every { obsClient.snapshot } returns MutableStateFlow(byteArrayOf(1, 2, 3))

        assertEquals(listOf("Mic/Aux"), repository.obsInputs.value)
        assertEquals(mapOf("Mic/Aux" to true), repository.obsMuteStates.value)
        assertEquals(mapOf("Mic/Aux" to -12.5f), repository.obsAudioLevels.value)
        assertEquals(mapOf("Mic/Aux" to 50_000_000L), repository.obsSyncOffsets.value)
        assertEquals(listOf("Live"), repository.obsScenes.value)
        assertEquals("Live", repository.obsCurrentProgramScene.value)
        assertEquals(listOf(1, 2, 3), repository.obsSnapshot.value?.map { it.toInt() })
    }

    @Test
    fun `obs control actions delegate to the client`() {
        every { obsClient.refreshInputs() } just runs
        every { obsClient.refreshScenes() } just runs
        every { obsClient.refreshProgramScene() } just runs
        every { obsClient.toggleMute("Mic/Aux") } just runs
        every { obsClient.refreshSyncOffset("Mic/Aux") } just runs
        every { obsClient.setSyncOffset("Mic/Aux", 50_000_000L) } just runs
        every { obsClient.setProgramScene("Intro") } just runs
        every { obsClient.createScene("Vivid Blackout") } just runs
        every { obsClient.createBlackoutInput("Vivid Blackout", "Blackout") } just runs
        every { obsClient.takeScreenshot("Live", 640, 360) } just runs

        repository.obsRefreshInputs()
        repository.obsRefreshScenes()
        repository.obsRefreshProgramScene()
        repository.obsToggleMute("Mic/Aux")
        repository.obsRefreshSyncOffset("Mic/Aux")
        repository.obsSetSyncOffset("Mic/Aux", 50_000_000L)
        repository.obsSetProgramScene("Intro")
        repository.obsCreateScene("Vivid Blackout")
        repository.obsCreateBlackoutInput("Vivid Blackout", "Blackout")
        repository.obsTakeScreenshot("Live", 640, 360)

        verify { obsClient.refreshInputs() }
        verify { obsClient.refreshScenes() }
        verify { obsClient.refreshProgramScene() }
        verify { obsClient.toggleMute("Mic/Aux") }
        verify { obsClient.refreshSyncOffset("Mic/Aux") }
        verify { obsClient.setSyncOffset("Mic/Aux", 50_000_000L) }
        verify { obsClient.setProgramScene("Intro") }
        verify { obsClient.createScene("Vivid Blackout") }
        verify { obsClient.createBlackoutInput("Vivid Blackout", "Blackout") }
        verify { obsClient.takeScreenshot("Live", 640, 360) }
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
