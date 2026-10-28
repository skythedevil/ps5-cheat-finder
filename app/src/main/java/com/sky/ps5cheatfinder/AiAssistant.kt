package com.sky.ps5cheatfinder

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/** The AI services the user can choose from. */
enum class AiProviderType(val label: String, val defaultModel: String) {
    GEMINI("Gemini (free key)", "gemini-2.5-flash"),
    ANTHROPIC("Claude (paid key)", "claude-haiku-5-5"),
    OPENAI_COMPATIBLE("ChatGPT / Groq / OpenRouter", "gpt-4o-mini")
}

/** Everything needed to call one AI service. Keys are passed in memory and never saved by this class. */
data class AiSettings(
    val type: AiProviderType,
    val apiKey: String,
    val model: String,
    val baseUrl: String = ""
)

/** Sends a question to the chosen AI service and returns its text reply. */
class AiClient {
    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .build()

    /** imageBase64 is optional JPEG data encoded as base64 text. */
    fun ask(settings: AiSettings, prompt: String, imageBase64: String? = null): String {
        return try {
            when (settings.type) {
                AiProviderType.GEMINI -> askGemini(settings, prompt, imageBase64)
                AiProviderType.ANTHROPIC -> askAnthropic(settings, prompt, imageBase64)
                AiProviderType.OPENAI_COMPATIBLE -> askOpenAi(settings, prompt, imageBase64)
            }
        } catch (e: Exception) {
            "AI error: ${e.message}"
        }
    }

    /** Send JSON to the service. Never include the API key in app logs. */
    private fun post(url: String, headers: Map<String, String>, json: JSONObject): String {
        val builder = Request.Builder()
            .url(url)
            .post(json.toString().toRequestBody("application/json".toMediaType()))
        headers.forEach { (name, value) -> builder.addHeader(name, value) }
        http.newCall(builder.build()).execute().use { resp ->
            val text = resp.body?.string().orEmpty()
            if (!resp.isSuccessful) throw IllegalStateException("HTTP ${resp.code}: ${text.take(300)}")
            return text
        }
    }

    /** Google Gemini uses the x-goog-api-key header. */
    private fun askGemini(s: AiSettings, prompt: String, image: String?): String {
        val parts = JSONArray()
        if (image != null) {
            parts.put(JSONObject().put("inline_data",
                JSONObject().put("mime_type", "image/jpeg").put("data", image)))
        }
        parts.put(JSONObject().put("text", prompt))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", parts)))
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${s.model}:generateContent"
        val raw = post(url, mapOf("x-goog-api-key" to s.apiKey), body)
        val partsOut = JSONObject(raw).getJSONArray("candidates").getJSONObject(0)
            .getJSONObject("content").getJSONArray("parts")
        return (0 until partsOut.length()).mapNotNull { i ->
            partsOut.getJSONObject(i).optString("text").takeIf { it.isNotBlank() }
        }.joinToString("\n").ifBlank { "The AI returned no text. Check the selected model and provider response." }
    }

    /** Anthropic Claude uses the x-api-key header. */
    private fun askAnthropic(s: AiSettings, prompt: String, image: String?): String {
        val content = JSONArray()
        if (image != null) {
            content.put(JSONObject().put("type", "image").put("source",
                JSONObject().put("type", "base64").put("media_type", "image/jpeg").put("data", image)))
        }
        content.put(JSONObject().put("type", "text").put("text", prompt))
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val headers = mapOf("x-api-key" to s.apiKey, "anthropic-version" to "2023-06-01")
        val raw = post("https://api.anthropic.com/v1/messages", headers, body)
        val parts = JSONObject(raw).getJSONArray("content")
        return (0 until parts.length()).mapNotNull { i ->
            parts.getJSONObject(i).optString("text").takeIf { it.isNotBlank() }
        }.joinToString("\n")
    }

    /** OpenAI-style endpoint: OpenAI, Groq, OpenRouter and other compatible services. */
    private fun askOpenAi(s: AiSettings, prompt: String, image: String?): String {
        val content = JSONArray()
        content.put(JSONObject().put("type", "text").put("text", prompt))
        if (image != null) {
            content.put(JSONObject().put("type", "image_url").put("image_url",
                JSONObject().put("url", "data:image/jpeg;base64,$image")))
        }
        val body = JSONObject()
            .put("model", s.model)
            .put("max_tokens", 600)
            .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
        val base = s.baseUrl.trimEnd('/').ifBlank { "https://api.openai.com/v1" }
        val raw = post("$base/chat/completions", mapOf("Authorization" to "Bearer ${s.apiKey}"), body)
        return JSONObject(raw).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("message").optString("content")
    }
}
