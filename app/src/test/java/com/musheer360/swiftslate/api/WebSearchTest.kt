package com.musheer360.swiftslate.api

import com.musheer360.swiftslate.provider.CustomConfig
import com.musheer360.swiftslate.provider.GroqConfig
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.net.InetSocketAddress

/**
 * Web search on every slot. The function-calling loop runs against a tiny fake
 * OpenAI-compatible server on this machine, so the whole round trip is exercised:
 * model asks for a search, Bell searches, model answers with the results.
 */
@RunWith(RobolectricTestRunner::class)
class WebSearchTest {

    private lateinit var server: HttpServer
    private val requests = mutableListOf<JSONObject>()
    private var replies = ArrayDeque<Pair<Int, String>>()

    @Before
    fun startServer() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/chat/completions") { ex ->
            requests.add(JSONObject(ex.requestBody.readBytes().toString(Charsets.UTF_8)))
            val (code, body) = replies.removeFirst()
            val bytes = body.toByteArray()
            ex.sendResponseHeaders(code, bytes.size.toLong())
            ex.responseBody.use { it.write(bytes) }
        }
        server.start()
    }

    @After
    fun stopServer() = server.stop(0)

    private val endpoint get() = "http://127.0.0.1:${server.address.port}/v1"

    private fun content(text: String) =
        """{"choices":[{"finish_reason":"stop","message":{"role":"assistant","content":${JSONObject.quote(text)}}}]}"""

    private val toolCall =
        """{"choices":[{"finish_reason":"tool_calls","message":{"role":"assistant","content":null,"reasoning":"x",
           "tool_calls":[{"id":"call_1","type":"function","function":{"name":"web_search","arguments":"{\"query\":\"Evansville weather\"}"}}]}}]}"""

    private class FakeSearcher : WebSearcher() {
        val queries = mutableListOf<String>()
        override fun search(query: String): Result<String> {
            queries.add(query)
            return Result.success("1. Weather\nhttps://example.com\nSunny and 70.")
        }
    }

    @Test
    fun model_asks_bell_searches_model_answers() = runBlocking {
        replies.addLast(200 to toolCall)
        replies.addLast(200 to content("It is sunny and 70 in Evansville."))
        val searcher = FakeSearcher()
        val result = OpenAICompatibleClient().generate(
            "Answer", "weather?", "k", "m", 0.5, endpoint,
            searchMode = SearchMode.FUNCTION, searcher = searcher
        )
        assertEquals("It is sunny and 70 in Evansville.", result.getOrThrow().text)
        assertEquals(listOf("Evansville weather"), searcher.queries)
        assertEquals(2, requests.size)
        assertTrue(requests[0].has("tools"))
        val second = requests[1].getJSONArray("messages")
        val echoed = second.getJSONObject(2)
        assertEquals("assistant", echoed.getString("role"))
        assertFalse("extra fields are not echoed back", echoed.has("reasoning"))
        val tool = second.getJSONObject(3)
        assertEquals("tool", tool.getString("role"))
        assertEquals("call_1", tool.getString("tool_call_id"))
        assertTrue(tool.getString("content").contains("Sunny"))
        assertEquals("none", requests[1].getString("tool_choice"))
    }

    @Test
    fun model_that_needs_no_search_answers_in_one_request() = runBlocking {
        replies.addLast(200 to content("Fixed text."))
        val searcher = FakeSearcher()
        val result = OpenAICompatibleClient().generate(
            "Fix", "fixd txt", "k", "m", 0.5, endpoint,
            searchMode = SearchMode.FUNCTION, searcher = searcher
        )
        assertEquals("Fixed text.", result.getOrThrow().text)
        assertTrue(searcher.queries.isEmpty())
        assertEquals(1, requests.size)
    }

    @Test
    fun server_without_tools_falls_back_to_a_plain_request() = runBlocking {
        replies.addLast(400 to """{"error":{"message":"tools are not supported by this model"}}""")
        replies.addLast(200 to content("Plain answer."))
        val result = OpenAICompatibleClient().generate(
            "Fix", "x", "k", "m", 0.5, endpoint,
            searchMode = SearchMode.FUNCTION, searcher = FakeSearcher()
        )
        assertEquals("Plain answer.", result.getOrThrow().text)
        assertFalse(requests[1].has("tools"))
    }

    @Test
    fun search_off_sends_no_tools() = runBlocking {
        replies.addLast(200 to content("ok"))
        OpenAICompatibleClient().generate("p", "t", "k", "m", 0.5, endpoint)
        assertFalse(requests[0].has("tools"))
    }

    @Test
    fun groq_compound_searches_on_its_own_and_everyone_else_gets_bells_search() {
        assertEquals(SearchMode.NONE, GroqConfig.searchMode("groq/compound"))
        assertEquals(SearchMode.NONE, GroqConfig.searchMode("groq/compound-mini"))
        assertEquals(SearchMode.FUNCTION, GroqConfig.searchMode("llama-3.3-70b-versatile"))
        assertEquals(SearchMode.FUNCTION, CustomConfig.searchMode("anything"))
    }

    @Test
    fun search_results_are_numbered_and_trimmed() {
        val json = """{"results":[
            {"title":"A","url":"https://a.example","content":"first   snippet"},
            {"title":"B","url":"https://b.example","content":"${"x".repeat(900)}"}]}"""
        val out = WebSearcher.formatResults(json)
        assertTrue(out.startsWith("1. A\nhttps://a.example\nfirst snippet"))
        assertTrue(out.contains("2. B"))
        assertTrue(out.length < 1000)
        assertEquals("No results.", WebSearcher.formatResults("""{"results":[]}"""))
    }

    @Test
    fun gemini_joins_answer_parts_and_skips_thoughts() {
        val parts = JSONArray("""[{"text":"thinking","thought":true},{"text":"Sunny "},{"text":"today."}]""")
        assertEquals("Sunny today.", GeminiClient().joinAnswerParts(parts))
    }
}
