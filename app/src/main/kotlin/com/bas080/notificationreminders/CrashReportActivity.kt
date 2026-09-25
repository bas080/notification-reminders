package com.bas080.notificationreminders

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import com.bas080.notificationreminders.utils.AppLogger

class CrashReportActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CRASH_TRACE = "extra_crash_trace"
        private const val REPORT_EMAIL = "bas080@hotmail.com"

        fun buildFormattedReport(
            context: Context,
            crashTrace: String,
            userComment: String,
            includeLogs: Boolean
        ): String {
            return StringBuilder().apply {
                append("## Crash Report\n\n")
                append("### User Comment\n")
                val commentText = userComment.trim()
                if (commentText.isNotEmpty()) {
                    append(commentText)
                } else {
                    append("None provided.")
                }
                append("\n\n")

                append("### Device Info\n")
                append("- App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
                append("- Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("- Device: ${Build.MANUFACTURER} ${Build.MODEL}\n\n")

                append("### Stack Trace\n")
                append("```\n")
                append(crashTrace)
                append("\n```\n")

                if (includeLogs) {
                    val logs = AppLogger.getLogs(context)
                    if (logs.isNotBlank()) {
                        append("\n### Application Logs\n")
                        append("```\n")
                        append(logs)
                        append("\n```\n")
                    }
                }
            }.toString()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_crash_report)

        val prefs = getSharedPreferences(NotificationRemindersApplication.PREFS_NAME, Context.MODE_PRIVATE)
        val crashTrace = intent.getStringExtra(EXTRA_CRASH_TRACE)
            ?: prefs.getString(NotificationRemindersApplication.KEY_CRASH_TRACE, null)
            ?: "No stack trace available."

        findViewById<TextView>(R.id.crash_stack_trace).text = crashTrace

        val etUserComment = findViewById<EditText>(R.id.et_user_comment)
        val cbIncludeLogs = findViewById<CheckBox>(R.id.cb_include_logs)

        val btnDontSend = findViewById<TextView>(R.id.btn_dont_send)
        if (btnDontSend != null) {
            markAsButtonAccessibility(btnDontSend)
            btnDontSend.setOnClickListener {
                prefs.edit().remove(NotificationRemindersApplication.KEY_CRASH_TRACE).apply()
                restartApp()
            }
        }

        val btnCopyReport = findViewById<TextView>(R.id.btn_copy_report)
        markAsButtonAccessibility(btnCopyReport)
        btnCopyReport.setOnClickListener {
            val comment = etUserComment.text.toString()
            val includeLogs = cbIncludeLogs.isChecked
            val report = buildFormattedReport(this, crashTrace, comment, includeLogs)

            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Crash Report", report)
            clipboard.setPrimaryClip(clip)

            Toast.makeText(this, R.string.toast_report_copied, Toast.LENGTH_SHORT).show()
        }

        val btnSendReport = findViewById<TextView>(R.id.btn_send_report)
        markAsButtonAccessibility(btnSendReport)
        btnSendReport.setOnClickListener {
            val comment = etUserComment.text.toString()
            val includeLogs = cbIncludeLogs.isChecked
            val report = buildFormattedReport(this, crashTrace, comment, includeLogs)
            sendEmail(report)
        }

        val btnRestartApp = findViewById<TextView>(R.id.btn_restart_app)
        markAsButtonAccessibility(btnRestartApp)
        btnRestartApp.setOnClickListener {
            restartApp()
        }
    }

    private fun markAsButtonAccessibility(view: android.view.View) {
        ViewCompat.setAccessibilityDelegate(view, object : AccessibilityDelegateCompat() {
            override fun onInitializeAccessibilityNodeInfo(host: android.view.View, info: AccessibilityNodeInfoCompat) {
                super.onInitializeAccessibilityNodeInfo(host, info)
                info.className = android.widget.Button::class.java.name
            }
        })
    }

    private fun sendEmail(reportText: String) {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:$REPORT_EMAIL")
            putExtra(Intent.EXTRA_SUBJECT, "Punt Crash Report")
            putExtra(Intent.EXTRA_TEXT, reportText)
        }
        try {
            startActivity(Intent.createChooser(intent, "Send Crash Report"))
        } catch (_: Exception) {
        }
    }

    private fun restartApp() {
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }
}
