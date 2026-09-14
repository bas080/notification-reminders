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
import java.io.PrintWriter
import java.io.StringWriter

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PREFS_NAME = "crash_prefs"
        private const val KEY_CRASH_TRACE = "key_crash_trace"
        private const val REPORT_EMAIL = "bas080@hotmail.com"
    }

    private lateinit var binding: ActivityMainBinding

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            checkAndRequestNotificationListenerPermission()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setupCrashHandler()

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        checkAndShowCrashReportDialog()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        if (isNotificationListenerEnabled()) {
            startReminderService()
        }
    }

    private fun setupCrashHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            saveCrashTrace(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    private fun saveCrashTrace(throwable: Throwable) {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()

        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_CRASH_TRACE, stackTrace).commit()
    }

    private fun checkAndShowCrashReportDialog() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = prefs.getString(KEY_CRASH_TRACE, null) ?: return

        AlertDialog.Builder(this)
            .setTitle("Application Crash Report")
            .setMessage("The app crashed during its previous run. Would you like to send a crash report to bas080@hotmail.com?")
            .setPositiveButton("Send Report") { _, _ ->
                sendCrashReportEmail(crashTrace)
                clearCrashTrace()
            }
            .setNegativeButton("Dismiss") { _, _ ->
                clearCrashTrace()
            }
            .setCancelable(false)
            .show()
    }

    private fun clearCrashTrace() {
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().remove(KEY_CRASH_TRACE).apply()
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
        ReminderNotificationListenerService.startService(this)
    }
}
