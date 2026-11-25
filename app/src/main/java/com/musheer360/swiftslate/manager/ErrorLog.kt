package com.musheer360.swiftslate.manager

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bell's little diary of what went wrong: the last [MAX] errors with when, where and what the
 * server actually said, so a vague toast can be checked afterwards. Phone only, never sent.
 */
class ErrorLog(private val prefs: SharedPreferences, private val clock: () -> Long = System::currentTimeMillis) {

    constructor(context: Context) : this(context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE))

    data class Entry(val time: Long, val where: String, val what: String)

    fun entries(): List<Entry> {
        val raw = try { prefs.getString(KEY, "[]") ?: "[]" } catch (_: Exception) { "[]" }
        val arr = try { JSONArray(raw) } catch (_: Exception) { JSONArray() }
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            Entry(o.optLong("t"), o.optString("w"), o.optString("x"))
        }
    }

    @Synchronized fun add(where: String, what: String) {
        val clean = what.trim().ifEmpty { "(no details)" }.take(MAX_CHARS)
        val list = (entries() + Entry(clock(), where.take(80), clean)).takeLast(MAX)
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("t", it.time).put("w", it.where).put("x", it.what)) }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    fun clear() = prefs.edit().remove(KEY).apply()

    companion object {
        const val PREFS_NAME = "bell_errors"
        private const val KEY = "log"
        const val MAX = 20
        const val MAX_CHARS = 400

        /** Never lets logging break the thing that failed. */
        fun record(context: Context, where: String, what: String) {
            try { ErrorLog(context).add(where, what) } catch (_: Exception) {}
        }
    }
}
