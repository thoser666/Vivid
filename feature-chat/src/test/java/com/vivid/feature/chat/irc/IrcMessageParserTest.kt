package com.vivid.feature.chat.irc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IrcMessageParserTest {

    private val parser = IrcMessageParser()

    @Test
    fun `blank lines return null`() {
        assertNull(parser.parse(""))
        assertNull(parser.parse("   "))
        assertNull(parser.parse("\n"))
    }

    @Test
    fun `parses a ping message`() {
        val message = parser.parse("PING :tmi.twitch.tv")
        assertEquals("PING", message!!.command)
        assertNull(message.prefix)
        assertEquals(emptyList<String>(), message.params)
        assertEquals("tmi.twitch.tv", message.trailing)
        assertTrue(message.tags.isEmpty())
    }

    @Test
    fun `parses a privmsg without tags`() {
        val message = parser.parse(":user!user@user.tmi.twitch.tv PRIVMSG #channel :Hello world")
        assertEquals("PRIVMSG", message!!.command)
        assertEquals("user!user@user.tmi.twitch.tv", message.prefix)
        assertEquals(listOf("#channel"), message.params)
        assertEquals("Hello world", message.trailing)
    }

    @Test
    fun `parses a join message`() {
        val message = parser.parse(":justinfan1!justinfan1@justinfan1.tmi.twitch.tv JOIN #channel")
        assertEquals("JOIN", message!!.command)
        assertEquals("justinfan1!justinfan1@justinfan1.tmi.twitch.tv", message.prefix)
        assertEquals(listOf("#channel"), message.params)
        assertNull(message.trailing)
    }

    @Test
    fun `parses twitch welcome message`() {
        val message = parser.parse(":tmi.twitch.tv 001 justinfan123 :Welcome, GLHF!")
        assertEquals("001", message!!.command)
        assertEquals("tmi.twitch.tv", message.prefix)
        assertEquals(listOf("justinfan123"), message.params)
        assertEquals("Welcome, GLHF!", message.trailing)
    }

    @Test
    fun `parses cap ack`() {
        val message = parser.parse(":tmi.twitch.tv CAP * ACK :twitch.tv/tags twitch.tv/commands twitch.tv/membership")
        assertEquals("CAP", message!!.command)
        assertEquals(listOf("*", "ACK"), message.params)
        assertEquals("twitch.tv/tags twitch.tv/commands twitch.tv/membership", message.trailing)
    }

    @Test
    fun `parses tags, prefix, params and trailing`() {
        val line = "@badges=moderator/1;color=#FF0000;display-name=PogChamp;id=abc;mod=1;" +
            "subscriber=0;tmi-sent-ts=1500000000000;user-id=456 :pogchamp!pogchamp@pogchamp.tmi.twitch.tv " +
            "PRIVMSG #channel :HeyGuys"
        val message = parser.parse(line)
        assertEquals("PRIVMSG", message!!.command)
        assertEquals("pogchamp!pogchamp@pogchamp.tmi.twitch.tv", message.prefix)
        assertEquals(listOf("#channel"), message.params)
        assertEquals("HeyGuys", message.trailing)
        assertEquals("#FF0000", message.tags["color"])
        assertEquals("PogChamp", message.tags["display-name"])
        assertEquals("moderator/1", message.tags["badges"])
        assertEquals("1", message.tags["mod"])
        assertEquals("1500000000000", message.tags["tmi-sent-ts"])
    }

    @Test
    fun `unescapes tag values`() {
        val line = "@display-name=Has\\sSpace;emotes=25:0-4\\s9-13;badges=mod\\:2;slash=ab\\\\cd " +
            ":user!user@user.tmi.twitch.tv PRIVMSG #channel :hi"
        val message = parser.parse(line)
        assertEquals("Has Space", message!!.tags["display-name"])
        assertEquals("25:0-4 9-13", message.tags["emotes"])
        assertEquals("mod;2", message.tags["badges"])
        assertEquals("ab\\cd", message.tags["slash"])
    }

    @Test
    fun `tag without value becomes empty string`() {
        val message = parser.parse("@v= :user!user@user.tmi.twitch.tv PRIVMSG #channel :hi")
        assertEquals("", message!!.tags["v"])
    }
}
