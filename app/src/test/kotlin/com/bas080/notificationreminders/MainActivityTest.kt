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

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ItemViewHolder
        assertNotNull(holder)

        holder!!.reminderInput.setText("Buy Groceries")
        holder.btnAction.performClick()

        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun testInputFiltersRemindersInRealTimeAndAddsOnButtonClick() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Typing "milk" should filter displayed items to 1 reminder ("Buy milk")
        holder.reminderInput.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        // 1 input + 1 matched ("Buy milk") + 1 footer = 3 items
        assertEquals("Expected 3 items when filtered", 3, recyclerView.adapter!!.itemCount)

        // Clicking '+' button should add "milk" as a new reminder and clear search query
        holder.btnAction.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()
        assertEquals("Reminder created", ShadowToast.getTextOfLatestToast())

        // Input text should be cleared and all 3 reminders displayed
        assertEquals("Expected 5 items total", 5, recyclerView.adapter!!.itemCount)
    }

    @Test
    fun testAddReminderEmptyShowsFailureToast() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertNotNull(recyclerView)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ItemViewHolder
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
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder
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
        val cal = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, 2026)
            set(java.util.Calendar.MONTH, java.util.Calendar.OCTOBER)
            set(java.util.Calendar.DAY_OF_MONTH, 15)
            set(java.util.Calendar.HOUR_OF_DAY, 10)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val now = cal.timeInMillis
        val todaySnooze = now + 2 * 3600 * 1000L
        val tomorrowSnooze = now + 24 * 3600 * 1000L

        val todayFormatted = MainActivity.formatSnoozeUntil(todaySnooze, now)
        assertTrue("Expected 'today at ...', got: $todayFormatted", todayFormatted.startsWith("today at"))

        val tomorrowFormatted = MainActivity.formatSnoozeUntil(tomorrowSnooze, now)
        assertTrue("Expected 'tomorrow at ...', got: $tomorrowFormatted", tomorrowFormatted.startsWith("tomorrow at"))
    }

    @Test
    fun testSnoozedReminderDisplaysStatusLabel() {
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
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as? RemindersAdapter.ItemViewHolder
        assertNotNull(holder)

        assertEquals(View.VISIBLE, holder!!.txtStatus.visibility)
        assertTrue(holder.txtStatus.text.toString().startsWith("Snoozed • until"))
        assertEquals(View.VISIBLE, holder.btnShare.visibility)
    }

    @Test
    fun testItemShareClickLaunchesShareIntent() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Shared Task")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals(View.VISIBLE, holder.btnShare.visibility)

        holder.btnShare.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)
    }

    @Test
    fun testFilterRemindersActiveAndSnoozed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Active Task", "Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val btnFilterActive = activity.findViewById<TextView>(R.id.btn_filter_active)
        val btnFilterSnoozed = activity.findViewById<TextView>(R.id.btn_filter_snoozed)

        // Filter ACTIVE
        btnFilterActive.performClick()
        assertEquals(3, recyclerView.adapter!!.itemCount) // 1 create input + 1 active task + 1 footer

        val activeHolder = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        recyclerView.adapter!!.onBindViewHolder(activeHolder, 1)
        assertEquals("Active Task", activeHolder.reminderInput.text.toString())

        // Filter SNOOZED
        btnFilterSnoozed.performClick()
        assertEquals(3, recyclerView.adapter!!.itemCount) // 1 create input + 1 snoozed task + 1 footer

        val snoozedHolder = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        recyclerView.adapter!!.onBindViewHolder(snoozedHolder, 1)
        assertEquals("Snoozed Task", snoozedHolder.reminderInput.text.toString())
    }

    @Test
    fun testActiveItemsSortedFirstAndSnoozedItemsSortedAscendingBySnoozeTime() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeLater = now + 7200000L // 2h
        val snoozeSooner = now + 3600000L // 1h
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task Later", "Task Sooner", "Task Active"))
            .putLong("snooze_task later", snoozeLater)
            .putLong("snooze_task sooner", snoozeSooner)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        val holder1 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        val holder2 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder
        val holder3 = recyclerView.adapter!!.createViewHolder(recyclerView, RemindersAdapter.TYPE_ACTIVE_REMINDER) as RemindersAdapter.ItemViewHolder

        recyclerView.adapter!!.onBindViewHolder(holder1, 1)
        recyclerView.adapter!!.onBindViewHolder(holder2, 2)
        recyclerView.adapter!!.onBindViewHolder(holder3, 3)

        // Active item comes first ("Task Active"), followed by sooner snooze ("Task Sooner"), then later snooze ("Task Later")
        assertEquals("Task Active", holder1.reminderInput.text.toString())
        assertEquals("Task Sooner", holder2.reminderInput.text.toString())
        assertEquals("Task Later", holder3.reminderInput.text.toString())
    }

    @Test
    fun testFilterPillsShowAccurateCountsAndTriggerFilterOnClick() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Active Task", "Snoozed Task"))
            .putLong("snooze_snoozed task", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val pillAll = activity.findViewById<TextView>(R.id.pill_filter_all)
        val pillActive = activity.findViewById<TextView>(R.id.pill_filter_active)
        val pillSnoozed = activity.findViewById<TextView>(R.id.pill_filter_snoozed)

        assertNotNull(pillAll)
        assertNotNull(pillActive)
        assertNotNull(pillSnoozed)

        assertEquals("2", pillAll.text.toString())
        assertEquals("1", pillActive.text.toString())
        assertEquals("1", pillSnoozed.text.toString())

        // Click active pill
        pillActive.performClick()
        assertEquals(3, recyclerView.adapter!!.itemCount) // 1 input + 1 active + 1 footer

        // Click snoozed pill
        pillSnoozed.performClick()
        assertEquals(3, recyclerView.adapter!!.itemCount) // 1 input + 1 snoozed + 1 footer

        // Click all pill
        pillAll.performClick()
        assertEquals(4, recyclerView.adapter!!.itemCount) // 1 input + 2 tasks + 1 footer
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

    @Test
    fun testSearchZeroResultsRetainsFocusOnInputAndShowsEmptyText() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        holder.reminderInput.requestFocus()
        assertTrue("Position 0 input should have focus initially", holder.reminderInput.hasFocus())

        // Search for non-matching text
        holder.reminderInput.setText("nonexistentquery123")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Only create input (1) + footer (1) = 2 items in adapter
        assertEquals("Adapter should have 2 items when 0 reminders match", 2, recyclerView.adapter!!.itemCount)

        val txtEmpty = activity.findViewById<TextView>(R.id.txt_empty_reminders)
        assertEquals("Empty reminders view should be VISIBLE", View.VISIBLE, txtEmpty.visibility)

        assertTrue("Position 0 input should retain focus even when 0 items match", holder.reminderInput.hasFocus())
    }

    @Test
    fun testSearchInputDoesNotLoseFocusWhileTypingCharacterByCharacter() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage", "Walk dog")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        holder.reminderInput.requestFocus()
        assertTrue("Input should initially have focus", holder.reminderInput.hasFocus())

        val querySequence = "garage"
        val currentText = StringBuilder()

        for (char in querySequence) {
            currentText.append(char)
            holder.reminderInput.setText(currentText.toString())
            shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
            assertTrue("Input should retain focus while typing character '$char'", holder.reminderInput.hasFocus())
        }

        // Final check on filtered item count (1 input + 1 match ("Clean garage") + 1 footer = 3 items)
        assertEquals("Adapter should display filtered match", 3, recyclerView.adapter!!.itemCount)
        assertTrue("Input should remain focused after typing completes", holder.reminderInput.hasFocus())
    }

    @Test
    fun testSnoozedReminderSwipeOpensDialogWithUnsnoozeOption() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val snoozeTime = System.currentTimeMillis() + 3600000L
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Snoozed Item"))
            .putLong("snooze_snoozed item", snoozeTime)
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        // Trigger snooze dialog directly for snoozed item
        val method = MainActivity::class.java.getDeclaredMethod("showSnoozeOptionsDialog", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "Snoozed Item")

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Snooze dialog should be displayed", dialog)

        val listView = dialog!!.listView
        assertNotNull("Dialog list view should exist", listView)
        assertEquals("First option should be Unsnooze", "Unsnooze", listView.adapter.getItem(0))

        // Click "Unsnooze" (index 0)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Snooze cancelled", ShadowToast.getTextOfLatestToast())
        val updatedSnooze = prefs.getLong("snooze_snoozed item", 0L)
        assertEquals(0L, updatedSnooze)
    }

    @Test
    fun testMarkDoneAppendsDoneTagAndFiltersFromActiveAndSnoozed() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Task 1")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnFilterActive = activity.findViewById<TextView>(R.id.btn_filter_active)
        val btnFilterAll = activity.findViewById<TextView>(R.id.btn_filter_all)
        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        // Show mark done dialog for "Task 1"
        val method = MainActivity::class.java.getDeclaredMethod("showMarkDoneConfirmationDialog", String::class.java)
        method.isAccessible = true
        method.invoke(activity, "Task 1")

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull(dialog)
        dialog!!.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Reminder marked done", ShadowToast.getTextOfLatestToast())

        val savedSet = prefs.getStringSet("key_reminders_list", emptySet()) ?: emptySet()
        assertTrue("Saved set should contain 'Task 1 #done'", savedSet.contains("Task 1 #done"))

        // Active filter should exclude done items
        btnFilterActive.performClick()
        assertEquals(2, recyclerView.adapter!!.itemCount) // 1 create input + 0 items + 1 footer

        // All filter should include done items
        btnFilterAll.performClick()
        assertEquals(3, recyclerView.adapter!!.itemCount) // 1 create input + 1 done item + 1 footer
    }
}
