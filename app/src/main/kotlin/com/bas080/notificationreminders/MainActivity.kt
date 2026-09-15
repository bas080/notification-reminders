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
import android.widget.Button
import android.widget.EditText
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.services.ReminderNotificationListenerService
import com.bas080.notificationreminders.utils.AppLogger

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
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
        AppLogger.log(this, "MainActivity", "MainActivity onCreate")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupNavigation()
        setupRecyclerView()
        loadReminders()

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    private fun setupNavigation() {
        binding.btnNavReminders.setOnClickListener {
            showRemindersView()
        }

        binding.btnNavLogs.setOnClickListener {
            showLogsView()
        }

        binding.btnClearLogs.setOnClickListener {
            AppLogger.clearLogs(this)
            AppLogger.log(this, "MainActivity", "Logs cleared by user")
            loadLogs()
        }
    }

    private fun showRemindersView() {
        AppLogger.log(this, "MainActivity", "Navigated to Reminders view")
        binding.remindersContainer.visibility = View.VISIBLE
        binding.logsContainer.visibility = View.GONE
    }

    private fun showLogsView() {
        AppLogger.log(this, "MainActivity", "Navigated to Logs view")
        binding.remindersContainer.visibility = View.GONE
        binding.logsContainer.visibility = View.VISIBLE
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
                adapter.notifyDataSetChanged()
                AppLogger.log(this, "MainActivity", "Added new reminder (total count: ${activeReminders.size})")
            },
            onUpdateReminder = { index, updatedText ->
                if (index in activeReminders.indices) {
                    activeReminders[index] = updatedText
                    saveRemindersToPrefs()
                }
            },
            onDeleteReminderRequested = { index ->
                if (index in activeReminders.indices) {
                    showDeleteConfirmationDialog(index)
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
                    activeReminders.removeAt(index)
                    saveRemindersToPrefs()
                    adapter.notifyDataSetChanged()
                    AppLogger.log(this, "MainActivity", "Deleted reminder at index $index (total count: ${activeReminders.size})")
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
        adapter.notifyDataSetChanged()
    }

    private fun saveRemindersToPrefs() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()
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

        AppLogger.log(this, "MainActivity", "Uncaught crash trace detected, launching CrashReportActivity")
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
        AppLogger.log(this, "MainActivity", "Showing notification listener permission dialog")
        AlertDialog.Builder(this)
            .setTitle(R.string.app_name)
            .setMessage("Notification Reminders requires Notification Listener Access to monitor notifications and trigger your reminders.")
            .setPositiveButton("Enable") { _, _ ->
                AppLogger.log(this, "MainActivity", "User agreed to open notification listener settings")
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                startActivity(intent)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startReminderService() {
        try {
            AppLogger.log(this, "MainActivity", "Starting ReminderNotificationListenerService")
            ReminderNotificationListenerService.startService(this)
        } catch (e: Exception) {
            AppLogger.log(this, "MainActivity", "Failed to start ReminderNotificationListenerService: ${e.message}")
        }
    }
}

class RemindersAdapter(
    private val activeReminders: List<String>,
    private val onAddReminder: (String) -> Unit,
    private val onUpdateReminder: (Int, String) -> Unit,
    private val onDeleteReminderRequested: (Int) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

    companion object {
        const val TYPE_CREATE_INPUT = 0
        const val TYPE_ACTIVE_REMINDER = 1
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val btnAction: Button = view.findViewById(R.id.btn_action)
        var textWatcher: TextWatcher? = null
    }

    override fun getItemViewType(position: Int): Int {
        return if (position == 0 || position == activeReminders.size + 1) {
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

        val viewType = getItemViewType(position)

        if (viewType == TYPE_CREATE_INPUT) {
            holder.reminderInput.setText("")
            holder.reminderInput.hint = "Add a new reminder..."
            holder.btnAction.text = "+"

            val submitAction = {
                val text = holder.reminderInput.text.toString().trim()
                if (text.isNotEmpty()) {
                    holder.reminderInput.setText("")
                    onAddReminder(text)
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
            holder.reminderInput.hint = "Reminder"
            holder.reminderInput.setText(activeReminders[reminderIndex])
            holder.btnAction.text = "✕"

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

    override fun getItemCount(): Int = activeReminders.size + 2
}
