package com.bas080.notificationreminders.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderMatcherTest {

    @Test
    fun matches_caseInsensitiveMatching() {
        // Upper case reminder, lower case notification
        assertTrue(ReminderMatcher.matches("BUY MILK", "Remember to buy milk"))
        // Lower case reminder, mixed case notification
        assertTrue(ReminderMatcher.matches("buy milk", "Please Buy Milk today"))
    }

    @Test
    fun matches_filtersOutCommonWords() {
        // "the", "a", "is" are common words and should be ignored
        // If only common words match, result should be false
        assertFalse(ReminderMatcher.matches("the a is", "the a is present"))

        // Significant word "groceries" should match despite common words present
        assertTrue(ReminderMatcher.matches("the groceries", "buy the groceries"))
    }

    @Test
    fun matches_wordOrderIndependent() {
        // Reminder words in different order than notification
        assertTrue(ReminderMatcher.matches("milk buy", "I need to buy whole milk"))
        assertTrue(ReminderMatcher.matches("doctor call urgent", "urgent notification: call your doctor"))
    }

    @Test
    fun matches_partialWordMatches() {
        // Substring matching: "grocer" matches "groceries"
        assertTrue(ReminderMatcher.matches("grocer", "heading to the groceries store"))
        assertTrue(ReminderMatcher.matches("meeting", "calendar event: team-meetings"))
    }

    @Test
    fun matches_noMatchWhenNoSignificantWordsMatch() {
        assertFalse(ReminderMatcher.matches("buy apples", "pick up bananas from store"))
    }
}
