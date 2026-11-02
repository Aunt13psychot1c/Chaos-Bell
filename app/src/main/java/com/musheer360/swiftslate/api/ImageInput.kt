package com.musheer360.swiftslate.api

import org.json.JSONObject

/**
 * A picture sent along with a message, already shrunk and base64-encoded (see
 * [com.musheer360.swiftslate.ui.processtext.ImagePrep]). Each provider wants it wrapped a
 * little differently, so it knows how to dress itself for all three.
 */
data class ImageInput(val mimeType: String, val base64: String) {

    /** OpenAI-compatible chat (OpenAI, Groq, OpenRouter, Cloudflare...): a data: URL. */
    fun openAiPart(): JSONObject = JSONObject()
        .put("type", "image_url")
        .put("image_url", JSONObject().put("url", "data:$mimeType;base64,$base64"))

    /** Claude's Messages API. */
    fun anthropicPart(): JSONObject = JSONObject()
        .put("type", "image")
        .put("source", JSONObject()
            .put("type", "base64")
            .put("media_type", mimeType)
            .put("data", base64))

    /** Gemini's generateContent. */
    fun geminiPart(): JSONObject = JSONObject()
        .put("inline_data", JSONObject()
            .put("mime_type", mimeType)
            .put("data", base64))

    // Data classes print every field; never dump megabytes of base64 into a log.
    override fun toString(): String = "ImageInput($mimeType, ${base64.length} chars)"
}
