package com.bas080.notificationreminders.utils

object TagUtils {

    private val TAG_REGEX = Regex("#[a-zA-Z0-9_]+")

    /**
     * Extracts all tag tokens (e.g. ["#groceries", "#work"]) from text.
     */
    fun extractTags(text: String): List<String> {
        return TAG_REGEX.findAll(text)
            .map { it.value.lowercase() }
            .distinct()
            .toList()
    }

    /**
     * Safely replaces or removes a tag token in text based on whitespace/boundary matches.
     * Ensures replacing e.g. #todo does not alter #todolist.
     */
    fun replaceTag(text: String, tag: String, replacement: String): String {
        val trimmedTag = tag.trim()
        if (trimmedTag.isBlank()) return text

        val tagPattern = Regex("(?i)(?<=^|\\s)${Regex.escape(trimmedTag)}(?=\\s|$)")
        val result = if (replacement.isNotBlank()) {
            text.replace(tagPattern, replacement)
        } else {
            text.replace(tagPattern, "")
        }

        return result.replace(Regex("\\s+"), " ").trim()
    }

    /**
     * Safely removes a tag token from text.
     */
    fun removeTag(text: String, tag: String): String {
        return replaceTag(text, tag, "")
    }
}
