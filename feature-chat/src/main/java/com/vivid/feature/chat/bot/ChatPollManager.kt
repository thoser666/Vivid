package com.vivid.feature.chat.bot

/**
 * Flüchtiger Poll-Zustand für einen laufenden Chat-Bot-Stream.
 *
 * Die Klasse enthält keine Twitch- oder UI-Abhängigkeiten und kann deshalb
 * unabhängig getestet werden. Ein Poll wird beim Engine-Start/-Stop verworfen.
 */
class ChatPollManager {
    data class Poll(
        val question: String,
        val options: List<String>,
        val votes: List<Int>,
    ) {
        val totalVotes: Int
            get() = votes.sum()
    }

    sealed interface StartResult {
        data object Started : StartResult
        data object AlreadyActive : StartResult
        data class Invalid(val reason: String) : StartResult
    }

    sealed interface VoteResult {
        data object NoActivePoll : VoteResult
        data object AlreadyVoted : VoteResult
        data object InvalidOption : VoteResult
        data class Accepted(val option: String) : VoteResult
    }

    private data class ActivePoll(
        val question: String,
        val options: List<String>,
        val votes: MutableList<Int>,
        val voters: MutableSet<String>,
    )

    private var active: ActivePoll? = null

    val current: Poll?
        get() = active?.let { Poll(it.question, it.options, it.votes.toList()) }

    fun start(question: String, options: List<String>): StartResult {
        if (active != null) return StartResult.AlreadyActive
        val normalizedQuestion = question.trim()
        val normalizedOptions = options.map(String::trim)
        if (normalizedQuestion.isBlank() || normalizedQuestion.length > MAX_QUESTION_LENGTH) {
            return StartResult.Invalid("Die Frage muss 1–$MAX_QUESTION_LENGTH Zeichen enthalten.")
        }
        if (normalizedOptions.size !in MIN_OPTIONS..MAX_OPTIONS) {
            return StartResult.Invalid("Ein Poll braucht $MIN_OPTIONS–$MAX_OPTIONS Antwortoptionen.")
        }
        if (normalizedOptions.any { it.isBlank() || it.length > MAX_OPTION_LENGTH }) {
            return StartResult.Invalid("Jede Antwortoption muss 1–$MAX_OPTION_LENGTH Zeichen enthalten.")
        }
        if (normalizedOptions.map(String::lowercase).toSet().size != normalizedOptions.size) {
            return StartResult.Invalid("Die Antwortoptionen müssen eindeutig sein.")
        }
        active = ActivePoll(
            question = normalizedQuestion,
            options = normalizedOptions,
            votes = MutableList(normalizedOptions.size) { 0 },
            voters = mutableSetOf(),
        )
        return StartResult.Started
    }

    fun vote(userId: String, selection: String): VoteResult {
        val poll = active ?: return VoteResult.NoActivePoll
        val voter = userId.trim()
        if (voter.isBlank() || voter in poll.voters) return VoteResult.AlreadyVoted
        val optionIndex = selection.trim().toIntOrNull()?.let { it - 1 }
            ?: poll.options.indexOfFirst { it.equals(selection.trim(), ignoreCase = true) }
        if (optionIndex !in poll.options.indices) return VoteResult.InvalidOption
        poll.voters += voter
        poll.votes[optionIndex] += 1
        return VoteResult.Accepted(poll.options[optionIndex])
    }

    fun end(): Poll? {
        val result = current
        active = null
        return result
    }

    companion object {
        const val MIN_OPTIONS = 2
        const val MAX_OPTIONS = 4
        const val MAX_QUESTION_LENGTH = 160
        const val MAX_OPTION_LENGTH = 40
    }
}
