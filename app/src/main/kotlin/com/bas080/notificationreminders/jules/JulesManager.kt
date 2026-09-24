package com.bas080.notificationreminders.jules

import android.content.Context
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import com.bas080.notificationreminders.utils.TagUtils
import java.io.BufferedReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONArray
import org.json.JSONObject

object JulesManager {

    private const val PREFS_REMINDERS = "reminders_prefs"
    private const val KEY_REMINDERS = "key_reminders_list"
    private val isProcessing = AtomicBoolean(false)

    /**
     * Checks if a reminder contains all required AND tags (case-insensitive)
     * and is not marked as #done. Uses exact tag token matching to prevent substring mismatches.
     */
    fun matchesAndTags(reminderText: String, requiredAndTags: List<String>): Boolean {
        if (reminderText.contains("#done", ignoreCase = true)) {
            return false
        }
        if (requiredAndTags.isEmpty()) {
            return false
        }
        val reminderTags = TagUtils.extractTags(reminderText)
        return requiredAndTags.all { requiredTag ->
            reminderTags.contains(requiredTag.lowercase())
        }
    }

    /**
     * Filters a list of reminders to find those matching all required AND tags.
     */
    fun findMatchingReminders(reminders: List<String>, requiredAndTags: List<String>): List<String> {
        if (requiredAndTags.isEmpty()) return emptyList()
        return reminders.filter { matchesAndTags(it, requiredAndTags) }
    }

    /**
     * Applies tag replacements/removals to a reminder string using TagUtils.
     * Replaces tag with new string or removes tag if replacement is empty.
     * Any required AND tag not explicitly configured in replacements is removed by default.
     */
    fun applyTagReplacements(
        reminderText: String,
        replacements: Map<String, String>,
        requiredAndTags: List<String> = emptyList()
    ): String {
        val effectiveReplacements = mutableMapOf<String, String>()

        // Default all required AND tags to empty removal
        for (tag in requiredAndTags) {
            effectiveReplacements[tag.lowercase()] = ""
        }
        // Explicit user tag replacement rules take priority
        for ((tag, replacement) in replacements) {
            effectiveReplacements[tag.lowercase()] = replacement
        }

        if (effectiveReplacements.isEmpty()) return reminderText

        var result = reminderText
        for ((tag, replacement) in effectiveReplacements) {
            result = TagUtils.replaceTag(result, tag, replacement)
        }

        return result
    }

    /**
     * Main sync function to process pending Jules tasks if online and latest session is idle.
     */
    fun checkAndProcessJulesQueue(context: Context) {
        val settings = JulesSettings(context)
        if (!settings.enabled) return

        val requiredAndTags = settings.getParsedAndTags()
        if (requiredAndTags.isEmpty()) return

        if (!isProcessing.compareAndSet(false, true)) {
            return
        }

        // Asynchronously check Jules session status and dispatch task if idle
        Thread {
            try {
                val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                val currentReminders = (prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()).toList()
                val matchingReminders = findMatchingReminders(currentReminders, requiredAndTags)

                if (matchingReminders.isNotEmpty()) {
                    val targetReminder = matchingReminders.first()
                    val isIdle = isLatestSessionIdle(settings)
                    if (isIdle) {
                        val sendSuccess = sendTaskToSession(settings, targetReminder)
                        if (sendSuccess) {
                            // Dynamically re-read preferences right before saving to prevent overwriting user edits
                            val latestSavedList = (prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()).toMutableList()
                            val updatedReminder = applyTagReplacements(
                                targetReminder,
                                settings.getParsedTagReplacements(),
                                requiredAndTags
                            )

                            val idx = latestSavedList.indexOf(targetReminder)
                            if (idx != -1) {
                                if (updatedReminder.isNotBlank()) {
                                    latestSavedList[idx] = updatedReminder
                                } else {
                                    latestSavedList.removeAt(idx)
                                }
                                prefs.edit().putStringSet(KEY_REMINDERS, latestSavedList.toSet()).apply()
                                ReminderNotificationListenerService.instance?.showStatusNotification()
                            }

                            AppLogger.log(context, "JulesManager", "Task sent to Jules: '$targetReminder' -> '$updatedReminder'")
                        }
                    }
                }
            } catch (e: Exception) {
                // Offline friendly: log non-fatal diagnostic and keep work queued for next poll
                AppLogger.log(context, "JulesManager", "Jules sync skipped/failed: ${e.message}")
            } finally {
                isProcessing.set(false)
            }
        }.start()
    }

    /**
     * Checks whether the latest session is idle via HTTP GET request.
     */
    fun isLatestSessionIdle(settings: JulesSettings): Boolean {
        val urlStr = "${settings.baseUrl.trimEnd('/')}/v1/sessions/latest"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (settings.apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

        return try {
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val json = JSONObject(responseText)
                val status = json.optString("status", "idle")
                val state = json.optString("state", "idle")
                val isBusy = json.optBoolean("busy", false)
                !isBusy && (status.equals("idle", ignoreCase = true) || state.equals("idle", ignoreCase = true))
            } else {
                false
            }
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Fetches available codebases from Jules API endpoint.
     */
    fun fetchAvailableCodebases(settings: JulesSettings): List<String> {
        val urlStr = "${settings.baseUrl.trimEnd('/')}/v1/codebases"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        if (settings.apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

        return try {
            val responseCode = conn.responseCode
            if (responseCode in 200..299) {
                val responseText = conn.inputStream.bufferedReader().use(BufferedReader::readText)
                val codebases = mutableListOf<String>()
                if (responseText.trim().startsWith("[")) {
                    val array = JSONArray(responseText)
                    for (i in 0 until array.length()) {
                        val item = array.opt(i)
                        if (item is JSONObject) {
                            val name = item.optString("name", item.optString("id", ""))
                            if (name.isNotBlank()) codebases.add(name)
                        } else if (item is String && item.isNotBlank()) {
                            codebases.add(item)
                        }
                    }
                } else if (responseText.trim().startsWith("{")) {
                    val obj = JSONObject(responseText)
                    val array = obj.optJSONArray("codebases") ?: obj.optJSONArray("sources") ?: obj.optJSONArray("projects")
                    if (array != null) {
                        for (i in 0 until array.length()) {
                            val item = array.opt(i)
                            if (item is JSONObject) {
                                val name = item.optString("name", item.optString("id", ""))
                                if (name.isNotBlank()) codebases.add(name)
                            } else if (item is String && item.isNotBlank()) {
                                codebases.add(item)
                            }
                        }
                    }
                }
                codebases.distinct()
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        } finally {
            conn.disconnect()
        }
    }

    /**
     * Sends a reminder task to the latest Jules session via HTTP POST request.
     */
    fun sendTaskToSession(settings: JulesSettings, taskText: String): Boolean {
        val urlStr = "${settings.baseUrl.trimEnd('/')}/v1/sessions/latest/tasks"
        val url = URL(urlStr)
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 5000
        conn.readTimeout = 5000
        conn.doOutput = true
        conn.setRequestProperty("Content-Type", "application/json")
        if (settings.apiKey.isNotBlank()) {
            conn.setRequestProperty("Authorization", "Bearer ${settings.apiKey}")
        }

        val jsonBody = JSONObject().apply {
            put("task", taskText)
            put("content", taskText)
            if (settings.selectedCodebase.isNotBlank()) {
                put("codebase", settings.selectedCodebase)
            }
        }

        return try {
            OutputStreamWriter(conn.outputStream).use { writer ->
                writer.write(jsonBody.toString())
                writer.flush()
            }
            val responseCode = conn.responseCode
            responseCode in 200..299
        } finally {
            conn.disconnect()
        }
    }
}
