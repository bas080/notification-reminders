package com.bas080.notificationreminders.receivers

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.R
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

class CreateReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"

        fun canonicalizeSnoozeChoice(input: String?): String? {
            val raw = input?.trim()?.lowercase() ?: ""
            if (raw.isEmpty()) return "1h"

            when {
                raw == "15m" || raw == "15 mins" || raw == "15 minutes" || raw == "15min" -> return "15m"
                raw == "1h" || raw == "1 hour" || raw == "1 hr" || raw == "1hour" -> return "1h"
                raw == "4h" || raw == "4 hours" || raw == "4 hrs" || raw == "4hour" -> return "4h"
                raw == "24h" || raw == "1 day" || raw == "24 hours" || raw == "24 hrs" || raw == "1day" -> return "24h"
                raw == "1w" || raw == "1 week" || raw == "1week" || raw == "w" -> return "1w"
            }

            val weekdayResult = parseWeekdaySnooze(raw, System.currentTimeMillis())
            if (weekdayResult != null) {
                return weekdayResult.second
            }

            val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(raw)
            if (amPmMatch != null) {
                var hour = amPmMatch.groupValues[1].toInt()
                val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
                val amPm = amPmMatch.groupValues[3]
                if (hour in 1..12 && min in 0..59) {
                    if (amPm == "pm" && hour < 12) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    return String.format(java.util.Locale.US, "%02d:%02d", hour, min)
                }
            }

            val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(raw)
            if (timeColonMatch != null) {
                val hour = timeColonMatch.groupValues[1].toInt()
                val min = timeColonMatch.groupValues[2].toInt()
                if (hour in 0..23 && min in 0..59) {
                    return String.format(java.util.Locale.US, "%02d:%02d", hour, min)
                }
            }

            if (raw.length in 3..4 && raw.all { it.isDigit() }) {
                val hour = if (raw.length == 4) raw.substring(0, 2).toInt() else raw.substring(0, 1).toInt()
                val min = if (raw.length == 4) raw.substring(2, 4).toInt() else raw.substring(1, 3).toInt()
                if (hour in 0..23 && min in 0..59) {
                    return String.format(java.util.Locale.US, "%02d:%02d", hour, min)
                }
            }

            val numberMatch = Regex("^(\\d+)\\s*([mhdw]?)$").find(raw)
            if (numberMatch != null) {
                val num = numberMatch.groupValues[1].toLongOrNull() ?: return null
                val unit = numberMatch.groupValues[2].ifEmpty { "h" }
                if (num <= 0) return null
                return "$num$unit"
            }

            return null
        }

        fun parseSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String>? {
            val raw = input?.trim()?.lowercase() ?: ""
            if (raw.isEmpty()) {
                return Pair(60 * 60 * 1000L, "1 hour")
            }

            when {
                raw == "15m" || raw == "15 mins" || raw == "15 minutes" || raw == "15min" ->
                    return Pair(15 * 60 * 1000L, "15 minutes")
                raw == "1h" || raw == "1 hour" || raw == "1 hr" || raw == "1hour" ->
                    return Pair(60 * 60 * 1000L, "1 hour")
                raw == "4h" || raw == "4 hours" || raw == "4 hrs" || raw == "4hour" ->
                    return Pair(4 * 60 * 60 * 1000L, "4 hours")
                raw == "24h" || raw == "1 day" || raw == "24 hours" || raw == "24 hrs" || raw == "1day" ->
                    return Pair(24 * 60 * 60 * 1000L, "24 hours")
                raw == "1w" || raw == "1 week" || raw == "1week" || raw == "w" ->
                    return Pair(7 * 24 * 60 * 60 * 1000L, "1 week")
            }

            val weekdayResult = parseWeekdaySnooze(raw, nowMillis)
            if (weekdayResult != null) {
                return weekdayResult.first
            }

            val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(raw)
            if (amPmMatch != null) {
                var hour = amPmMatch.groupValues[1].toInt()
                val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
                val amPm = amPmMatch.groupValues[3]

                if (hour in 1..12 && min in 0..59) {
                    if (amPm == "pm" && hour < 12) hour += 12
                    if (amPm == "am" && hour == 12) hour = 0
                    return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
                }
            }

            val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(raw)
            if (timeColonMatch != null) {
                val hour = timeColonMatch.groupValues[1].toInt()
                val min = timeColonMatch.groupValues[2].toInt()
                if (hour in 0..23 && min in 0..59) {
                    return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
                }
            }

            if (raw.length in 3..4 && raw.all { it.isDigit() }) {
                val hour = if (raw.length == 4) raw.substring(0, 2).toInt() else raw.substring(0, 1).toInt()
                val min = if (raw.length == 4) raw.substring(2, 4).toInt() else raw.substring(1, 3).toInt()
                if (hour in 0..23 && min in 0..59) {
                    return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
                }
            }

            val numberMatch = Regex("^(\\d+)\\s*([mhdw]?)$").find(raw)
            if (numberMatch != null) {
                val num = numberMatch.groupValues[1].toLongOrNull() ?: return null
                val unit = numberMatch.groupValues[2]
                if (num <= 0) return null
                return when (unit) {
                    "m" -> Pair(num * 60 * 1000L, if (num == 1L) "1 minute" else "$num minutes")
                    "d" -> Pair(num * 24 * 60 * 60 * 1000L, if (num == 1L) "1 day" else "$num days")
                    "w" -> Pair(num * 7 * 24 * 60 * 60 * 1000L, if (num == 1L) "1 week" else "$num weeks")
                    else -> Pair(num * 60 * 60 * 1000L, if (num == 1L) "1 hour" else "$num hours")
                }
            }

            return null
        }

        private fun parseWeekdaySnooze(raw: String, nowMillis: Long): Pair<Pair<Long, String>, String>? {
            val weekdayRegex = Regex("\\b(mon|monday|tue|tues|tuesday|wed|wednesday|thu|thur|thurs|thursday|fri|friday|sat|saturday|sun|sunday)\\b")
            val match = weekdayRegex.find(raw) ?: return null
            val weekdayStr = match.value

            val (dayOfWeek, fullDisplayName, shortAbbr) = when (weekdayStr) {
                "mon", "monday" -> Triple(java.util.Calendar.MONDAY, "Monday", "mon")
                "tue", "tues", "tuesday" -> Triple(java.util.Calendar.TUESDAY, "Tuesday", "tue")
                "wed", "wednesday" -> Triple(java.util.Calendar.WEDNESDAY, "Wednesday", "wed")
                "thu", "thur", "thurs", "thursday" -> Triple(java.util.Calendar.THURSDAY, "Thursday", "thu")
                "fri", "friday" -> Triple(java.util.Calendar.FRIDAY, "Friday", "fri")
                "sat", "saturday" -> Triple(java.util.Calendar.SATURDAY, "Saturday", "sat")
                "sun", "sunday" -> Triple(java.util.Calendar.SUNDAY, "Sunday", "sun")
                else -> return null
            }

            val timePart = raw.replace(weekdayStr, "").trim()
            var targetHour = 9
            var targetMin = 0
            var timeSpecified = false

            if (timePart.isNotEmpty()) {
                val amPmMatch = Regex("^(\\d{1,2})(?::(\\d{2}))?\\s*(am|pm)$").find(timePart)
                if (amPmMatch != null) {
                    var hour = amPmMatch.groupValues[1].toInt()
                    val min = amPmMatch.groupValues[2].let { if (it.isEmpty()) 0 else it.toInt() }
                    val amPm = amPmMatch.groupValues[3]
                    if (hour in 1..12 && min in 0..59) {
                        if (amPm == "pm" && hour < 12) hour += 12
                        if (amPm == "am" && hour == 12) hour = 0
                        targetHour = hour
                        targetMin = min
                        timeSpecified = true
                    } else return null
                } else {
                    val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(timePart)
                    if (timeColonMatch != null) {
                        val hour = timeColonMatch.groupValues[1].toInt()
                        val min = timeColonMatch.groupValues[2].toInt()
                        if (hour in 0..23 && min in 0..59) {
                            targetHour = hour
                            targetMin = min
                            timeSpecified = true
                        } else return null
                    } else if (timePart.length in 3..4 && timePart.all { it.isDigit() }) {
                        val hour = if (timePart.length == 4) timePart.substring(0, 2).toInt() else timePart.substring(0, 1).toInt()
                        val min = if (timePart.length == 4) timePart.substring(2, 4).toInt() else timePart.substring(1, 3).toInt()
                        if (hour in 0..23 && min in 0..59) {
                            targetHour = hour
                            targetMin = min
                            timeSpecified = true
                        } else return null
                    } else if (timePart.all { it.isDigit() }) {
                        val hour = timePart.toInt()
                        if (hour in 0..23) {
                            targetHour = hour
                            targetMin = 0
                            timeSpecified = true
                        } else return null
                    } else {
                        return null
                    }
                }
            }

            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMin)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }

            val currentDayOfWeek = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }.get(java.util.Calendar.DAY_OF_WEEK)
            var daysDiff = dayOfWeek - currentDayOfWeek
            if (daysDiff < 0) {
                daysDiff += 7
            } else if (daysDiff == 0 && cal.timeInMillis <= nowMillis) {
                daysDiff = 7
            }

            if (daysDiff > 0) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, daysDiff)
            }

            val snoozeMs = cal.timeInMillis - nowMillis
            val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", targetHour, targetMin)
            val durationLabel = "$fullDisplayName at $timeFormatted"
            val canonicalChoice = if (timeSpecified) "$shortAbbr $timeFormatted" else shortAbbr

            return Pair(Pair(snoozeMs, durationLabel), canonicalChoice)
        }

        private fun calculateAbsoluteTimeSnooze(targetHour: Int, targetMin: Int, nowMillis: Long): Pair<Long, String> {
            val cal = java.util.Calendar.getInstance().apply {
                timeInMillis = nowMillis
                set(java.util.Calendar.HOUR_OF_DAY, targetHour)
                set(java.util.Calendar.MINUTE, targetMin)
                set(java.util.Calendar.SECOND, 0)
                set(java.util.Calendar.MILLISECOND, 0)
            }
            var targetTime = cal.timeInMillis
            var dayLabel = "today"
            if (targetTime <= nowMillis) {
                cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
                targetTime = cal.timeInMillis
                dayLabel = "tomorrow"
            }
            val snoozeMs = targetTime - nowMillis
            val timeFormatted = String.format(java.util.Locale.US, "%02d:%02d", targetHour, targetMin)
            return Pair(snoozeMs, "$dayLabel at $timeFormatted")
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ReminderNotificationListenerService.ACTION_CREATE_REMINDER -> {
                val results = RemoteInput.getResultsFromIntent(intent)
                if (results != null) {
                    val reminderText = results.getCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY)?.toString()?.trim()
                    if (!reminderText.isNullOrEmpty()) {
                        com.bas080.notificationreminders.utils.AppLogger.log(context, "CreateReminderReceiver", "Created reminder from notification reply")
                        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        savedSet.add(reminderText)
                        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

                        ReminderNotificationListenerService.instance?.postMatchNotification(reminderText)
                        ReminderNotificationListenerService.instance?.showStatusNotification()
                            ?: ReminderNotificationListenerService.startService(context)
                        com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                        Toast.makeText(context, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ReminderNotificationListenerService.ACTION_DONE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    com.bas080.notificationreminders.utils.AppLogger.log(context, "CreateReminderReceiver", "Marked reminder done from notification action")
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")

                    val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()

                    if (savedSet.contains(reminderText)) {
                        savedSet.remove(reminderText)
                        val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                            reminderText
                        } else {
                            "$reminderText #done"
                        }
                        savedSet.add(doneText)
                        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).remove("snooze_$trimmed").apply()
                    }

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    ReminderNotificationListenerService.instance?.showStatusNotification()
                        ?: ReminderNotificationListenerService.startService(context)

                    com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                    Toast.makeText(context, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
                }
            }
            ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    val remoteResults = RemoteInput.getResultsFromIntent(intent)
                    val chosenDurationStr = remoteResults?.getCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY)?.toString()

                    val parseResult = parseSnoozeDuration(chosenDurationStr)
                    if (parseResult == null) {
                        Toast.makeText(context, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
                        return
                    }

                    val canonicalChoice = canonicalizeSnoozeChoice(chosenDurationStr)
                    if (canonicalChoice != null) {
                        val freqPrefs = context.getSharedPreferences(PREFS_SNOOZE_FREQ, Context.MODE_PRIVATE)
                        freqPrefs.edit().putLong(canonicalChoice, System.currentTimeMillis()).apply()
                    }

                    val (snoozeMs, durationLabel) = parseResult
                    val snoozeUntil = System.currentTimeMillis() + snoozeMs
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil

                    val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    prefs.edit().putLong("snooze_$trimmed", snoozeUntil).apply()

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    val toastText = context.getString(R.string.toast_reminder_snoozed_duration, durationLabel)
                    com.bas080.notificationreminders.providers.RemindersContentProvider.notifyChange(context)
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
