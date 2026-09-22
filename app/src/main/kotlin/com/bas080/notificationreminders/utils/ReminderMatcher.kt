package com.bas080.notificationreminders.utils

object ReminderMatcher {

    val DEFAULT_COMMON_WORDS = setOf(
        "a", "about", "above", "after", "again", "against", "all", "am", "an", "and", "any", "are", "aren't",
        "as", "at", "be", "because", "been", "before", "being", "below", "between", "both", "but", "by",
        "can", "can't", "cannot", "could", "couldn't", "did", "didn't", "do", "does", "doesn't", "doing",
        "don't", "down", "during", "each", "few", "for", "from", "further", "had", "hadn't", "has", "hasn't",
        "have", "haven't", "having", "he", "he'd", "he'll", "he's", "her", "here", "here's", "hers", "herself",
        "him", "himself", "his", "how", "how's", "i", "i'd", "i'll", "i'm", "i've", "if", "in", "into", "is",
        "isn't", "it", "it's", "its", "itself", "let's", "me", "more", "most", "mustn't", "my", "myself", "no",
        "nor", "not", "of", "off", "on", "once", "only", "or", "other", "ought", "our", "ours", "ourselves",
        "out", "over", "own", "same", "shan't", "she", "she'd", "she'll", "she's", "should", "shouldn't", "so",
        "some", "such", "than", "that", "that's", "the", "their", "theirs", "them", "themselves", "then",
        "there", "there's", "these", "they", "they'd", "they'll", "they're", "they've", "this", "those",
        "through", "to", "too", "under", "until", "up", "very", "was", "wasn't", "we", "we'd", "we'll",
        "we're", "we've", "were", "weren't", "what", "what's", "when", "when's", "where", "where's",
        "which", "while", "who", "who's", "whom", "why", "why's", "with", "won't", "would", "wouldn't",
        "you", "you'd", "you'll", "you're", "you've", "your", "yours", "yourself", "yourselves"
    )

    fun parseCommonWords(commaSeparated: String): Set<String> {
        if (commaSeparated.isBlank()) return DEFAULT_COMMON_WORDS
        return commaSeparated.lowercase()
            .split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    fun extractNonCommonWords(
        text: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Set<String> {
        return text.lowercase()
            .split(Regex("[^a-zA-Z0-9]+"))
            .filter { word -> word.length >= 2 && word !in commonWords }
            .toSet()
    }

    /**
     * Checks if a reminder matches the notification content based on non-common word matching
     * or substring inclusion.
     */
    fun matches(
        reminder: String,
        notificationContent: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Boolean {
        val trimmed = reminder.trim()
        if (trimmed.isEmpty()) return false

        val reminderWords = extractNonCommonWords(trimmed, commonWords)
        val notificationWords = extractNonCommonWords(notificationContent, commonWords)

        if (reminderWords.isNotEmpty() && notificationWords.isNotEmpty()) {
            if (reminderWords.any { it in notificationWords }) {
                return true
            }
        }

        return notificationContent.contains(trimmed, ignoreCase = true)
    }

    /**
     * Lenient search matching function to check if a reminder matches a search query.
     */
    fun matchesSearchQuery(
        reminder: String,
        query: String,
        commonWords: Set<String> = DEFAULT_COMMON_WORDS
    ): Boolean {
        val q = query.trim()
        if (q.isEmpty()) return true

        // 1. Direct case-insensitive substring match
        if (reminder.contains(q, ignoreCase = true)) return true

        // 2. Normalized alphanumeric match
        val normalizedReminder = reminder.lowercase().replace(Regex("[^a-z0-9]+"), " ")
        val normalizedQuery = q.lowercase().replace(Regex("[^a-z0-9]+"), " ")

        if (normalizedReminder.contains(normalizedQuery)) return true

        // 3. Token-level matching: any query token matches a reminder token
        val queryTokens = normalizedQuery.split(Regex("\\s+")).filter { it.length >= 2 }
        val reminderTokens = normalizedReminder.split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (queryTokens.isNotEmpty()) {
            val hasTokenMatch = queryTokens.any { qToken ->
                reminderTokens.any { rToken -> rToken.contains(qToken) || qToken.contains(rToken) }
            }
            if (hasTokenMatch) return true
        }

        // 4. Non-common word overlap
        val nonCommonQueryWords = extractNonCommonWords(q, commonWords)
        val nonCommonReminderWords = extractNonCommonWords(reminder, commonWords)
        if (nonCommonQueryWords.isNotEmpty() && nonCommonReminderWords.isNotEmpty()) {
            if (nonCommonQueryWords.any { qWord -> nonCommonReminderWords.any { rWord -> rWord.contains(qWord) || qWord.contains(rWord) } }) {
                return true
            }
        }

        return false
    }
}
