package com.musheer360.swiftslate.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImageInputTest {

    private val pic = ImageInput("image/jpeg", "QUJD")

    @Test
    fun each_provider_gets_its_own_wrapping() {
        assertEquals("data:image/jpeg;base64,QUJD", pic.openAiPart().getJSONObject("image_url").getString("url"))
        assertEquals("image_url", pic.openAiPart().getString("type"))
        val claude = pic.anthropicPart().getJSONObject("source")
        assertEquals("base64", claude.getString("type"))
        assertEquals("image/jpeg", claude.getString("media_type"))
        assertEquals("QUJD", claude.getString("data"))
        assertEquals("QUJD", pic.geminiPart().getJSONObject("inline_data").getString("data"))
    }

    @Test
    fun logs_never_get_the_picture_itself() {
        assertFalse(ImageInput("image/jpeg", "x".repeat(5000)).toString().contains("xxxxx"))
    }

    @Test
    fun claude_request_puts_the_picture_before_the_words() {
        val body = AnthropicClient().buildRequestBody(
            prompt = "Answer", text = "what breed?", model = "claude-haiku-5-5",
            temperature = 0.5, webSearch = false, sendTemperature = true, image = pic
        )
        val content = body.getJSONArray("messages").getJSONObject(0).getJSONArray("content")
        assertEquals("image", content.getJSONObject(0).getString("type"))
        assertEquals("text", content.getJSONObject(1).getString("type"))
        assertTrue(content.getJSONObject(1).getString("text").contains("what breed?"))
    }

    @Test
    fun claude_request_without_a_picture_is_unchanged() {
        val body = AnthropicClient().buildRequestBody("p", "hi", "claude-haiku-5-5", 0.5, webSearch = false, sendTemperature = false)
        assertTrue(body.getJSONArray("messages").getJSONObject(0).getString("content").contains("hi"))
    }
}
