package com.vivid.feature.chat.bot

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ChatPollManagerTest {
    @Test
    fun `starts a poll and exposes zeroed vote counts`() {
        val manager = ChatPollManager()

        assertEquals(
            ChatPollManager.StartResult.Started,
            manager.start("Wohin heute?", listOf("Berg", "See")),
        )
        assertEquals(
            ChatPollManager.Poll("Wohin heute?", listOf("Berg", "See"), listOf(0, 0)),
            manager.current,
        )
    }

    @Test
    fun `rejects invalid option count and duplicate options`() {
        val manager = ChatPollManager()

        assertTrue(manager.start("Frage", listOf("Nur eine")) is ChatPollManager.StartResult.Invalid)
        assertTrue(manager.start("Frage", listOf("Ja", "ja")) is ChatPollManager.StartResult.Invalid)
        assertEquals(null, manager.current)
    }

    @Test
    fun `rejects a second poll while one is active`() {
        val manager = ChatPollManager()
        manager.start("Frage", listOf("Ja", "Nein"))

        assertEquals(
            ChatPollManager.StartResult.AlreadyActive,
            manager.start("Andere Frage", listOf("A", "B")),
        )
        assertEquals("Frage", manager.current?.question)
    }

    @Test
    fun `accepts numeric and text votes but only once per user`() {
        val manager = ChatPollManager()
        manager.start("Frage", listOf("Ja", "Nein"))

        assertEquals(ChatPollManager.VoteResult.Accepted("Ja"), manager.vote("user-1", "1"))
        assertEquals(ChatPollManager.VoteResult.AlreadyVoted, manager.vote("user-1", "Nein"))
        assertEquals(ChatPollManager.VoteResult.Accepted("Nein"), manager.vote("user-2", " nein "))
        assertEquals(listOf(1, 1), manager.current?.votes)
    }

    @Test
    fun `invalid votes do not reserve a users vote`() {
        val manager = ChatPollManager()
        manager.start("Frage", listOf("Ja", "Nein"))

        assertEquals(ChatPollManager.VoteResult.InvalidOption, manager.vote("user-1", "3"))
        assertEquals(ChatPollManager.VoteResult.Accepted("Ja"), manager.vote("user-1", "1"))
    }

    @Test
    fun `ending returns the result and clears the poll`() {
        val manager = ChatPollManager()
        manager.start("Frage", listOf("Ja", "Nein"))
        manager.vote("user-1", "1")

        assertEquals(1, manager.end()?.totalVotes)
        assertEquals(null, manager.current)
        assertEquals(ChatPollManager.VoteResult.NoActivePoll, manager.vote("user-2", "1"))
    }
}
