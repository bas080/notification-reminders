package com.bas080.notificationreminders.services

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
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

        val matchedNotifId = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val matchedNotif = shadowNM.getNotification(matchedNotifId)
        assertNotNull("Matched reminder notification should be posted", matchedNotif)
        assertTrue(
            "Individual match notification should have FLAG_AUTO_CANCEL set",
            (matchedNotif.flags and Notification.FLAG_AUTO_CANCEL) != 0
        )
        assertEquals("buy milk", matchedNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString())
        assertTrue(
            "Matched notification should not contain matched notification content in text",
            matchedNotif.extras.getCharSequence(Notification.EXTRA_TEXT) == null
        )
        assertTrue(
            "Matched notification contentIntent should be null to allow expand on click",
            matchedNotif.contentIntent == null
        )
    }

    @Test
    fun testStatusNotificationTitleTextAndNoContentIntent() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNM = Shadows.shadowOf(notificationManager)

        val statusNotif = shadowNM.getNotification(ReminderNotificationListenerService.NOTIFICATION_ID)
        assertNotNull("Status notification should be posted", statusNotif)

        val title = statusNotif.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        assertEquals("Add Reminder", title)

        val text = statusNotif.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
        assertTrue("Status notification text should hint about tapping to add", text.contains("Tap to add a new reminder"))
        assertTrue("Status notification contentIntent should be null to allow expand on click", statusNotif.contentIntent == null)
    }
}
