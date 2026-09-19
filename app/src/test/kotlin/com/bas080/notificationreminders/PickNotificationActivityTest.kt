package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.os.Bundle
import android.service.notification.StatusBarNotification
import androidx.appcompat.app.AlertDialog
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PickNotificationActivityTest {

    @org.junit.After
    fun tearDown() {
        PickNotificationActivity.mockActiveNotifications = null
    }

    @Test
    fun testFormatNotificationText() {
        assertEquals("Shopping: Need milk", PickNotificationActivity.formatNotificationText("Shopping", "Need milk"))
        assertEquals("Shopping", PickNotificationActivity.formatNotificationText("Shopping", ""))
        assertEquals("Need milk", PickNotificationActivity.formatNotificationText("", "Need milk"))
        assertEquals("", PickNotificationActivity.formatNotificationText("", ""))
    }

    @Test
    fun testShowsNoNotificationsDialogWhenEmpty() {
        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown when no active notifications exist", dialog)

        dialog!!.getButton(AlertDialog.BUTTON_NEGATIVE).performClick()
        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()
        assertTrue(activity.isFinishing)
    }

    @Test
    fun testSelectionSavesReminderToPrefs() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val extras = Bundle().apply {
            putCharSequence("android.title", "Email")
            putCharSequence("android.text", "Meeting at 3pm")
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

        // Mock active notifications
        PickNotificationActivity.mockActiveNotifications = arrayOf(sbn)

        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Dialog should be shown for active notifications", dialog)

        val listView = dialog!!.listView
        assertNotNull(listView)
        assertEquals(1, listView.adapter.count)
        assertEquals("Email: Meeting at 3pm", listView.adapter.getItem(0))

        // Perform click on first item
        shadowOf(listView).performItemClick(0)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val savedReminders = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved reminders should contain selected notification text", savedReminders.contains("Email: Meeting at 3pm"))

        assertTrue(activity.isFinishing)
    }
}
