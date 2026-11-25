package com.musheer360.swiftslate.manager

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ErrorLogTest {
    private var now = 1_000L
    private fun log() = ErrorLog(
        ApplicationProvider.getApplicationContext<Application>().getSharedPreferences(ErrorLog.PREFS_NAME, 0)
    ) { now }

    @Test
    fun keeps_only_the_newest_and_clears() {
        val l = log()
        repeat(ErrorLog.MAX + 5) { now++; l.add("Command", "error $it") }
        val e = l.entries()
        assertEquals(ErrorLog.MAX, e.size)
        assertEquals("error ${ErrorLog.MAX + 4}", e.last().what)
        l.clear()
        assertEquals(0, l.entries().size)
    }
}
