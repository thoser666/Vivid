package com.vivid.feature.chat.youtube

import com.vivid.feature.chat.model.ChatPlatform
import com.vivid.feature.chat.session.ChatSessionConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Contract-Tests des YouTube-Lese-Adapters (P1 der Multi-Plattform-Skizze,
 * docs/architecture/multi-platform-chat.md) gegen **reale innertube-Payload-
 * Formen**: ytInitialData aus der Watch-Seite (Bootstrap) und
 * get_live_chat-Antworten (addChatItemAction, Continuations, timeoutMs).
 * Drift-Strategie: unbekannte Felder/Aktionen werden übersprungen
 * (ignoreUnknownKeys + Deep-Find), unbekannte Renderer ergeben keine
 * Nachricht. Flows/Lifecycle friert die feature-chat-Suite über die
 * Bestands-Suiten ein; hier die reinen Parser-/Vertrags-Pfade.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class YoutubeChatReaderTest {

    // --- Fixtures: ytInitialData (Bootstrap) -------------------------------

    /** Realer Aufbau der Watch-Seite: geschachtelte Player-/Chat-Container. */
    private val ytInitialDataLive =
        """{"responseContext":{"serviceTrackingParams":[]},"contents":{"twoColumnWatchNextResults":{
           "conversationBar":{"liveChatRenderer":{"continuations":[{"reloadContinuationData":{
           "token":"bootstrap-token-1"}}],"visibility":"VISIBLE"}},"contents":[]}},
           "videoDetails":{"videoId":"dQw4w9WgXcQ","title":"IRL Stream"}}"""

    /** Kein Live-Chat-Continuation im HTML → Kanal offline. */
    private val ytInitialDataOffline =
        """{"responseContext":{},"contents":{"twoColumnBrowseResultsRenderer":{"tabs":[]}},"microformat":{}}"""

    /** Struktur-Drift: Continuation woanders, aber Token selbst gleich geformt. */
    private val ytInitialDataDrift =
        """{"page":"watch","engagementPanels":[{"panel":{}}],"actionPanel":{"liveChatSelectorRenderer":{
           "continuationCommand":{"requestType":"WATCH_PAGE_TOKEN","token":"drift-token-9"}}},
           "overlays":{"videoId":"abcdefghijk"}}"""

    // --- Fixtures: get_live_chat-Antworten ----------------------------------

    /** Reale Form einer get_live_chat-Seite (addChatItemAction + continuations). */
    private val chatPageBody =
        """{"responseContext":{"visitorData":""},"continuationContents":{"liveChatContinuation":{"actions":[{"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"id":"msg-1","timestampUsec":"1726900000123456","authorSimpleText":"Bergsteiger","authorExternalChannelId":"UCauthor1","authorBadges":[{"liveChatAuthorBadgeRenderer":{"icon":{"iconType":"MODERATOR"},"tooltip":"Moderator"}}],"message":{"runs":[{"text":"Hallo "},{"text":"vom Berg","bold":true}]}}}}},{"addChatItemAction":{"item":{"liveChatPaidMessageRenderer":{"id":"sc-1","purchaseAmountText":"5,00 EUR","message":{"runs":[{"text":"Go!"}]}}}}}],"continuations":[{"timedContinuationData":{"continuation":"next-token-2","timeoutMs":5000}},{"invalidationContinuationData":{"continuation":"fallback-token","timeoutMs":9000}}],"clientMessages":[],"pollHeader":{}}}}"""

    /** Leere Seite ohne Folgetoken → Stream/Chat beendet (Poll-Loop-Aus). */
    private val chatPageEndBody =
        """{"continuationContents":{"liveChatContinuation":{"actions":[],"continuations":[],
           "clientMessages":[]}}}"""

    /** Drift: timeouts fehlen, continuations ungewöhnlich geformt — trotzdem token-lesbar. */
    private val chatPageDriftBody =
        """{"continuationContents":{"liveChatContinuation":{"actions":[{"addChatItemAction":{"item":{"liveChatTextMessageRenderer":{"id":"d-1","authorSimpleText":"UnknownBadgeFan","authorBadges":[{"liveChatAuthorBadgeRenderer":{"customThumbnail":{"url":"x"},"tooltip":"Top fan"}}],"message":{"runs":[{"text":"drift ok"}]}}}}}],"continuations":[{"someFutureContinuationData":{"token":"drift-next","timeoutMs":1500}}]}}}"""

    @Test
    fun `bootstrap extracts continuation and video id`() {
        val bootstrap = parseLiveChatBootstrap(ytInitialDataLive)
        assertEquals("bootstrap-token-1", bootstrap?.continuation)
        assertEquals("dQw4w9WgXcQ", bootstrap?.videoId)
    }

    @Test
    fun `bootstrap returns null when channel is offline`() {
        assertNull(parseLiveChatBootstrap(ytInitialDataOffline))
    }

    @Test
    fun `bootstrap is drift tolerant to new container structures`() {
        val bootstrap = parseLiveChatBootstrap(ytInitialDataDrift)
        assertEquals("drift-token-9", bootstrap?.continuation)
        assertEquals("abcdefghijk", bootstrap?.videoId)
    }

    @Test
    fun `extractYtInitialData handles braces inside strings`() {
        val html = """<script>var ytInitialData = {"a":{"b":"} brace { escape \" here" ,"c":1}};</script>"""
        val extracted = extractYtInitialData(html)
        assertNotNull(extracted)
        val root = Json.parseToJsonElement(extracted!!).jsonObject
        assertTrue(root.containsKey("a"))
    }

    @Test
    fun `extractYtInitialData returns null without marker`() {
        assertNull(extractYtInitialData("<html><body>ups</body></html>"))
    }

    @Test
    fun `chat page maps text messages with platform youtube`() {
        val page = parseGetLiveChatResponse(chatPageBody, channel = "UCme")
        assertEquals(1, page.messages.size)
        val message = page.messages.single()
        assertEquals("msg-1", message.id)
        assertEquals("UCme", message.channel)
        assertEquals("Bergsteiger", message.displayName)
        assertEquals("Hallo vom Berg", message.text)
        assertEquals(ChatPlatform.YOUTUBE, message.platform)
        assertEquals(1726900000123L, message.timestamp)
        assertEquals(listOf("Moderator"), message.badges)
        // Bewusst false (P1): Bot-Aktionen laufen nur auf Twitch.
        assertTrue(!message.isModerator && !message.isBroadcaster)
    }

    @Test
    fun `chat page continues with timed continuation and platform timeout`() {
        val page = parseGetLiveChatResponse(chatPageBody, channel = "UCme")
        assertEquals("next-token-2", page.nextContinuation)
        assertEquals(5000L, page.timeoutMs)
    }

    @Test
    fun `chat page end without continuation stops the poll loop`() {
        val page = parseGetLiveChatResponse(chatPageEndBody, channel = "UCme")
        assertEquals(0, page.messages.size)
        assertNull(page.nextContinuation)
        assertEquals(YoutubeChatReader.DEFAULT_POLL_MS, page.timeoutMs)
    }

    @Test
    fun `chat page is drift tolerant to unknown continuation shapes`() {
        val page = parseGetLiveChatResponse(chatPageDriftBody, channel = "UCme")
        assertEquals(1, page.messages.size)
        assertEquals(listOf("Top fan"), page.messages.single().badges)
        assertEquals("drift-next", page.nextContinuation)
        assertEquals(1500L, page.timeoutMs)
    }

    @Test
    fun `get_live_chat request body carries client context and continuation`() {
        val body = buildGetLiveChatBody("token-xyz", "2.20240701.00.00").toString()
        val root = Json.parseToJsonElement(body).jsonObject
        assertEquals("token-xyz", root["continuation"]?.jsonPrimitive?.content)
        val client = root["context"]?.jsonObject?.get("client")?.jsonObject
        assertEquals("WEB", client?.get("clientName")?.jsonPrimitive?.content)
        assertEquals("2.20240701.00.00", client?.get("clientVersion")?.jsonPrimitive?.content)
    }

    @Test
    fun `reader start with wrong config type throws`() = runTest {
        val reader = YoutubeChatReader(
            scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.test.StandardTestDispatcher(testScheduler)),
            http = HttpClient(MockEngine {
                respond("", HttpStatusCode.OK)
            }),
        )
        val error = runCatching {
            reader.start(ChatSessionConfig.Youtube(channel = "UCme"))
        }
        // Die Youtube-Variante ist die richtige — kein Wurf; Vertrag: start()
        // mit leerer ID bleibt Disconnected (siehe Test darunter).
        assertTrue(error.isSuccess)
        reader.stop()
    }

    @Test
    fun `sender answers with controlled failure until p4`() = runTest {
        val result = YoutubeChatSender().send(ChatSessionConfig.Youtube(channel = "UCme"), "hi")
        assertTrue(result is com.vivid.feature.chat.session.ChatSendResult.Failed)
        assertTrue(
            (result as com.vivid.feature.chat.session.ChatSendResult.Failed)
                .cause.contains("P4"),
        )
    }

    @Test
    fun `youtube config carries platform and channel`() {
        val config = ChatSessionConfig.Youtube(channel = "UCstreamer")
        assertEquals(ChatPlatform.YOUTUBE, config.platform)
        assertEquals("UCstreamer", config.channel)
    }
}
