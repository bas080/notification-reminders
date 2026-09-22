package com.bas080.notificationreminders

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Looper
import android.service.notification.StatusBarNotification
import android.view.View
import android.widget.TextView
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowAlertDialog
import com.bas080.notificationreminders.utils.AppLogger
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenshotGeneratorTest {

    private lateinit var screenshotsDir: File

    @Before
    fun setUp() {
        var dir = File("fastlane/metadata/android/en-US/images/phoneScreenshots")
        if (!dir.exists()) {
            dir = File("../fastlane/metadata/android/en-US/images/phoneScreenshots")
        }
        if (!dir.exists()) {
            dir.mkdirs()
        }
        assertTrue("phoneScreenshots directory must exist", dir.exists() && dir.isDirectory)
        screenshotsDir = dir
    }

    @After
    fun tearDown() {
        PickNotificationActivity.mockActiveNotifications = null
    }

    @Test
    fun captureFeatureScreenshots() {
        captureRemindersListScreenshot()
        captureLogsViewScreenshot()
        captureNotificationDrawerScreenshot()
    }

    private fun captureRemindersListScreenshot() {
        val app = RuntimeEnvironment.getApplication()
        val prefs = app.getSharedPreferences("reminders_prefs", Context.MODE_PRIVATE)
        val initialReminders = setOf("Buy groceries", "Call dentist at 3 PM", "Pay electricity bill")
        prefs.edit().putStringSet("key_reminders_list", initialReminders).commit()

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(screenshotsDir, "1.png"))
    }

    private fun captureLogsViewScreenshot() {
        val app = RuntimeEnvironment.getApplication()
        AppLogger.clearLogs(app)
        AppLogger.log(app, "App", "Application started successfully")
        AppLogger.log(app, "Service", "Notification listener service bound")
        AppLogger.log(app, "Matcher", "Matched reminder: 'Buy groceries'")

        val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
        val activity = controller.get()

        val btnLogs = activity.findViewById<TextView>(R.id.btn_nav_logs)
        btnLogs?.performClick()
        shadowOf(Looper.getMainLooper()).idle()

        val decorView = activity.window.decorView
        renderAndSaveView(decorView, File(screenshotsDir, "2.png"))
    }

    private fun captureNotificationDrawerScreenshot() {
        val sbn1 = createMockSbn("com.whatsapp", "WhatsApp", "Meeting with design team")
        val sbn2 = createMockSbn("com.android.calendar", "Calendar", "Doctor's Appointment at 4 PM")
        val sbn3 = createMockSbn("com.google.android.gm", "Email", "Flight confirmation")

        PickNotificationActivity.mockActiveNotifications = arrayOf(sbn1, sbn2, sbn3)

        val controller = Robolectric.buildActivity(PickNotificationActivity::class.java).setup()
        val activity = controller.get()
        shadowOf(Looper.getMainLooper()).idle()

        val dialog = ShadowAlertDialog.getLatestDialog()
        val viewToRender = dialog?.window?.decorView ?: activity.window.decorView

        renderAndSaveView(viewToRender, File(screenshotsDir, "3.png"))
    }

    private fun createMockSbn(packageName: String, title: String, text: String): StatusBarNotification {
        val context = RuntimeEnvironment.getApplication()
        val extras = Bundle().apply {
            putCharSequence("android.title", title)
            putCharSequence("android.text", text)
        }
        @Suppress("DEPRECATION")
        val notification = Notification.Builder(context, "test_channel")
            .setExtras(extras)
            .build()
        @Suppress("DEPRECATION")
        return StatusBarNotification(
            packageName,
            packageName,
            1,
            "tag",
            1000,
            1000,
            1,
            notification,
            android.os.Process.myUserHandle(),
            System.currentTimeMillis()
        )
    }

    private fun renderAndSaveView(view: View, outputFile: File) {
        val width = 1080
        val height = 2160

        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)

        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }

        assertTrue("Screenshot ${outputFile.name} should exist", outputFile.exists())
        assertTrue("Screenshot ${outputFile.name} should not be empty", outputFile.length() > 0)
    }
}
