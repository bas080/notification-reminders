package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.bas080.notificationreminders.utils.AppLogger

class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_TRACE = "extra_crash_trace"
        private const val REPORT_EMAIL = "bas080@hotmail.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = intent.getStringExtra(EXTRA_CRASH_TRACE)
            ?: prefs.getString(NotificationRemindersApplication.KEY_CRASH_TRACE, null)
            ?: "No stack trace available."

        findViewById<TextView>(R.id.crash_stack_trace).text = crashTrace

        val cbIncludeLogs = findViewById<CheckBox>(R.id.cb_include_logs)

        val btnSendReport = findViewById<TextView>(R.id.btn_send_report)
        ViewCompat.setAccessibilityDelegate(btnSendReport, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
        btnSendReport.setOnClickListener {
            val includeLogs = cbIncludeLogs.isChecked
            sendEmail(crashTrace, includeLogs)
        }
    }

    private fun sendEmail(crashTrace: String, includeLogs: Boolean) {
        val emailBody = StringBuilder().apply {
            append("App Version: 1.0.0\n")
            append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE})\n\n")
            append("Stack Trace:\n")
            append(crashTrace)
            if (includeLogs) {
                append("\n\n--- Application Logs ---\n")
                append(AppLogger.getLogs(this@CrashReportActivity))
            }
        }.toString()

        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$REPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Notification Reminders Crash Report")
            putExtra(Intent.EXTRA_TEXT, emailBody)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send Crash Report"))
        } catch (_: Exception) {
        }
    }
}
