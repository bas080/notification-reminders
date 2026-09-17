package com.bas080.notificationreminders

import android.content.Intent
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MainActivityTest {

    @Test
    fun testExtraFocusInputConstant() {
        assertEquals("extra_focus_input", MainActivity.EXTRA_FOCUS_INPUT)
    }

    @Test
    fun testActivityFocusesInputWhenExtraPresent() {
        val intent = Intent(RuntimeEnvironment.getApplication(), MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_FOCUS_INPUT, true)
        }
        val controller = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        val activity = controller.get()

        Shadows.shadowOf(android.os.Looper.getMainLooper()).idle()

        val recyclerView = activity.findViewById<RecyclerView>(R.id.reminders_list)
        assertNotNull(recyclerView)
        val viewHolder = recyclerView.findViewHolderForAdapterPosition(0) as? RemindersAdapter.ViewHolder
        assertNotNull("Position 0 viewholder should exist", viewHolder)
        assertTrue("Input field should be focused", viewHolder!!.reminderInput.hasFocus())
    }
}
