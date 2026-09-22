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
    fun testMatchesSearchQueryLenientMatching() {
        val reminder = "Buy milk at supermarket"

        // 1. Direct substring match
        assertTrue(ReminderMatcher.matchesSearchQuery(reminder, "milk"))
        assertTrue(ReminderMatcher.matchesSearchQuery(reminder, "MILK"))

        // 2. Token / word stem match
        assertTrue(ReminderMatcher.matchesSearchQuery(reminder, "supermar"))

        // 3. Normalized punctuation / token match
        assertTrue(ReminderMatcher.matchesSearchQuery(reminder, "buy-milk!"))

        // 4. Non-matching search query
        assertFalse(ReminderMatcher.matchesSearchQuery(reminder, "dentist appointment"))
    }
}
