package com.bas080.notificationreminders.utils

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object AppLogger {
    private const val LOG_FILE_NAME = "app_logs.txt"
    private const val MAX_FILE_SIZE_BYTES = 100 * 1024 // 100 KB max log size

    fun log(context: Context, tag: String, message: String) {
        try {
            val file = getLogFile(context)
            if (file.exists() && file.length() > MAX_FILE_SIZE_BYTES) {
                file.delete()
            }
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
            val logEntry = "$timestamp [$tag]: $message\n"
            file.appendText(logEntry)
        } catch (_: Exception) {
        }
    }

    fun getLogs(context: Context, maxLines: Int = 50): String {
        return try {
            val file = getLogFile(context)
            if (file.exists()) {
                val lines = file.readLines()
                val recentLines = lines.takeLast(maxLines)
                recentLines.joinToString("\n")
            } else {
                "No logs recorded."
            }
        } catch (_: Exception) {
            "Unable to read logs."
        }
    }

    fun clearLogs(context: Context) {
        try {
            val file = getLogFile(context)
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun getLogFile(context: Context): File {
        return File(context.filesDir, LOG_FILE_NAME)
    }
}
