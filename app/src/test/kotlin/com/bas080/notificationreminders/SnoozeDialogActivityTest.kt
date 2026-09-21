package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlertDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SnoozeDialogActivityTest {

    @Test
    fun testSingleReminderSnoozeFromSwipeIntent() {
        val context = RuntimeEnvironment.getApplication()
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_TEXT, "Buy milk")
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("List view in dialog should exist", listView)

        // Select first duration option (e.g. 15m)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should be finishing after snooze choice", activity.isFinishing)
        assertTrue("Toast should indicate reminder snoozed", ShadowToast.getTextOfLatestToast().startsWith("Reminder snoozed for"))

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = prefs.getLong("snooze_buy milk", 0L)
        assertTrue("Snooze timestamp should be saved in preferences", snoozeTime > System.currentTimeMillis())
    }

    @Test
    fun testGroupSummaryMultipleRemindersSnoozeFromSwipeIntent() {
        val context = RuntimeEnvironment.getApplication()
        val targets = arrayOf("Buy milk", "Call doctor", "Pay bill")
        val intent = Intent(context, SnoozeDialogActivity::class.java).apply {
            putExtra(SnoozeDialogActivity.EXTRA_REMINDER_LIST, targets)
        }

        val controller = Robolectric.buildActivity(SnoozeDialogActivity::class.java, intent).setup()
        val activity = controller.get()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze All dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull("List view in dialog should exist", listView)

        // Select first duration option (e.g. 15m)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertTrue("Activity should finish after snoozing all reminders", activity.isFinishing)

        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        for (target in targets) {
            val key = "snooze_" + target.trim().lowercase()
            val snoozeTime = prefs.getLong(key, 0L)
            assertTrue("Snooze time for '$target' should be in future", snoozeTime > now)
        }
    }
}
