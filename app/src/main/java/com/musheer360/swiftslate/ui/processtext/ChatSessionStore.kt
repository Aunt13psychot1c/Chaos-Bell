package com.musheer360.swiftslate.ui.processtext

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the chat-mode conversation for an hour, so closing the popup by accident and reopening
 * it brings the chat back instead of starting blank. The Settings chat and the Help "Ask Bell"
 * chat are kept apart, since they carry different background.
 */
class ChatSessionStore(context: Context) {

    private val prefs = context.getSharedPreferences("chat_session", Context.MODE_PRIVATE)

    fun load(kind: String, now: Long = System.currentTimeMillis()): List<ChatTurn> {
        val savedAt = prefs.getLong("${kind}_at", 0L)
        if (now - savedAt > KEEP_MS) return emptyList()
        return try {
            val array = JSONArray(prefs.getString("${kind}_turns", "[]"))
            List(array.length()) { i ->
                val o = array.getJSONObject(i)
                ChatTurn(fromUser = o.getBoolean("me"), text = o.getString("text"))
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun save(kind: String, turns: List<ChatTurn>, now: Long = System.currentTimeMillis()) {
        val array = JSONArray()
        turns.takeLast(MAX_TURNS).forEach { t ->
            array.put(JSONObject().put("me", t.fromUser).put("text", t.text))
        }
        prefs.edit()
            .putString("${kind}_turns", array.toString())
            .putLong("${kind}_at", now)
            .apply()
    }

    companion object {
        const val KEEP_MS = 60 * 60 * 1000L
        const val MAX_TURNS = 40
    }
}
