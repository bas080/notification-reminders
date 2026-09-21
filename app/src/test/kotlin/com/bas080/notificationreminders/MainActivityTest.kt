package com.bas080.notificationreminders

import android.content.Context
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
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
class MainActivityTest {

    @Test
    fun testAddReminderShowsSuccessToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertNotNull(recyclerView)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ViewHolder
        assertNotNull(holder)

        holder!!.reminderInput.setText("Buy Groceries")
        holder.btnAction.performClick()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testAddReminderEmptyShowsFailureToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertNotNull(recyclerView)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ViewHolder
        assertNotNull(holder)

        holder!!.reminderInput.setText("   ")
        holder.btnAction.performClick()

        assertEquals("Failed to create reminder: Text cannot be empty", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testClearLogsShowsToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnClearLogs = activity.findViewById<TextView>(R.id.btn_clear_logs)
        assertNotNull(btnClearLogs)

        btnClearLogs.performClick()

        assertEquals("Logs cleared", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testExportMarkdownWithRemindersLaunchesShareIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ViewHolder
        holder.reminderInput.setText("Buy milk")
        holder.btnAction.performClick()

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)
    }

    @Test
    fun testImportMarkdownDialogParsesAndAddsReminders() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnImport = activity.findViewById<TextView>(R.id.btn_import_markdown)
        assertNotNull(btnImport)
        btnImport.performClick()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Import dialog should be shown", dialog)

        val editText = dialog!!.findViewById<EditText>(R.id.import_input)
        assertNotNull("Import EditText should exist inside dialog", editText)

        editText!!.setText("- [ ] Clean garage unique 123\n- [ ] Fix bike unique 123")
        val positiveBtn = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
        assertNotNull("Positive button should exist", positiveBtn)
        positiveBtn.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Imported 2 new reminder(s)", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testFormatSnoozeUntilTodayAndTomorrow() {
        val now = System.currentTimeMillis()
        val todaySnooze = now + 2 * 3600 * 1000L
        val tomorrowSnooze = now + 24 * 3600 * 1000L

        val todayFormatted = MainActivity.formatSnoozeUntil(todaySnooze, now)
        assertTrue("Expected 'today at ...', got: $todayFormatted", todayFormatted.startsWith("today at"))

        val tomorrowFormatted = MainActivity.formatSnoozeUntil(tomorrowSnooze, now)
        assertTrue("Expected 'tomorrow at ...' or weekday format, got: $tomorrowFormatted", tomorrowFormatted.contains("at"))
    }

    @Test
    fun testSnoozedReminderDisplaysStatusAndUnsnoozeButton() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as? RemindersAdapter.ViewHolder
        assertNotNull(holder)

        assertEquals(View.VISIBLE, holder!!.txtStatus.visibility)
        assertTrue(holder.txtStatus.text.toString().startsWith("SNOOZED • until"))
        assertEquals(View.VISIBLE, holder.btnUnsnooze.visibility)
    }

    @Test
    fun testUnsnoozeClickClearsSnoozeAndShowsToast() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Call Dentist"))
            .putLong("snooze_call dentist", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ViewHolder
        assertNotNull(holder)

        holder.btnUnsnooze.performClick()

        assertEquals("Snooze cancelled", ShadowToast.getTextOfLatestToast())
        assertEquals(0L, prefs.getLong("snooze_call dentist", 0L))

        // Re-bind to verify updated UI state
        recyclerView.adapter!!.onBindViewHolder(holder, 1)
        assertEquals(View.GONE, holder.txtStatus.visibility)
        assertEquals(View.GONE, holder.btnUnsnooze.visibility)
    }

    @Test
    fun testSummaryHeaderShowsActiveAndSnoozedCounts() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Active Task", "Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val txtSummary = activity.findViewById<TextView>(R.id.txt_reminders_summary)
        assertNotNull(txtSummary)
        assertEquals("1 active • 1 snoozed", txtSummary.text.toString())
    }

    @Test
    fun testEmptyStateVisibility() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val txtEmpty = activity.findViewById<TextView>(R.id.txt_empty_reminders)
        assertNotNull(txtEmpty)
        assertEquals(View.VISIBLE, txtEmpty.visibility)
    }
}
