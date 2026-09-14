package com.bas080.notificationreminders

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
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

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REPORT_EMAIL = "bas080@hotmail.com"
        private const val PREFS_REMINDERS = "reminders_prefs"
        private const val KEY_REMINDERS = "key_reminders_list"
    }

    private lateinit var binding: ActivityMainBinding
    private val activeReminders = mutableListOf<String>()
    private val displayItems = mutableListOf<String>()
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
    }

    private fun setupRecyclerView() {
        adapter = RemindersAdapter(
            displayItems,
            onItemTextChanged = { pos, newText -> onTextChanged(pos, newText) },
            onItemFocusLost = { pos -> onItemFocusLost(pos) },
            onClearClicked = { editText ->
                editText.setText("")
                editText.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
            }
        )
        binding.remindersList.layoutManager = LinearLayoutManager(this)
        binding.remindersList.adapter = adapter
    }

    private fun loadReminders() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        val savedSet = prefs.getStringSet(KEY_REMINDERS, emptySet()) ?: emptySet()
        activeReminders.clear()
        activeReminders.addAll(savedSet)
        rebuildDisplayItems()
    }

    private fun rebuildDisplayItems() {
        displayItems.clear()
        displayItems.add("") // Empty placeholder at top
        displayItems.addAll(activeReminders)
        displayItems.add("") // Empty placeholder at bottom
        adapter.notifyDataSetChanged()
    }

    private fun saveRemindersToPrefs() {
        val prefs = getSharedPreferences(PREFS_REMINDERS, Context.MODE_PRIVATE)
        prefs.edit().putStringSet(KEY_REMINDERS, activeReminders.toSet()).apply()
    }

    private fun onTextChanged(position: Int, newText: String) {
        if (position !in displayItems.indices) return
        displayItems[position] = newText

        // Recompute activeReminders from displayItems excluding empty placeholders
        val newActive = displayItems.filter { it.trim().isNotEmpty() }
        if (newActive != activeReminders) {
            activeReminders.clear()
            activeReminders.addAll(newActive)
            saveRemindersToPrefs()

            // If top or bottom empty placeholder received text, re-ensure top and bottom placeholders exist
            if ((position == 0 && newText.trim().isNotEmpty()) ||
                (position == displayItems.size - 1 && newText.trim().isNotEmpty())
            ) {
                binding.remindersList.post {
                    rebuildDisplayItems()
                }
            }
        }
    }

    private fun onItemFocusLost(position: Int) {
        if (position !in displayItems.indices) return
        val text = displayItems[position].trim()
        if (text.isEmpty()) {
            val newActive = displayItems.filter { it.trim().isNotEmpty() }
            if (newActive != activeReminders) {
                activeReminders.clear()
                activeReminders.addAll(newActive)
                saveRemindersToPrefs()
                rebuildDisplayItems()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        loadReminders()
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
    private val items: List<String>,
    private val onItemTextChanged: (Int, String) -> Unit,
    private val onItemFocusLost: (Int) -> Unit,
    private val onClearClicked: (EditText) -> Unit
) : RecyclerView.Adapter<RemindersAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val reminderInput: EditText = view.findViewById(R.id.reminder_input)
        val btnClear: Button = view.findViewById(R.id.btn_clear)
        var textWatcher: TextWatcher? = null
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_reminder, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textWatcher?.let { holder.reminderInput.removeTextChangedListener(it) }

        holder.reminderInput.setText(items[position])

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onItemTextChanged(currentPos, s?.toString() ?: "")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        }

        holder.reminderInput.addTextChangedListener(watcher)
        holder.textWatcher = watcher

        holder.reminderInput.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
            if (!hasFocus) {
                val currentPos = holder.bindingAdapterPosition
                if (currentPos != RecyclerView.NO_POSITION) {
                    onItemFocusLost(currentPos)
                }
            }
        }

        holder.btnClear.setOnClickListener {
            onClearClicked(holder.reminderInput)
        }
    }

    override fun getItemCount(): Int = items.size
}
