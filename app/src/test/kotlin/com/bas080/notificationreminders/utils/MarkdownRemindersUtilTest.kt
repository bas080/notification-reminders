package com.bas080.notificationreminders.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownRemindersUtilTest {

    @Test
    fun testExportToMarkdownFormatsChecklist() {
        val reminders = listOf("Buy milk", "Call mom", "Doctor appointment")
        val markdown = MarkdownRemindersUtil.exportToMarkdown(reminders)

        val expected = "- [ ] Buy milk\n- [ ] Call mom\n- [ ] Doctor appointment"
        assertEquals(expected, markdown)
    }

    @Test
    fun testExportToMarkdownEmptyListReturnsEmptyString() {
        val markdown = MarkdownRemindersUtil.exportToMarkdown(emptyList())
        assertEquals("", markdown)
    }

    @Test
    fun testImportFromMarkdownParsesChecklistAndLists() {
        val markdownInput = """
            # Shopping List
            - [ ] Buy apples
            - [x] Buy bread
            * Fresh oranges
            1. Call electrician
            Plain reminder item
        """.trimIndent()

        val imported = MarkdownRemindersUtil.importFromMarkdown(markdownInput)

        assertEquals(5, imported.size)
        assertEquals("Buy apples", imported[0])
        assertEquals("Buy bread", imported[1])
        assertEquals("Fresh oranges", imported[2])
        assertEquals("Call electrician", imported[3])
        assertEquals("Plain reminder item", imported[4])
    }

    @Test
    fun testImportFromMarkdownBlankInputReturnsEmptyList() {
        val imported = MarkdownRemindersUtil.importFromMarkdown("   \n   ")
        assertTrue(imported.isEmpty())
    }
}
