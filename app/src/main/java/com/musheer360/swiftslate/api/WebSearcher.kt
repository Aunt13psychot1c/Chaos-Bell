package com.musheer360.swiftslate.api

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Bell's own web search, for providers that have no search built in (Custom endpoints and
 * most Groq models). The AI asks for a search, Bell runs it here, and hands the results back.
 *
 * Uses Tavily's keyless mode: free, no sign-up, rate limited per phone. Tavily suggests its
 * free key for heavy use; [apiKey] takes one if it is ever added.
 */
open class WebSearcher(private val apiKey: String? = null) {

    companion object {
        private const val ENDPOINT = "https://api.tavily.com/search"
        private const val MAX_RESULTS = 5

        /** Longest snippet kept per result, so results never swamp the AI's context. */
        private const val MAX_SNIPPET_CHARS = 600

        /**
         * Turns a Tavily response into plain numbered results for the AI to read:
         * title, link and a short snippet each.
         */
        internal fun formatResults(json: String): String {
            val results = JSONObject(json).optJSONArray("results") ?: return "No results."
            if (results.length() == 0) return "No results."
            val sb = StringBuilder()
            for (i in 0 until results.length()) {
                val r = results.optJSONObject(i) ?: continue
                val snippet = r.optString("content", "").replace(Regex("\\s+"), " ").trim()
                sb.append(i + 1).append(". ").append(r.optString("title", "")).append('\n')
                sb.append(r.optString("url", "")).append('\n')
                sb.append(snippet.take(MAX_SNIPPET_CHARS)).append("\n\n")
            }
            return sb.toString().trim()
        }
    }

    /** Runs one search. Blocking: call it off the main thread. */
    open fun search(query: String): Result<String> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(ENDPOINT).openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            if (apiKey.isNullOrBlank()) {
                connection.setRequestProperty("X-Tavily-Access-Mode", "keyless")
            } else {
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
            }
            connection.doOutput = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            val body = JSONObject().apply {
                put("query", query.take(400))
                put("max_results", MAX_RESULTS)
            }
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code in 200..299) {
                Result.success(formatResults(ApiClientUtils.readResponseBounded(connection)))
            } else {
                val msg = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                Result.failure(Exception("Search failed ($code)${if (msg.isNotEmpty()) ": $msg" else ""}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}
