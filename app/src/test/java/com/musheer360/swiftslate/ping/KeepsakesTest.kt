package com.musheer360.swiftslate.ping

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeepsakesTest {

    @Test fun rememberLinesArePulledOutOfTheReply() {
        val reply = "Aww, Erin sounds iconic 🦥\n\n[[remember: has a plush sloth named Erin the Slew]]\n[[Remember:loves rave music]]"
        val (clean, kept) = Keepsakes.extract(reply)
        assertEquals("Aww, Erin sounds iconic 🦥", clean)
        assertEquals(listOf("has a plush sloth named Erin the Slew", "loves rave music"), kept)
    }

    @Test fun repliesWithoutTagsAreUntouched() {
        val (clean, kept) = Keepsakes.extract("Just a normal answer.")
        assertEquals("Just a normal answer.", clean)
        assertTrue(kept.isEmpty())
    }

    @Test fun emptyTagsKeepNothingButStillDisappear() {
        val (clean, kept) = Keepsakes.extract("Hi [[remember:   ]] there")
        assertTrue(kept.isEmpty())
        assertFalse(clean.contains("[["))
    }

    @Test fun pingPromptAsksForTheExactTagFormat() {
        assertTrue(Keepsakes.ATTENTION_PING.contains("[[remember:"))
    }
}
