package com.bas080.notificationreminders.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

class CreateReminderReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val results = RemoteInput.getResultsFromIntent(intent)
        if (results != null) {
            val reminderText = results.getCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY)?.toString()?.trim()
            if (!reminderText.isNullOrEmpty()) {
                val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet())?.toMutableSet() ?: mutableSetOf()
                savedSet.add(reminderText)
                prefs.edit().putStringSet(KEY_REMINDERS, savedSet).apply()

                ReminderNotificationListenerService.startService(context)
            }
        }
    }
}
