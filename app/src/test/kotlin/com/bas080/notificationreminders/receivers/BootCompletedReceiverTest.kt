package com.bas080.notificationreminders.receivers

import android.content.Intent
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BootCompletedReceiverTest {

    @Test
    fun testOnReceiveBootCompletedStartsListenerService() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = BootCompletedReceiver()
        val intent = Intent(Intent.ACTION_BOOT_COMPLETED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context)
        val nextService = shadowApp.nextStartedService
        assertNotNull("Expected service to be started on BOOT_COMPLETED", nextService)
    }

    @Test
    fun testOnReceivePackageReplacedStartsListenerService() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = BootCompletedReceiver()
        val intent = Intent(Intent.ACTION_MY_PACKAGE_REPLACED)

        receiver.onReceive(context, intent)

        val shadowApp = shadowOf(context)
        val nextService = shadowApp.nextStartedService
        assertNotNull("Expected service to be started on MY_PACKAGE_REPLACED", nextService)
    }
}
