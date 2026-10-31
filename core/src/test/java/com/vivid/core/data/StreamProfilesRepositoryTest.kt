package com.vivid.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class StreamProfilesRepositoryTest {
    @TempDir lateinit var dir: File

    @Test fun `empty installation has no profiles`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "empty.preferences_pb") })
        val settings = SettingsRepository(store).appSettingsFlow.first()
        assertTrue(settings.streamProfiles.isEmpty())
        assertNull(settings.activeStreamId)
    }

    @Test fun `legacy destination migrates with secrets secondary target and encoder settings`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "legacy.preferences_pb") })
        val repo = SettingsRepository(store)
        repo.updateStreamSettings("rtmp://live.twitch.tv/app", "legacy-key", true)
        repo.updateSecondaryStreamSettings("rtmp://backup.example/app", "backup-key", true)
        repo.updateEncoderPreset(EncoderPreset.FHD60)
        val settings = repo.appSettingsFlow.first()
        val migrated = settings.streamProfiles.single()
        assertEquals(StreamPlatformType.Twitch, migrated.platform)
        assertEquals("legacy-key", migrated.streamKey)
        assertEquals("backup-key", migrated.secondaryStreamKey)
        assertTrue(migrated.secondaryStreamUseTls)
        assertEquals(60, migrated.videoFps)
        assertEquals(migrated.id, settings.activeStreamId)
        repo.updateStreamProfiles(settings.streamProfiles, null)
        val disabled = SettingsRepository(store).appSettingsFlow.first()
        assertNull(disabled.activeStreamId)
        assertEquals("", disabled.streamUrl)
        assertEquals("", disabled.secondaryStreamUrl)
        assertEquals(migrated, disabled.streamProfiles.single())
    }

    @Test fun `selection applies only that profile and persists independently of repository instance`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "select.preferences_pb") })
        val repo = SettingsRepository(store)
        val a = StreamProfile("a", "First", streamUrl = "rtmp://first.example/app", streamKey = "key-a",
            secondaryStreamUrl = "rtmp://backup.example/app", secondaryStreamKey = "second-key",
            videoBitrateKbps = 3_000, videoFps = 60, keyframeIntervalSeconds = 4,
            lowLightBoostEnabled = true, obsRemoteEnabled = true, obsPassword = "obs-a",
            twitchChannelOauthToken = "token-a", replayAudioMode = ReplayAudioMode.VIDEO_ONLY)
        val b = StreamProfile("b", "Second", streamUrl = "rtmp://second.example/app", streamKey = "key-b")
        repo.updateStreamProfiles(listOf(a, b), a.id)
        val loaded = SettingsRepository(store).appSettingsFlow.first()
        assertEquals(a.streamUrl, loaded.streamUrl)
        assertEquals(3_000, loaded.videoBitrateKbps)
        assertEquals(60, loaded.videoFps)
        assertEquals(4, loaded.keyframeIntervalSeconds)
        assertTrue(loaded.lowLightBoostEnabled)
        assertEquals("obs-a", loaded.obsPassword)
        assertEquals(ReplayAudioMode.VIDEO_ONLY, loaded.replayAudioMode)
        repo.updateStreamProfiles(loaded.streamProfiles, b.id)
        val selected = repo.appSettingsFlow.first()
        assertEquals("b", selected.activeStreamId)
        assertEquals(b.streamKey, selected.streamKey)
        assertEquals("", selected.secondaryStreamUrl)
        assertEquals(6_000, selected.videoBitrateKbps)
        assertFalse(selected.lowLightBoostEnabled)
        assertFalse(selected.obsRemoteEnabled)
        assertEquals(a, selected.streamProfiles.first())
    }

    @Test fun `disabled profiles never resurrect old global destinations`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "off.preferences_pb") })
        val repo = SettingsRepository(store)
        repo.updateStreamSettings("rtmp://old.example/live", "old-key")
        repo.updateStreamProfiles(listOf(StreamProfile("a", "Off", streamUrl = "rtmp://saved.example/live")), null)
        val loaded = repo.appSettingsFlow.first()
        assertEquals("", loaded.streamUrl)
        assertEquals("", loaded.secondaryStreamUrl)
        assertFalse(loaded.obsRemoteEnabled)
        assertNull(loaded.activeStreamId)
    }
    @Test fun `existing OBS and scene writers update only the selected profile`() = runTest {
        val store = PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { File(dir, "editors.preferences_pb") })
        val repo = SettingsRepository(store)
        val a = StreamProfile("a", "A", streamUrl = "rtmp://a.example/live")
        val b = StreamProfile("b", "B", streamUrl = "rtmp://b.example/live")
        repo.updateStreamProfiles(listOf(a,b), "b")
        repo.updateObsSettings("obs.example", "4456", "new-password", true)
        repo.updateStreamSettings("rtmp://scene.example/live", "scene-key", true)
        val actual = SettingsRepository(store).appSettingsFlow.first()
        assertEquals("rtmp://scene.example/live", actual.streamUrl)
        assertEquals("obs.example", actual.obsHost)
        assertEquals("new-password", actual.streamProfiles.last().obsPassword)
        assertEquals(a, actual.streamProfiles.first())
    }
}
