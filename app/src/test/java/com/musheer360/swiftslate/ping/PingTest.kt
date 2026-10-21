package com.musheer360.swiftslate.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class PingTest {

    private val zone = TimeZone.getTimeZone("America/Chicago")
    private fun at(s: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone }.parse(s)!!.time
    private val now = at("2026-10-21 12:30")

    @Test fun remindersArePulledOutOfTheReply() {
        val reply = "You got it, I'll remind you at 3 to take your meds 💊\n\n[[remind 2026-10-21 15:00 | take your meds]]"
        val (clean, found) = Reminders.extract(reply, now, zone)
        assertEquals("You got it, I'll remind you at 3 to take your meds 💊", clean)
        assertEquals(1, found.size)
        assertEquals(at("2026-10-21 15:00"), found[0].atMillis)
        assertEquals("take your meds", found[0].text)
    }

    @Test fun toleratesLooseSpacingAndCase() {
        val (_, found) = Reminders.extract("ok [[ Remind 2026-10-22 9:05|call Aaron ]]", now, zone)
        assertEquals(listOf(Reminders.Found(at("2026-10-22 09:05"), "call Aaron")), found)
    }

    @Test fun pastTimesAndBadDatesAreDropped() {
        val (clean, found) = Reminders.extract("a [[remind 2026-10-20 09:00 | old]] b [[remind 2026-13-40 25:99 | nope]]", now, zone)
        assertTrue(found.isEmpty())
        assertFalse(clean.contains("[["))
    }

    @Test fun repliesWithoutRemindersAreUntouched() {
        val (clean, found) = Reminders.extract("Just a normal answer.", now, zone)
        assertEquals("Just a normal answer.", clean)
        assertTrue(found.isEmpty())
    }

    @Test fun activeHoursIncludingOvernight() {
        assertTrue(PingStore.isActive(8, 8, 22))
        assertFalse(PingStore.isActive(22, 8, 22))
        assertFalse(PingStore.isActive(3, 8, 22))
        assertTrue(PingStore.isActive(23, 22, 6))
        assertTrue(PingStore.isActive(2, 22, 6))
        assertFalse(PingStore.isActive(12, 22, 6))
        assertTrue(PingStore.isActive(0, 0, 24))
        assertFalse(PingStore.isActive(5, 5, 5))
    }

    @Test fun instructionTellsTheModelTheTime() {
        val text = Reminders.instruction(now, zone)
        assertTrue(text.contains("2026-10-21 12:30"))
        assertTrue(text.contains("[[remind"))
    }
}
