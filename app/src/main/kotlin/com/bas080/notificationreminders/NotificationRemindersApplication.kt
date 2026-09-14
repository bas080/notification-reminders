package com.bas080.notificationreminders

import android.app.Application
import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

class NotificationRemindersApplication : Application() {

    companion object {
        const val PREFS_NAME = "crash_prefs"
        const val KEY_CRASH_TRACE = "key_crash_trace"
    }

    override fun onCreate() {
        super.onCreate()
        setupGlobalCrashHandler()
    }

    private fun setupGlobalCrashHandler() {
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
}
