package com.musheer360.swiftslate.api

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnthropicHostTest {

    @Test
    fun claude_hosts_get_anthropic_headers() {
        assertTrue(OpenAICompatibleClient.isAnthropicHost("api.anthropic.com"))
        assertTrue(OpenAICompatibleClient.isAnthropicHost("API.Anthropic.com"))
        assertTrue(OpenAICompatibleClient.isAnthropicHost("anthropic.com"))
    }

    @Test
    fun other_hosts_are_left_alone() {
        assertFalse(OpenAICompatibleClient.isAnthropicHost("api.groq.com"))
        assertFalse(OpenAICompatibleClient.isAnthropicHost("api.openai.com"))
        assertFalse(OpenAICompatibleClient.isAnthropicHost("192.168.1.20"))
        assertFalse(OpenAICompatibleClient.isAnthropicHost("notanthropic.com"))
        assertFalse(OpenAICompatibleClient.isAnthropicHost("anthropic.com.evil.example"))
        assertFalse(OpenAICompatibleClient.isAnthropicHost(null))
    }
}
