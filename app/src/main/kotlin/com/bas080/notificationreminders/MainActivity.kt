package com.bas080.notificationreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.bas080.notificationreminders.databinding.ActivityMainBinding
import com.bas080.notificationreminders.services.ReminderNotificationListenerService

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REPORT_EMAIL = "bas080@hotmail.com"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
    }

    private lateinit var binding: ActivityMainBinding
    private val remindersList = mutableListOf<String>()
    private lateinit var adapter: RemindersAdapter

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupRecyclerView()
        loadReminders()

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()

        binding.fabAddReminder.setOnClickListener {
            showCreateReminderDialog()
        }

        handleIntent(intent)
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            remindersList,
            onEdit = { position, oldText -> showEditReminderDialog(position, oldText) },
            onDelete = { position -> deleteReminder(position) }
        )
        binding.remindersList.layoutManager = LinearLayoutManager(this)
        binding.remindersList.adapter = adapter
    }

    private fun loadReminders() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        remindersList.clear()
        remindersList.addAll(savedSet)
        adapter.notifyDataSetChanged()
    }

    private fun saveRemindersToPrefs() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, remindersList.toSet()).apply()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == ReminderNotificationListenerService.ACTION_CREATE_REMINDER) {
            showCreateReminderDialog()
        }
    }

    fun showCreateReminderDialog() {
        val inputEditText = android.widget.EditText(this).apply {
            hint = getString(R.string.enter_reminder_text)
        }
        val container = android.widget.FrameLayout(this).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            val params = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(margin, margin / 2, margin, margin / 2)
            layoutParams = params
            addView(inputEditText)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.add_reminder)
            .setView(container)
            .setPositiveButton(R.string.add_reminder) { dialog, _ ->
                val text = inputEditText.text.toString().trim()
                if (text.isNotEmpty()) {
                    saveReminder(text)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    private fun saveReminder(reminderText: String) {
        remindersList.add(reminderText)
        saveRemindersToPrefs()
        adapter.notifyItemInserted(remindersList.size - 1)
    }

    private fun deleteReminder(position: Int) {
        if (position in remindersList.indices) {
            remindersList.removeAt(position)
            saveRemindersToPrefs()
            adapter.notifyItemRemoved(position)
        }
    }

    private fun showEditReminderDialog(position: Int, oldText: String) {
        val inputEditText = android.widget.EditText(this).apply {
            setText(oldText)
            hint = getString(R.string.enter_reminder_text)
        }
        val container = android.widget.FrameLayout(this).apply {
            val margin = (16 * resources.displayMetrics.density).toInt()
            val params = android.widget.FrameLayout.LayoutParams(
                android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.ViewGroup.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(margin, margin / 2, margin, margin / 2)
            layoutParams = params
            addView(inputEditText)
        }

        AlertDialog.Builder(this)
            .setTitle(R.string.edit_reminder)
            .setView(container)
            .setPositiveButton("Save") { dialog, _ ->
                val newText = inputEditText.text.toString().trim()
                if (newText.isNotEmpty() && position in remindersList.indices) {
                    remindersList[position] = newText
                    saveRemindersToPrefs()
                    adapter.notifyItemChanged(position)
                }
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onResume() {
        super.onResume()
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

    private fun sendCrashReportEmail(crashTrace: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$REPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Notification Reminders Crash Report")
            putExtra(
                Intent.EXTRA_TEXT,
                "App Version: 1.0.0\nDevice: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n\nStack Trace:\n$crashTrace"
            )
        }
        try {
            startActivity(Intent.createChooser(intent, "Send Crash Report"))
        } catch (_: Exception) {
        }
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
    private val items: List<String>,
    private val onEdit: (Int, String) -> Unit,
    private val onDelete: (Int) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderText: TextView = view.findViewById(R.id.reminder_text)
        val btnEdit: Button = view.findViewById(R.id.btn_edit)
        val btnDelete: Button = view.findViewById(R.id.btn_delete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.reminderText.text = item
        holder.btnEdit.setOnClickListener { onEdit(holder.bindingAdapterPosition, item) }
        holder.btnDelete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
    }

    override fun getItemCount(): Int = items.size
}
