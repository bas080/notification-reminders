package com.bas080.notificationreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.receivers.CreateReminderReceiver
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class ReminderFilter { ALL, ACTIVE, SNOOZED }

const val HEADER_SNOOZED_SECTION_MARKER = "HEADER_SNOOZED_SECTION_MARKER"

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
        private const val PREFS_SNOOZE_FREQ = "snooze_freq_prefs"

        fun formatSnoozeUntil(snoozeUntil: Long, now: Long = System.currentTimeMillis()): String {
            val snoozeCal = Calendar.getInstance().apply { timeInMillis = snoozeUntil }
            val nowCal = Calendar.getInstance().apply { timeInMillis = now }

            val sameYear = snoozeCal.get(Calendar.YEAR) == nowCal.get(Calendar.YEAR)
            val dayOfYearDiff = snoozeCal.get(Calendar.DAY_OF_YEAR) - nowCal.get(Calendar.DAY_OF_YEAR)

            val timeFormat = SimpleDateFormat("HH:mm", Locale.US)
            val timeStr = timeFormat.format(snoozeCal.time)

            return when {
                sameYear && dayOfYearDiff == 0 -> "today at $timeStr"
                sameYear && dayOfYearDiff == 1 -> "tomorrow at $timeStr"
                sameYear && dayOfYearDiff in 2..6 -> {
                    val dayFormat = SimpleDateFormat("EEE 'at' HH:mm", Locale.US)
                    dayFormat.format(snoozeCal.time)
                }
                sameYear -> {
                    val dateFormat = SimpleDateFormat("MMM d 'at' HH:mm", Locale.US)
                    dateFormat.format(snoozeCal.time)
                }
                else -> {
                    val fullFormat = SimpleDateFormat("MMM d, yyyy 'at' HH:mm", Locale.US)
                    fullFormat.format(snoozeCal.time)
                }
            }
        }
    }

    private lateinit var binding: ActivityMainBinding
    private val activeReminders = mutableListOf<String>()
    private val displayedReminders = mutableListOf<String>()
    private lateinit var adapter: RemindersAdapter

    private var currentFilter = ReminderFilter.ALL
    private var currentSearchQuery = ""

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        AppLogger.log(this, "MainActivity", "onCreate called")

        setupNavigation()
        setupRecyclerView()
        setupSwipeGestures()
        loadReminders()

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    private fun setupNavigation() {
        markAsButtonAccessibility(binding.btnNavReminders)
        markAsButtonAccessibility(binding.btnNavAbout)
        markAsButtonAccessibility(binding.btnClearLogs)
        markAsButtonAccessibility(binding.btnExportMarkdown)
        markAsButtonAccessibility(binding.btnImportMarkdown)
        markAsButtonAccessibility(binding.btnFeedback)
        markAsButtonAccessibility(binding.btnTagsFilter)
        markAsButtonAccessibility(binding.btnClearSearch)

        binding.btnNavReminders.setOnClickListener {
            showRemindersView()
        }

        binding.btnNavAbout.setOnClickListener {
            showAboutView()
        }

        binding.btnFeedback.setOnClickListener {
            sendFeedbackEmail()
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clearLogs(this)
            loadLogs()
            Toast.makeText(this, R.string.toast_logs_cleared, Toast.LENGTH_SHORT).show()
        }

        binding.btnExportMarkdown.setOnClickListener {
            exportRemindersToMarkdown()
        }

        binding.btnImportMarkdown.setOnClickListener {
            showImportMarkdownDialog()
        }

        binding.btnTagsFilter.setOnClickListener {
            showTagsSelectionDialog()
        }

        binding.btnClearSearch.setOnClickListener {
            currentSearchQuery = ""
            adapter.setSearchQueryText("")
            val holder = binding.remindersList.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ItemViewHolder
            holder?.reminderInput?.setText("")
            updateSummaryAndAdapter()
        }
    }

    private fun sendFeedbackEmail() {
        val versionName = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (_: Exception) {
            "1.0"
        }

        val feedbackBody = StringBuilder().apply {
            append("## Feedback\n\n")
            append("[ Please type your feedback here ]\n\n")

            append("### Device Info\n")
            append("- App Version: $versionName (${BuildConfig.VERSION_CODE})\n")
            append("- Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
            append("- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")

            val logs = AppLogger.getLogs(this@MainActivity)
            if (logs.isNotBlank()) {
                append("### Application Logs\n")
                append("```\n")
                append(logs)
                append("\n```\n")
            }
        }.toString()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = android.net.Uri.parse("mailto:bas080@hotmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Notification Reminders Feedback")
            putExtra(Intent.EXTRA_TEXT, feedbackBody)
        }
        val chooserIntent = Intent.createChooser(intent, getString(R.string.feedback))
        startActivity(chooserIntent)
    }

    private fun extractAllTags(): List<String> {
        val tagRegex = Regex("#[a-zA-Z0-9_]+")
        val tagsSet = mutableSetOf<String>()
        for (reminder in activeReminders) {
            tagRegex.findAll(reminder).forEach { match ->
                tagsSet.add(match.value.lowercase())
            }
        }
        return tagsSet.sorted()
    }

    private fun showTagsSelectionDialog() {
        val allTags = extractAllTags()
        if (allTags.isEmpty()) {
            Toast.makeText(this, "No tags found in reminders.", Toast.LENGTH_SHORT).show()
            return
        }

        val checkedItems = BooleanArray(allTags.size) { i ->
            val tag = allTags[i]
            currentSearchQuery.contains(tag, ignoreCase = true)
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle("Filter by Tags")
            .setMultiChoiceItems(allTags.toTypedArray(), checkedItems) { _, which, isChecked ->
                checkedItems[which] = isChecked
            }
            .setPositiveButton("Apply") { _, _ ->
                var updatedQuery = currentSearchQuery

                for (i in allTags.indices) {
                    val tag = allTags[i]
                    val isChecked = checkedItems[i]
                    val containsTag = updatedQuery.contains(tag, ignoreCase = true)

                    if (isChecked && !containsTag) {
                        updatedQuery = if (updatedQuery.isBlank()) tag else "$updatedQuery $tag"
                    } else if (!isChecked && containsTag) {
                        updatedQuery = updatedQuery.replace(Regex("(?i)\\b${Regex.escape(tag)}\\b|${Regex.escape(tag)}"), "")
                            .replace(Regex("\\s+"), " ")
                            .trim()
                    }
                }

                currentSearchQuery = updatedQuery
                adapter.setSearchQueryText(updatedQuery)
                updateSummaryAndAdapter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }


    private fun exportRemindersToMarkdown() {
        if (activeReminders.isEmpty()) {
            Toast.makeText(this, R.string.toast_no_reminders_to_export, Toast.LENGTH_SHORT).show()
            return
        }
        val markdownText = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.exportToMarkdown(activeReminders)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, markdownText)
            putExtra(Intent.EXTRA_SUBJECT, "Reminders Export")
        }
        val chooserIntent = Intent.createChooser(shareIntent, "Export Reminders")
        startActivity(chooserIntent)
    }

    private fun showImportMarkdownDialog() {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            id = R.id.import_input
            hint = getString(R.string.import_dialog_hint)
            setLines(6)
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            setPadding(padding, padding / 2, padding, 0)
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.import_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.import_button) { _, _ ->
                val markdownText = input.text.toString()
                val importedItems = com.bas080.notificationreminders.utils.MarkdownRemindersUtil.importFromMarkdown(markdownText)
                if (importedItems.isNotEmpty()) {
                    var addedCount = 0
                    for (item in importedItems) {
                        if (!activeReminders.contains(item)) {
                            activeReminders.add(item)
                            addedCount++
                        }
                    }
                    if (addedCount > 0) {
                        saveRemindersToPrefs()
                        updateSummaryAndAdapter()
                        Toast.makeText(this, getString(R.string.toast_imported_reminders, addedCount), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun markAsButtonAccessibility(view: View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
    }

    private fun showRemindersView() {
        binding.remindersContainer.visibility = View.VISIBLE
        binding.aboutContainer.visibility = View.GONE

        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.btnNavAbout.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavAbout.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun showAboutView() {
        binding.remindersContainer.visibility = View.GONE
        binding.aboutContainer.visibility = View.VISIBLE

        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnNavAbout.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavAbout.setTextColor(ContextCompat.getColor(this, R.color.text_primary))

        try {
            val pInfo = packageManager.getPackageInfo(packageName, 0)
            binding.txtAppVersion.text = getString(R.string.app_version_format, pInfo.versionName)
        } catch (_: Exception) {
            binding.txtAppVersion.text = getString(R.string.app_version_format, "1.0")
        }

        loadLogs()
    }

    private fun loadLogs() {
        val logs = AppLogger.getLogs(this)
        binding.txtLogs.text = if (logs.isNotBlank()) logs else "No logs available."
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            displayedReminders,
            onAddReminder = { newReminder ->
                AppLogger.log(this, "MainActivity", "Created reminder")
                activeReminders.add(newReminder)
                currentSearchQuery = ""
                saveRemindersToPrefs()
                ReminderNotificationListenerService.instance?.postMatchNotification(newReminder)
                updateSummaryAndAdapter()
            },
            onUpdateReminder = { index, updatedText ->
                if (index in displayedReminders.indices) {
                    val oldText = displayedReminders[index]
                    val masterIdx = activeReminders.indexOf(oldText)
                    if (masterIdx != -1) {
                        if (oldText != updatedText) {
                            AppLogger.log(this, "MainActivity", "Updated reminder text")
                            // Cancel any active notification for old reminder text
                            val oldNotifId = ReminderNotificationListenerService.getNotificationIdForReminder(oldText)
                            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                            notificationManager?.cancel(oldNotifId)

                            // Migrate snooze timestamp if snoozed
                            val oldTrimmed = oldText.trim().lowercase()
                            val newTrimmed = updatedText.trim().lowercase()
                            if (oldTrimmed != newTrimmed) {
                                val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                                val snoozeTime = prefs.getLong("snooze_$oldTrimmed", 0L)
                                if (snoozeTime > 0L) {
                                    prefs.edit().remove("snooze_$oldTrimmed").putLong("snooze_$newTrimmed", snoozeTime).apply()
                                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$oldTrimmed")
                                    ReminderNotificationListenerService.lastTriggeredMap["snooze_$newTrimmed"] = snoozeTime
                                }
                            }
                        }

                        activeReminders[masterIdx] = updatedText
                        displayedReminders[index] = updatedText
                        saveRemindersToPrefs(updateStatusNotification = false)
                        updateSummary()
                    }
                }
            },
            onShareReminderRequested = { index ->
                if (index in displayedReminders.indices) {
                    val reminderText = displayedReminders[index]
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, reminderText)
                    }
                    val chooserIntent = Intent.createChooser(shareIntent, getString(R.string.share))
                    startActivity(chooserIntent)
                }
            },
            onSearchQueryChanged = { query ->
                currentSearchQuery = query
                updateSummaryAndAdapter()
            }
        )
        binding.remindersList.layoutManager = LinearLayoutManager(this)
        binding.remindersList.adapter = adapter
    }

    private fun setupSwipeGestures() {
        val swipeHandler = object : ItemTouchHelper.SimpleCallback(0, ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT) {
            override fun getSwipeDirs(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
                if (viewHolder.itemViewType != RemindersAdapter.TYPE_ACTIVE_REMINDER) {
                    return 0 // Disable swipe on Create Input Row, Snoozed Section Header, and Footer Instructions
                }
                return super.getSwipeDirs(recyclerView, viewHolder)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position in 1..displayedReminders.size) {
                    val index = position - 1
                    val reminderText = displayedReminders[index]

                    if (direction == ItemTouchHelper.LEFT) {
                        // Swipe left -> Open Snooze options dialog (with Unsnooze option if snoozed)
                        adapter.notifyItemChanged(position)
                        showSnoozeOptionsDialog(reminderText)
                    } else if (direction == ItemTouchHelper.RIGHT) {
                        // Swipe right -> Mark Done confirmation
                        adapter.notifyItemChanged(position)
                        showMarkDoneConfirmationDialog(reminderText)
                    }
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX != 0f) {
                    val background = ColorDrawable()
                    val icon: Drawable?

                    if (dX > 0) {
                        // Swipe Right -> Mark Done (Checkmark icon)
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_surface)
                        background.setBounds(itemView.left, itemView.top, itemView.left + dX.toInt(), itemView.bottom)
                        background.draw(c)

                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_done)
                        icon?.let {
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + margin
                            val bottom = top + it.intrinsicHeight
                            val left = itemView.left + margin
                            val right = left + it.intrinsicWidth
                            it.setBounds(left, top, right, bottom)
                            it.draw(c)
                        }
                    } else if (dX < 0) {
                        // Swipe Left -> Snooze (Clock icon)
                        background.color = ContextCompat.getColor(this@MainActivity, R.color.bg_surface)
                        background.setBounds(itemView.right + dX.toInt(), itemView.top, itemView.right, itemView.bottom)
                        background.draw(c)

                        icon = ContextCompat.getDrawable(this@MainActivity, R.drawable.ic_action_snooze)
                        icon?.let {
                            val margin = (itemView.height - it.intrinsicHeight) / 2
                            val top = itemView.top + margin
                            val bottom = top + it.intrinsicHeight
                            val right = itemView.right - margin
                            val left = right - it.intrinsicWidth
                            it.setBounds(left, top, right, bottom)
                            it.draw(c)
                        }
                    }
                }

                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        }

        ItemTouchHelper(swipeHandler).attachToRecyclerView(binding.remindersList)
    }

    private fun showSnoozeOptionsDialog(reminderText: String) {
        val trimmed = reminderText.trim().lowercase()
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
            if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
        }
        val isSnoozed = snoozeUntil > now

        val topChoices = ReminderNotificationListenerService.getTopSnoozeChoices(this).map { it.toString() }
        val durations = (topChoices + "Custom...").toTypedArray()
        val options = if (isSnoozed) {
            arrayOf(getString(R.string.unsnooze)) + durations
        } else {
            durations
        }

        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.snooze_dialog_title)
            .setItems(options) { _, which ->
                if (isSnoozed && which == 0) {
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
                    prefs.edit().remove("snooze_$trimmed").apply()
                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
                } else {
                    val durationIndex = if (isSnoozed) which - 1 else which
                    if (durationIndex in 0 until durations.size - 1) {
                        applySnoozeDuration(reminderText, durations[durationIndex])
                    } else {
                        showCustomSnoozeInputDialog(reminderText)
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showCustomSnoozeInputDialog(reminderText: String) {
        val padding = (16 * resources.displayMetrics.density).toInt()
        val input = EditText(this).apply {
            id = R.id.import_input
            hint = getString(R.string.snooze_custom_hint)
            setSingleLine(true)
            setPadding(padding, padding / 2, padding, padding / 2)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.snooze_dialog_title)
            .setView(input)
            .setPositiveButton(R.string.snooze) { _, _ ->
                val customInput = input.text.toString().trim()
                applySnoozeDuration(reminderText, customInput)
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun applySnoozeDuration(reminderText: String, durationChoice: String) {
        val parseResult = CreateReminderReceiver.parseSnoozeDuration(durationChoice)
        if (parseResult == null) {
            AppLogger.log(this, "MainActivity", "Failed to snooze: invalid duration '$durationChoice'")
            Toast.makeText(this, R.string.toast_invalid_snooze_input, Toast.LENGTH_SHORT).show()
            return
        }

        val canonicalChoice = CreateReminderReceiver.canonicalizeSnoozeChoice(durationChoice)
        if (canonicalChoice != null) {
            val freqPrefs = getSharedPreferences(PREFS_SNOOZE_FREQ, Context.MODE_PRIVATE)
            freqPrefs.edit().putLong(canonicalChoice, System.currentTimeMillis()).apply()
        }

        val (snoozeMs, durationLabel) = parseResult
        val snoozeUntil = System.currentTimeMillis() + snoozeMs
        val trimmed = reminderText.trim().lowercase()

        ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] = snoozeUntil
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putLong("snooze_$trimmed", snoozeUntil).apply()

        ReminderNotificationListenerService.instance?.showStatusNotification()
        updateSummaryAndAdapter()

        AppLogger.log(this, "MainActivity", "Snoozed reminder for $durationLabel")
        val toastText = getString(R.string.toast_reminder_snoozed_duration, durationLabel)
        Toast.makeText(this, toastText, Toast.LENGTH_SHORT).show()
    }

    private fun showMarkDoneConfirmationDialog(reminderText: String) {
        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.mark_done)
            .setMessage("Are you sure you want to mark \"$reminderText\" as done?")
            .setPositiveButton(R.string.mark_done) { _, _ ->
                val idx = activeReminders.indexOf(reminderText)
                if (idx != -1) {
                    val doneText = if (reminderText.contains("#done", ignoreCase = true)) {
                        reminderText
                    } else {
                        "$reminderText #done"
                    }
                    activeReminders[idx] = doneText
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
                    val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).remove("snooze_$trimmed").apply()

                    val notificationId = ReminderNotificationListenerService.getNotificationIdForReminder(reminderText)
                    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    notificationManager?.cancel(notificationId)

                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    AppLogger.log(this, "MainActivity", "Marked reminder done")
                    Toast.makeText(this, R.string.toast_reminder_done, Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun loadReminders() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        activeReminders.clear()
        activeReminders.addAll(savedSet)
        updateSummaryAndAdapter()
    }

    private fun saveRemindersToPrefs(updateStatusNotification: Boolean = true) {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()
        if (updateStatusNotification) {
            ReminderNotificationListenerService.instance?.showStatusNotification()
        }
    }

    private fun updateSummaryAndAdapter() {
        if (binding.remindersList.isComputingLayout) {
            binding.remindersList.post { updateSummaryAndAdapter() }
            return
        }

        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()

        val searchContainsDone = currentSearchQuery.contains("#done", ignoreCase = true)

        // 1. Filter items by status tab and #done tag (#done items only shown if search query contains #done)
        val filteredByStatus = when (currentFilter) {
            ReminderFilter.ALL -> activeReminders.filter { reminder ->
                val isDone = reminder.contains("#done", ignoreCase = true)
                if (isDone) searchContainsDone else true
            }
            ReminderFilter.ACTIVE -> activeReminders.filter { reminder ->
                val isDone = reminder.contains("#done", ignoreCase = true)
                if (isDone) return@filter searchContainsDone
                val trimmed = reminder.trim().lowercase()
                val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                    if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
                }
                snoozeUntil <= now
            }
            ReminderFilter.SNOOZED -> activeReminders.filter { reminder ->
                val isDone = reminder.contains("#done", ignoreCase = true)
                if (isDone) return@filter searchContainsDone
                val trimmed = reminder.trim().lowercase()
                val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                    if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
                }
                snoozeUntil > now
            }
        }

        // 2. Filter items by search query using tiered search matching
        val filtered = com.bas080.notificationreminders.utils.ReminderMatcher.filterSearchQueryTiered(filteredByStatus, currentSearchQuery)

        // 3. Sort items: Active items sorted by creation (more recently added first), then snoozed items ordered ascendingly by snooze time
        val activeItems = mutableListOf<String>()
        val snoozedItems = mutableListOf<Pair<String, Long>>()

        for (reminder in filtered) {
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
            }
            if (snoozeUntil > now) {
                snoozedItems.add(reminder to snoozeUntil)
            } else {
                activeItems.add(reminder)
            }
        }

        activeItems.reverse()
        snoozedItems.sortBy { it.second }

        val sorted = mutableListOf<String>()
        sorted.addAll(activeItems)

        if (currentFilter == ReminderFilter.ALL && snoozedItems.isNotEmpty()) {
            sorted.add(HEADER_SNOOZED_SECTION_MARKER)
        }
        sorted.addAll(snoozedItems.map { it.first })

        val snoozeMap = mutableMapOf<String, Long>()
        for (reminder in activeReminders) {
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
            }
            if (snoozeUntil > 0L) {
                snoozeMap[trimmed] = snoozeUntil
            }
        }

        adapter.updateList(sorted, snoozeMap)
        updateSummary()
    }

    private fun updateSummary() {
        val selectedTags = Regex("#[a-zA-Z0-9_]+").findAll(currentSearchQuery).map { it.value }.toList()
        if (selectedTags.isEmpty()) {
            binding.txtSelectedTags.text = "All"
        } else {
            binding.txtSelectedTags.text = selectedTags.joinToString(" ")
        }

        val hasSearchText = currentSearchQuery.isNotBlank()
        binding.btnClearSearch.isEnabled = hasSearchText
        binding.btnClearSearch.isClickable = hasSearchText
        binding.btnClearSearch.isFocusable = hasSearchText
        if (hasSearchText) {
            binding.btnClearSearch.setTextColor(ContextCompat.getColor(this, R.color.accent))
            binding.btnClearSearch.alpha = 1.0f
        } else {
            binding.btnClearSearch.setTextColor(ContextCompat.getColor(this, R.color.text_muted))
            binding.btnClearSearch.alpha = 0.4f
        }

        if (displayedReminders.isEmpty()) {
            binding.txtEmptyReminders.visibility = View.VISIBLE
        } else {
            binding.txtEmptyReminders.visibility = View.GONE
        }
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
        if (binding.aboutContainer.visibility == View.VISIBLE) {
            loadLogs()
        }
        if (isNotificationListenerEnabled()) {
            startReminderService()
        }
    }

    private fun checkAndShowCrashReportDialog() {
        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(NotificationRemindersApplication.KEY_CRASH_TRACE, null) ?: return

        clearCrashTrace()

        val intent = Intent(this, CrashReportActivity::class.java).apply {
            putExtra(CrashReportActivity.EXTRA_CRASH_TRACE, crashTrace)
        }
        startActivity(intent)
    }

    private fun clearCrashTrace() {
        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(NotificationRemindersApplication.KEY_CRASH_TRACE).apply()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndRequestNotificationListenerPermission()
            }
        } else {
            checkAndRequestNotificationListenerPermission()
        }
    }

    private fun checkAndRequestNotificationListenerPermission() {
        if (!isNotificationListenerEnabled()) {
            showNotificationListenerDialog()
        } else {
            startReminderService()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return enabledPackages.contains(packageName)
    }

    private fun showNotificationListenerDialog() {
        AlertDialog.Builder(this, R.style.Theme_NotificationReminders_Dialog)
            .setTitle(R.string.app_name)
            .setMessage("Notification Reminders requires Notification Listener Access to monitor notifications and trigger your reminders.")
            .setPositiveButton("Enable") { _, _ ->
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startReminderService() {
        try {
            ReminderNotificationListenerService.startService(this)
        } catch (_: Exception) {
        }
    }
}

class RemindersAdapter(
    private val displayedReminders: MutableList<String>,
    private val onAddReminder: (String) -> Unit,
    private val onUpdateReminder: (Int, String) -> Unit,
    private val onShareReminderRequested: (Int) -> Unit,
    private val onSearchQueryChanged: (String) -> Unit = {}
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_CREATE_INPUT = 0
        const val TYPE_ACTIVE_REMINDER = 1
        const val TYPE_FOOTER_INSTRUCTIONS = 2
        const val TYPE_SNOOZED_HEADER = 3
        private const val PREFS_REMINDERS = "reminders_prefs"
    }

    private var currentSearchQueryText: String = ""

    fun setSearchQueryText(query: String) {
        currentSearchQueryText = query
        notifyItemChanged(0)
    }

    private val currentSnoozeMap = mutableMapOf<String, Long>()

    private class RemindersDiffCallback(
        private val oldList: List<String>,
        private val newList: List<String>,
        private val oldSnoozeMap: Map<String, Long>,
        private val newSnoozeMap: Map<String, Long>
    ) : androidx.recyclerview.widget.DiffUtil.Callback() {
        override fun getOldListSize(): Int = if (oldList.isEmpty()) 1 else oldList.size + 2
        override fun getNewListSize(): Int = if (newList.isEmpty()) 1 else newList.size + 2

        private fun getOldType(position: Int): Int {
            if (position == 0) return TYPE_CREATE_INPUT
            if (oldList.isNotEmpty() && position == oldList.size + 1) return TYPE_FOOTER_INSTRUCTIONS
            return if (oldList.getOrNull(position - 1) == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
        }

        private fun getNewType(position: Int): Int {
            if (position == 0) return TYPE_CREATE_INPUT
            if (newList.isNotEmpty() && position == newList.size + 1) return TYPE_FOOTER_INSTRUCTIONS
            return if (newList.getOrNull(position - 1) == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
        }

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_CREATE_INPUT -> true
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SNOOZED_HEADER -> true
                else -> oldList.getOrNull(oldItemPosition - 1) == newList.getOrNull(newItemPosition - 1)
            }
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            val oldType = getOldType(oldItemPosition)
            val newType = getNewType(newItemPosition)

            if (oldType != newType) return false

            return when (oldType) {
                TYPE_CREATE_INPUT -> true
                TYPE_FOOTER_INSTRUCTIONS -> true
                TYPE_SNOOZED_HEADER -> true
                else -> {
                    val oldItem = oldList.getOrNull(oldItemPosition - 1) ?: return true
                    val newItem = newList.getOrNull(newItemPosition - 1) ?: return true
                    if (oldItem != newItem) return false

                    val oldTrimmed = oldItem.trim().lowercase()
                    val newTrimmed = newItem.trim().lowercase()

                    val oldSnooze = oldSnoozeMap[oldTrimmed] ?: 0L
                    val newSnooze = newSnoozeMap[newTrimmed] ?: 0L

                    oldSnooze == newSnooze
                }
            }
        }
    }

    fun updateList(newList: List<String>, newSnoozeMap: Map<String, Long> = emptyMap()) {
        val diffCallback = RemindersDiffCallback(displayedReminders, newList, currentSnoozeMap, newSnoozeMap)
        val diffResult = androidx.recyclerview.widget.DiffUtil.calculateDiff(diffCallback)
        displayedReminders.clear()
        displayedReminders.addAll(newList)
        currentSnoozeMap.clear()
        currentSnoozeMap.putAll(newSnoozeMap)
        diffResult.dispatchUpdatesTo(this)
    }

    class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val txtStatus: TextView = view.findViewById(R.id.txt_status)
        val btnShare: ImageView = view.findViewById(R.id.btn_share)
        val btnAction: ImageView = view.findViewById(R.id.btn_action)
        var textWatcher: TextWatcher? = null
    }

    class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtHeaderTitle: TextView = view.findViewById(R.id.txt_header_title)
    }

    class FooterViewHolder(view: View) : RecyclerView.ViewHolder(view)

    override fun getItemViewType(position: Int): Int {
        if (position == 0) return TYPE_CREATE_INPUT
        if (displayedReminders.isNotEmpty() && position == displayedReminders.size + 1) return TYPE_FOOTER_INSTRUCTIONS
        val item = displayedReminders[position - 1]
        return if (item == HEADER_SNOOZED_SECTION_MARKER) TYPE_SNOOZED_HEADER else TYPE_ACTIVE_REMINDER
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_FOOTER_INSTRUCTIONS -> {
                val view = inflater.inflate(R.layout.item_footer_instructions, parent, false)
                FooterViewHolder(view)
            }
            TYPE_SNOOZED_HEADER -> {
                val view = inflater.inflate(R.layout.item_section_header, parent, false)
                HeaderViewHolder(view)
            }
            else -> {
                val view = inflater.inflate(R.layout.item_reminder, parent, false)
                ItemViewHolder(view)
            }
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is HeaderViewHolder) {
            holder.txtHeaderTitle.text = "SNOOZED"
            return
        }
        if (holder !is ItemViewHolder) return

        holder.textWatcher?.let { holder.reminderInput.removeTextChangedListener(it) }

        ViewCompat.setAccessibilityDelegate(holder.btnAction, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        ViewCompat.setAccessibilityDelegate(holder.btnShare, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        val viewType = getItemViewType(position)
        val context = holder.itemView.context

        if (viewType == TYPE_CREATE_INPUT) {
            holder.reminderInput.hint = "Add or search reminders..."
            holder.txtStatus.visibility = View.GONE
            holder.btnShare.visibility = View.GONE
            holder.btnAction.visibility = View.VISIBLE
            holder.btnAction.setImageResource(R.drawable.ic_action_add)
            holder.btnAction.setColorFilter(ContextCompat.getColor(context, R.color.accent))
            holder.btnAction.contentDescription = context.getString(R.string.add_reminder)

            if (holder.reminderInput.text.toString() != currentSearchQueryText && !holder.reminderInput.hasFocus()) {
                holder.reminderInput.setText(currentSearchQueryText)
            }

            val searchHandler = android.os.Handler(android.os.Looper.getMainLooper())
            var searchRunnable: Runnable? = null

            val submitActionWithCancel = {
                searchRunnable?.let { searchHandler.removeCallbacks(it) }
                val text = holder.reminderInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    holder.reminderInput.setText("")
                    onAddReminder(text)
                    Toast.makeText(holder.itemView.context, R.string.toast_reminder_created, Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(holder.itemView.context, R.string.toast_reminder_create_failed_empty, Toast.LENGTH_SHORT).show()
                }
            }

            holder.btnAction.setOnClickListener {
                submitActionWithCancel()
            }

            holder.reminderInput.setOnFocusChangeListener(null)

            holder.reminderInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    submitActionWithCancel()
                    true
                } else {
                    false
                }
            }

            val searchWatcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    searchRunnable?.let { searchHandler.removeCallbacks(it) }
                    val query = s?.toString() ?: ""
                    searchRunnable = Runnable { onSearchQueryChanged(query) }
                    searchHandler.postDelayed(searchRunnable!!, 200L)
                }
                override fun afterTextChanged(s: Editable?) {}
            }
            holder.reminderInput.addTextChangedListener(searchWatcher)
            holder.textWatcher = searchWatcher
        } else {
            val reminderIndex = position - 1
            val reminderText = displayedReminders[reminderIndex]
            holder.reminderInput.hint = "Reminder"
            holder.reminderInput.setText(reminderText)

            holder.btnAction.visibility = View.GONE
            holder.btnShare.visibility = View.VISIBLE
            holder.btnShare.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val idx = currentPos - 1
                    if (idx in displayedReminders.indices) {
                        onShareReminderRequested(idx)
                    }
                }
            }

            val trimmed = reminderText.trim().lowercase()
            val prefs = context.getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
            val now = System.currentTimeMillis()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
            }

            if (snoozeUntil > now) {
                val formattedTime = MainActivity.formatSnoozeUntil(snoozeUntil, now)
                holder.txtStatus.visibility = View.VISIBLE
                holder.txtStatus.text = context.getString(R.string.snooze_status_format, formattedTime)
            } else {
                holder.txtStatus.visibility = View.GONE
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentPos = holder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        val idx = currentPos - 1
                        if (idx in displayedReminders.indices) {
                            onUpdateReminder(idx, s?.toString() ?: "")
                        }
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }

            holder.reminderInput.addTextChangedListener(watcher)
            holder.textWatcher = watcher
        }
    }

    override fun getItemCount(): Int = if (displayedReminders.isEmpty()) 1 else displayedReminders.size + 2
}
