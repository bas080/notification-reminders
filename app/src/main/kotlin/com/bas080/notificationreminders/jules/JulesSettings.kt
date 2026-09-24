package com.bas080.notificationreminders.jules

import android.content.Context
import android.content.SharedPreferences

class JulesSettings(context: Context) {

    companion object {
        private const val PREFS_NAME = "jules_prefs"
        private const val KEY_ENABLED = "jules_enabled"
        private const val KEY_API_KEY = "jules_api_key"
        private const val KEY_AND_TAGS = "jules_and_tags"
        private const val KEY_TAG_REPLACEMENTS = "jules_tag_replacements"
        private const val KEY_SELECTED_CODEBASE = "jules_selected_codebase"
        const val DEFAULT_BASE_URL = "https://api.jules.ai"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_ENABLED, value).apply()

    val baseUrl: String
        get() = DEFAULT_BASE_URL

    var apiKey: String
        get() = prefs.getString(KEY_API_KEY, "") ?: ""
        set(value) = prefs.edit().putString(KEY_API_KEY, value.trim()).apply()

    var andTags: String
        get() = prefs.getString(KEY_AND_TAGS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_AND_TAGS, value.trim()).apply()

    var tagReplacements: String
        get() = prefs.getString(KEY_TAG_REPLACEMENTS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_TAG_REPLACEMENTS, value.trim()).apply()

    var selectedCodebase: String
        get() = prefs.getString(KEY_SELECTED_CODEBASE, "") ?: ""
        set(value) = prefs.edit().putString(KEY_SELECTED_CODEBASE, value.trim()).apply()

    /**
     * Parses andTags string into a list of lowercase tag tokens (e.g. ["#jules", "#todo"]).
     */
    fun getParsedAndTags(): List<String> {
        val tagRegex = Regex("#[a-zA-Z0-9_]+")
        return tagRegex.findAll(andTags)
            .map { it.value.lowercase() }
            .distinct()
            .toList()
    }

    /**
     * Parses tagReplacements text into a map of original tag (lowercase) -> replacement tag.
     * Expected format lines: "#jules -> #in-progress" or "#todo -> " (empty string removes tag).
     */
    fun getParsedTagReplacements(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val lines = tagReplacements.lineSequence()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            if (trimmed.contains("->")) {
                val parts = trimmed.split("->", limit = 2)
                val originalTag = parts[0].trim().lowercase()
                val replacement = parts[1].trim()
                if (originalTag.isNotBlank()) {
                    map[originalTag] = replacement
                }
            }
        }
        return map
    }
}
