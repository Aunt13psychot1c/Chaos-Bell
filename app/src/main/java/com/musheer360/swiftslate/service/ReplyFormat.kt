package com.musheer360.swiftslate.service

/**
 * Answer-mode replies (?talk, ?huh, ?search) replace the whole text field, which used to wipe
 * out what the user asked. This keeps the user's words on top so both sides stay readable:
 *
 *   You: "what you asked"
 *   ━━━━ 🔔 ━━━━
 *   Bell: her answer
 *
 * The app adds this itself instead of asking the AI to, so it can never be forgotten.
 */
object ReplyFormat {
    const val DIVIDER = "━━━━ 🔔 ━━━━"

    fun withQuestion(question: String, answer: String): String {
        val asked = question.trim()
        val reply = answer.trim()
        if (asked.isEmpty()) return reply
        return "You: \"$asked\"\n$DIVIDER\nBell: $reply"
    }
}
