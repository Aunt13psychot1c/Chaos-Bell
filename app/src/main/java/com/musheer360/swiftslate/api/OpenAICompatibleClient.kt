package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.provider.EndpointValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

class OpenAICompatibleClient {

    companion object {
        private val HTTP_PREFIX_REGEX = Regex("^HTTP_\\d+:\\s*")

        /** Most searches one command may run, so a chatty model cannot loop forever. */
        private const val MAX_SEARCHES = 3

        /** The web_search function offered to models when Bell does the searching. */
        internal fun searchTools(): JSONArray = JSONArray().put(JSONObject().apply {
            put("type", "function")
            put("function", JSONObject().apply {
                put("name", "web_search")
                put("description", "Search the web for current information. Use it only when the transformation needs facts that are recent or that you do not know.")
                put("parameters", JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("query", JSONObject().apply {
                            put("type", "string")
                            put("description", "What to search for")
                        })
                    })
                    put("required", JSONArray().put("query"))
                })
            })
        })

        /**
         * Rebuilds an assistant message that asked for tools with only the fields every
         * OpenAI-compatible server accepts back (some add extras like "reasoning" and then
         * reject them when echoed).
         */
        internal fun cleanToolCallMessage(message: JSONObject): JSONObject {
            val calls = JSONArray()
            val raw = message.optJSONArray("tool_calls") ?: JSONArray()
            for (i in 0 until raw.length()) {
                val call = raw.optJSONObject(i) ?: continue
                val fn = call.optJSONObject("function") ?: continue
                calls.put(JSONObject().apply {
                    put("id", call.optString("id", "call_$i"))
                    put("type", "function")
                    put("function", JSONObject().apply {
                        put("name", fn.optString("name", ""))
                        put("arguments", fn.optString("arguments", "{}"))
                    })
                })
            }
            return JSONObject().apply {
                put("role", "assistant")
                put("content", message.optString("content", "").let { if (it == "null") "" else it })
                put("tool_calls", calls)
            }
        }

        /** Pulls the search query out of a tool call's JSON arguments. */
        internal fun queryFromArguments(arguments: String): String = try {
            JSONObject(arguments).optString("query", "").trim()
        } catch (_: Exception) {
            ""
        }

        /** Anthropic API version sent alongside the key on Claude endpoints. */
        private const val ANTHROPIC_VERSION = "2023-06-01"

        /**
         * True for Claude's API. Its OpenAI-compatible /chat/completions accepts a Bearer
         * key, but its native routes that key validation and the model dropdown hit
         * (/v1/models) also require `x-api-key` and `anthropic-version`, so without them
         * adding a Claude key fails with "anthropic-version: header is required".
         */
        internal fun isAnthropicHost(host: String?): Boolean {
            val h = host?.lowercase() ?: return false
            return h == "anthropic.com" || h.endsWith(".anthropic.com")
        }
    }

    /**
     * Sets the auth headers for [apiKey] (none when blank, for keyless local servers).
     * Claude endpoints also get `x-api-key` and `anthropic-version`; every other
     * provider gets exactly the Bearer header it always did.
     */
    private fun HttpURLConnection.applyAuth(apiKey: String?) {
        if (apiKey.isNullOrBlank()) return
        setRequestProperty("Authorization", "Bearer $apiKey")
        if (isAnthropicHost(url.host)) {
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", ANTHROPIC_VERSION)
        }
    }

    suspend fun validateKey(apiKey: String, endpoint: String): Result<String> = withContext(Dispatchers.IO) {
        if (EndpointValidator.validate(endpoint) != EndpointValidator.Error.NONE) {
            return@withContext Result.failure(Exception("Endpoint must be https:// or an http:// private-LAN address"))
        }
        var connection: HttpURLConnection? = null
        try {
            val baseUrl = endpoint.trimEnd('/')
            connection = URL("$baseUrl/models")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth(apiKey)
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                connection.inputStream?.use { stream ->
                    val buf = ByteArray(1024)
                    while (stream.read(buf) != -1) { /* drain */ }
                }
                Result.success("Valid")
            } else {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)

                when (responseCode) {
                    429 -> Result.failure(Exception("Rate limited. Please try again later."))
                    401, 403 -> {
                        val signinUrl = ApiClientUtils.extractSigninUrl(errorBody)
                        val detail = when {
                            signinUrl != null || apiMessage.contains("not currently signed in", ignoreCase = true) ->
                                "${ApiClientUtils.SIGNIN_REQUIRED_MARKER}: ${apiMessage.ifEmpty { "server sign-in required" }}"
                            apiMessage.isNotEmpty() -> apiMessage
                            else -> "Invalid API key"
                        }
                        Result.failure(Exception(detail))
                    }
                    404 -> {
                        // Some local servers (Ollama) only serve the OpenAI-compatible API
                        // under /v1; the stored endpoint may just be missing that suffix.
                        connection?.disconnect()
                        connection = null
                        if (probeModels("$baseUrl/v1/models", apiKey)) {
                            Result.failure(Exception(ApiClientUtils.NEEDS_V1_MARKER))
                        } else {
                            val detail = if (apiMessage.isNotEmpty()) apiMessage else "Unexpected error"
                            Result.failure(Exception("Error $responseCode: $detail"))
                        }
                    }
                    else -> {
                        val detail = if (apiMessage.isNotEmpty()) apiMessage else "Unexpected error"
                        Result.failure(Exception("Error $responseCode: $detail"))
                    }
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * GETs [url] and reports whether the server answered 2xx. Used by [validateKey]'s
     * /v1 fallback probe — a 200 here proves the endpoint is reachable, nothing more.
     */
    private fun probeModels(url: String, apiKey: String): Boolean {
        var probe: HttpURLConnection? = null
        return try {
            probe = URL(url).openConnection() as HttpURLConnection
            probe.requestMethod = "GET"
            probe.applyAuth(apiKey)
            probe.connectTimeout = 15_000
            probe.readTimeout = 15_000
            val code = probe.responseCode
            if (code in 200..299) {
                probe.inputStream?.use { stream ->
                    val buf = ByteArray(1024)
                    while (stream.read(buf) != -1) { /* drain */ }
                }
            }
            code in 200..299
        } catch (_: Exception) {
            false
        } finally {
            probe?.disconnect()
        }
    }

    /**
     * Fetches the model list from a Custom (OpenAI-compatible) endpoint for the Settings
     * model dropdown. Probes the paths local LLM servers actually implement, in coverage
     * order: `/v1/models` (OpenAI/Ollama/LM Studio/vLLM/llama.cpp/LiteLLM), then `/models`,
     * then Ollama's native `/api/tags`, then Open WebUI's `/api/models`. Stops at the first
     * 2xx with a non-empty list.
     *
     *  - 401/403 stops the probe immediately: the server exists and rejected the key, and
     *    every other path on it will reject too. The failure carries the same signin/raw
     *    message handling as [validateKey].
     *  - Connection failures stop the probe too — the endpoint is unreachable, not the path.
     *  - Any 2xx (even an empty list) marks the server reachable: if no path yields model
     *    ids, the result is an empty success list so the UI can show "No models found".
     *    A failure is returned only when every path 404s/5xxes — the server is not serving
     *    any known models path at all.
     *
     * [apiKey] may be null: local servers without a configured key (Ollama, LM Studio,
     * llama.cpp) want no Authorization header at all, and sending a placeholder helps nobody.
     */
    suspend fun fetchModels(apiKey: String?, endpoint: String): Result<List<String>> = withContext(Dispatchers.IO) {
        if (EndpointValidator.validate(endpoint) != EndpointValidator.Error.NONE) {
            return@withContext Result.failure(Exception("Endpoint must be https:// or an http:// private-LAN address"))
        }
        val baseUrl = endpoint.trimEnd('/')
        var lastDetail: String? = null
        // Set on the first 2xx, whatever the body: a server that answered is reachable even
        // when it lists no models, so "No models found" must win over a 404 seen elsewhere.
        var reachable = false
        for (path in listOf("$baseUrl/v1/models", "$baseUrl/models", "$baseUrl/api/tags", "$baseUrl/api/models")) {
            val probe = try {
                httpGet(path, apiKey)
            } catch (e: Exception) {
                return@withContext Result.failure(e)
            }
            when {
                probe.code in 200..299 -> {
                    reachable = true
                    val ids = ApiClientUtils.parseModelIds(probe.body)
                    if (ids.isNotEmpty()) return@withContext Result.success(ids)
                    // Reachable but empty at this path — try the next one.
                }
                probe.code == 401 || probe.code == 403 -> {
                    val apiMessage = ApiClientUtils.extractApiErrorMessage(probe.body)
                    val signinUrl = ApiClientUtils.extractSigninUrl(probe.body)
                    val detail = when {
                        signinUrl != null || apiMessage.contains("not currently signed in", ignoreCase = true) ->
                            "${ApiClientUtils.SIGNIN_REQUIRED_MARKER}: ${apiMessage.ifEmpty { "server sign-in required" }}"
                        apiMessage.isNotEmpty() -> apiMessage
                        else -> "Invalid API key"
                    }
                    return@withContext Result.failure(Exception(detail))
                }
                else -> {
                    // 404/405/5xx — this path is not served here; remember the detail and
                    // try the next one.
                    val apiMessage = ApiClientUtils.extractApiErrorMessage(probe.body)
                    lastDetail = if (apiMessage.isNotEmpty()) apiMessage else "Unexpected error"
                }
            }
        }
        if (lastDetail != null && !reachable) Result.failure(Exception(lastDetail))
        else Result.success(emptyList())
    }

    private data class Probe(val code: Int, val body: String)

    /** GETs [url], returning the status code and the (bounded) body on either stream. */
    private fun httpGet(url: String, apiKey: String?): Probe {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.applyAuth(apiKey)
            connection.connectTimeout = 15_000
            connection.readTimeout = 15_000
            val code = connection.responseCode
            val body = if (code in 200..299) ApiClientUtils.readResponseBounded(connection)
            else ApiClientUtils.readErrorBody(connection)
            Probe(code, body)
        } finally {
            connection?.disconnect()
        }
    }

    suspend fun generate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String,
        useJsonObjectMode: Boolean = false,
        extraParams: Map<String, Any> = emptyMap(),
        searchMode: SearchMode = SearchMode.NONE,
        searcher: WebSearcher = WebSearcher()
    ): Result<GenerateResult> = withContext(Dispatchers.IO) {
        if (searchMode == SearchMode.FUNCTION) {
            val searched = generateWithSearch(prompt, text, apiKey, model, temperature, endpoint, extraParams, searcher)
            if (searched != null) {
                val cleaned = stripHttpPrefix(searched.map { it.text })
                val meta = searched.getOrNull()
                return@withContext cleaned.map { GenerateResult(it, truncated = meta?.truncated == true) }
            }
            // null: this server does not do function calling, so run the command without search.
        }

        var result = doGenerate(prompt, text, apiKey, model, temperature, endpoint, useJsonObjectMode, extraParams)

        // Retry once for transient network/server errors (with 1.5s backoff)
        if (result.isFailure && result.exceptionOrNull().isTransientNetwork()) {
            kotlinx.coroutines.delay(1500)
            result = doGenerate(prompt, text, apiKey, model, temperature, endpoint, useJsonObjectMode, extraParams)
        }

        val cleaned = stripHttpPrefix(result.map { it.text })
        val meta = result.getOrNull()
        cleaned.map { GenerateResult(it, meta?.structuredOutputFailed == true, meta?.truncated == true) }
    }

    /**
     * Runs a command with Bell's own web search on offer. The model either answers right away
     * or asks for searches; Bell runs those (up to [MAX_SEARCHES]) and sends the results back
     * for the final answer.
     *
     * Returns null when the server rejects function calling, so the caller can fall back to
     * a plain request. Search failures never fail the command: the model is told the search
     * did not work and answers from what it knows.
     */
    private suspend fun generateWithSearch(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String,
        extraParams: Map<String, Any>,
        searcher: WebSearcher
    ): Result<GenerateResult>? {
        val tools = searchTools()
        var first = doGenerate(prompt, text, apiKey, model, temperature, endpoint, false, extraParams, tools = tools)
        if (first.isFailure && first.exceptionOrNull().isTransientNetwork()) {
            kotlinx.coroutines.delay(1500)
            first = doGenerate(prompt, text, apiKey, model, temperature, endpoint, false, extraParams, tools = tools)
        }
        val firstMsg = first.exceptionOrNull()?.message.orEmpty()
        if (first.isFailure && firstMsg.startsWith("HTTP_4") && firstMsg.contains("tool", ignoreCase = true)) return null

        val request = first.getOrNull()?.searchRequest ?: return first

        val followUp = JSONArray().put(request)
        val calls = request.getJSONArray("tool_calls")
        for (i in 0 until calls.length()) {
            val call = calls.getJSONObject(i)
            val query = queryFromArguments(call.getJSONObject("function").optString("arguments", "{}"))
            val results = when {
                i >= MAX_SEARCHES -> "Search limit reached; answer with what you have."
                query.isEmpty() -> "No search query was given."
                else -> searcher.search(query).getOrElse { "The search did not work (${it.message}). Answer from what you already know." }
            }
            followUp.put(JSONObject().apply {
                put("role", "tool")
                put("tool_call_id", call.getString("id"))
                put("content", results)
            })
        }
        return doGenerate(
            prompt, text, apiKey, model, temperature, endpoint, false, extraParams,
            tools = tools, toolChoice = "none", followUp = followUp
        )
    }

    private fun stripHttpPrefix(result: Result<String>): Result<String> {
        if (result.isFailure) {
            val msg = result.exceptionOrNull()?.message ?: ""
            val cleaned = msg.replaceFirst(HTTP_PREFIX_REGEX, "")
            if (cleaned != msg) return Result.failure(Exception(cleaned))
        }
        return result
    }

    private fun doGenerate(
        prompt: String,
        text: String,
        apiKey: String,
        model: String,
        temperature: Double,
        endpoint: String,
        withJsonObject: Boolean = false,
        extraParams: Map<String, Any> = emptyMap(),
        tools: JSONArray? = null,
        toolChoice: String = "auto",
        followUp: JSONArray? = null
    ): Result<GenerateResult> {
        if (EndpointValidator.validate(endpoint) != EndpointValidator.Error.NONE) {
            return Result.failure(Exception("Endpoint must be https:// or an http:// private-LAN address"))
        }
        var connection: HttpURLConnection? = null
        return try {
            val baseUrl = endpoint.trimEnd('/')
            connection = URL("$baseUrl/chat/completions")
                .openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.applyAuth(apiKey)
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 60_000

            val systemContent = if (withJsonObject) {
                ApiClientUtils.SYSTEM_PROMPT_PREFIX + prompt + " Respond with JSON: {\"text\": \"your result\"}"
            } else {
                ApiClientUtils.SYSTEM_PROMPT_PREFIX + prompt
            }

            val jsonBody = JSONObject().apply {
                val safeModel = model.replace(Regex("[^a-zA-Z0-9._\\-/: ]"), "")
                put("model", safeModel)
                put("messages", JSONArray().apply {
                    put(JSONObject().apply {
                        put("role", "system")
                        put("content", systemContent)
                    })
                    put(JSONObject().apply {
                        put("role", "user")
                        put("content", ApiClientUtils.wrapUserText(text))
                    })
                    // The model's search request and Bell's results, on the second round.
                    if (followUp != null) for (i in 0 until followUp.length()) put(followUp.get(i))
                })
                if (tools != null) {
                    put("tools", tools)
                    put("tool_choice", toolChoice)
                }
                put("temperature", temperature)
                if (withJsonObject) {
                    put("response_format", JSONObject().apply {
                        put("type", "json_object")
                    })
                }
                // Extra provider-specific params (e.g. Groq reasoning controls),
                // resolved by the caller from the active provider config. Empty
                // for providers/models that take none, so nothing is sent.
                extraParams.forEach { (k, v) -> put(k, v) }
            }

            connection.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            val responseCode = connection.responseCode
            if (responseCode in 200..299) {
                val response = ApiClientUtils.readResponseBounded(connection)

                val jsonResponse = JSONObject(response)
                val choices = jsonResponse.optJSONArray("choices")
                if (choices != null && choices.length() > 0) {
                    val choice = choices.getJSONObject(0)

                    val finishReason = choice.optString("finish_reason", "")
                    if (finishReason == "content_filter") {
                        return Result.failure(Exception("Response blocked by content filter"))
                    }

                    val message = choice.optJSONObject("message")
                    // The model wants Bell to search before it answers.
                    if (tools != null && toolChoice != "none" &&
                        (message?.optJSONArray("tool_calls")?.length() ?: 0) > 0) {
                        return Result.success(GenerateResult("", searchRequest = cleanToolCallMessage(message!!)))
                    }
                    var resultText = message?.optString("content", "") ?: ""
                    // Off-catalog reasoning models get no reasoning params (we cannot know
                    // which values they accept), so they run at Groq's default: reasoning ON
                    // with reasoning_format "raw", which embeds the chain of thought in the
                    // content inside <think> tags. Measured on qwen/qwen3.6-27b. Strip it
                    // before the blank check, so a reply that is nothing but reasoning counts
                    // as empty instead of pasting the model's monologue into the user's text.
                    resultText = ApiClientUtils.stripReasoningBlock(resultText)
                    if (resultText.isBlank()) {
                        return Result.failure(Exception("Model returned empty response"))
                    }

                    if (withJsonObject) {
                        val (extracted, parseFailed) = ApiClientUtils.tryExtractStructuredText(resultText)
                        if (extracted != null) return Result.success(GenerateResult(extracted))
                        // Do not fall through with a raw JSON payload — that pasted literal
                        // JSON such as {"text": ""} (parsed, no usable field) or a truncated
                        // object (unparseable but clearly JSON) into the user's text field.
                        if (!parseFailed || resultText.trimStart().startsWith("{")) {
                            return Result.failure(Exception(
                                "Model returned empty response (${ApiClientUtils.STRUCTURED_UNUSABLE_MARKER})"))
                        }
                        // Genuinely not JSON: the model ignored response_format. Use the text,
                        // but flag it so the caller disables JSON mode for 24h.
                        resultText = ApiClientUtils.stripMarkdownFences(resultText)
                        return Result.success(GenerateResult(
                            resultText, structuredOutputFailed = true, truncated = finishReason == "length"))
                    }

                    resultText = ApiClientUtils.stripMarkdownFences(resultText)
                    Result.success(GenerateResult(resultText, truncated = finishReason == "length"))
                } else {
                    Result.failure(Exception("No choices found in response"))
                }
            } else if (responseCode == 413) {
                // Request too large for this key's per-minute token budget. Groq enforces
                // TPM per organization, so another key (different org) may still have
                // headroom. Classified as a rate limit so the caller cools this key down
                // briefly and rotates, instead of hard-failing the whole command.
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)
                val detail = if (apiMessage.isNotEmpty()) apiMessage else "Request too large"
                Result.failure(ApiException(ApiError.RequestTooLarge(detail), detail))
            } else if (responseCode == 429) {
                val retryAfter = connection.getHeaderField("Retry-After")
                val seconds = retryAfter?.toIntOrNull()
                val msg = if (seconds != null) "Rate limit exceeded, retry after ${seconds}s" else "Rate limit exceeded"
                Result.failure(ApiException(ApiError.RateLimit(msg, seconds), msg))
            } else if (responseCode == 400 || responseCode == 422) {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)
                val detail = if (apiMessage.isNotEmpty()) apiMessage else "Bad request"
                // Keep the machine-readable reason: Groq puts json_validate_failed in
                // error.code, not error.message, and that code is the only thing separating
                // "the model could not produce valid JSON" (recoverable by dropping JSON mode)
                // from a genuine bad request. Never reaches the user — the service maps every
                // raw message onto a localized string.
                val providerCode = ApiClientUtils.extractApiErrorCode(errorBody)
                val withCode = if (providerCode.isNotEmpty()) "$detail [$providerCode]" else detail
                Result.failure(Exception("HTTP_${responseCode}: $withCode"))
            } else if (responseCode == 401 || responseCode == 403) {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                val apiMessage = ApiClientUtils.extractApiErrorMessage(errorBody)
                val signinUrl = ApiClientUtils.extractSigninUrl(errorBody)
                val detail = when {
                    signinUrl != null || apiMessage.contains("not currently signed in", ignoreCase = true) ->
                        "${ApiClientUtils.SIGNIN_REQUIRED_MARKER}: ${apiMessage.ifEmpty { "server sign-in required" }}"
                    apiMessage.isNotEmpty() -> apiMessage
                    else -> "Invalid API key"
                }
                Result.failure(ApiException(ApiError.InvalidKey(detail), detail))
            } else {
                val errorBody = ApiClientUtils.readErrorBody(connection)
                var detail = ApiClientUtils.sanitizeErrorForUser(responseCode, errorBody, "Unexpected error (HTTP $responseCode)")
                // Groq reports an unknown or inaccessible model as HTTP 404 with the reason only
                // in error.code ("model_not_found"); its message ("The model `x` does not exist or
                // you do not have access to it.") matches none of the localized patterns, so the
                // user was shown raw English. Normalize onto the existing translated string.
                val providerCode = ApiClientUtils.extractApiErrorCode(errorBody)
                if (responseCode == 404 || providerCode == "model_not_found") {
                    detail = "Model not found. $detail"
                }
                val apiError = if (responseCode in 500..599) ApiError.ServerError(detail) else ApiError.Other(detail)
                Result.failure(ApiException(apiError, detail))
            }
        } catch (e: Exception) {
            val apiError = when (e) {
                is ApiException -> e.apiError
                is SocketTimeoutException, is UnknownHostException, is ConnectException, is java.net.SocketException -> ApiError.Network(e.message ?: "Network error")
                is org.json.JSONException -> ApiError.Other("Invalid response from server")
                else -> ApiError.Other(e.message ?: "Unknown error")
            }
            if (e is ApiException) Result.failure(e) else Result.failure(ApiException(apiError, e.message ?: "Unknown error"))
        } finally {
            connection?.disconnect()
        }
    }
}

