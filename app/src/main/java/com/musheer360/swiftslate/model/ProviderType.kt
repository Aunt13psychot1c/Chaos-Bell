package com.musheer360.swiftslate.model

object ProviderType {
    const val GEMINI = "gemini"
    const val GROQ = "groq"
    const val CUSTOM = "custom"
    const val ANTHROPIC = "anthropic"

    private val VALID = setOf(GEMINI, GROQ, CUSTOM, ANTHROPIC)
    fun sanitize(value: String?): String = if (value in VALID) value!! else GEMINI
}
