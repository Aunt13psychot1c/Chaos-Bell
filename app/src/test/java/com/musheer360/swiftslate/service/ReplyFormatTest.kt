package com.musheer360.swiftslate.service

import org.junit.Assert.assertEquals
import org.junit.Test

class ReplyFormatTest {

    @Test
    fun keeps_question_on_top_with_labels() {
        assertEquals(
            "You: \"is it raining\"\n${ReplyFormat.DIVIDER}\nBell: Nope, clear skies.",
            ReplyFormat.withQuestion("  is it raining ", "Nope, clear skies.\n")
        )
    }

    @Test
    fun empty_question_returns_just_the_answer() {
        assertEquals("Hi!", ReplyFormat.withQuestion("   ", "Hi!"))
    }

    @Test
    fun signsWithTheSoulThatIsTalking() {
        assertEquals(
            "You: \"who are you\"\n${ReplyFormat.DIVIDER}\nStan: Stan.",
            ReplyFormat.withQuestion("who are you", "Stan.", "Stan")
        )
        assertEquals(
            "You: \"hi\"\n${ReplyFormat.DIVIDER}\nBell: Hey!",
            ReplyFormat.withQuestion("hi", "Hey!", "  ")
        )
    }
}
