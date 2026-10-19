package com.musheer360.swiftslate.service

import kotlin.random.Random

/**
 * The hat behind `?chaos`: it picks ONE effect at random and goes all the way with it.
 *
 * Two kinds of effects live in the hat:
 *  - [Pick.Local] effects are plain code. Bell does them herself, instantly, with no
 *    internet and no API key, and they never fail.
 *  - [Pick.Ai] effects need Claude (or whichever provider is picked), so they are sent
 *    as an ordinary AI command with an over-the-top instruction.
 *
 * Pure Kotlin with no Android parts, so every effect can be unit tested.
 */
object ChaosHat {

    sealed interface Pick {
        val name: String

        /** Done on the phone. [result] is the finished text. */
        data class Local(override val name: String, val result: String) : Pick

        /** Done by the AI with this [prompt]. */
        data class Ai(override val name: String, val prompt: String) : Pick
    }

    private class LocalEffect(val name: String, val apply: (String, Random) -> String)

    private val localEffects = listOf(
        LocalEffect("scramble", ::scramble),
        LocalEffect("emoji vowels") { t, _ -> emojiVowels(t) },
        LocalEffect("reverse") { t, _ -> reverseWords(t) },
        LocalEffect("symbols", ::symbolEveryThirdLetter),
        LocalEffect("pig latin") { t, _ -> pigLatin(t) },
        LocalEffect("screaming") { t, _ -> screaming(t) },
        LocalEffect("uwu") { t, _ -> uwu(t) },
        LocalEffect("bark", ::bark)
    )

    private val aiEffects = listOf(
        "groot" to "Rewrite the text so every sentence becomes only some form of \"I am Groot\", with the punctuation, emphasis and emotion of the original sentence. Nothing but Groot.",
        "brainrot" to "Rewrite the text in the most unhinged internet brainrot possible: skibidi, rizz, gyatt, fanum tax, sigma, ohio, no cap, it's giving. Cram in as much as it can hold while the original meaning is still faintly visible.",
        "klingon" to "Translate the text into Klingon. Output only the Klingon.",
        "baby talk" to "Rewrite the text as over-the-top baby talk: goo goo ga ga, wittle, wuv, nap time, fuzzy wuzzy, the works.",
        "rally mode" to "Rewrite the text as a bombastic rally-stage politician would say it: everything is the best or a total disaster, repeat yourself, give things nicknames, say \"many people are saying\" and \"believe me\", wander off on a tangent and come back. Do not name or imitate any real person.",
        "riddle" to "Turn the text into a riddle whose answer is the original message. Output only the riddle.",
        "song" to "Turn the text into dramatic song lyrics with a verse and a chorus, as if it were a power ballad about this exact message."
    )

    /** Every effect name in the hat, local ones first. */
    val allNames: List<String> = localEffects.map { it.name } + aiEffects.map { it.first }

    /** Pulls one effect out of the hat and, if it is a local one, runs it on [text]. */
    fun roll(text: String, random: Random = Random.Default): Pick {
        val index = random.nextInt(localEffects.size + aiEffects.size)
        return if (index < localEffects.size) {
            val effect = localEffects[index]
            Pick.Local(effect.name, effect.apply(text, random))
        } else {
            val (name, prompt) = aiEffects[index - localEffects.size]
            Pick.Ai(name, prompt)
        }
    }

    private val WORD = Regex("[\\p{L}\\p{N}']+")

    /** Shuffles the letters inside every word, so "hello there" might become "lehol herte". */
    fun scramble(text: String, random: Random): String =
        WORD.replace(text) { m -> m.value.toList().shuffled(random).joinToString("") }

    private val VOWEL_EMOJI = mapOf('a' to "🍎", 'e' to "🥚", 'i' to "🍦", 'o' to "🍩", 'u' to "☂️")

    /** Every vowel becomes an emoji. */
    fun emojiVowels(text: String): String = buildString {
        for (c in text) append(VOWEL_EMOJI[c.lowercaseChar()] ?: c.toString())
    }

    /** Words in reverse order: "I love dogs" becomes "dogs love I". */
    fun reverseWords(text: String): String =
        text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.reversed().joinToString(" ")

    private const val KEYBOARD_SYMBOLS = "!@#$%^&*~?+=<>|/"

    /** Drops a random keyboard symbol in after every third letter. */
    fun symbolEveryThirdLetter(text: String, random: Random): String = buildString {
        var letters = 0
        for (c in text) {
            append(c)
            if (c.isLetter()) {
                letters++
                if (letters % 3 == 0) append(KEYBOARD_SYMBOLS[random.nextInt(KEYBOARD_SYMBOLS.length)])
            }
        }
    }

    /** Classic Pig Latin: "hello" becomes "ellohay", "apple" becomes "appleway". */
    fun pigLatin(text: String): String = WORD.replace(text) { m ->
        val word = m.value
        if (word.none { it.isLetter() }) return@replace word
        val vowels = "aeiouAEIOU"
        val first = word.indices.firstOrNull { i ->
            word[i] in vowels || (word[i] == 'y' || word[i] == 'Y') && i > 0
        } ?: -1
        val latin = when {
            first == 0 -> word + "way"
            first < 0 -> word + "ay"
            else -> word.substring(first) + word.substring(0, first).lowercase() + "ay"
        }
        if (word[0].isUpperCase()) latin.lowercase().replaceFirstChar { it.uppercase() } else latin
    }

    /** ALL CAPS, every sentence ends in a pile of exclamation points. */
    fun screaming(text: String): String {
        val loud = text.uppercase().replace(Regex("[.!?]+"), "!!!")
        return if (loud.trimEnd().endsWith("!!!")) loud else loud.trimEnd() + "!!!"
    }

    /** UwU-ifies the text. */
    fun uwu(text: String): String {
        val owo = text
            .replace(Regex("[rl]"), "w").replace(Regex("[RL]"), "W")
            .replace(Regex("n([aeiou])"), "ny$1").replace(Regex("N([aeiouAEIOU])"), "Ny$1")
            .replace("ove", "uv")
        return owo.trimEnd() + " uwu"
    }

    private val BARKS = listOf("bark", "woof", "arf", "ruff", "yip")

    /** Every word becomes a dog noise, for the five dogs. Punctuation stays put. */
    fun bark(text: String, random: Random): String = WORD.replace(text) { m ->
        val noise = BARKS[random.nextInt(BARKS.size)]
        when {
            m.value.length > 1 && m.value.all { !it.isLetter() || it.isUpperCase() } -> noise.uppercase()
            m.value[0].isUpperCase() -> noise.replaceFirstChar { it.uppercase() }
            else -> noise
        }
    }
}
