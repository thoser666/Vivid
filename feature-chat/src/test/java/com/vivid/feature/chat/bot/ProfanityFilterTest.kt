package com.vivid.feature.chat.bot

import com.vivid.core.data.ChatBotMode
import com.vivid.feature.chat.ai.LlmConfig
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ProfanityFilterTest {

    private lateinit var filter: ProfanityFilter

    private fun defaultConfig(
        enabled: Boolean = true,
        categories: Set<ProfanityCategory> = ProfanityCategory.entries.toSet(),
        customWords: Set<String> = emptySet(),
        excludedWords: Set<String> = emptySet(),
    ) = ChatBotConfig(
        channel = "testchannel",
        login = "testbot",
        oauthToken = "oauth_token",
        systemPrompt = "",
        mentionsOnly = false,
        replyCooldownMillis = 0L,
        maxRepliesPerMinute = 0,
        mode = ChatBotMode.COMMAND,
        llm = LlmConfig(
            baseUrl = "https://api.openai.com",
            apiKey = "test-key",
            model = "gpt-4o-mini",
        ),
        profanityEnabled = enabled,
        profanityCategories = categories,
        profanityCustomWords = customWords,
        profanityExcludedWords = excludedWords,
    )

    @BeforeEach
    fun setUp() {
        filter = ProfanityFilter()
        filter.configure(defaultConfig())
    }

    @Test
    fun `clean message passes`() {
        val result = filter.check("Hey everyone, how's the stream today?")
        assertFalse(result.blocked)
    }

    @Test
    fun `direct profanity blocked`() {
        assertTrue(filter.check("You are a fuck").blocked)
        assertTrue(filter.check("This is shit").blocked)
        assertTrue(filter.check("You bitch").blocked)
    }

    @Test
    fun `leetspeak blocked`() {
        assertTrue(filter.check("You are a f.u.c.k").blocked)
        assertTrue(filter.check("s h i t").blocked)
        assertTrue(filter.check("b!tch").blocked)
    }

    @Test
    fun `punctuation in word blocked`() {
        assertTrue(filter.check("f-u-c-k").blocked)
        assertTrue(filter.check("s.h.i.t").blocked)
    }

    @Test
    fun `repeated characters blocked`() {
        assertTrue(filter.check("fuuuuck").blocked)
        assertTrue(filter.check("shiiit").blocked)
    }

    @Test
    fun `substrings not false positive on innocent words`() {
        assertFalse(filter.check("The assassin creed game").blocked)
        assertFalse(filter.check("That was a classic move").blocked)
    }

    @Test
    fun `empty text passes`() {
        assertFalse(filter.check("").blocked)
        assertFalse(filter.check("   ").blocked)
    }

    @Test
    fun `mixed case handled`() {
        assertTrue(filter.check("FUCK this").blocked)
        assertTrue(filter.check("ShIt").blocked)
    }

    @Test
    fun `all blocklist words detected`() {
        val blocklistWords = listOf(
            "fuck", "shit", "ass", "bitch", "damn", "dick",
            "cock", "cunt", "bastard", "whore", "slut",
            "crap", "douche", "piss", "prick",
        )
        for (word in blocklistWords) {
            assertTrue(filter.check("you are a $word").blocked) { "Expected '$word' to be blocked" }
        }
    }

    @Test
    fun `whitespace and newlines handled`() {
        assertTrue(filter.check("f u c k").blocked)
        assertTrue(filter.check("s\nh\ni\nt").blocked)
    }

    // ── Kategorie-Tests ───────────────────────────────────────────────────

    @Test
    fun `disabling PROFANITY category allows profanity words`() {
        val cfg = defaultConfig(categories = setOf(ProfanityCategory.SLURS))
        filter.configure(cfg)
        assertFalse(filter.check("This is shit").blocked)
    }

    @Test
    fun `disabling HOSTILITY category allows hostile phrases`() {
        val cfg = defaultConfig(categories = setOf(ProfanityCategory.PROFANITY))
        filter.configure(cfg)
        assertFalse(filter.check("I will kill you").blocked)
    }

    @Test
    fun `enabling only SLURS catches slurs but not profanity`() {
        val cfg = defaultConfig(categories = setOf(ProfanityCategory.SLURS))
        filter.configure(cfg)
        assertFalse(filter.check("This is shit").blocked)
    }

    // ── Custom Words Tests ────────────────────────────────────────────────

    @Test
    fun `custom word is blocked`() {
        val cfg = defaultConfig(customWords = setOf("banword"))
        filter.configure(cfg)
        assertTrue(filter.check("you are a banword").blocked)
    }

    @Test
    fun `custom word is case insensitive`() {
        val cfg = defaultConfig(customWords = setOf("banword"))
        filter.configure(cfg)
        assertTrue(filter.check("BANWORD").blocked)
    }

    // ── Excluded Words Tests ──────────────────────────────────────────────

    @Test
    fun `excluded word is not blocked`() {
        val cfg = defaultConfig(excludedWords = setOf("ass"))
        filter.configure(cfg)
        assertFalse(filter.check("you are an ass").blocked)
    }

    @Test
    fun `excluded word does not block other words`() {
        val cfg = defaultConfig(excludedWords = setOf("ass"))
        filter.configure(cfg)
        assertTrue(filter.check("you are a fuck").blocked)
    }

    // ── Disabled Filter Tests ─────────────────────────────────────────────

    @Test
    fun `disabled filter passes everything`() {
        val cfg = defaultConfig(enabled = false)
        filter.configure(cfg)
        assertFalse(filter.check("fuck shit bitch").blocked)
    }

    // ── Pattern Tests (Twitch AutoMod-like) ──────────────────────────────

    @Test
    fun `kill yourself blocked`() {
        assertTrue(filter.check("kill yourself").blocked)
    }

    @Test
    fun `kys blocked`() {
        assertTrue(filter.check("kys").blocked)
    }

    @Test
    fun `sexual content blocked`() {
        assertTrue(filter.check("porn").blocked)
        assertTrue(filter.check("dildo").blocked)
    }
}
