package com.musheer360.swiftslate.model

import androidx.compose.runtime.Immutable

enum class CommandType {
    AI, TEXT_REPLACER
}

@Immutable
data class Command(
    val trigger: String,
    val prompt: String,
    val isBuiltIn: Boolean = false,
    val type: CommandType = CommandType.AI,
    /**
     * Answer mode: the AI answers or explains the text (with the user's About-me and recent
     * conversation) instead of rewriting it as a pure text transformation.
     */
    val answerMode: Boolean = false,
    /** Always search the web first, whatever the Web search switch says. */
    val forceSearch: Boolean = false,
    /** Answer-mode only: false leaves Bell's Soul out, for plain, normal answers (?notalk). */
    val useSoul: Boolean = true
)
