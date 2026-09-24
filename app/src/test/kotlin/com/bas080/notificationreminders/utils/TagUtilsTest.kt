package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class TagUtilsTest {

    @Test
    fun testExtractTags() {
        val input = "Fix bug #jules in #todolist with #todo tag"
        val tags = TagUtils.extractTags(input)

        assertEquals(listOf("#jules", "#todolist", "#todo"), tags)
    }

    @Test
    fun testReplaceTagDoesNotCorruptLongerTags() {
        val input = "Review #todo item in #todolist"
        val result = TagUtils.replaceTag(input, "#todo", "#done")

        assertEquals("Review #done item in #todolist", result)
    }

    @Test
    fun testRemoveTagRemovesTagAndCleansWhitespace() {
        val input = "Buy milk #groceries #urgent today"
        val result = TagUtils.removeTag(input, "#groceries")

        assertEquals("Buy milk #urgent today", result)
    }

    @Test
    fun testCaseInsensitiveTagReplacement() {
        val input = "Fix issue #JULES now"
        val result = TagUtils.replaceTag(input, "#jules", "#sent")

        assertEquals("Fix issue #sent now", result)
    }
}
