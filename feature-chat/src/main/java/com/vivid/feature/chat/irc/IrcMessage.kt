package com.vivid.feature.chat.irc

data class IrcMessage(
    val tags: Map<String, String> = emptyMap(),
    val prefix: String? = null,
    val command: String,
    val params: List<String> = emptyList(),
    val trailing: String? = null,
)
