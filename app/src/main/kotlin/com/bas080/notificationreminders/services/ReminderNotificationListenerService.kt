package com.bas080.notificationreminders.services

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.MainActivity
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
        const val EXTRA_REMINDER_TEXT = "extra_reminder_text"
        const val KEY_TEXT_REPLY = "key_text_reply"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val COOL_DOWN_MS = 10 * 60 * 1000L // 10 minutes cool-down per notification match

        val lastTriggeredMap = ConcurrentHashMap<String, Long>()

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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        showStatusNotification()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        showStatusNotification()
        return START_STICKY
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        showStatusNotification()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null || sbn.packageName == packageName) return

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
            if (ReminderMatcher.matches(reminder, fullContent, commonWordsSet)) {
                val trimmed = reminder.trim()
                val trackingKey = "${sbnKey}_${trimmed.lowercase()}"
                val lastTime = lastTriggeredMap[trackingKey] ?: 0L

                if (now - lastTime >= COOL_DOWN_MS) {
                    lastTriggeredMap[trackingKey] = now
                    postMatchNotification(trimmed)
                }
                break
            }
        }
    }

    private fun postMatchNotification(matchedReminder: String) {
        try {
            val notificationId = getNotificationIdForReminder(matchedReminder)

            val intent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

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

            val matchNotification = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(matchedReminder)
                .setAutoCancel(true)
                .setContentIntent(pendingIntent)
                .addAction(doneAction)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val summaryIntent = Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            val summaryPendingIntent: PendingIntent = PendingIntent.getActivity(
                this,
                SUMMARY_NOTIFICATION_ID,
                summaryIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val summaryNotification = NotificationCompat.Builder(this, MATCH_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setStyle(NotificationCompat.InboxStyle().setSummaryText("Matched Reminders"))
                .setContentIntent(summaryPendingIntent)
                .setAutoCancel(false)
                .setGroup(GROUP_KEY_REMINDERS)
                .setGroupSummary(true)
                .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .build()

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.notify(notificationId, matchNotification)
            notificationManager.notify(SUMMARY_NOTIFICATION_ID, summaryNotification)
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

            val matchChannel = NotificationChannel(
                MATCH_CHANNEL_ID,
                "Reminder Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for matched reminders"
            }

            val notificationManager: NotificationManager =
                getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(statusChannel)
            notificationManager.createNotificationChannel(matchChannel)
        }
    }

    private fun showStatusNotification() {
        try {
            val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val savedReminders = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
            val activeCount = savedReminders.filter { it.trim().isNotEmpty() }.size

            val statusText = if (activeCount == 1) {
                "Monitoring 1 active reminder"
            } else {
                "Monitoring $activeCount active reminders"
            }
            val expandHintText = "$statusText. Tap '+' to add a reminder."

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

            val addAction = NotificationCompat.Action.Builder(
                R.drawable.ic_action_add,
                getString(R.string.add_reminder),
                addReminderPendingIntent
            )
                .addRemoteInput(remoteInput)
                .build()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification_reminder)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(expandHintText)
                .setStyle(NotificationCompat.BigTextStyle().bigText(expandHintText))
                .setOngoing(true)
                .addAction(addAction)
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
