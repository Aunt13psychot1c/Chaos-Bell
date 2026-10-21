package com.musheer360.swiftslate.ping

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Ping's settings, its activity log and the reminders Bell has set. One preferences file,
 * on the phone only.
 */
class PingStore(private val prefs: SharedPreferences) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    enum class Status(val badge: String) { OK("✅"), SAID("💬"), FAIL("⚠️"), SKIP("⏸️") }

    data class Entry(val time: Long, val status: Status, val text: String)

    data class Reminder(val id: String, val atMillis: Long, val text: String)

    var enabled: Boolean
        get() = prefs.getBoolean(KEY_ENABLED, false)
        set(v) = prefs.edit().putBoolean(KEY_ENABLED, v).apply()

    var intervalMinutes: Int
        get() = prefs.getInt(KEY_INTERVAL, 60).coerceIn(MIN_INTERVAL, MAX_INTERVAL)
        set(v) = prefs.edit().putInt(KEY_INTERVAL, v.coerceIn(MIN_INTERVAL, MAX_INTERVAL)).apply()

    /** Ping only runs from [startHour] up to [endHour] (0–24). A start after the end wraps past midnight. */
    var startHour: Int
        get() = prefs.getInt(KEY_START, 8).coerceIn(0, 24)
        set(v) = prefs.edit().putInt(KEY_START, v.coerceIn(0, 24)).apply()

    var endHour: Int
        get() = prefs.getInt(KEY_END, 22).coerceIn(0, 24)
        set(v) = prefs.edit().putInt(KEY_END, v.coerceIn(0, 24)).apply()

    var prompt: String
        get() = prefs.getString(KEY_PROMPT, null)?.takeIf { it.isNotBlank() } ?: DEFAULT_PROMPT
        set(v) = prefs.edit().putString(KEY_PROMPT, v.take(MAX_PROMPT_CHARS)).apply()

    fun log(): List<Entry> = readArray(KEY_LOG).mapNotNull { o ->
        val status = try { Status.valueOf(o.optString("s")) } catch (_: Exception) { return@mapNotNull null }
        Entry(o.optLong("t"), status, o.optString("x"))
    }

    @Synchronized fun addLog(entry: Entry) {
        val all = (log() + entry).takeLast(MAX_LOG)
        writeArray(KEY_LOG, all.map { JSONObject().put("t", it.time).put("s", it.status.name).put("x", it.text.take(500)) })
    }

    fun reminders(): List<Reminder> = readArray(KEY_REMINDERS).map { o ->
        Reminder(o.optString("id"), o.optLong("at"), o.optString("x"))
    }.sortedBy { it.atMillis }

    @Synchronized fun addReminder(r: Reminder) {
        writeArray(KEY_REMINDERS, (reminders() + r).map(::toJson))
    }

    @Synchronized fun removeReminder(id: String) {
        writeArray(KEY_REMINDERS, reminders().filter { it.id != id }.map(::toJson))
    }

    private fun toJson(r: Reminder) = JSONObject().put("id", r.id).put("at", r.atMillis).put("x", r.text)

    private fun readArray(key: String): List<JSONObject> {
        val arr = try { JSONArray(prefs.getString(key, "[]") ?: "[]") } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
    }

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "bell_ping"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_INTERVAL = "interval_min"
        private const val KEY_START = "start_hour"
        private const val KEY_END = "end_hour"
        private const val KEY_PROMPT = "prompt"
        private const val KEY_LOG = "log"
        private const val KEY_REMINDERS = "reminders"

        /** Android won't repeat background work more often than every 15 minutes. */
        const val MIN_INTERVAL = 15
        const val MAX_INTERVAL = 240
        const val MAX_PROMPT_CHARS = 4_000
        private const val MAX_LOG = 30
        const val OK_WORD = "PING_OK"

        val DEFAULT_PROMPT =
            "This is Ping, your automatic check-in. Look over what you know about the user, your soul, " +
                "and the reminders listed below. If nothing needs saying right now, reply with exactly: $OK_WORD\n" +
                "If something is worth a short nudge (a reminder coming up soon, a gentle check-in, a follow-up " +
                "on something they told you), write one short, warm message to them instead. Never repeat a " +
                "message you already sent recently."

        /** Whether [hour] (0–23) falls inside the active window, which may wrap past midnight. */
        fun isActive(hour: Int, start: Int, end: Int): Boolean = when {
            start == end -> false
            start < end -> hour in start until end
            else -> hour >= start || hour < end
        }
    }
}
