package com.bas080.notificationreminders.services

import org.junit.Assert.assertEquals
import org.junit.Test

class ReminderNotificationListenerServiceTest {

    @Test
    fun testActionCreateReminderConstant() {
        assertEquals(
            "com.bas080.notificationreminders.ACTION_CREATE_REMINDER",
            ReminderNotificationListenerService.ACTION_CREATE_REMINDER
        )
    }

    @Test
    fun testChannelIdConstant() {
        assertEquals(
            "notification_reminders_status_channel",
            ReminderNotificationListenerService.CHANNEL_ID
        )
    }

    @Test
    fun testNotificationIdConstant() {
        assertEquals(
            1001,
            ReminderNotificationListenerService.NOTIFICATION_ID
        )
    }
}
