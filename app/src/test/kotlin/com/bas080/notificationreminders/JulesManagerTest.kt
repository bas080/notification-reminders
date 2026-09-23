package com.bas080.notificationreminders

import android.content.Context
import com.bas080.notificationreminders.jules.JulesManager
import com.bas080.notificationreminders.jules.JulesSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class JulesManagerTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
    }

    @Test
    fun testMatchesAndTags() {
        val andTags = listOf("#jules", "#todo")

        // Matches both tags
        assertTrue(JulesManager.matchesAndTags("Fix bug #jules #todo", andTags))
        assertTrue(JulesManager.matchesAndTags("Fix bug #TODO #JULES", andTags))

        // Missing one required tag
        assertFalse(JulesManager.matchesAndTags("Fix bug #jules", andTags))

        // Contains #done
        assertFalse(JulesManager.matchesAndTags("Fix bug #jules #todo #done", andTags))
    }

    @Test
    fun testFindMatchingReminders() {
        val reminders = listOf(
            "Task 1 #jules #todo",
            "Task 2 #jules",
            "Task 3 #todo",
            "Task 4 #jules #todo #done"
        )
        val andTags = listOf("#jules", "#todo")

        val matches = JulesManager.findMatchingReminders(reminders, andTags)
        assertEquals(1, matches.size)
        assertEquals("Task 1 #jules #todo", matches[0])
    }

    @Test
    fun testApplyTagReplacementsWithReplacementTag() {
        val replacements = mapOf("#jules" to "#in-progress")
        val input = "Fix critical issue #jules #urgent"
        val result = JulesManager.applyTagReplacements(input, replacements)

        assertEquals("Fix critical issue #in-progress #urgent", result)
    }

    @Test
    fun testApplyTagReplacementsWithNoReplacerRemovesTag() {
        val replacements = mapOf("#todo" to "")
        val input = "Review PR #jules #todo"
        val result = JulesManager.applyTagReplacements(input, replacements)

        assertEquals("Review PR #jules", result)
    }

    @Test
    fun testApplyTagReplacementsRemovesRequiredAndTagsByDefault() {
        val input = "Task #jules #todo #backend"
        val replacements = mapOf("#jules" to "#sent")
        val requiredAndTags = listOf("#jules", "#todo", "#backend")

        val result = JulesManager.applyTagReplacements(input, replacements, requiredAndTags)
        assertEquals("Task #sent", result)
    }

    @Test
    fun testJulesSettingsParsing() {
        val settings = JulesSettings(context).apply {
            andTags = "#jules #todo #backend"
            tagReplacements = "#jules -> #in-progress\n#todo -> \n#backend -> #done"
        }

        val parsedAndTags = settings.getParsedAndTags()
        assertEquals(listOf("#jules", "#todo", "#backend"), parsedAndTags)

        val parsedReplacements = settings.getParsedTagReplacements()
        assertEquals("#in-progress", parsedReplacements["#jules"])
        assertEquals("", parsedReplacements["#todo"])
        assertEquals("#done", parsedReplacements["#backend"])
    }
}
