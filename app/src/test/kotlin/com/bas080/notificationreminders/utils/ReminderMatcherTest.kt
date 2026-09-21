package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
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
    fun testCustomCommonWordsParsingAndMatching() {
        val customStr = "de, het, een, van, en, in"
        val commonWords = ReminderMatcher.parseCommonWords(customStr)

        assertTrue(commonWords.contains("de"))
        assertTrue(commonWords.contains("het"))
        assertTrue(commonWords.contains("een"))
        assertEquals(6, commonWords.size)

        val reminder = "Melk halen in de supermarkt"
        val notification = "Verse melk in de aanbieding!"

        assertTrue(ReminderMatcher.matches(reminder, notification, commonWords))
    }

    @Test
    fun testCommonWordsOnlyDoNotMatchUnlessSubstring() {
        val reminder = "and"
        val notification = "This and that"

        // "and" is in DEFAULT_COMMON_WORDS, falls back to substring match
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

    @Test
    fun testTieredSearchQueryMatching() {
        val reminders = listOf("Buy milk at supermarket", "Call dentist", "Buy groceries")

        // Tier 1 / Tier 2 AND match
        val res1 = ReminderMatcher.filterSearchQueryTiered(reminders, "Buy milk")
        assertEquals(listOf("Buy milk at supermarket"), res1)

        // Substring / Sub-word match
        val res2 = ReminderMatcher.filterSearchQueryTiered(reminders, "supermar")
        assertEquals(listOf("Buy milk at supermarket"), res2)

        // Non-matching query
        val res3 = ReminderMatcher.filterSearchQueryTiered(reminders, "doctor appointment")
        assertTrue(res3.isEmpty())
    }
}
