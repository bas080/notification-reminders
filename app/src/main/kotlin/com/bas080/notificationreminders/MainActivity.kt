package com.bas080.notificationreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"

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
    private lateinit var adapter: RemindersAdapter

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupRecyclerView()
        loadReminders()

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    private fun setupNavigation() {
        markAsButtonAccessibility(binding.btnNavReminders)
        markAsButtonAccessibility(binding.btnNavLogs)
        markAsButtonAccessibility(binding.btnClearLogs)
        markAsButtonAccessibility(binding.btnExportMarkdown)
        markAsButtonAccessibility(binding.btnImportMarkdown)

        binding.btnNavReminders.setOnClickListener {
            showRemindersView()
        }

        binding.btnNavLogs.setOnClickListener {
            showLogsView()
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

        AlertDialog.Builder(this)
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
        binding.logsContainer.visibility = View.GONE
        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        binding.btnNavLogs.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavLogs.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
    }

    private fun showLogsView() {
        binding.remindersContainer.visibility = View.GONE
        binding.logsContainer.visibility = View.VISIBLE
        binding.btnNavReminders.setTypeface(null, android.graphics.Typeface.NORMAL)
        binding.btnNavReminders.setTextColor(ContextCompat.getColor(this, R.color.text_secondary))
        binding.btnNavLogs.setTypeface(null, android.graphics.Typeface.BOLD)
        binding.btnNavLogs.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
        loadLogs()
    }

    private fun loadLogs() {
        val logs = AppLogger.getLogs(this)
        binding.txtLogs.text = if (logs.isNotBlank()) logs else "No logs available."
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            activeReminders,
            onAddReminder = { newReminder ->
                activeReminders.add(newReminder)
                saveRemindersToPrefs()
                ReminderNotificationListenerService.instance?.postMatchNotification(newReminder)
                updateSummaryAndAdapter()
            },
            onUpdateReminder = { index, updatedText ->
                if (index in activeReminders.indices) {
                    activeReminders[index] = updatedText
                    saveRemindersToPrefs()
                    updateSummary()
                }
            },
            onDeleteReminderRequested = { index ->
                if (index in activeReminders.indices) {
                    showDeleteConfirmationDialog(index)
                }
            },
            onUnsnoozeReminder = { index ->
                if (index in activeReminders.indices) {
                    val reminderText = activeReminders[index]
                    val trimmed = reminderText.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
                    val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    prefs.edit().remove("snooze_$trimmed").apply()
                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    Toast.makeText(this, R.string.toast_snooze_cancelled, Toast.LENGTH_SHORT).show()
                }
            }
        )
        binding.remindersList.layoutManager = LinearLayoutManager(this)
        binding.remindersList.adapter = adapter
    }

    private fun showDeleteConfirmationDialog(index: Int) {
        val reminderText = activeReminders.getOrNull(index) ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete Reminder")
            .setMessage("Are you sure you want to delete \"$reminderText\"?")
            .setPositiveButton("Delete") { _, _ ->
                if (index in activeReminders.indices) {
                    val deletedItem = activeReminders.removeAt(index)
                    val trimmed = deletedItem.trim().lowercase()
                    ReminderNotificationListenerService.lastTriggeredMap.remove("snooze_$trimmed")
                    val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
                    prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).remove("snooze_$trimmed").apply()
                    ReminderNotificationListenerService.instance?.showStatusNotification()
                    updateSummaryAndAdapter()
                    Toast.makeText(this, R.string.toast_reminder_deleted, Toast.LENGTH_SHORT).show()
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

    private fun saveRemindersToPrefs() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()
        ReminderNotificationListenerService.instance?.showStatusNotification()
    }

    private fun updateSummaryAndAdapter() {
        adapter.notifyDataSetChanged()
        updateSummary()
    }

    private fun updateSummary() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        var snoozedCount = 0
        for (reminder in activeReminders) {
            val trimmed = reminder.trim().lowercase()
            val snoozeUntil = prefs.getLong("snooze_$trimmed", 0L).let {
                if (it > 0L) it else (ReminderNotificationListenerService.lastTriggeredMap["snooze_$trimmed"] ?: 0L)
            }
            if (snoozeUntil > now) {
                snoozedCount++
            }
        }
        val totalCount = activeReminders.size
        val activeCount = totalCount - snoozedCount

        if (totalCount == 0) {
            binding.txtRemindersSummary.setText(R.string.no_active_reminders)
            binding.txtEmptyReminders.visibility = View.VISIBLE
        } else {
            binding.txtEmptyReminders.visibility = View.GONE
            if (snoozedCount > 0) {
                binding.txtRemindersSummary.text = getString(R.string.reminders_summary_combined, activeCount, snoozedCount)
            } else if (totalCount == 1) {
                binding.txtRemindersSummary.setText(R.string.active_reminder_single)
            } else {
                binding.txtRemindersSummary.text = getString(R.string.active_reminders_count, totalCount)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
        if (binding.logsContainer.visibility == View.VISIBLE) {
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
        AlertDialog.Builder(this)
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
    private val activeReminders: List<String>,
    private val onAddReminder: (String) -> Unit,
    private val onUpdateReminder: (Int, String) -> Unit,
    private val onDeleteReminderRequested: (Int) -> Unit,
    private val onUnsnoozeReminder: (Int) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

    companion object {
        const val TYPE_CREATE_INPUT = 0
        const val TYPE_ACTIVE_REMINDER = 1
        private const val PREFS_REMINDERS = "reminders_prefs"
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val txtStatus: TextView = view.findViewById(R.id.txt_status)
        val btnUnsnooze: TextView = view.findViewById(R.id.btn_unsnooze)
        val btnAction: TextView = view.findViewById(R.id.btn_action)
        var textWatcher: TextWatcher? = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0) {
            TYPE_CREATE_INPUT
        } else {
            TYPE_ACTIVE_REMINDER
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textWatcher?.let { holder.reminderInput.removeTextChangedListener(it) }

        ViewCompat.setAccessibilityDelegate(holder.btnAction, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        ViewCompat.setAccessibilityDelegate(holder.btnUnsnooze, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })

        val viewType = getItemViewType(position)

        if (viewType == TYPE_CREATE_INPUT) {
            holder.reminderInput.setText("")
            holder.reminderInput.hint = "Add a new reminder..."
            holder.txtStatus.visibility = View.GONE
            holder.btnUnsnooze.visibility = View.GONE
            holder.btnAction.text = "+"
            holder.btnAction.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.accent))

            val submitAction = {
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
                submitAction()
            }

            holder.reminderInput.setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                    submitAction()
                    true
                } else {
                    false
                }
            }
        } else {
            val reminderIndex = position - 1
            val reminderText = activeReminders[reminderIndex]
            holder.reminderInput.hint = "Reminder"
            holder.reminderInput.setText(reminderText)
            holder.btnAction.text = "✕"
            holder.btnAction.setTextColor(ContextCompat.getColor(holder.itemView.context, R.color.accent_danger))

            val context = holder.itemView.context
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
                holder.btnUnsnooze.visibility = View.VISIBLE
                holder.btnUnsnooze.setOnClickListener {
                    val currentPos = holder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        val idx = currentPos - 1
                        if (idx in activeReminders.indices) {
                            onUnsnoozeReminder(idx)
                        }
                    }
                }
            } else {
                holder.txtStatus.visibility = View.GONE
                holder.btnUnsnooze.visibility = View.GONE
            }

            val watcher = object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    val currentPos = holder.bindingAdapterPosition
                    if (currentPos != RecyclerView.NO_POSITION) {
                        val idx = currentPos - 1
                        if (idx in activeReminders.indices) {
                            onUpdateReminder(idx, s?.toString() ?: "")
                        }
                    }
                }
                override fun afterTextChanged(s: Editable?) {}
            }

            holder.reminderInput.addTextChangedListener(watcher)
            holder.textWatcher = watcher

            holder.btnAction.setOnClickListener {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    val idx = currentPos - 1
                    if (idx in activeReminders.indices) {
                        onDeleteReminderRequested(idx)
                    }
                }
            }
        }
    }

    override fun getItemCount(): Int = activeReminders.size + 1
}
