package com.bas080.notificationreminders.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReminderNotificationListenerServiceTest {

    @Test
    fun testActionCreateReminderConstant() {
        assertEquals(
            "com.bas080.notificationreminders.ACTION_CREATE_REMINDER",
            ReminderNotificationListenerService.ACTION_CREATE_REMINDER
        )
    }

    @Test
    fun testChannelIdConstant() {
        assertEquals(
            "notification_reminders_status_channel",
            ReminderNotificationListenerService.CHANNEL_ID
        )
    }

    @Test
    fun testNotificationIdConstant() {
        assertEquals(
            1001,
            ReminderNotificationListenerService.NOTIFICATION_ID
        )
    }

    @Test
    fun testGetNotificationIdForReminderIsDeterministic() {
        val id1 = ReminderNotificationListenerService.getNotificationIdForReminder("Buy milk")
        val id2 = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val id3 = ReminderNotificationListenerService.getNotificationIdForReminder("  Buy Milk  ")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
        assertNotEquals(ReminderNotificationListenerService.NOTIFICATION_ID, id1)
    }

    @Test
    fun testSummaryNotificationDoesNotAutoCancel() {
        val context = RuntimeEnvironment.getApplication()

        // Set up saved reminder in SharedPreferences
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Create mock StatusBarNotification
        val extras = Bundle().apply {
            putCharSequence("android.title", "Shopping")
            putCharSequence("android.text", "Need to buy milk today")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.otherapp",
            "com.example.otherapp",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val summaryNotif = shadowNM.getNotification(ReminderNotificationListenerService.SUMMARY_NOTIFICATION_ID)
        assertNotNull("Summary notification should be posted", summaryNotif)
        assertFalse(
            "Summary notification should NOT have FLAG_AUTO_CANCEL set",
            (summaryNotif.flags and Notification.FLAG_AUTO_CANCEL) != 0
        )
        org.junit.Assert.assertNull("Summary notification should not have a deleteIntent", summaryNotif.deleteIntent)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        assertNotNull("Matched reminder notification should be posted", matchedNotif)
        assertTrue(
            "Individual match notification should have FLAG_AUTO_CANCEL set",
            (matchedNotif.flags and Notification.FLAG_AUTO_CANCEL) != 0
        )
        org.junit.Assert.assertNull("Matched notification should not have a deleteIntent", matchedNotif.deleteIntent)
        assertEquals("buy milk", matchedNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue(
            "Matched notification should not contain matched notification content in text",
            matchedNotif.extras.getCharSequence(Notification.EXTRA_TEXT) == null
        )
    }

    @Test
    fun testStatusNotificationTitleTextAndContentIntent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Buy milk", "Call mom"))
            .putLong("snooze_call mom", System.currentTimeMillis() + 3600000L)
            .commit()

        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be posted", statusNotif)

        val title = statusNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        assertEquals("Add Reminder", title)

        val statusText = statusNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertEquals("1 active • 1 punted", statusText)

        assertNotNull("Content intent should be set on status notification", statusNotif.contentIntent)
        val shadowPendingIntent = Shadows.shadowOf(statusNotif.contentIntent)
        val targetIntent = shadowPendingIntent.savedIntent
        assertNotNull("Target intent should not be null", targetIntent)
        assertEquals("com.bas080.notificationreminders.MainActivity", targetIntent.component?.className)

        assertNotNull("Status notification should have actions", statusNotif.actions)
        assertEquals(2, statusNotif.actions.size)

        val addAction = statusNotif.actions[0]
        assertEquals("From Text", addAction.title.toString())
        assertNotNull("From Text action should have remoteInputs", addAction.remoteInputs)
        assertEquals(1, addAction.remoteInputs.size)

        val remoteInput = addAction.remoteInputs[0]
        assertEquals(ReminderNotificationListenerService.KEY_TEXT_REPLY, remoteInput.resultKey)
        assertEquals("Add Reminder", remoteInput.label.toString())

        val fromNotifAction = statusNotif.actions[1]
        assertEquals("From Notification", fromNotifAction.title.toString())
    }

    @Test
    fun testMatchedNotificationHasShareAction() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val extras = Bundle().apply {
            putCharSequence("android.title", "Shopping")
            putCharSequence("android.text", "Need to buy milk today")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.otherapp",
            "com.example.otherapp",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        assertNotNull("Matched reminder notification should be posted", matchedNotif)
        assertNotNull("Matched notification actions should not be null", matchedNotif.actions)
        assertEquals(2, matchedNotif.actions.size)

        val doneAction = matchedNotif.actions[0]
        assertEquals("Done", doneAction.title.toString())

        val shareAction = matchedNotif.actions[1]
        assertEquals("Share", shareAction.title.toString())
        assertNotNull("Share action intent should not be null", shareAction.actionIntent)

        val shadowPendingIntent = Shadows.shadowOf(shareAction.actionIntent)
        val chooserIntent = shadowPendingIntent.savedIntent
        assertNotNull("Chooser intent should not be null", chooserIntent)
        assertEquals(Intent.ACTION_CHOOSER, chooserIntent.action)

        @Suppress("DEPRECATION")
        val shareIntent = chooserIntent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertNotNull("Share intent inside chooser should not be null", shareIntent)
        assertEquals(Intent.ACTION_SEND, shareIntent?.action)
        assertEquals("text/plain", shareIntent?.type)
        assertEquals("buy milk", shareIntent?.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun testSelfNotificationIsProcessedForReminderMatch() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Create mock StatusBarNotification originating from the app's own package name
        val extras = Bundle().apply {
            putCharSequence("android.title", "Reminder Alert")
            putCharSequence("android.text", "Need to buy milk today")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            context.packageName,
            context.packageName,
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        assertNotNull("Notification from self package should still trigger reminder match", matchedNotif)
    }

    @Test
    fun testMatchNotificationChannelConfiguresVibrationAndSound() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = notificationManager.getNotificationChannel(ReminderNotificationListenerService.MATCH_CHANNEL_ID)

        assertNotNull("Match notification channel should exist", channel)
        assertTrue("Vibration should be enabled", channel.shouldVibrate())
        assertNotNull("Vibration pattern should not be null", channel.vibrationPattern)
        assertNotNull("Sound URI should not be null", channel.sound)
    }

    @Test
    fun testActiveSnoozeOverrulesNotificationMatch() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("buy milk"))
            .putLong("snooze_buy milk", System.currentTimeMillis() + 60000L)
            .commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val extras = Bundle().apply {
            putCharSequence("android.title", "Shopping")
            putCharSequence("android.text", "Need to buy milk today")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.otherapp",
            "com.example.otherapp",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        org.junit.Assert.assertNull("Notification match should NOT be posted while snooze is active", matchedNotif)
    }

    @Test
    fun testSnoozeExpiryRetriggersOnAnyNotificationWithNormalPriority() {
        ReminderNotificationListenerService.lastTriggeredMap.clear()
        val context = RuntimeEnvironment.getApplication()

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("buy milk")).commit()

        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        // Set snooze in the past (expired)
        ReminderNotificationListenerService.lastTriggeredMap["snooze_buy milk"] = System.currentTimeMillis() - 1000L

        // Post an unrelated notification that does NOT contain "milk"
        val extras = Bundle().apply {
            putCharSequence("android.title", "Battery Low")
            putCharSequence("android.text", "15% remaining")
        }
        @Suppress("DEPRECATION")
        val targetNotification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        val sbn = StatusBarNotification(
            "com.example.system",
            "com.example.system",
            1,
            "tag",
            1000,
            1000,
            1,
            targetNotification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )

        service.onNotificationPosted(sbn)

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        assertNotNull("Expired snoozed item should re-trigger when any notification arrives", matchedNotif)
        @Suppress("DEPRECATION")
        assertEquals(Notification.PRIORITY_DEFAULT, matchedNotif.priority)
    }

    @Test
    fun testGetTopSnoozeChoicesDefaultAndRecent() {
        val context = RuntimeEnvironment.getApplication()
        val defaultChoices = ReminderNotificationListenerService.getTopSnoozeChoices(context)
        assertEquals(5, defaultChoices.size)
        assertEquals("15m", defaultChoices[0].toString())

        // Save custom snooze timestamps
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putLong("30m", 1000L)
            .putLong("2h", 2000L)
            .commit()

        val updatedChoices = ReminderNotificationListenerService.getTopSnoozeChoices(context)
        assertEquals(5, updatedChoices.size)
        assertEquals("15m", updatedChoices[0].toString())
        assertEquals("30m", updatedChoices[1].toString())
        assertEquals("1h", updatedChoices[2].toString())
        assertEquals("2h", updatedChoices[3].toString())
        assertEquals("4h", updatedChoices[4].toString())
    }

    @Test
    fun testGetTopSnoozeChoicesLimitsToTop5LatestUsedAndSortsShortToLong() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)

        // Record 7 choices with different timestamps
        prefs.edit()
            .putLong("10m", 100L)
            .putLong("20m", 200L)
            .putLong("30m", 300L)
            .putLong("2h", 400L)
            .putLong("3d", 500L)
            .putLong("5m", 10L)
            .putLong("1w", 5L)
            .commit()

        val choices = ReminderNotificationListenerService.getTopSnoozeChoices(context)

        assertEquals(5, choices.size)
        assertEquals("10m", choices[0].toString())
        assertEquals("20m", choices[1].toString())
        assertEquals("30m", choices[2].toString())
        assertEquals("2h", choices[3].toString())
        assertEquals("3d", choices[4].toString())
    }

    @Test
    fun testGetTopSnoozeChoicesIncludesCustomTextInputsAndSortsShortToLong() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("snooze_freq_prefs", Context.MODE_PRIVATE)

        // Custom duration and absolute time inputs typed by user with timestamps
        prefs.edit()
            .putLong("5m", 500L)
            .putLong("12h", 400L)
            .putLong("2w", 300L)
            .putLong("45m", 200L)
            .putLong("1d", 100L)
            .commit()

        val choices = ReminderNotificationListenerService.getTopSnoozeChoices(context)

        // Should contain top 5 user choices sorted from short to long duration: 5m, 45m, 12h, 1d, 2w
        assertEquals(5, choices.size)
        assertEquals("5m", choices[0].toString())
        assertEquals("45m", choices[1].toString())
        assertEquals("12h", choices[2].toString())
        assertEquals("1d", choices[3].toString())
        assertEquals("2w", choices[4].toString())
    }

    @Test
    fun testPostMatchNotificationPostsReminderAndReAddsStatusNotification() {
        val context = RuntimeEnvironment.getApplication()
        val service = Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        service.postMatchNotification("Buy grocers")

        val matchId = ReminderNotificationListenerService.getNotificationIdForReminder("Buy grocers")
        val matchNotif = shadowNM.getNotification(matchId)
        assertNotNull("Match notification should be posted directly", matchNotif)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be re-added when a reminder notification is posted", statusNotif)
    }
}
