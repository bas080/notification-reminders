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

        fun parseSnoozeDuration(input: String?): Pair<Long, String> {
            val raw = input?.trim()?.lowercase() ?: ""
            if (raw.isEmpty()) {
                return Pair(60 * 60 * 1000L, "1 hour")
            }

            return when {
                raw == "15m" || raw == "15 mins" || raw == "15 minutes" || raw == "15min" ->
                    Pair(15 * 60 * 1000L, "15 minutes")
                raw == "1h" || raw == "1 hour" || raw == "1 hr" || raw == "1hour" ->
                    Pair(60 * 60 * 1000L, "1 hour")
                raw == "4h" || raw == "4 hours" || raw == "4 hrs" || raw == "4hour" ->
                    Pair(4 * 60 * 60 * 1000L, "4 hours")
                raw == "24h" || raw == "1 day" || raw == "24 hours" || raw == "24 hrs" || raw == "1day" ->
                    Pair(24 * 60 * 60 * 1000L, "24 hours")
                else -> {
                    val numberMatch = Regex("^(\\d+)\\s*([mhd]?)$").find(raw)
                    if (numberMatch != null) {
                        val num = numberMatch.groupValues[1].toLongOrNull() ?: 1L
                        val unit = numberMatch.groupValues[2]
                        when (unit) {
                            "m" -> Pair(num * 60 * 1000L, if (num == 1L) "1 minute" else "$num minutes")
                            "d" -> Pair(num * 24 * 60 * 60 * 1000L, if (num == 1L) "1 day" else "$num days")
                            else -> Pair(num * 60 * 60 * 1000L, if (num == 1L) "1 hour" else "$num hours")
                        }
                    } else {
                        Pair(60 * 60 * 1000L, "1 hour")
                    }
                }
            }
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
