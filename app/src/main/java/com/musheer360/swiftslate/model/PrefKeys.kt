package com.musheer360.swiftslate.model

/**
 * Single source of truth for the SharedPreferences keys used by the
 * provider/model configuration flow. Centralizing these prevents silent
 * breakage from mistyped string literals scattered across UI, service, and
 * client code.
 *
 * Values are unchanged from the literals previously used inline, so existing
 * stored preferences continue to resolve identically.
 */
object PrefKeys {
    /** Active provider ("gemini" | "groq" | "custom" | "anthropic") — see [ProviderType]. */
    const val PROVIDER_TYPE = "provider_type"

    /** Selected Gemini model id. */
    const val GEMINI_MODEL = "model"

    /** Selected Groq model id. */
    const val GROQ_MODEL = "groq_model"

    /** Selected Claude model id for the Anthropic provider. */
    const val ANTHROPIC_MODEL = "anthropic_model"

    /** Whether the AI may search the web while running a command, on every provider (Boolean, default on). */
    const val WEB_SEARCH = "web_search"

    /** Custom (OpenAI-compatible) model id. */
    const val CUSTOM_MODEL = "custom_model"

    /** Custom (OpenAI-compatible) endpoint base URL. */
    const val CUSTOM_ENDPOINT = "custom_endpoint"

    /**
     * Bell is paused (Boolean): ?commands, the chat window and check-ins are ignored (see
     * BellPause), but reminders still ring. The accessibility permission and the saved keys
     * stay put, so resuming is one tap.
     */
    const val PAUSED = "paused"

    /** Sampling temperature (Float). */
    const val TEMPERATURE = "temperature"

    /** Epoch millis when structured output was last disabled (0 = never). */
    const val STRUCTURED_OUTPUT_DISABLED_AT = "structured_output_disabled_at"
}
