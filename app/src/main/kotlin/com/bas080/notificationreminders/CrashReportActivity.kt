package com.bas080.notificationreminders

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

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

        findViewById<Button>(R.id.btn_send_report).setOnClickListener {
            sendEmail(crashTrace)
        }
    }

    private fun sendEmail(crashTrace: String) {
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
}
