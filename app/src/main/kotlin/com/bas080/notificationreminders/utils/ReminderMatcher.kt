package com.bas080.notificationreminders.utils

class ReminderMatcher {
    companion object {
        private val commonWords = setOf(
            "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for",
            "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
            "being", "have", "has", "had", "do", "does", "did", "will", "would",
            "could", "should", "may", "might", "must", "can", "as", "if", "while",
            "because", "when", "where", "which", "who", "what", "why", "how",
            "it", "this", "that", "these", "those", "i", "you", "he", "she", "we", "they"
        )

        /**
         * Matches reminder text against notification content
         * Returns true if any significant words from the reminder are found in the notification
         */
        fun matches(reminderText: String, notificationText: String): Boolean {
            val reminderWords = extractSignificantWords(reminderText)
            val notificationWords = extractSignificantWords(notificationText)

            // Check if any reminder word is contained in notification text
            return reminderWords.any { reminderWord ->
                notificationWords.any { notificationWord ->
                    notificationWord.contains(reminderWord, ignoreCase = true)
                }
            }
        }

        /**
         * Extracts significant words from text (filters out common words)
         */
        private fun extractSignificantWords(text: String): List<String> {
            return text.lowercase()
                .split(Regex("\\s+|[,.:;!?]"))
                .filter { it.isNotEmpty() && it !in commonWords }
        }
    }
}
