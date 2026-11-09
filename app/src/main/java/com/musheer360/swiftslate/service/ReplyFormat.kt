package com.musheer360.swiftslate.service

/**
 * Answer-mode replies (?talk, ?huh, ?search) replace the whole text field, which used to wipe
 * out what the user asked. This keeps the user's words on top so both sides stay readable:
 *
 *   You: "what you asked"
 *   ━━━━ 🔔 ━━━━
 *   Bell: her answer
 *
 * The answer is signed by whichever soul is talking (Mary:, Stan:, Frog:...), so you can tell
 * who answered. ?notalk turns the soul off, so that one is always signed Bell.
 *
 * The app adds this itself instead of asking the AI to, so it can never be forgotten.
 */
object ReplyFormat {
    const val DIVIDER = "━━━━ 🔔 ━━━━"

    fun withQuestion(question: String, answer: String, speaker: String = "Bell"): String {
        val asked = question.trim()
        val reply = answer.trim()
        if (asked.isEmpty()) return reply
        val name = speaker.trim().ifEmpty { "Bell" }
        return "You: \"$asked\"\n$DIVIDER\n$name: $reply"
    }
}
