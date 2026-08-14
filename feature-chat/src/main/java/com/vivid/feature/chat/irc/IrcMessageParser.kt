package com.vivid.feature.chat.irc

import javax.inject.Inject

/**
 * Parser for IRCv3 messages as used by Twitch chat.
 *
 * Format: `@tags :prefix COMMAND param1 param2 :trailing`
 */
class IrcMessageParser @Inject constructor() {

    fun parse(line: String): IrcMessage? {
        var rest = line.trim()
        if (rest.isEmpty()) return null

        var tags: Map<String, String> = emptyMap()
        if (rest.startsWith("@")) {
            val separator = rest.indexOf(' ')
            val tagSection = if (separator == -1) rest else rest.substring(0, separator)
            tags = parseTags(tagSection.substring(1))
            rest = if (separator == -1) "" else rest.substring(separator + 1)
        }

        var prefix: String? = null
        if (rest.startsWith(":")) {
            val separator = rest.indexOf(' ')
            prefix = if (separator == -1) rest.substring(1) else rest.substring(1, separator)
            rest = if (separator == -1) "" else rest.substring(separator + 1)
        }
        if (rest.isEmpty()) return null

        val trailing: String?
        val params: List<String>
        val trailingStart = rest.indexOf(" :")
        if (trailingStart != -1) {
            val head = rest.substring(0, trailingStart)
            trailing = rest.substring(trailingStart + 2)
            params = if (head.isEmpty()) emptyList() else head.split(' ')
        } else {
            trailing = null
            params = rest.split(' ')
        }

        val command = params.firstOrNull() ?: return null
        return IrcMessage(
            tags = tags,
            prefix = prefix,
            command = command,
            params = params.drop(1),
            trailing = trailing,
        )
    }

    private fun parseTags(section: String): Map<String, String> {
        if (section.isEmpty()) return emptyMap()
        return section.split(';').associate { entry ->
            val index = entry.indexOf('=')
            if (index == -1) {
                entry to ""
            } else {
                entry.substring(0, index) to unescape(entry.substring(index + 1))
            }
        }
    }

    private fun unescape(value: String): String {
        if (value.indexOf('\\') == -1) return value
        return buildString {
            var i = 0
            while (i < value.length) {
                val c = value[i]
                if (c == '\\' && i + 1 < value.length) {
                    when (value[i + 1]) {
                        ':' -> append(';')
                        's' -> append(' ')
                        '\\' -> append('\\')
                        'r' -> append('\r')
                        'n' -> append('\n')
                        else -> {
                            append(c)
                            append(value[i + 1])
                        }
                    }
                    i += 2
                } else {
                    append(c)
                    i += 1
                }
            }
        }
    }
}
