package com.bas080.notificationreminders.receivers

import android.content.Intent
import android.os.Bundle
import androidx.core.app.RemoteInput
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
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
}
