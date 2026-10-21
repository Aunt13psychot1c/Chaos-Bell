package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bell's small memory, used only by the answer-mode commands (?talk, ?huh, ?search):
 *
 *  - **Soul**: who Bell is and how she acts, in the user's own words (also read by Ping).
 *  - **About me**: a few lines the user writes once (name, how they talk, what they are
 *    into), sent along so the AI always knows who it is talking to.
 *  - **Short-term memory**: the last few answer-mode exchanges, so a follow-up such as
 *    "and what about tomorrow?" makes sense. Each one fades after [MEMORY_TTL_MS].
 *
 * No long-term facts on purpose: Bell is the mini assistant, the big memory is George's job.
 * Stored in its own preferences file, on the phone only.
 */
class BellMemory(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    companion object {
        const val PREFS_NAME = "bell_memory"
        private const val KEY_ABOUT_ME = "about_me"
        private const val KEY_SOUL = "soul"
        private const val KEY_HISTORY = "history"

        const val MAX_ABOUT_ME_CHARS = 1_500
        const val MAX_SOUL_CHARS = 4_000
        private const val MAX_EXCHANGES = 6
        private const val MAX_TEXT_CHARS = 600
        const val MEMORY_TTL_MS = 60 * 60 * 1000L // one hour
    }

    var aboutMe: String
        get() = try { prefs.getString(KEY_ABOUT_ME, "") ?: "" } catch (_: Exception) { "" }
        set(value) { prefs.edit().putString(KEY_ABOUT_ME, value.take(MAX_ABOUT_ME_CHARS)).apply() }

    /** Who Bell is and how she acts, in the user's own words. Leads every answer-mode prompt. */
    var soul: String
        get() = try { prefs.getString(KEY_SOUL, "") ?: "" } catch (_: Exception) { "" }
        set(value) { prefs.edit().putString(KEY_SOUL, value.take(MAX_SOUL_CHARS)).apply() }

    private data class Exchange(val time: Long, val asked: String, val answered: String)

    private fun loadHistory(): List<Exchange> {
        val raw = try { prefs.getString(KEY_HISTORY, "[]") ?: "[]" } catch (_: Exception) { "[]" }
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        val now = clock()
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Exchange(o.optLong("t"), o.optString("q"), o.optString("a"))
        }.filter { now - it.time < MEMORY_TTL_MS }
    }

    private fun saveHistory(history: List<Exchange>) {
        val arr = JSONArray()
        history.forEach { arr.put(JSONObject().put("t", it.time).put("q", it.asked).put("a", it.answered)) }
        prefs.edit().putString(KEY_HISTORY, arr.toString()).apply()
    }

    /** Remembers one answer-mode exchange, keeping only the newest few. */
    @Synchronized fun remember(asked: String, answered: String) {
        val history = loadHistory() + Exchange(clock(), asked.take(MAX_TEXT_CHARS), answered.take(MAX_TEXT_CHARS))
        saveHistory(history.takeLast(MAX_EXCHANGES))
    }

    /** Forgets the short-term conversation (About me stays). */
    fun forgetConversation() = prefs.edit().remove(KEY_HISTORY).apply()

    /**
     * The memory, written out for the AI to read before the command's own instruction, or
     * empty when there is nothing to remember yet.
     */
    fun contextBlock(includeSoul: Boolean = true): String {
        val sb = StringBuilder()
        val soulText = if (includeSoul) soul.trim() else ""
        if (soulText.isNotEmpty()) sb.append("Who you are (your soul, written by the user):\n").append(soulText).append("\n\n")
        val about = aboutMe.trim()
        if (about.isNotEmpty()) sb.append("About the user:\n").append(about).append("\n\n")
        val history = loadHistory()
        if (history.isNotEmpty()) {
            sb.append("Recent conversation with the user, oldest first. Use it only if the new text follows on from it:\n")
            history.forEach { sb.append("User: ").append(it.asked).append('\n').append("Bell: ").append(it.answered).append('\n') }
            sb.append('\n')
        }
        return sb.toString()
    }

    /** Memory for the backup file. */
    fun export(): JSONObject = JSONObject()
        .put("about_me", aboutMe)
        .put("history", JSONArray(try { prefs.getString(KEY_HISTORY, "[]") ?: "[]" } catch (_: Exception) { "[]" }))

    /** Restores memory from a backup file; expired conversation simply fades as usual. */
    fun import(json: JSONObject) {
        aboutMe = json.optString("about_me", "")
        val history = json.optJSONArray("history") ?: JSONArray()
        prefs.edit().putString(KEY_HISTORY, history.toString()).apply()
    }
}

/**
 * The backup file. Older backups are a bare array of commands; Bell's backups wrap that in an
 * object so her memory can travel in the same file. Both are read back.
 */
object BellBackup {
    fun build(commandsJson: String, memory: JSONObject): String = JSONObject()
        .put("bell_backup", 1)
        .put("commands", JSONArray(commandsJson))
        .put("memory", memory)
        .toString(2)

    /** Splits a backup into its commands array (as text) and memory, if any. */
    fun parse(text: String): Pair<String, JSONObject?> {
        val trimmed = text.trimStart()
        if (!trimmed.startsWith("{")) return trimmed to null
        val obj = JSONObject(trimmed)
        return (obj.optJSONArray("commands") ?: JSONArray()).toString() to obj.optJSONObject("memory")
    }
}
