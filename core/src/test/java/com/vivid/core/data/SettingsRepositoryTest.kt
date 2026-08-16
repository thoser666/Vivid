package com.vivid.core.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class SettingsRepositoryTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `appSettingsFlow should return saved values`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Arrange
        val testUrl = "rtmp://test.url/live"
        val testKey = "test_key_123"
        
        // Act
        repository.updateStreamSettings(testUrl, testKey)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(testUrl, settings.streamUrl)
        assertEquals(testKey, settings.streamKey)
    }

    @Test
    fun `appSettingsFlow should return default values if nothing is saved`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("", settings.streamUrl)
        assertEquals("localhost", settings.obsHost)
    }

    @Test
    fun `appSettingsFlow should default streamUseTls to false (plain rtmp)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_stream_tls_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(false, settings.streamUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved stream tls flag`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_stream_tls.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateStreamSettings("rtmp://live.example/app", "key-1", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(true, settings.streamUseTls)
        assertEquals("rtmp://live.example/app", settings.streamUrl)
        assertEquals("key-1", settings.streamKey)
    }

    @Test
    fun `appSettingsFlow should return saved obs settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_obs.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateObsSettings("192.168.1.100", "4456", "obs-secret")
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("192.168.1.100", settings.obsHost)
        assertEquals("4456", settings.obsPort)
        assertEquals("obs-secret", settings.obsPassword)
    }

    @Test
    fun `appSettingsFlow should default obsUseTls to false (plain ws)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_tls_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(false, settings.obsUseTls)
    }

    @Test
    fun `appSettingsFlow should default secondary stream settings to empty (disabled)`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("", settings.secondaryStreamUrl)
        assertEquals("", settings.secondaryStreamKey)
        assertEquals(false, settings.secondaryStreamUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved secondary stream settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateSecondaryStreamSettings("rtmp://secondary.example/app", "key-2", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("rtmp://secondary.example/app", settings.secondaryStreamUrl)
        assertEquals("key-2", settings.secondaryStreamKey)
        assertEquals(true, settings.secondaryStreamUseTls)
        // Das primäre Ziel bleibt unberührt.
        assertEquals("", settings.streamUrl)
    }

    @Test
    fun `appSettingsFlow should keep secondary settings untouched when updating the primary`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_secondary_independent.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        repository.updateStreamSettings("rtmp://primary.example/app", "key-1")
        val settings = repository.appSettingsFlow.first()

        assertEquals("rtmp://primary.example/app", settings.streamUrl)
        assertEquals("", settings.secondaryStreamUrl)
        assertEquals("", settings.secondaryStreamKey)
        assertEquals(false, settings.secondaryStreamUseTls)
    }

    @Test
    fun `appSettingsFlow should return the saved obs tls flag`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_tls.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateObsSettings("192.168.1.100", "4455", "obs-secret", useTls = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(true, settings.obsUseTls)
    }

    @Test
    fun `appSettingsFlow should default chat settings to disabled and empty`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_chat_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("", settings.chatChannel)
        assertEquals(false, settings.chatOverlayEnabled)
    }

    @Test
    fun `appSettingsFlow should return the saved chat settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_chat.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateChatSettings(channel = "meinKanal", overlayEnabled = true)
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals("meinKanal", settings.chatChannel)
        assertEquals(true, settings.chatOverlayEnabled)
        // Andere Bereiche bleiben unberührt.
        assertEquals("", settings.streamUrl)
        assertEquals("localhost", settings.obsHost)
    }

    @Test
    fun `appSettingsFlow should keep chat and stream settings independent`() = runTest {
        // Chat-Daten: Das Schreiben darf die Stream-Einstellungen nicht anfassen.
        val chatDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_chat_chat.preferences_pb") }
        )
        val chatRepository = SettingsRepository(chatDataStore)
        chatRepository.updateChatSettings(channel = "meinKanal", overlayEnabled = true)
        val afterChat = chatRepository.appSettingsFlow.first()

        assertEquals("meinKanal", afterChat.chatChannel)
        assertEquals(true, afterChat.chatOverlayEnabled)
        assertEquals("", afterChat.streamUrl)

        // Stream-Daten: Das Schreiben darf die Chat-Einstellungen nicht anfassen.
        val streamDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_chat_stream.preferences_pb") }
        )
        val streamRepository = SettingsRepository(streamDataStore)
        streamRepository.updateStreamSettings("rtmp://primary.example/app", "key-1")
        val afterStream = streamRepository.appSettingsFlow.first()

        assertEquals("rtmp://primary.example/app", afterStream.streamUrl)
        assertEquals("", afterStream.chatChannel)
        assertEquals(false, afterStream.chatOverlayEnabled)
    }

    @Test
    fun `appSettingsFlow should default chat bot settings to disabled with sensible defaults`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_bot_default.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(false, settings.chatBotEnabled)
        assertEquals("https://api.openai.com", settings.chatBotApiBaseUrl)
        assertEquals("", settings.chatBotApiKey)
        assertEquals("gpt-4o-mini", settings.chatBotModel)
        assertEquals(true, settings.chatBotMentionsOnly)
        assertEquals(8L, settings.chatBotReplyCooldownSeconds)
        assertEquals(10, settings.chatBotMaxRepliesPerMinute)
        assertEquals(ChatBotMode.AUTONOMOUS, settings.chatBotMode)
        assertEquals("", settings.chatBotLogin)
        assertEquals("", settings.chatBotOauthToken)
        assertEquals("", settings.chatBotIgnoreBots)
        assertEquals(ChatBotCommandScope.ALL, settings.chatBotCommandScope)
        assertEquals("", settings.chatBotCommandPrefix)
    }

    @Test
    fun `appSettingsFlow should return the saved chat bot settings`() = runTest {
        val testDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_bot.preferences_pb") }
        )
        val repository = SettingsRepository(testDataStore)

        // Act
        repository.updateChatBotSettings(
            enabled = true,
            apiBaseUrl = "https://llm.example",
            apiKey = "my-secret-key",
            model = "my-model",
            systemPrompt = "Du bist mein Bot.",
            replyCooldownSeconds = 5,
            mentionsOnly = false,
            maxRepliesPerMinute = 20,
            mode = ChatBotMode.COMMAND,
            login = "vividbot",
            oauthToken = "oauth:tok123",
            ignoreBots = "rivuletbot, otherbot",
            commandScope = ChatBotCommandScope.PREFIX,
            commandPrefix = "v",
        )
        val settings = repository.appSettingsFlow.first()

        // Assert
        assertEquals(true, settings.chatBotEnabled)
        assertEquals("https://llm.example", settings.chatBotApiBaseUrl)
        assertEquals("my-secret-key", settings.chatBotApiKey)
        assertEquals("my-model", settings.chatBotModel)
        assertEquals("Du bist mein Bot.", settings.chatBotSystemPrompt)
        assertEquals(5L, settings.chatBotReplyCooldownSeconds)
        assertEquals(false, settings.chatBotMentionsOnly)
        assertEquals(20, settings.chatBotMaxRepliesPerMinute)
        assertEquals(ChatBotMode.COMMAND, settings.chatBotMode)
        assertEquals("vividbot", settings.chatBotLogin)
        assertEquals("oauth:tok123", settings.chatBotOauthToken)
        assertEquals("rivuletbot, otherbot", settings.chatBotIgnoreBots)
        assertEquals(ChatBotCommandScope.PREFIX, settings.chatBotCommandScope)
        assertEquals("v", settings.chatBotCommandPrefix)
        // Andere Bereiche bleiben unberührt.
        assertEquals("", settings.chatChannel)
        assertEquals("localhost", settings.obsHost)
    }

    @Test
    fun `appSettingsFlow should keep chat bot and chat overlay settings independent`() = runTest {
        val botDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_bot_indep.preferences_pb") }
        )
        val botRepository = SettingsRepository(botDataStore)
        botRepository.updateChatBotSettings(
            enabled = true,
            apiBaseUrl = "https://llm.example",
            apiKey = "key",
            model = "model",
            systemPrompt = "",
            replyCooldownSeconds = 8,
            mentionsOnly = true,
            maxRepliesPerMinute = 10,
            login = "vividbot",
            oauthToken = "token",
        )
        val afterBot = botRepository.appSettingsFlow.first()

        assertEquals(true, afterBot.chatBotEnabled)
        assertEquals("", afterBot.chatChannel)
        assertEquals(false, afterBot.chatOverlayEnabled)

        // Chat-Overlay: Das Schreiben darf die Bot-Einstellungen nicht anfassen.
        val overlayDataStore = PreferenceDataStoreFactory.create(
            scope = this,
            produceFile = { File(tempDir.toFile(), "test_bot_overlay.preferences_pb") }
        )
        val overlayRepository = SettingsRepository(overlayDataStore)
        overlayRepository.updateChatSettings(channel = "meinKanal", overlayEnabled = true)
        val afterOverlay = overlayRepository.appSettingsFlow.first()

        assertEquals("meinKanal", afterOverlay.chatChannel)
        assertEquals(true, afterOverlay.chatOverlayEnabled)
        assertEquals(false, afterOverlay.chatBotEnabled)
        assertEquals("", afterOverlay.chatBotOauthToken)
    }
}
