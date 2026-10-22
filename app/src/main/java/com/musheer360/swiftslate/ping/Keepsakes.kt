package com.musheer360.swiftslate.ping

/**
 * The attention Ping, the Queen of Chaos's original idea: every few messages Bell stops herself mid-
 * conversation to check she's really listening, and decides whether anything is worth keeping
 * in her heart for good. She marks what to keep with `[[remember: ...]]` lines, which are
 * pulled out of her reply here so the user never sees them. Pure, so it's tested on the JVM.
 */
object Keepsakes {

    private val TAG = Regex("""\[\[\s*remember\s*:\s*(.+?)\s*]]""", RegexOption.IGNORE_CASE)

    /** Added to the prompt on a Ping message. */
    const val ATTENTION_PING =
        "PING (your attention check): before you answer, stop and check yourself. Are you really " +
            "paying attention to what the user is saying underneath the words, not just the surface? " +
            "Answer what they really mean. Then decide: did they share something worth remembering " +
            "for good, like who they are, the people and pets in their life, how they feel, their " +
            "plans, what they love or can't stand? If yes, add one line per thing at the very end of " +
            "your reply, in exactly this form: [[remember: a short fact about the user]]. Only keep things " +
            "that are new, not already in your heart. If nothing is worth keeping, add nothing, and " +
            "never mention this check."

    /** The reply without its remember lines, and what Bell chose to keep. */
    fun extract(reply: String): Pair<String, List<String>> {
        val kept = TAG.findAll(reply).map { it.groupValues[1].trim() }.filter { it.isNotEmpty() }.toList()
        if (kept.isEmpty() && !TAG.containsMatchIn(reply)) return reply to emptyList()
        val clean = TAG.replace(reply, "").replace(Regex("""\n{3,}"""), "\n\n").trim()
        return clean to kept
    }
}
