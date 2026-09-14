package com.bas080.notificationreminders.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

    @Test
    fun testGetNotificationIdForReminderIsDeterministic() {
        val id1 = ReminderNotificationListenerService.getNotificationIdForReminder("Buy milk")
        val id2 = ReminderNotificationListenerService.getNotificationIdForReminder("buy milk")
        val id3 = ReminderNotificationListenerService.getNotificationIdForReminder("  Buy Milk  ")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
        assertNotEquals(ReminderNotificationListenerService.NOTIFICATION_ID, id1)
    }
}
