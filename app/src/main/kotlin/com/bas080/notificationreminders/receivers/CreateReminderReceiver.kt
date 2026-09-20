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

        fun parseSnoozeDuration(input: String?, nowMillis: Long = System.currentTimeMillis()): Pair<Long, String> {
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

            // 12-hour AM/PM absolute time format e.g. "7pm", "1am", "11:30am", "7:00 pm", "12pm"
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

            // 24-hour time format with colon e.g. "18:00", "09:30", "9:30", "0:15"
            val timeColonMatch = Regex("^(\\d{1,2}):(\\d{2})$").find(raw)
            if (timeColonMatch != null) {
                val hour = timeColonMatch.groupValues[1].toInt()
                val min = timeColonMatch.groupValues[2].toInt()
                if (hour in 0..23 && min in 0..59) {
                    return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
                }
            }

            // 24-hour time format without colon e.g. "1800", "0930", "0800", "930"
            if (raw.length in 3..4 && raw.all { it.isDigit() }) {
                val hour = if (raw.length == 4) raw.substring(0, 2).toInt() else raw.substring(0, 1).toInt()
                val min = if (raw.length == 4) raw.substring(2, 4).toInt() else raw.substring(1, 3).toInt()
                if (hour in 0..23 && min in 0..59) {
                    return calculateAbsoluteTimeSnooze(hour, min, nowMillis)
                }
            }

            // Relative unit duration e.g. "2w", "30m", "2h", "3d", "5"
            val numberMatch = Regex("^(\\d+)\\s*([mhdw]?)$").find(raw)
            if (numberMatch != null) {
                val num = numberMatch.groupValues[1].toLongOrNull() ?: 1L
                val unit = numberMatch.groupValues[2]
                return when (unit) {
                    "m" -> Pair(num * 60 * 1000L, if (num == 1L) "1 minute" else "$num minutes")
                    "d" -> Pair(num * 24 * 60 * 60 * 1000L, if (num == 1L) "1 day" else "$num days")
                    "w" -> Pair(num * 7 * 24 * 60 * 60 * 1000L, if (num == 1L) "1 week" else "$num weeks")
                    else -> Pair(num * 60 * 60 * 1000L, if (num == 1L) "1 hour" else "$num hours")
                }
            }

            return Pair(60 * 60 * 1000L, "1 hour")
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
                        val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
                        savedSet.add(reminderText)
                        prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

                        ReminderNotificationListenerService.instance?.postMatchNotification(reminderText)
                        ReminderNotificationListenerService.instance?.showStatusNotification()
                            ?: ReminderNotificationListenerService.startService(context)
                        Toast.makeText(context, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
                    }
                }
            }
            ReminderNotificationListenerService.ACTION_DONE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
                    savedSet.remove(reminderText)
                    prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    ReminderNotificationListenerService.instance?.showStatusNotification()
                        ?: ReminderNotificationListenerService.startService(context)

                    Toast.makeText(context, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
                }
            }
            ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER -> {
                val reminderText = intent.getStringExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT)
                if (!reminderText.isNullOrEmpty()) {
                    val remoteResults = RemoteInput.getResultsFromIntent(intent)
                    val chosenDurationStr = remoteResults?.getCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY)?.toString()

                    val (snoozeMs, durationLabel) = parseSnoozeDuration(chosenDurationStr)
                    val snoozeUntil = System.currentTimeMillis() + snoozeMs
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    notificationManager.cancel(notificationId)

                    val toastText = context.getString(R.string.toast_reminder_snoozed_duration, durationLabel)
                    Toast.makeText(context, toastText, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
