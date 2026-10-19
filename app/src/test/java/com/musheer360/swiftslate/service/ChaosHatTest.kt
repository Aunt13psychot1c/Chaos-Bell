package com.musheer360.swiftslate.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ChaosHatTest {

    @Test
    fun hat_holds_all_fifteen_effects() {
        assertEquals(15, ChaosHat.allNames.size)
        assertEquals(15, ChaosHat.allNames.toSet().size)
    }

    @Test
    fun every_effect_can_come_out_of_the_hat() {
        val seen = mutableSetOf<String>()
        val random = Random(13)
        repeat(2000) { seen.add(ChaosHat.roll("Hello there friend.", random).name) }
        assertEquals(ChaosHat.allNames.toSet(), seen)
    }

    @Test
    fun local_effects_finish_the_text_and_ai_effects_carry_a_prompt() {
        val random = Random(7)
        repeat(200) {
            when (val pick = ChaosHat.roll("Hello there", random)) {
                is ChaosHat.Pick.Local -> assertTrue(pick.result.isNotBlank())
                is ChaosHat.Pick.Ai -> assertTrue(pick.prompt.length > 20)
            }
        }
    }

    @Test
    fun scramble_keeps_the_same_letters_in_each_word() {
        val out = ChaosHat.scramble("hello there", Random(1))
        val words = out.split(" ")
        assertEquals("ehllo", words[0].toList().sorted().joinToString(""))
        assertEquals("eehrt", words[1].toList().sorted().joinToString(""))
    }

    @Test
    fun emoji_vowels_swaps_every_vowel() {
        assertEquals("h🥚ll🍩", ChaosHat.emojiVowels("hello"))
        assertEquals("🍎PPL🥚", ChaosHat.emojiVowels("APPLE"))
    }

    @Test
    fun reverse_flips_word_order() {
        assertEquals("dogs love I", ChaosHat.reverseWords("I love dogs"))
    }

    @Test
    fun symbols_land_after_every_third_letter() {
        val out = ChaosHat.symbolEveryThirdLetter("abcdef", Random(3))
        assertEquals(8, out.length)
        assertEquals("abc", out.substring(0, 3))
        assertEquals("def", out.substring(4, 7))
    }

    @Test
    fun pig_latin_follows_the_rules() {
        assertEquals("ellohay orldway", ChaosHat.pigLatin("hello world"))
        assertEquals("appleway", ChaosHat.pigLatin("apple"))
        assertEquals("Ellohay", ChaosHat.pigLatin("Hello"))
        assertEquals("ingstray", ChaosHat.pigLatin("string"))
    }

    @Test
    fun screaming_shouts() {
        assertEquals("HI THERE!!!", ChaosHat.screaming("hi there."))
        assertEquals("HI!!!", ChaosHat.screaming("hi"))
    }

    @Test
    fun uwu_softens_everything() {
        assertEquals("I wuv you uwu", ChaosHat.uwu("I love you"))
    }

    @Test
    fun bark_turns_words_into_dog_noises_and_keeps_punctuation() {
        val out = ChaosHat.bark("Hello there, friend!", Random(5))
        assertTrue(out.endsWith("!"))
        assertTrue(out.contains(","))
        assertTrue(out[0].isUpperCase())
        assertEquals(3, out.split(Regex("[^a-zA-Z]+")).filter { it.isNotEmpty() }.size)
    }
}
