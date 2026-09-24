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
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

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
    fun testAboutViewDisplaysVersionAndFeedbackLaunchesIntent() {
        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnNavAbout = activity.findViewById<TextView>(R.id.btn_nav_about)
        assertNotNull(btnNavAbout)

        btnNavAbout.performClick()

        val aboutContainer = activity.findViewById<View>(R.id.about_container)
        assertEquals(View.VISIBLE, aboutContainer.visibility)

        val txtVersion = activity.findViewById<TextView>(R.id.txt_app_version)
        assertNotNull(txtVersion)
        assertTrue(txtVersion.text.toString().startsWith("Version"))

        val btnFeedback = activity.findViewById<TextView>(R.id.btn_feedback)
        assertNotNull(btnFeedback)
        btnFeedback.performClick()

        val nextStartedActivity = shadowOf(activity).nextStartedActivity
        assertNotNull(nextStartedActivity)
        assertEquals(android.content.Intent.ACTION_CHOOSER, nextStartedActivity.action)

        @Suppress("DEPRECATION")
        val targetIntent = nextStartedActivity.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        val body = targetIntent!!.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""
        assertTrue("Feedback body should include Feedback header", body.contains("## Feedback"))
        assertTrue("Feedback body should include Device Info", body.contains("### Device Info"))
        org.junit.Assert.assertFalse("Feedback body should not include stack trace", body.contains("### Stack Trace"))
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
    fun testExportMarkdownOnlyExportsFilteredReminders() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk #punt", "Clean garage #home", "Fix bike #punt")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Filter by "#punt"
        holder.reminderInput.setText("#punt")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Share chooser intent should be launched", chooserIntent)

        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        val exportedText = targetIntent!!.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""

        assertTrue("Export should contain 'Buy milk #punt'", exportedText.contains("Buy milk #punt"))
        assertTrue("Export should contain 'Fix bike #punt'", exportedText.contains("Fix bike #punt"))
        org.junit.Assert.assertFalse("Export should NOT contain 'Clean garage #home'", exportedText.contains("Clean garage #home"))
    }

    @Test
    fun testExportMarkdownWhenFilteredListIsEmptyShowsToast() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk #punt")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Filter by "nonexistentquery"
        holder.reminderInput.setText("nonexistentquery")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnExport = activity.findViewById<TextView>(R.id.btn_export_markdown)
        assertNotNull(btnExport)
        btnExport.performClick()

        assertEquals("No reminders to export", ShadowToast.getTextOfLatestToast())
        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        org.junit.Assert.assertNull("No share chooser activity should be started when export list is empty", chooserIntent)
    }

    @Test
    fun testExportButtonOnRemindersListExportsFilteredReminders() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Task 1 #punt", "Task 2 #other")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Filter by "#punt"
        holder.reminderInput.setText("#punt")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        val btnListExport = activity.findViewById<TextView>(R.id.btn_list_export)
        assertNotNull(btnListExport)
        btnListExport.performClick()

        var chooserIntent: android.content.Intent? = shadowOf(activity).nextStartedActivity
        while (chooserIntent != null && chooserIntent.action != android.content.Intent.ACTION_CHOOSER) {
            chooserIntent = shadowOf(activity).nextStartedActivity
        }
        assertNotNull("Share chooser intent should be launched when clicking list export button", chooserIntent)

        @Suppress("DEPRECATION")
        val targetIntent = chooserIntent!!.getParcelableExtra<android.content.Intent>(android.content.Intent.EXTRA_INTENT)
        assertNotNull(targetIntent)
        val exportedText = targetIntent!!.getStringExtra(android.content.Intent.EXTRA_TEXT) ?: ""

        assertTrue("Export should contain 'Task 1 #punt'", exportedText.contains("Task 1 #punt"))
        org.junit.Assert.assertFalse("Export should NOT contain 'Task 2 #other'", exportedText.contains("Task 2 #other"))
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
        // Position 0 = create input, Position 1 = SNOOZED header, Position 2 = Snoozed Task
        val holder = recyclerView.findViewHolderForAdapterPosition(2) as? RemindersAdapter.ItemViewHolder
        assertNotNull(holder)

        assertEquals(View.VISIBLE, holder!!.txtStatus.visibility)
        assertTrue(holder.txtStatus.text.toString().startsWith("Punted • until"))
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
    fun testClearSearchButtonClearsQueryAndResetsList() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Clean garage")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val btnClearSearch = activity.findViewById<TextView>(R.id.btn_clear_search)
        assertNotNull(btnClearSearch)

        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Type "milk" to filter list
        holder.reminderInput.setText("milk")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        assertEquals("Expected 3 items when filtered", 3, recyclerView.adapter!!.itemCount)
        assertTrue("Clear button should be enabled when text is entered", btnClearSearch.isEnabled)

        // Click Clear button
        btnClearSearch.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("", holder.reminderInput.text.toString())
        assertEquals("Expected 4 items total when cleared", 4, recyclerView.adapter!!.itemCount)
        org.junit.Assert.assertFalse("Clear button should be disabled when search is cleared", btnClearSearch.isEnabled)
    }

    @Test
    fun testTagFilterSelectionDialog() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear()
            .putStringSet("key_reminders_list", setOf("Buy milk #groceries", "Finish report #work"))
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnTagsFilter = activity.findViewById<android.widget.LinearLayout>(R.id.btn_tags_filter)
        val txtSelectedTags = activity.findViewById<TextView>(R.id.txt_selected_tags)
        assertNotNull(btnTagsFilter)
        assertNotNull(txtSelectedTags)

        // Open filter selection dialog
        btnTagsFilter.performClick()

        val dialog = ShadowAlertDialog.getLatestDialog() as? AlertDialog
        assertNotNull("Filter selection dialog should be shown", dialog)

        val listView = dialog!!.listView
        assertNotNull(listView)
        assertEquals(5, listView.adapter.count) // 3 state options + 2 tags

        // Select first tag (#groceries at index 3)
        shadowOf(listView).performItemClick(3)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Both • #groceries", txtSelectedTags.text.toString())
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

        recyclerView.adapter!!.onBindViewHolder(holder1, 1) // Task Active
        recyclerView.adapter!!.onBindViewHolder(holder2, 3) // Task Sooner (Position 2 is SNOOZED header)
        recyclerView.adapter!!.onBindViewHolder(holder3, 4) // Task Later

        // Active item comes first ("Task Active"), followed by SNOOZED header, then sooner snooze ("Task Sooner"), then later snooze ("Task Later")
        assertEquals("Task Active", holder1.reminderInput.text.toString())
        assertEquals("Task Sooner", holder2.reminderInput.text.toString())
        assertEquals("Task Later", holder3.reminderInput.text.toString())
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

        // Only create input (1) when 0 reminders match (footer instructions hidden)
        assertEquals("Adapter should have 1 item when 0 reminders match", 1, recyclerView.adapter!!.itemCount)

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
        assertEquals("First option should be Unpunt", "Unpunt", listView.adapter.getItem(0))

        // Click "Unpunt" (index 0)
        shadowOf(listView).performItemClick(0)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("Punt cancelled", ShadowToast.getTextOfLatestToast())
        val updatedSnooze = prefs.getLong("snooze_snoozed item", 0L)
        assertEquals(0L, updatedSnooze)
    }

    @Test
    fun testMarkDoneAppendsDoneTagAndFiltersFromOverview() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Task 1")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

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

        // Item should be excluded from overview unless search contains #done
        assertEquals(1, recyclerView.adapter!!.itemCount) // 1 create input + 0 items (footer hidden when empty)
    }

    @Test
    fun testDoneItemsHiddenFromSearchUnlessSearchContainsHashDone() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Buy milk", "Buy bread #done")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder

        // Search "Buy" -> should match "Buy milk" but exclude "Buy bread #done"
        holder.reminderInput.setText("Buy")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Should show 1 match ('Buy milk') when searching 'Buy'", 3, recyclerView.adapter!!.itemCount) // 1 input + 1 match + 1 footer

        // Search "#done" -> should match "Buy bread #done"
        holder.reminderInput.setText("#done")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("Should show 1 match ('Buy bread #done') when searching '#done'", 3, recyclerView.adapter!!.itemCount) // 1 input + 1 match + 1 footer
    }

    @Test
    fun testSnoozedDividerAppearsWhenSnoozedItemsExist() {
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

        // Items in overview: position 0 = create input, position 1 = Active Task, position 2 = SNOOZED header, position 3 = Snoozed Task, position 4 = footer
        assertEquals(5, recyclerView.adapter!!.itemCount)

        val headerType = recyclerView.adapter!!.getItemViewType(2)
        assertEquals(RemindersAdapter.TYPE_SNOOZED_HEADER, headerType)
    }

    @Test
    fun testSearchButtonConvertsToSearchWhenInputScrolledOutOfView() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val items = (1..10).map { "Task $it" }.toSet()
        prefs.edit().putStringSet("key_reminders_list", items).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val btnClearSearch = activity.findViewById<TextView>(R.id.btn_clear_search)
        val btnSearch = activity.findViewById<TextView>(R.id.btn_search)

        // Initially search input is at position 0: btn_clear_search visible, btn_search gone
        assertEquals(View.VISIBLE, btnClearSearch.visibility)
        assertEquals(View.GONE, btnSearch.visibility)

        // Scroll list so position 0 is out of view
        recyclerView.scrollToPosition(5)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // btn_search should now be visible and btn_clear_search gone
        assertEquals(View.GONE, btnClearSearch.visibility)
        assertEquals(View.VISIBLE, btnSearch.visibility)
    }

    @Test
    fun testClickingSearchButtonScrollsToTopAndPutsFocusOnSearchWithoutClearingSearch() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val items = (1..10).map { "Task $it" }.toSet()
        prefs.edit().putStringSet("key_reminders_list", items).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val btnClearSearch = activity.findViewById<TextView>(R.id.btn_clear_search)
        val btnSearch = activity.findViewById<TextView>(R.id.btn_search)

        val holder0 = recyclerView.findViewHolderForAdapterPosition(0) as RemindersAdapter.ItemViewHolder
        holder0.reminderInput.setText("Task")
        shadowOf(android.os.Looper.getMainLooper()).idleFor(250, java.util.concurrent.TimeUnit.MILLISECONDS)

        // Scroll down
        recyclerView.scrollToPosition(5)
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals(View.VISIBLE, btnSearch.visibility)

        // Click "Search" button
        btnSearch.performClick()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // btn_clear_search becomes visible again
        assertEquals(View.VISIBLE, btnClearSearch.visibility)
        assertEquals(View.GONE, btnSearch.visibility)

        // Position 0 input should be focused, contain "Task", and selection cursor at position 4 (end)
        val holderTop = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ItemViewHolder
        assertNotNull(holderTop)
        assertEquals("Task", holderTop!!.reminderInput.text.toString())
        assertTrue("Search input should gain focus when clicking Search button", holderTop.reminderInput.hasFocus())
        assertEquals(4, holderTop.reminderInput.selectionEnd)
    }

    @Test
    fun testSnoozeAndUnsnoozeUpdatesCardStatusView() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().putStringSet("key_reminders_list", setOf("Task to snooze")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)

        // Verify initial state: active task at position 1 has txtStatus GONE
        val holder1 = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals(View.GONE, holder1.txtStatus.visibility)

        // Snooze the item for 1 hour
        val applyMethod = MainActivity::class.java.getDeclaredMethod("applySnoozeDuration", String::class.java, String::class.java)
        applyMethod.isAccessible = true
        applyMethod.invoke(activity, "Task to snooze", "1h")
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Position 2 is now Snoozed Item (Position 1 is SNOOZED header)
        val holderSnoozed = recyclerView.findViewHolderForAdapterPosition(2) as RemindersAdapter.ItemViewHolder
        assertEquals(View.VISIBLE, holderSnoozed.txtStatus.visibility)
        assertTrue(holderSnoozed.txtStatus.text.toString().startsWith("Punted • until"))

        // Unsnooze the item
        val dialogMethod = MainActivity::class.java.getDeclaredMethod("showSnoozeOptionsDialog", String::class.java)
        dialogMethod.isAccessible = true
        dialogMethod.invoke(activity, "Task to snooze")

        val dialog = ShadowAlertDialog.getLatestDialog() as AlertDialog
        val listView = dialog.listView
        shadowOf(listView).performItemClick(0) // Click Unsnooze
        shadowOf(android.os.Looper.getMainLooper()).idle()

        // Item moves back to position 1 as active task with txtStatus GONE
        val holderUnsnoozed = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder
        assertEquals(View.GONE, holderUnsnoozed.txtStatus.visibility)
    }

    @Test
    fun testAttachedScreenshotIsDisplayedOnReminderCard() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putStringSet("key_reminders_list", setOf("Task with image"))
            .putString("screenshot_task with image", "file:///tmp/screenshot.png")
            .commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        assertEquals(View.VISIBLE, holder.imgScreenshot.visibility)
    }

    @Test
    fun testReminderInputExpandsMaxLinesOnFocus() {
        val context = RuntimeEnvironment.getApplication()
        val prefs = context.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        prefs.edit().clear().putStringSet("key_reminders_list", setOf("Multi-line task")).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        val holder = recyclerView.findViewHolderForAdapterPosition(1) as RemindersAdapter.ItemViewHolder

        holder.reminderInput.requestFocus()
        shadowOf(android.os.Looper.getMainLooper()).idle()

        assertEquals("maxLines should expand to Int.MAX_VALUE on focus", Int.MAX_VALUE, holder.reminderInput.maxLines)
    }
}
