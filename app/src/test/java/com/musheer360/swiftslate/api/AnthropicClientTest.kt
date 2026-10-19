package com.musheer360.swiftslate.api

import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Runs under Robolectric because AnthropicClient uses org.json. */
@RunWith(RobolectricTestRunner::class)
class AnthropicClientTest {

    @Test
    fun model_list_keeps_claude_models_in_order() {
        val json = """
            {"data":[
              {"type":"model","id":"claude-opus-5-5"},
              {"type":"model","id":"claude-haiku-5-5"},
              {"type":"model","id":"something-else"}
            ],"has_more":false}
        """.trimIndent()
        assertEquals(listOf("claude-opus-5-5", "claude-haiku-5-5"), AnthropicClient.parseModelIds(json))
    }

    @Test
    fun model_list_handles_missing_data() {
        assertEquals(emptyList<String>(), AnthropicClient.parseModelIds("{}"))
    }

    @Test
    fun plain_answer_joins_all_text_blocks() {
        val content = JSONArray("""[{"type":"text","text":"Hello "},{"type":"text","text":"there"}]""")
        assertEquals("Hello there", AnthropicClient.extractText(content))
    }

    @Test
    fun searched_answer_drops_the_narration_before_the_search() {
        val content = JSONArray("""[
            {"type":"text","text":"Let me look that up."},
            {"type":"server_tool_use","id":"s1","name":"web_search","input":{"query":"x"}},
            {"type":"web_search_tool_result","tool_use_id":"s1","content":[]},
            {"type":"text","text":"The answer "},
            {"type":"text","text":"is 42.","citations":[]}
        ]""")
        assertEquals("The answer is 42.", AnthropicClient.extractText(content))
    }

    @Test
    fun request_body_has_claude_shape() {
        val body = AnthropicClient().buildRequestBody(
            prompt = "Fix spelling", text = "helo", model = "claude-haiku-5-5",
            temperature = 1.7, webSearch = true, sendTemperature = true
        )
        assertEquals("claude-haiku-5-5", body.getString("model"))
        assertTrue(body.getString("system").endsWith("Fix spelling"))
        assertEquals(1.0, body.getDouble("temperature"), 0.0)
        val msg = body.getJSONArray("messages").getJSONObject(0)
        assertEquals("user", msg.getString("role"))
        assertTrue(msg.getString("content").contains("helo"))
        val tool = body.getJSONArray("tools").getJSONObject(0)
        assertEquals(AnthropicClient.WEB_SEARCH_TOOL_TYPE, tool.getString("type"))
        assertEquals("web_search", tool.getString("name"))
    }

    @Test
    fun request_body_leaves_out_search_and_temperature_when_told() {
        val body = AnthropicClient().buildRequestBody("p", "t", "claude-haiku-5-5", 0.5, webSearch = false, sendTemperature = false)
        assertFalse(body.has("tools"))
        assertFalse(body.has("temperature"))
    }

    @Test
    fun recognizes_the_errors_it_can_recover_from() {
        assertTrue(AnthropicClient.isWebSearchDisabledError("HTTP_400: Web search is not enabled for this organization"))
        assertTrue(AnthropicClient.isTemperatureRejectedError("HTTP_400: temperature is not supported for this model"))
        assertFalse(AnthropicClient.isWebSearchDisabledError("HTTP_400: max_tokens too large"))
    }
}
