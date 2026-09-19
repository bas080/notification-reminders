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

                        ReminderNotificationListenerService.startService(context)
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

                    Toast.makeText(context, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
