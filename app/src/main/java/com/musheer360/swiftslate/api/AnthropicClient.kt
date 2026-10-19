package com.musheer360.swiftslate.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

/**
 * Talks to Claude through Anthropic's own Messages API (`/v1/messages`) instead of the
 * OpenAI-compatible doorway, so Bell can use Claude-only features such as the server-side
 * web search tool.
 *
 * Every request carries Claude's two ID badges: `x-api-key` (the key) and
 * `anthropic-version` (which version of the API rules this app speaks).
 */
class AnthropicClient {

    companion object {
        const val BASE_URL = "https://api.anthropic.com/v1"
        const val API_VERSION = "2023-06-01"

        /** Basic web search: works on every platform that offers web search at all. */
        const val WEB_SEARCH_TOOL_TYPE = "web_search_20250305"

        /** Keeps a single command from running up a big search bill. */
        private const val WEB_SEARCH_MAX_USES = 3

        /** Room for long rewrites without letting one command run forever. */
        private const val MAX_OUTPUT_TOKENS = 4096

        private val HTTP_PREFIX_REGEX = Regex("^HTTP_\\d+:\\s*")

        /**
         * Pulls the model ids out of a `/v1/models` response, keeping only Claude chat
         * models and the order Anthropic lists them in (newest first).
         */
        internal fun parseModelIds(json: String): List<String> {
            val data = JSONObject(json).optJSONArray("data") ?: return emptyList()
            val ids = mutableListOf<String>()
            for (i in 0 until data.length()) {
                val id = data.optJSONObject(i)?.optString("id", "").orEmpty()
                if (id.startsWith("claude")) ids.add(id)
            }
            return ids
        }

        /**
         * Builds the answer text from a Messages response's `content` blocks.
         *
         * With web search on, Claude may say something like "Let me look that up" before it
         * searches. That narration is not part of the answer, so only the text blocks after
         * the last search result are kept. Without a search, every text block is kept.
         */
        internal fun extractText(content: JSONArray?): String {
            if (content == null) return ""
            var start = 0
            for (i in 0 until content.length()) {
                if (content.optJSONObject(i)?.optString("type") == "web_search_tool_result") {
                    start = i + 1
                }
            }
            val sb = StringBuilder()
            for (i in start until content.length()) {
                val block = content.optJSONObject(i) ?: continue
                if (block.optString("type") == "text") sb.append(block.optString("text", ""))
            }
            return sb.toString()
        }

        /** Claude accepts temperatures from 0 to 1; the app's slider can go higher. */
        internal fun clampTemperature(t: Double): Double = t.coerceIn(0.0, 1.0)

        /** True when a 400 says web search is switched off for this organization. */
        internal fun isWebSearchDisabledError(message: String): Boolean {
            val m = message.lowercase()
            return m.contains("web search") || m.contains("web_search")
        }

        /** True when a 400 says this model does not take a temperature. */
        internal fun isTemperatureRejectedError(message: String): Boolean =
            message.lowercase().contains("temperature")
    }

    private fun HttpURLConnection.applyAnthropicHeaders(apiKey: String) {
        setRequestProperty("x-api-key", apiKey)
        setRequestProperty("anthropic-version", API_VERSION)
    }

    /** Checks a key by asking for one model. A 200 means Claude accepts the key. */
    suspend fun validateKey(apiKey: String): Result<String> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$BASE_URL/models?limit=1").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAnthropicHeaders(apiKey)
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            if (code in 200..299) {
                ApiClientUtils.readResponseBounded(connection)
                Result.success("Valid")
            } else {
                val apiMessage = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                when (code) {
                    429 -> Result.failure(Exception("Rate limited. Please try again later."))
                    401, 403 -> Result.failure(Exception(apiMessage.ifEmpty { "Invalid API key" }))
                    else -> Result.failure(Exception("Error $code: ${apiMessage.ifEmpty { "Unexpected error" }}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /** Fetches the Claude models this key can use, for the Settings dropdown. */
    suspend fun fetchModels(apiKey: String): Result<List<String>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            connection = URL("$BASE_URL/models?limit=100").openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAnthropicHeaders(apiKey)
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            if (code in 200..299) {
                Result.success(parseModelIds(ApiClientUtils.readResponseBounded(connection)))
            } else {
                val apiMessage = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                Result.failure(Exception(apiMessage.ifEmpty { "Error $code" }))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Runs one command. If the organization has web search switched off, or the model
     * refuses a temperature, the request is quietly retried without that piece instead of
     * failing the whole command.
     */
    suspend fun generate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        webSearch: Boolean,
        answerMode: Boolean = false
    ): Result<GenerateResult> = withContext(Dispatchers.IO) {
        var useSearch = webSearch
        var sendTemperature = true
        var result = doGenerate(prompt, text, apiKey, model, temperature, useSearch, sendTemperature, answerMode)

        repeat(2) {
            val msg = result.exceptionOrNull()?.message ?: return@repeat
            if (!msg.startsWith("HTTP_400")) return@repeat
            when {
                useSearch && isWebSearchDisabledError(msg) -> useSearch = false
                sendTemperature && isTemperatureRejectedError(msg) -> sendTemperature = false
                else -> return@repeat
            }
            result = doGenerate(prompt, text, apiKey, model, temperature, useSearch, sendTemperature, answerMode)
        }

        // Retry once for transient network/server errors (with 1.5s backoff), like the
        // other clients.
        if (result.isFailure && result.exceptionOrNull().isTransientNetwork()) {
            kotlinx.coroutines.delay(1500)
            result = doGenerate(prompt, text, apiKey, model, temperature, useSearch, sendTemperature, answerMode)
        }

        if (result.isFailure) {
            val e = result.exceptionOrNull()
            val msg = e?.message ?: ""
            val cleaned = msg.replaceFirst(HTTP_PREFIX_REGEX, "")
            if (cleaned != msg && e !is ApiException) return@withContext Result.failure(Exception(cleaned))
        }
        result
    }

    internal fun buildRequestBody(
        prompt: String,
        text: String,
        model: String,
        temperature: Double,
        webSearch: Boolean,
        sendTemperature: Boolean,
        answerMode: Boolean = false
    ): JSONObject = JSONObject().apply {
        put("model", model.replace(Regex("[^a-zA-Z0-9._\\-/: ]"), ""))
        put("max_tokens", MAX_OUTPUT_TOKENS)
        put("system", ApiClientUtils.systemPrompt(prompt, answerMode))
        put("messages", JSONArray().apply {
            put(JSONObject().apply {
                put("role", "user")
                put("content", ApiClientUtils.wrapUserText(text))
            })
        })
        if (sendTemperature) put("temperature", clampTemperature(temperature))
        if (webSearch) {
            put("tools", JSONArray().apply {
                put(JSONObject().apply {
                    put("type", WEB_SEARCH_TOOL_TYPE)
                    put("name", "web_search")
                    put("max_uses", WEB_SEARCH_MAX_USES)
                })
            })
        }
    }

    private fun doGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        webSearch: Boolean,
        sendTemperature: Boolean,
        answerMode: Boolean
    ): Result<GenerateResult> {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("$BASE_URL/messages").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.applyAnthropicHeaders(apiKey)
            connection.doOutput = true
            connection.connectTimeout = 30_000
            // Web searches take longer than a plain rewrite, but both callers give up on the
            // whole command after 90 seconds, so stay under that.
            connection.readTimeout = if (webSearch) 80_000 else 60_000

            val body = buildRequestBody(prompt, text, model, temperature, webSearch, sendTemperature, answerMode)
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            when {
                code in 200..299 -> {
                    val json = JSONObject(ApiClientUtils.readResponseBounded(connection))
                    val stopReason = json.optString("stop_reason", "")
                    if (stopReason == "refusal") {
                        return Result.failure(Exception("Response blocked by content filter"))
                    }
                    val answer = ApiClientUtils.stripMarkdownFences(extractText(json.optJSONArray("content")).trim())
                    if (answer.isBlank()) return Result.failure(Exception("Model returned empty response"))
                    Result.success(GenerateResult(answer, truncated = stopReason == "max_tokens"))
                }
                code == 429 -> {
                    val seconds = connection.getHeaderField("Retry-After")?.toIntOrNull()
                    val msg = if (seconds != null) "Rate limit exceeded, retry after ${seconds}s" else "Rate limit exceeded"
                    Result.failure(ApiException(ApiError.RateLimit(msg, seconds), msg))
                }
                code == 413 -> {
                    val detail = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                        .ifEmpty { "Request too large" }
                    Result.failure(ApiException(ApiError.RequestTooLarge(detail), detail))
                }
                code == 400 || code == 422 -> {
                    val detail = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                        .ifEmpty { "Bad request" }
                    Result.failure(Exception("HTTP_$code: $detail"))
                }
                code == 401 || code == 403 -> {
                    val detail = ApiClientUtils.extractApiErrorMessage(ApiClientUtils.readErrorBody(connection))
                        .ifEmpty { "Invalid API key" }
                    Result.failure(ApiException(ApiError.InvalidKey(detail), detail))
                }
                else -> {
                    val errorBody = ApiClientUtils.readErrorBody(connection)
                    var detail = ApiClientUtils.sanitizeErrorForUser(code, errorBody, "Unexpected error (HTTP $code)")
                    if (code == 404) detail = "Model not found. $detail"
                    // 529 is Claude's "overloaded": temporary, like any 5xx.
                    val apiError = if (code >= 500) ApiError.ServerError(detail) else ApiError.Other(detail)
                    Result.failure(ApiException(apiError, detail))
                }
            }
        } catch (e: Exception) {
            val apiError = when (e) {
                is ApiException -> e.apiError
                is SocketTimeoutException, is UnknownHostException, is ConnectException, is java.net.SocketException ->
                    ApiError.Network(e.message ?: "Network error")
                is org.json.JSONException -> ApiError.Other("Invalid response from server")
                else -> ApiError.Other(e.message ?: "Unknown error")
            }
            if (e is ApiException) Result.failure(e) else Result.failure(ApiException(apiError, e.message ?: "Unknown error"))
        } finally {
            connection?.disconnect()
        }
    }
}
