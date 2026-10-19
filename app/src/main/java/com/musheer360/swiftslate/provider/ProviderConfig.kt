package com.musheer360.swiftslate.provider

import com.musheer360.swiftslate.api.SearchMode
import com.musheer360.swiftslate.model.GeminiModels
import com.musheer360.swiftslate.model.GroqModels
import com.musheer360.swiftslate.model.PrefKeys
import com.musheer360.swiftslate.model.ProviderType

/**
 * Which transport client handles a provider's requests.
 * - [GEMINI_NATIVE]: Gemini's own API format (GeminiClient).
 * - [OPENAI_COMPAT]: OpenAI-compatible chat completions (OpenAICompatibleClient),
 *   shared by Groq and Custom.
 * - [ANTHROPIC_NATIVE]: Claude's own Messages API (AnthropicClient).
 */
enum class Transport { GEMINI_NATIVE, OPENAI_COMPAT, ANTHROPIC_NATIVE }

/**
 * Per-provider configuration: everything the request pipeline needs to know
 * about a provider, in one place. This replaces the inline provider `if/else`
 * ladder and the scattered defaults/endpoints/keys.
 *
 * Implementations are pure (no Android/network dependencies) so they are trivial
 * to reason about and test. The service reads SharedPreferences and passes the
 * relevant values in.
 */
interface ProviderConfig {

    /** Provider id, matching a [ProviderType] constant. */
    val type: String

    /** Which client transport to use. */
    val transport: Transport

    /** SharedPreferences key holding this provider's selected model. */
    val modelPrefKey: String

    /** Default model when none is stored. */
    val defaultModel: String

    /** Normalize a stored model value (coercion where applicable). */
    fun sanitizeModel(stored: String?): String

    /**
     * Resolve the endpoint base URL. [customEndpoint] is the stored custom
     * endpoint value, used only by providers that need it.
     */
    fun resolveEndpoint(customEndpoint: String): String

    /**
     * Extra request-body params (e.g. reasoning controls) for [model].
     * Empty for providers/models that take none.
     */
    fun reasoningParams(model: String): Map<String, Any> = emptyMap()

    /**
     * Gemini-native thinking level (generationConfig.thinkingConfig.thinkingLevel)
     * for [model], or null to send none. No-op for non-Gemini providers.
     */
    fun thinkingLevel(model: String): String? = null

    /**
     * Whether the OpenAI-compatible request should use json_object response
     * format, given whether structured output is currently enabled.
     */
    fun useJsonObjectMode(structuredOutputEnabled: Boolean): Boolean = false

    /**
     * How an OpenAI-compatible request gets web search for [model]. Bell's own search by
     * default; providers whose models search on their own say so here. Unused by the
     * Gemini and Anthropic transports, which have search built in.
     */
    fun searchMode(model: String): SearchMode = SearchMode.FUNCTION

    /** Whether the resolved [model]/[endpoint] are usable (Custom requires both). */
    fun isConfigured(model: String, endpoint: String): Boolean = true
}

/** Gemini — native API, model-gated thinking level (spec-driven). */
object GeminiConfig : ProviderConfig {
    override val type = ProviderType.GEMINI
    override val transport = Transport.GEMINI_NATIVE
    override val modelPrefKey = PrefKeys.GEMINI_MODEL
    override val defaultModel = GeminiModels.DEFAULT
    override fun sanitizeModel(stored: String?): String = GeminiModels.sanitize(stored)
    override fun resolveEndpoint(customEndpoint: String): String = ""
    override fun thinkingLevel(model: String): String? = GeminiModels.thinkingLevel(model)
}

/** Groq — OpenAI-compatible, fixed endpoint, per-model reasoning controls. */
object GroqConfig : ProviderConfig {
    const val ENDPOINT = "https://api.groq.com/openai/v1"

    override val type = ProviderType.GROQ
    override val transport = Transport.OPENAI_COMPAT
    override val modelPrefKey = PrefKeys.GROQ_MODEL
    override val defaultModel = GroqModels.DEFAULT
    override fun sanitizeModel(stored: String?): String = GroqModels.sanitize(stored)
    override fun resolveEndpoint(customEndpoint: String): String = ENDPOINT
    override fun reasoningParams(model: String): Map<String, Any> = GroqModels.reasoningParams(model)
    override fun useJsonObjectMode(structuredOutputEnabled: Boolean): Boolean = structuredOutputEnabled
    // Groq's compound systems search the web on their own; every other Groq model gets
    // Bell's search.
    override fun searchMode(model: String): SearchMode =
        if (model.startsWith("groq/compound")) SearchMode.NONE else SearchMode.FUNCTION
}

/** Custom OpenAI-compatible endpoint — user-supplied endpoint and model. */
object CustomConfig : ProviderConfig {
    override val type = ProviderType.CUSTOM
    override val transport = Transport.OPENAI_COMPAT
    override val modelPrefKey = PrefKeys.CUSTOM_MODEL
    override val defaultModel = ""
    override fun sanitizeModel(stored: String?): String = stored?.trim() ?: ""
    override fun resolveEndpoint(customEndpoint: String): String = customEndpoint
    override fun isConfigured(model: String, endpoint: String): Boolean =
        model.isNotBlank() && endpoint.isNotBlank()
}

/** Anthropic — Claude's own Messages API, fixed endpoint, optional web search. */
object AnthropicConfig : ProviderConfig {
    /** Fast and cheap: a good fit for quick keyboard commands. */
    const val DEFAULT_MODEL = "claude-haiku-5-5"
    const val API_KEY_URL = "https://platform.claude.com/settings/keys"

    override val type = ProviderType.ANTHROPIC
    override val transport = Transport.ANTHROPIC_NATIVE
    override val modelPrefKey = PrefKeys.ANTHROPIC_MODEL
    override val defaultModel = DEFAULT_MODEL
    override fun sanitizeModel(stored: String?): String = stored?.trim().orEmpty().ifEmpty { DEFAULT_MODEL }
    override fun resolveEndpoint(customEndpoint: String): String = ""
}

/** Registry resolving a stored provider value to its [ProviderConfig]. */
object Providers {
    fun forType(type: String?): ProviderConfig = when (ProviderType.sanitize(type)) {
        ProviderType.GROQ -> GroqConfig
        ProviderType.CUSTOM -> CustomConfig
        ProviderType.ANTHROPIC -> AnthropicConfig
        else -> GeminiConfig
    }
}
