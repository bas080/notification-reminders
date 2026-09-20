package com.bas080.notificationreminders.receivers

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CreateReminderReceiverTest {

    @Test
    fun testCreateReminderShowsSuccessToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "Buy apples")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testCreateReminderPostsNotificationDirectly() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val receiver = CreateReminderReceiver()
        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "Buy apples")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
        val shadowNM = org.robolectric.Shadows.shadowOf(notificationManager)
        val notifId = ReminderNotificationListenerService.getNotificationIdForReminder("Buy apples")
        val postedNotif = shadowNM.getNotification(notifId)
        org.junit.Assert.assertNotNull("Creating a reminder should directly post a notification for it", postedNotif)
    }

    @Test
    fun testCreateReminderEmptyShowsFailureToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_TEXT_REPLY, "   ")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_CREATE_REMINDER)
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_TEXT_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testDoneReminderShowsSuccessToast() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val intent = Intent(ReminderNotificationListenerService.ACTION_DONE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        receiver.onReceive(context, intent)

        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testSnoozeReminderShowsSuccessToastAndSetsSnoozeTimestamp() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val intent = Intent(ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        receiver.onReceive(context, intent)

        assertEquals("Reminder snoozed for 1 hour", ShadowToast.getTextOfLatestToast())
        val snoozeUntil = ReminderNotificationListenerService.lastTriggeredMap["snooze_buy milk"] ?: 0L
        org.junit.Assert.assertTrue("Snooze timestamp should be in the future", snoozeUntil > System.currentTimeMillis())
    }

    @Test
    fun testSnoozeWithSelectedDuration() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = CreateReminderReceiver()

        val results = Bundle().apply {
            putCharSequence(ReminderNotificationListenerService.KEY_SNOOZE_REPLY, "15m")
        }
        val intent = Intent(ReminderNotificationListenerService.ACTION_SNOOZE_REMINDER).apply {
            putExtra(ReminderNotificationListenerService.EXTRA_REMINDER_TEXT, "Buy bread")
        }
        RemoteInput.addResultsToIntent(
            arrayOf(RemoteInput.Builder(ReminderNotificationListenerService.KEY_SNOOZE_REPLY).build()),
            intent,
            results
        )

        receiver.onReceive(context, intent)

        assertEquals("Reminder snoozed for 15 minutes", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testParseSnoozeDurationHelper() {
        val (ms15m, label15m) = CreateReminderReceiver.parseSnoozeDuration("15m")
        assertEquals(15 * 60 * 1000L, ms15m)
        assertEquals("15 minutes", label15m)

        val (ms4h, label4h) = CreateReminderReceiver.parseSnoozeDuration("4h")
        assertEquals(4 * 60 * 60 * 1000L, ms4h)
        assertEquals("4 hours", label4h)

        val (ms24h, label24h) = CreateReminderReceiver.parseSnoozeDuration("24h")
        assertEquals(24 * 60 * 60 * 1000L, ms24h)
        assertEquals("24 hours", label24h)

        val (ms2w, label2w) = CreateReminderReceiver.parseSnoozeDuration("2w")
        assertEquals(2 * 7 * 24 * 60 * 60 * 1000L, ms2w)
        assertEquals("2 weeks", label2w)

        // Absolute time test with fixed base timestamp e.g. 12:00 PM today
        val baseCal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 12)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val baseMillis = baseCal.timeInMillis

        // "18:00" from 12:00 -> 6 hours later today
        val (ms1800, label1800) = CreateReminderReceiver.parseSnoozeDuration("18:00", baseMillis)
        assertEquals(6 * 60 * 60 * 1000L, ms1800)
        assertEquals("today at 18:00", label1800)

        // "1800" (no colon) from 12:00 -> 6 hours later today
        val (ms1800NoColon, label1800NoColon) = CreateReminderReceiver.parseSnoozeDuration("1800", baseMillis)
        assertEquals(6 * 60 * 60 * 1000L, ms1800NoColon)
        assertEquals("today at 18:00", label1800NoColon)

        // "7pm" from 12:00 -> 7 hours later today (19:00)
        val (ms7pm, label7pm) = CreateReminderReceiver.parseSnoozeDuration("7pm", baseMillis)
        assertEquals(7 * 60 * 60 * 1000L, ms7pm)
        assertEquals("today at 19:00", label7pm)

        // "1am" from 12:00 PM -> 13 hours later tomorrow (01:00)
        val (ms1am, label1am) = CreateReminderReceiver.parseSnoozeDuration("1am", baseMillis)
        assertEquals(13 * 60 * 60 * 1000L, ms1am)
        assertEquals("tomorrow at 01:00", label1am)
    }
}
