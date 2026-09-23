package com.bas080.notificationreminders.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.PickNotificationActivity
import com.bas080.notificationreminders.R
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.utils.ReminderMatcher
import java.util.concurrent.ConcurrentHashMap

class ReminderNotificationListenerService : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "notification_reminders_status_channel"
        const val MATCH_CHANNEL_ID = "notification_reminders_match_channel"
        const val NOTIFICATION_ID = 1001
        const val SUMMARY_NOTIFICATION_ID = 1000
        const val GROUP_KEY_REMINDERS = "com.bas080.notificationreminders.REMINDER_MATCHES"
        const val ACTION_CREATE_REMINDER = "com.bas080.notificationreminders.ACTION_CREATE_REMINDER"
        const val ACTION_DONE_REMINDER = "com.bas080.notificationreminders.ACTION_DONE_REMINDER"
        const val ACTION_SNOOZE_REMINDER = "com.bas080.notificationreminders.ACTION_SNOOZE_REMINDER"
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val KEY_TEXT_REPLY = "key_text_reply"
        const val KEY_SNOOZE_REPLY = "key_snooze_reply"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val COOL_DOWN_MS = 10 * 60 * 1000L // 10 minutes cool-down per notification match

        var instance: ReminderNotificationListenerService? = null
        val lastTriggeredMap = ConcurrentHashMap<String, Long>()
        val activePostedReminders = ConcurrentHashMap.newKeySet<String>()

        fun startService(context: Context) {
            try {
                val intent = Intent(context, ReminderNotificationListenerService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (_: Exception) {
            }
        }

        fun getNotificationIdForReminder(reminder: String): Int {
            val hash = reminder.trim().lowercase().hashCode() and 0x7fffffff
            return if (hash == NOTIFICATION_ID || hash == SUMMARY_NOTIFICATION_ID) 1002 else if (hash == 0) 1003 else hash
        }

        fun getTopSnoozeChoices(context: Context): Array<CharSequence> {
            val defaultChoices = listOf("15m", "1h", "4h", "24h", "1w")
            val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)
            val allEntries = prefs.all
            if (allEntries.isEmpty()) {
                return defaultChoices.toTypedArray()
            }

            val sortedUserChoices = allEntries.entries
                .mapNotNull { entry ->
                    val timestamp = (entry.value as? Number)?.toLong() ?: 0L
                    if (timestamp > 0L) entry.key to timestamp else null
                }
                .sortedByDescending { it.second }
                .map { it.first }

            val combined = mutableListOf<String>()
            for (choice in sortedUserChoices) {
                if (!combined.contains(choice) && combined.size < 5) {
                    combined.add(choice)
                }
            }
            for (defaultChoice in defaultChoices) {
                if (!combined.contains(defaultChoice) && combined.size < 5) {
                    combined.add(defaultChoice)
                }
            }
            combined.sortBy { choice ->
                CreateReminderReceiver.parseSnoozeDuration(choice)?.first ?: Long.MAX_VALUE
            }
            return Array(combined.size) { combined[it] }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        showStatusNotification()
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showStatusNotification()
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        com.bas080.notificationreminders.utils.AppLogger.log(this, "NotificationListener", "Listener connected")
        showStatusNotification()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return

        val extras = sbn.notification?.extras ?: return
        val title = extras.getCharSequence("android.title")?.toString() ?: ""
        val text = extras.getCharSequence("android.text")?.toString() ?: ""
        val fullContent = "$title $text"

        val sbnKey = sbn.key ?: "${sbn.packageName}_${sbn.id}"
        checkAndTriggerReminderMatch(fullContent, sbnKey)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn == null) return
        val sbnKey = sbn.key ?: "${sbn.packageName}_${sbn.id}"
        lastTriggeredMap.keys.removeIf { it.startsWith(sbnKey) }
    }

    private fun checkAndTriggerReminderMatch(fullContent: String, sbnKey: String) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        val now = System.currentTimeMillis()

        val commonWordsStr = getString(R.string.common_words)
        val commonWordsSet = ReminderMatcher.parseCommonWords(commonWordsStr)

        for (reminder in savedReminders) {
            if (reminder.contains("#done", ignoreCase = true)) {
                continue
            }

            val trimmed = reminder.trim()
            val lower = trimmed.lowercase()
            val trackingKey = "${sbnKey}_$lower"
            val lastTime = lastTriggeredMap[trackingKey] ?: 0L
            val snoozeUntil = prefs.getLong("snooze_$lower", 0L).let {
                if (it > 0L) it else (lastTriggeredMap["snooze_$lower"] ?: 0L)
            }

            val isSnoozed = (snoozeUntil > 0L && now < snoozeUntil)
            if (isSnoozed) {
                // Snooze overrules notification match; do not show notification while snoozed
                continue
            }

            val isSnoozeExpired = (snoozeUntil > 0L && now >= snoozeUntil)
            if (isSnoozeExpired) {
                prefs.edit().remove("snooze_$lower").apply()
                lastTriggeredMap.remove("snooze_$lower")
            }

            val isWordMatch = ReminderMatcher.matches(reminder, fullContent, commonWordsSet)

            if (isWordMatch) {
                if (now - lastTime >= COOL_DOWN_MS) {
                    lastTriggeredMap[trackingKey] = now
                    postMatchNotification(trimmed, isHighPriority = true)
                    break
                }
            } else if (isSnoozeExpired) {
                lastTriggeredMap[trackingKey] = now
                postMatchNotification(trimmed, isHighPriority = false)
                break
            }
        }
    }

    fun postMatchNotification(matchedReminder: String, isHighPriority: Boolean = true) {
        try {
            com.bas080.notificationreminders.utils.AppLogger.log(this, "NotificationListener", "Posting notification alert for reminder")
            activePostedReminders.add(matchedReminder)
            val notificationId = getNotificationIdForReminder(matchedReminder)

            val doneIntent = Intent(this, CreateReminderReceiver::class.java).apply {
                action = ACTION_DONE_REMINDER
                putExtra(EXTRA_REMINDER_TEXT, matchedReminder)
            }
            val donePendingIntent = PendingIntent.getBroadcast(
                this,
                notificationId,
                doneIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val doneAction = NotificationCompat.Action.Builder(
                R.drawable.ic_action_done,
                "Done",
                donePendingIntent
            ).build()

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, matchedReminder)
            }
            val chooserIntent = Intent.createChooser(shareIntent, null).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val sharePendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                chooserIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val shareAction = NotificationCompat.Action.Builder(
                R.drawable.ic_action_share,
                getString(R.string.share),
                sharePendingIntent
            ).build()

            val singleSwipeIntent = Intent(this, com.bas080.notificationreminders.SnoozeDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(com.bas080.notificationreminders.SnoozeDialogActivity.EXTRA_REMINDER_TEXT, matchedReminder)
            }
            val singleSwipePendingIntent = PendingIntent.getActivity(
                this,
                notificationId + 10000,
                singleSwipeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val groupSwipeIntent = Intent(this, com.bas080.notificationreminders.SnoozeDialogActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(com.bas080.notificationreminders.SnoozeDialogActivity.EXTRA_REMINDER_LIST, activePostedReminders.toTypedArray())
            }
            val groupSwipePendingIntent = PendingIntent.getActivity(
                this,
                SUMMARY_NOTIFICATION_ID + 10000,
                groupSwipeIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val priorityVal = if (isHighPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT

            val matchNotification = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(matchedReminder)
                .setAutoCancel(true)
                .addAction(doneAction)
                .addAction(shareAction)
                .setDeleteIntent(singleSwipePendingIntent)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(priorityVal)
                .build()

            val summaryNotification = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(getString(R.string.app_name))
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("Matched Reminders"))
                .setAutoCancel(false)
                .setDeleteIntent(groupSwipePendingIntent)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(priorityVal)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, matchNotification)
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
            showStatusNotification()
        } catch (_: Exception) {
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = getString(R.string.app_name)
            val descriptionText = "Status notification for Notification Reminders"
            val statusChannel = NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                description = descriptionText
            }

            val audioAttributes = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .build()
            val soundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

            val matchChannel = NotificationChannel(
                MATCH_CHANNEL_ID,
                "Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for matched reminders"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 250, 250)
                setSound(soundUri, audioAttributes)
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(matchChannel)
        }
    }

    fun showStatusNotification() {
        try {
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
            val count = savedReminders.size
            val statusText = when (count) {
                0 -> getString(R.string.no_active_reminders)
                1 -> getString(R.string.active_reminder_single)
                else -> getString(R.string.active_reminders_count, count)
            }

            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel(getString(R.string.add_reminder))
                .build()

            val addReminderIntent = Intent(this, CreateReminderReceiver::class.java).apply {
                action = ACTION_CREATE_REMINDER
            }
            val broadcastFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val addReminderPendingIntent: PendingIntent = PendingIntent.getBroadcast(
                this,
                1,
                addReminderIntent,
                broadcastFlags
            )

            val fromTextAction = NotificationCompat.Action.Builder(
                R.drawable.ic_action_add,
                getString(R.string.from_text),
                addReminderPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()

            val fromNotifIntent = Intent(this, PickNotificationActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val fromNotifPendingIntent = PendingIntent.getActivity(
                this,
                2,
                fromNotifIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            val fromNotifAction = NotificationCompat.Action.Builder(
                R.drawable.ic_action_add,
                getString(R.string.from_notification),
                fromNotifPendingIntent
            ).build()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_status)
                .setContentTitle(getString(R.string.add_reminder))
                .setContentText(statusText)
                .setOngoing(true)
                .addAction(fromTextAction)
                .addAction(fromNotifAction)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (_: Exception) {
        }
    }
}
