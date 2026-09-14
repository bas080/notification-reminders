package com.bas080.notificationreminders.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMatcherTest {

    @Test
    fun testNonCommonWordMatching() {
        val reminder = "Buy milk at supermarket"
        val notification = "Special offer: Fresh milk available!"

        assertTrue(ReminderMatcher.matches(reminder, notification))
    }

    @Test
    fun testCommonWordsOnlyDoNotMatchUnlessSubstring() {
        val reminder = "and"
        val notification = "This and that"

        // "and" is in COMMON_WORDS, falls back to substring match
        assertTrue(ReminderMatcher.matches(reminder, notification))
    }

    @Test
    fun testNoCommonWordMatch() {
        val reminder = "Buy milk"
        val notification = "Your flight is confirmed"

        assertFalse(ReminderMatcher.matches(reminder, notification))
    }

    @Test
    fun testEmptyReminder() {
        assertFalse(ReminderMatcher.matches("", "Some notification"))
    }
}
