package com.musheer360.swiftslate.ping

import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Lets Bell set reminders from a normal conversation. Answer-mode prompts tell the model to add
 * a line like `[[remind 2026-10-21 15:00 | take your meds]]` when the user asks to be reminded;
 * this pulls those lines out of the reply (so the user never sees them) and turns them into
 * times. Pure, so it's tested on the JVM.
 */
object Reminders {

    data class Found(val atMillis: Long, val text: String)

    private val TAG = Regex("""\[\[\s*remind\s+(\d{4}-\d{2}-\d{2})\s+(\d{1,2}:\d{2})\s*\|\s*(.+?)\s*]]""", RegexOption.IGNORE_CASE)

    /** What answer-mode prompts add so the model knows how to set one. */
    fun instruction(nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        "The user's local time right now is ${format(nowMillis, zone)}. " +
            "If, and only if, the user asks you to remind them of something, add one line at the very end of your reply " +
            "in exactly this form, using their local time: [[remind YYYY-MM-DD HH:MM | what to remind them]]. " +
            "Mention the reminder in plain words in your reply too. Never add that line otherwise."

    /** The reply without its reminder lines, and the reminders it asked for. Past times are dropped. */
    fun extract(reply: String, nowMillis: Long, zone: TimeZone = TimeZone.getDefault()): Pair<String, List<Found>> {
        val found = TAG.findAll(reply).mapNotNull { m ->
            val at = parse("${m.groupValues[1]} ${m.groupValues[2]}", zone) ?: return@mapNotNull null
            val text = m.groupValues[3].trim().take(300)
            if (at <= nowMillis || text.isEmpty()) null else Found(at, text)
        }.toList()
        val clean = TAG.replace(reply, "").replace(Regex("""\n{3,}"""), "\n\n").trim()
        return clean to found
    }

    fun format(millis: Long, zone: TimeZone = TimeZone.getDefault()): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm (EEEE)", Locale.US).apply { timeZone = zone }.format(millis)

    private fun parse(text: String, zone: TimeZone): Long? = try {
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).apply { timeZone = zone; isLenient = false }.parse(text)?.time
    } catch (_: Exception) {
        null
    }
}
