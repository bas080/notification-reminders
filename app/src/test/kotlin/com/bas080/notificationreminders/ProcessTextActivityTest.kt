package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProcessTextActivityTest {

    @Test
    fun testProcessTextActionCreatesReminder() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "Buy milk today")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved reminders should contain selected text", savedSet.contains("Buy milk today"))

        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }

    @Test
    fun testActionSendCreatesReminder() {
        val context = RuntimeEnvironment.getApplication()
        Robolectric.buildService(ReminderNotificationListenerService::class.java).create().get()

        val intent = Intent(Intent.ACTION_SEND).apply {
            putExtra(Intent.EXTRA_TEXT, "Call doctor tomorrow")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved reminders should contain shared text", savedSet.contains("Call doctor tomorrow"))

        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }

    @Test
    fun testProcessTextEmptyShowsFailureToast() {
        val intent = Intent(Intent.ACTION_PROCESS_TEXT).apply {
            putExtra(Intent.EXTRA_PROCESS_TEXT, "   ")
            type = "text/plain"
        }

        val controller = Robolectric.buildActivity(ProcessTextActivity::class.java, intent)
        controller.create()

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
        assertTrue("Activity should be finishing", controller.get().isFinishing)
    }
}
